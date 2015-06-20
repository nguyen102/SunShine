package com.example.android.sunshine;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A placeholder fragment containing a simple view.
 */
public class ForecastFragment extends Fragment {
  ArrayAdapter<String> mForecastAdapter;
  public ForecastFragment() {
  }

  public void onCreate(Bundle savedInstanceState){
    super.onCreate(savedInstanceState);
    // Add this line in order for this fragment to handle menu events.
    //Initializes callback methods for menus (onCreateOptionsMenu AND onOptionsItemSelected)
    setHasOptionsMenu(true);
  }
  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    inflater.inflate(R.menu.menu_forecastfragment, menu);
  }

  @Override
  public void onStart() {
    super.onStart();
    updateWeather();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item){
    int id = item.getItemId();
    if (id == R.id.action_refresh){
      updateWeather();
      return true;
    }else if (id == R.id.action_settings){
      startActivity(new Intent(getActivity(), SettingsActivity.class));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private void updateWeather(){
    FetchWeatherTask weatherTask = new FetchWeatherTask();
    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
    String location = prefs.getString(getString(R.string.pref_location_key),
            getString(R.string.pref_location_default));
    weatherTask.execute(location);
  }
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_main, container, false);

    //Get the textView in the list_item_forecast xml file
    mForecastAdapter = new ArrayAdapter<String>(getActivity(),
            R.layout.list_item_forecast, R.id.list_item_forecast_textview, new ArrayList<String>());

    //Get the listView in the fragment xml file
    ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
    listView.setAdapter(mForecastAdapter);
    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String forecast = mForecastAdapter.getItem(position);
        Intent intent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent
                .EXTRA_TEXT, forecast);
        startActivity(intent);
      }
    });

    return rootView;
  }
  public class FetchWeatherTask extends AsyncTask<String, Void, String[]>{
    final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
    public String getReadableDateString(long time){
      SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
      return shortenedDateFormat.format(time);
    }


    private String formatHighLows(double high, double low, String unitType) {

      if (unitType.equals(getString(R.string.pref_units_imperial))) {
        high = (high * 1.8) + 32;
        low = (low * 1.8) + 32;
      } else if (!unitType.equals(getString(R.string.pref_units_metric))) {
        Log.d(LOG_TAG, "Unit type not found: " + unitType);
      }

      // For presentation, assume the user doesn't care about tenths of a degree.
      long roundedHigh = Math.round(high);
      long roundedLow = Math.round(low);

      String highLowStr = roundedHigh + "/" + roundedLow;
      return highLowStr;
    }


    private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
            throws JSONException {

      // These are the names of the JSON objects that need to be extracted.
      final String OWM_LIST = "list";
      final String OWM_WEATHER = "weather";
      final String OWM_TEMPERATURE = "temp";
      final String OWM_MAX = "max";
      final String OWM_MIN = "min";
      final String OWM_DESCRIPTION = "main";

      JSONObject forecastJson = new JSONObject(forecastJsonStr);
      JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);


      Time dayTime = new Time();
      dayTime.setToNow();

      // we start at the day returned by local time. Otherwise this is a mess.
      int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

      // now we work exclusively in UTC
      dayTime = new Time();

      String[] resultStrs = new String[numDays];
      SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
      String unitType = sharedPrefs.getString( getString(R.string.pref_units_key),
                                               getString(R.string.pref_units_metric));

      for(int i = 0; i < weatherArray.length(); i++) {
        // For now, using the format "Day, description, hi/low"
        String day;
        String description;
        String highAndLow;

        // Get the JSON object representing the day
        JSONObject dayForecast = weatherArray.getJSONObject(i);

        long dateTime;
        dateTime = dayTime.setJulianDay(julianStartDay+i);
        day = getReadableDateString(dateTime);

        JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
        description = weatherObject.getString(OWM_DESCRIPTION);

        JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
        double high = temperatureObject.getDouble(OWM_MAX);
        double low = temperatureObject.getDouble(OWM_MIN);

        highAndLow = formatHighLows(high, low, unitType);
        resultStrs[i] = day + " - " + description + " - " + highAndLow;
      }

      for (String s : resultStrs) {
        Log.v(LOG_TAG, "Forecast entry: " + s);
      }
      return resultStrs;

    }

    //forecastArray is the return value of doInBackground.
    //onPostExcute parameter is populated from return of doInBackground
    @Override
    protected void onPostExecute(String[] forecastArray) {
      List<String> weekForecast = new ArrayList<String>(Arrays.asList(forecastArray));
      //Get the textView in the list_item_forecast xml file
      mForecastAdapter.clear();
      for(String s : weekForecast){
        mForecastAdapter.add(s);
      }
    }

    //params is an array of strings, so is the return value
    @Override
    protected String[] doInBackground(String... params) {
      if (params.length == 0){
        return null;
      }
      String postalCode = params[0];
      HttpURLConnection urlConnection = null;
      BufferedReader reader = null;
      String forecastJsonStr = null;

      String format = "json";
      String units = "metric";
      int numDays = 7;
      try {
        // Construct the URL for the OpenWeatherMap query
        // Possible parameters are avaiable at OWM's forecast API page, at
        // http://openweathermap.org/API#forecast
        final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
        final String QUERY_PARAM = "q";
        final String FORMAT_PARAM = "mode";
        final String UNITS_PARAM = "units";
        final String DAYS_PARAM = "cnt";



        Uri builtUri = Uri.parse(FORECAST_BASE_URL).buildUpon()
                .appendQueryParameter(QUERY_PARAM, postalCode)
                .appendQueryParameter(FORMAT_PARAM, format)
                .appendQueryParameter(UNITS_PARAM, units)
                .appendQueryParameter(DAYS_PARAM, Integer.toString(numDays))
                .build();

        URL url = new URL(builtUri.toString());
        Log.v(LOG_TAG, "Weather API URI: " + url.toString());

        // Create the request to OpenWeatherMap, and open the connection
        urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.setRequestMethod("GET");
        urlConnection.connect();

        // Read the input stream into a String
        InputStream inputStream = urlConnection.getInputStream();
        StringBuffer buffer = new StringBuffer();
        if (inputStream == null) {
          // Nothing to do.
          return null;
        }
        reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        while ((line = reader.readLine()) != null) {
          // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
          // But it does make debugging a *lot* easier if you print out the completed
          // buffer for debugging.
          buffer.append(line + "\n");
        }

        if (buffer.length() == 0) {
          return null;
        }
        forecastJsonStr = buffer.toString();
      } catch (IOException e) {
        Log.e(LOG_TAG, "Error ", e);
        return null;
      } finally{
        if (urlConnection != null) {
          urlConnection.disconnect();
        }
        if (reader != null) {
          try {
            reader.close();
          } catch (final IOException e) {
            Log.e(LOG_TAG, "Error closing stream", e);
          }
        }
      }
      try {
        return getWeatherDataFromJson(forecastJsonStr, numDays);
      } catch (JSONException e) {
        Log.e(LOG_TAG, e.getMessage(), e);
        e.printStackTrace();
      }
      return null;
    }
  }
}
