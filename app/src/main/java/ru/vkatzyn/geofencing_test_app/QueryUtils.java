package ru.vkatzyn.geofencing_test_app;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;

public final class QueryUtils {
    private static String LOG_TAG = QueryUtils.class.getSimpleName();

    private QueryUtils() {
    }

    public static ArrayList<Geodata> fetchGeodata(String requestUrl) {
        URL url = createUrl(requestUrl);

        String jsonResponse = null;
        try {
            jsonResponse = makeHttpRequest(url);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not make HTTP request.", e);
        }

        ArrayList<Geodata> geofences = extractGeodata(jsonResponse);
        return geofences;
    }

    public static URL createUrl(String requestUrl) {
        URL url = null;
        try {
            url = new URL(requestUrl);
        } catch (MalformedURLException e) {
            Log.e(LOG_TAG, "Could not create URL from given String.", e);
        }

        return url;
    }

    public static String makeHttpRequest(URL url) throws IOException {
        String jsonResponse = "";

        if (url == null)
            return jsonResponse;

        HttpURLConnection urlConnection = null;
        InputStream inputStream = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setConnectTimeout(10000);
            urlConnection.setReadTimeout(15000);
            urlConnection.setRequestMethod("GET");
            urlConnection.connect();

            if (urlConnection.getResponseCode() == 200) {
                inputStream = urlConnection.getInputStream();
                jsonResponse = readFromStream(inputStream);
            } else {
                Log.e(LOG_TAG, "Error response code: " + urlConnection.getResponseCode());
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Problem retrieving the JSON results.", e);
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
            if (inputStream != null)
                inputStream.close();
        }
        return jsonResponse;
    }

    public static String readFromStream(InputStream inputStream) throws IOException {
        StringBuilder output = new StringBuilder();
        if (inputStream != null) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charset.forName("UTF-8"));
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            String line = bufferedReader.readLine();
            while (line != null) {
                output.append(line);
                line = bufferedReader.readLine();
            }
        }
        return output.toString();
    }

    public static ArrayList<Geodata> extractGeodata(String jsonResponse) {
        ArrayList<Geodata> geodataArrayList = new ArrayList<>();
        try {
            JSONObject jsonRootObject = new JSONObject(jsonResponse);
            JSONArray modules = jsonRootObject.optJSONArray("modules");

            for (int i = 0; i < modules.length(); i++) {
                JSONObject module = modules.getJSONObject(i);
                int type = module.getInt("type");
                if (type == 5) {
                    JSONObject info = module.getJSONObject("info");
                    JSONArray points = info.getJSONArray("points");
                    for (int j = 0; j < points.length(); j++) {
                        JSONObject point = points.getJSONObject(j);
                        double latitude = point.getDouble("lat");
                        double longitude = point.getDouble("lon");
                        float radius = (float)point.getDouble("radius");
                        int id = point.getInt("id");
                        Geodata geodata = new Geodata(latitude, longitude, radius, id);
                        geodataArrayList.add(geodata);
                    }
                }
            }
            return geodataArrayList;
        } catch (JSONException e) {
            Log.e(QueryUtils.class.getSimpleName(), "Problem parsing the earthquake JSON results", e);
        }
        return null;
    }
}
