package com.example.hajalie.goodvibes2;

import android.content.Context;
import android.location.Location;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by Carl on 11/14/2015.
 */

// Singleton class - access methods with getInstance()
public class GoogleApi {

    private final String DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json?origin=";
    private static RequestQueue requestQueue = null;
    private static GoogleApi instance = null;

    private GoogleApi() {
    }

    public static GoogleApi getInstance(Context context) {
        if (instance == null) {
            requestQueue = Volley.newRequestQueue(context.getApplicationContext());
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
        String url = DIRECTIONS_API_URL + location.getLatitude() + "," + location.getLongitude();
        String destinationEncoded = URLEncoder.encode(destination, "UTF-8");
        url += "&destination=" + destinationEncoded;
        url += "&mode=walking";
        // Request a string response from the provided URL.
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
                responseListener, errorListener);
        // Add the request to the RequestQueue.
        requestQueue.add(jsonObjectRequest);
    }
}
