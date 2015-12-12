package com.example.hajalie.goodvibes2;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Directions extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener, SensorEventListener {

    private final Context context = this;
    private boolean requestingLocationUpdates = false;
    private boolean finished = false;
    private JSONObject response;
    private Route route; // may need sync
    private Location currentLocation; // may need sync
    private String destination;
    private GoogleApiClient googleApiClient;
    private LocationRequest locationRequest;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private Sensor magnetometer;
    private float azimuth; // may need sync
    private float prevAzimuth = 0;
    private float bearing;
    private float direction;
    private Timer timer;
    private String information;
    TextToSpeech t1;
    protected static final int RESULT_SPEECH = 1;
    private TextView textView0, textView1, textView2, textView3, textView4;

    Arduino arduino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directions);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Good Vibes Code //
        // Initialization
        destination = getIntent().getExtras().getString("destination");
        currentLocation = (Location) getIntent().getExtras().get("location");
        try {
            response = new JSONObject(getIntent().getExtras().getString("response"));
            route = new Route(response); // Todo: Exits out of app when route is too long (too much memory?)
        } catch (JSONException e) {
            Log.e("GoodVibes", "JSON exception", e);
            Intent intent = new Intent(context, MainActivity.class);
            startActivity(intent);
        }

        // Set up Location Request to periodically update currentLocation
        locationRequest = new LocationRequest();
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(4000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        // Manage text views
        textView0 = (TextView) findViewById(R.id.direction_output);
        textView1 = (TextView) findViewById(R.id.direction_output1);
        textView2 = (TextView) findViewById(R.id.direction_output2);
        textView3 = (TextView) findViewById(R.id.direction_output3);
        textView4 = (TextView) findViewById(R.id.direction_output4);
        //swapped textviews so that info is on top
        textView1.setText(Html.fromHtml(route.getTargetStep().htmlInstructions));
        information = new String();
        information += "Location: " + route.endAddress + "." + "\n";
        information += "Distance: " + addUnit(route.distanceText) + "." + "\n";
        information += "Time: " + addUnit(route.durationText) + "." + "\n";
        textView0.setText(information);
        // Build Google API client
        googleApiClient = new GoogleApiClient.Builder(getApplicationContext())
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();


        // Manage sensors for orientation
        sensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);


        // Set up TextToSpeech
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                    t1.speak(information, TextToSpeech.QUEUE_ADD, null);
                }
            }
        });

        SharedPreferences preferences = getApplicationContext().getSharedPreferences("preferences", 0);
        //retrieve interval from settings, default value of 5 if not defined
        //vibrate every $_INTERVAL seconds
        float interval = preferences.getFloat("interval", 5.0f);
        Long intervalInMs = Math.round(((double) interval) * 1000);
        final Handler handler = new Handler();
        timer = new Timer();
        TimerTask doAsynchronousTask = new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @SuppressWarnings("unchecked")
                    public void run() {
                        try {
                            vibrate();
                        }
                        catch (Exception e) {
                            // TODO Auto-generated catch block
                        }
                    }
                });
            }
        };
        timer.schedule(doAsynchronousTask, 0, intervalInMs);

        //This code sets up input so that touching the screen causes the voice input to start
        LinearLayout linearlayout = (LinearLayout) findViewById(R.id.directions_layout);
        linearlayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                t1.speak("", TextToSpeech.QUEUE_FLUSH, null);
                Intent intent = new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");

                try {
                    t1.speak("", TextToSpeech.QUEUE_FLUSH, null);
                    startActivityForResult(intent, RESULT_SPEECH);
                } catch (ActivityNotFoundException a) {
                    Toast t = Toast.makeText(getApplicationContext(),
                            "Opps! Your device doesn't support Speech to Text",
                            Toast.LENGTH_SHORT);
                    t.show();
                }
            }
        });

        //initialize Arduiono class
        arduino = new Arduino(getApplicationContext());
        arduino.write("hello");
    }

    /**
     *
     * @param str
     * @return
     *
     * Takes the string with an abbreviation at the end converts it to the full word
     * Example: "2.8 mi" returns "2.8 miles"
     * TODO: Possible add more units
     */
    private String addUnit(String str) {
        String unit = str.substring(str.length() - 2, str.length());
        String unit2 = str.substring(str.length() - 3, str.length());
        if (unit.equals("mi")) {
            return str + "les";
        }
        else if (unit.equals("ft")) {
            return str.substring(0, str.length()-1) + "eet";
        }
        else if (unit2.equals("min")) {
            return str + "utes";
        }
        else {
            return str;
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SPEECH: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> text = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String response = text.get(0);
                    if(MainActivity.containsWord(response, "cancel")) {
                        t1.speak("Canceling directions", TextToSpeech.QUEUE_FLUSH, null);
                        while(t1.isSpeaking()) {
                            SystemClock.sleep(100);
                        }
                        finish();
                    }
                }
                break;
            }

        }
    }

    // Information for each step along the route
    private class Step {
        String htmlInstructions;
        double startLat, startLng;
        double endLat, endLng;
        long durationValue;
        String durationText;
        long distanceValue;
        String distanceText;

        Step (JSONObject step) throws JSONException {
            htmlInstructions = step.getString("html_instructions");
            JSONObject object;
            object = step.getJSONObject("start_location");
            startLat = object.getDouble("lat");
            startLng = object.getDouble("lng");
            object = step.getJSONObject("end_location");
            endLat = object.getDouble("lat");
            endLng = object.getDouble("lng");
            object = step.getJSONObject("duration");
            durationValue = object.getLong("value");
            durationText = object.getString("text");
            object = step.getJSONObject("distance");
            distanceValue = object.getLong("value");
            distanceText = object.getString("text");
        }
    }

    // Information for the route to the destination
    // Set to the first route returned by the API call
    // Assumes no waypoints (single leg)
    private class Route {
        String startAddress;
        String endAddress;
        double startLat, startLng;
        double endLat, endLng;
        long durationValue;
        String durationText;
        long distanceValue;
        String distanceText;
        ArrayList<Step> steps;
        int numSteps;
        int currentStep;

        Route (JSONObject response) throws JSONException {
            JSONArray routes;
            JSONObject route;
            routes = response.getJSONArray("routes");
            route = routes.getJSONObject(0);
            JSONArray legs;
            JSONObject leg;
            legs = route.getJSONArray("legs");
            leg = legs.getJSONObject(0);
            startAddress = leg.getString("start_address");
            endAddress = leg.getString("end_address");
            JSONObject object;
            object = leg.getJSONObject("start_location");
            startLat = object.getDouble("lat");
            startLng = object.getDouble("lng");
            object = leg.getJSONObject("end_location");
            endLat = object.getDouble("lat");
            endLng = object.getDouble("lng");
            object = leg.getJSONObject("duration");
            durationValue = object.getLong("value");
            durationText = object.getString("text");
            object = leg.getJSONObject("distance");
            distanceValue = object.getLong("value");
            distanceText = object.getString("text");
            JSONArray steps = leg.getJSONArray("steps");
            JSONObject step;
            this.steps = new ArrayList<Step>();
            for (int i = 0; i < steps.length(); i++) {
                step = steps.getJSONObject(i);
                Step newStep = new Step(step);
                this.steps.add(newStep);
            }
            numSteps = this.steps.size();
            currentStep = 0;
        }

        Location getTargetLocation () {
            if (currentStep < numSteps) {
                Location location = new Location("");
                location.setLatitude(steps.get(currentStep).endLat);
                location.setLongitude(steps.get(currentStep).endLng);
                return location;
            }
            return null;
        }

        Step getTargetStep () {
            if (currentStep < numSteps) {
                return steps.get(currentStep);
            }
            return null;
        }

        void incrementTargetLocation () {
            currentStep++;
        }

        boolean arrivedAtDestination () {
            return currentStep >= numSteps;
        }
    }

    private void changeCurrentLocation(Location newLocation) {
        // Update location
        currentLocation = newLocation;
        Location targetLocation = route.getTargetLocation();

        // Check if reached target location
        String distance = "Distance: " + newLocation.distanceTo(targetLocation) + "\n";
        distance += Html.fromHtml(route.getTargetStep().htmlInstructions);
        textView1.setText(distance);
        if (newLocation.distanceTo(targetLocation) < Values.LOCATION_BUFFER) {
            route.incrementTargetLocation();
            targetLocation = route.getTargetLocation();
            distance = "Distance: " + newLocation.distanceTo(targetLocation) + "\n";
            distance += Html.fromHtml(route.getTargetStep().htmlInstructions);
            textView1.setText(distance);
            vibrate();
            // Check if arrived at destination
            if (route.arrivedAtDestination()) {
                // Stop giving directions
                if (googleApiClient.isConnected() && requestingLocationUpdates) {
                    stopLocationUpdates();
                }
                sensorManager.unregisterListener(this);
                Toast toast = Toast.makeText(context, Values.ARRIVE_AT_DESTINATION, Toast.LENGTH_SHORT);
                toast.show();
                finished = true;
                return;
            }
        }

        // Determine bearings and vibrate in desired direction
        bearing = currentLocation.bearingTo(targetLocation);
        if (bearing < 0) {
            bearing += 360;
        }
        bearing = bearing * (float)Math.PI / 180;
        direction = bearing - azimuth;
        if (direction < 0) {
            direction += 2 * (float)Math.PI;
        }
        // Todo: Improve accuracy - azimuth measurement, and true vs. magnetic north
        textView3.setText("Bearing: " + Float.toString(bearing));
        textView4.setText("Angle to point: " + Float.toString(direction));

        // Todo: Make new API request if necessary
//        try {
//            GoogleApi.getInstance(context).makeDirectionsHttpRequest(currentLocation, destination, new Response.Listener<JSONObject>() {
//                @Override
//                public void onResponse(JSONObject response) {
//                    String status = "";
//                    try {
//                        status = response.getString("status");
//                    } catch(JSONException e) {
//                        Log.e("GoodVibes", "JSON exception", e);
//                    }
//                    if (status.equals("OK")) {
//                        response = response;
//                    }
//                }
//            }, new Response.ErrorListener() {
//                @Override
//                public void onErrorResponse(VolleyError error) {
//
//                }
//            });
//        } catch (UnsupportedEncodingException e) {
//            Log.e("GoodVibes", "Unsupported Encoding exception", e);
//        }
    }

    @Override
    public void onConnected(Bundle bundle) {
        startLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void onLocationChanged(Location location) {
        changeCurrentLocation(location);
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(
                googleApiClient, locationRequest, this);
        requestingLocationUpdates = true;
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(
                googleApiClient, this);
        requestingLocationUpdates = false;
    }

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onStop() {
        super.onStop();
        timer.cancel();
        t1.stop();
        googleApiClient.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (googleApiClient.isConnected() && requestingLocationUpdates) {
            stopLocationUpdates();
        }
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!finished) {
            if (googleApiClient.isConnected() && !requestingLocationUpdates) {
                startLocationUpdates();
            }
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    float[] mGravity;
    float[] mGeomagnetic;
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;
        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            boolean success = SensorManager.getRotationMatrix(R, null, mGravity, mGeomagnetic);
            if (success) {
                float orientation[] = new float[3];
                SensorManager.getOrientation(R, orientation);
                // orientation contains: azimuth, pitch and roll
                if (orientation[0] < 0) {
                    azimuth = 2 * (float)Math.PI + orientation[0];
                } else {
                    azimuth = orientation[0];
                }
                //if change in orientation is big vibrate
                //TODO: make accurate for final release
                textView2.setText("Compass Orientation: "+ Float.toString(azimuth));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void vibrate() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(800);
    }
}
