package com.example.hajalie.goodvibes2;

import android.content.Context;
import android.location.Location;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by Carl on 11/14/2015.
 */

// Singleton class - access methods with getInstance()
public class GoogleApi {

    private final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json?origin=";
    private final String PLACES_API_URL = "https://maps.googleapis.com/maps/api/place/textsearch/json?location=";
    private final String API_KEY = "AIzaSyCKIzrvm1-Xg9C7LJcp5xQp50IB5JzhtjA";
    String directionsURL;
    private static RequestQueue requestQueue = null;
    private static RequestQueue requestQueue0 = null;
    private String coordinates = "NULL";
    private static GoogleApi instance = null;

    private GoogleApi() {
    }

    public static GoogleApi getInstance(Context context) {
        if (instance == null) {
            requestQueue = Volley.newRequestQueue(context.getApplicationContext());
            requestQueue0 = Volley.newRequestQueue(context.getApplicationContext());
            instance = new GoogleApi();
        }
        return instance;
    }

    void makeDirectionsHttpRequest(final Location location,
                                   final String destination,
                                   final Response.Listener<JSONObject> responseListener,
                                   final Response.ErrorListener errorListener)
            throws UnsupportedEncodingException {
        // Called when a new location is found by the network location provider.
        directionsURL = DIRECTIONS_API_URL + location.getLatitude() + "," + location.getLongitude();
        String destinationEncoded = URLEncoder.encode(destination, "UTF-8");
        //directionsURL += "&destination=" + destinationEncoded;
        directionsURL += "&mode=walking";
        directionsURL += "&destination=" + destination;
        // Request a string response from the provided URL.
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, directionsURL, null,
                responseListener, errorListener);
        // Add the request to the RequestQueue.
        requestQueue.add(jsonObjectRequest);
    }

    /*
    * Returns a string that contains the coordinates for the place
    * */
    public String makePlacesAPIRequest(final Location location, final String destination,
                                       final Response.Listener<JSONObject> responseListener,
                                       final Response.ErrorListener errorListener) throws UnsupportedEncodingException {
        String destinationEncoded = URLEncoder.encode(destination, "UTF-8");
        String placesURL = PLACES_API_URL + location.getLatitude() + "," + location.getLongitude();
        placesURL += "&query=" + destinationEncoded;
        placesURL += "&radius=1600";
        placesURL += "&key=" + API_KEY;
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, placesURL, null,
                responseListener, errorListener);
        // Add the request to the RequestQueue.
//        requestQueue.add(jsonObjectRequest);
//        JsonObjectRequest jsonObjectRequest0 = new JsonObjectRequest(Request.Method.GET, placesURL, null,
//                new Response.Listener<JSONObject>() {
//                    @Override
//                    public void onResponse(JSONObject response) {
//                        try {
//                            JSONObject location = response.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
//                            coordinates = location.getString("lat") + "," + location.getString("lng");
//
//                        } catch (JSONException e) {
//                            coordinates = "NULL";
//
//
//                        }
//                    }
//                }, new Response.ErrorListener() {
//            @Override
//            public void onErrorResponse(VolleyError error) {
//                VolleyLog.e("Error: ", error.getMessage());
//                coordinates = "NULL";
//            }
//        });
        requestQueue.add(jsonObjectRequest);
        Log.d("PLACESAPI" , "coordinates: " + coordinates);
        return coordinates;
    }
}
