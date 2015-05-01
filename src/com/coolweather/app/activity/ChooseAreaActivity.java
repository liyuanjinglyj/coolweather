package com.coolweather.app.activity;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.app.db.CoolWeatherDB;
import com.coolweather.app.model.City;
import com.coolweather.app.model.County;
import com.coolweather.app.model.Province;
import com.coolweather.app.util.HttpCallbackListener;
import com.coolweather.app.util.HttpUtil;
import com.coolweather.app.util.Utility;
import com.example.coolweather.R;

public class ChooseAreaActivity extends Activity {
	public static final int LEVEL_PROVINCE = 0;
	public static final int LEVEL_CITY = 1;
	public static final int LEVEL_COUNTY = 2;
	private List<Province> provinceList;
	private List<City> cityList;
	private List<County> countyList;
	private Province selectProvince;
	private City selectCity;
	private County selectCounty;
	private ListView listView;
	private TextView titleText;
	private List<String> dataList = new ArrayList<String>();
	private ArrayAdapter<String> adapter;
	private CoolWeatherDB coolWeatherDB;
	private int currentlevel;
	private ProgressDialog progressDialog;
	private boolean isFromWeatherActivity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		this.isFromWeatherActivity = getIntent().getBooleanExtra(
				"from_weather_activity", false);
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		if (prefs.getBoolean("city_selected", false) && !isFromWeatherActivity) {
			Intent intent = new Intent(this, WeatherActivity.class);
			startActivity(intent);
			finish();
			return;
		}
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.choose_area);
		this.listView = (ListView) findViewById(R.id.list_view);
		this.titleText = (TextView) findViewById(R.id.title_text);
		this.adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, dataList);
		this.listView.setAdapter(adapter);
		this.coolWeatherDB = CoolWeatherDB.getInstance(this);
		this.listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				if (currentlevel == LEVEL_PROVINCE) {
					selectProvince = provinceList.get(position);
					queryCities();
				} else if (currentlevel == LEVEL_CITY) {
					selectCity = cityList.get(position);
					queryCounties();
				} else if (currentlevel == LEVEL_COUNTY) {
					String countyCode = countyList.get(position)
							.getCountyCode();
					Intent intent = new Intent(ChooseAreaActivity.this,
							WeatherActivity.class);
					intent.putExtra("county_code", countyCode);
					startActivity(intent);
					finish();
				}
			}
		});
		queryProvinces();
	}

	/**
	 * 查询全国省数据
	 */
	private void queryProvinces() {
		provinceList = coolWeatherDB.loadProvinces();
		if (provinceList.size() > 0) {
			dataList.clear();
			for (Province province : provinceList) {
				dataList.add(province.getProvinceName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText("中国");
			currentlevel = LEVEL_PROVINCE;
		} else {
			this.queryFromServer(null, "province");
		}
	}

	/**
	 * 查询所有城市数据
	 */
	private void queryCities() {
		cityList = coolWeatherDB.loadCities(selectProvince.getId());
		if (cityList.size() > 0) {
			dataList.clear();
			for (City city : cityList) {
				dataList.add(city.getCityName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectProvince.getProvinceName());
			currentlevel = LEVEL_CITY;
		} else {
			this.queryFromServer(selectProvince.getProvinceCode(), "city");
		}
	}

	/**
	 * 查询所有县级数据
	 */
	private void queryCounties() {
		countyList = coolWeatherDB.loadCounties(selectCity.getId());
		if (countyList.size() > 0) {
			dataList.clear();
			for (County county : countyList) {
				dataList.add(county.getCountyName());
			}
			adapter.notifyDataSetChanged();
			listView.setSelection(0);
			titleText.setText(selectCity.getCityName());
			currentlevel = LEVEL_COUNTY;
		} else {
			this.queryFromServer(selectCity.getCityCode(), "county");
		}
	}

	/**
	 * 根据代号查询城市数据
	 * 
	 * @param code
	 * @param type
	 */
	private void queryFromServer(final String code, final String type) {
		String address;
		if (!TextUtils.isEmpty(code)) {
			address = "http://www.weather.com.cn/data/list3/city" + code
					+ ".xml";
		} else {
			address = "http://www.weather.com.cn/data/list3/city.xml";
		}
		this.showProgressDialog();
		HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {

			@Override
			public void onFinish(String response) {
				boolean result = false;
				if ("province".equals(type)) {
					result = Utility.handleProvinceResponse(coolWeatherDB,
							response);
				} else if ("city".equals(type)) {
					result = Utility.handleCitiesResponse(coolWeatherDB,
							response, selectProvince.getId());
				} else if ("county".equals(type)) {
					result = Utility.handleCountiesResponse(coolWeatherDB,
							response, selectCity.getId());
				}
				if (result) {
					runOnUiThread(new Runnable() {

						@Override
						public void run() {
							closeProgressDialog();
							if ("province".equals(type)) {
								queryProvinces();
							} else if ("city".equals(type)) {
								queryCities();
							} else if ("county".equals(type)) {
								queryCounties();
							}
						}
					});
				}
			}

			@Override
			public void onError(Exception e) {
				runOnUiThread(new Runnable() {

					@Override
					public void run() {
						closeProgressDialog();
						Toast.makeText(ChooseAreaActivity.this, "加载失败",
								Toast.LENGTH_SHORT).show();
					}
				});
			}
		});
	}

	/**
	 * 打开进度条
	 */
	private void showProgressDialog() {
		if (progressDialog == null) {
			progressDialog = new ProgressDialog(this);
			progressDialog.setMessage("正在加载....");
			progressDialog.setCanceledOnTouchOutside(false);
		}
		progressDialog.show();
	}

	/**
	 * 关闭进度条
	 */
	private void closeProgressDialog() {
		if (this.progressDialog != null) {
			this.progressDialog.dismiss();
		}
	}

	@Override
	public void onBackPressed() {
		if (currentlevel == LEVEL_COUNTY) {
			queryCities();
		} else if (currentlevel == LEVEL_CITY) {
			queryProvinces();
		} else {
			if (isFromWeatherActivity) {
				Intent intent = new Intent(this, WeatherActivity.class);
				startActivity(intent);
			}
			finish();
		}
	}

}
