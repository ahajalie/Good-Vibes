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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class Directions extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener, SensorEventListener {

    private final Context context = this;
    private final Object directionLock = new Object();
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
    private ArrayList<Float> azimuthList;
    private float bearing;
    private float direction;
    private Timer timer;
    private String information;
    private Float bestAccuracy;
    private Float minDistanceToNextPoint;
    private boolean paused = false;
    TextToSpeech t1;
    protected static final int RESULT_SPEECH = 1;
    private TextView textView0, textView1, textView2, textView3, textView4, textView5, textView6;

    Arduino arduino;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_directions);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //keep te screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Good Vibes Code //
        // Initialization
        bestAccuracy = null; // on new http
        minDistanceToNextPoint = Float.MAX_VALUE; // on new http

        destination = getIntent().getExtras().getString("destination");
        currentLocation = (Location) getIntent().getExtras().get("location");
        try {
            response = new JSONObject(getIntent().getExtras().getString("response"));
            route = new Route(response); // Todo: Exits out of app when route is too long (too much memory?)
        } catch (JSONException e) {
            Log.e("GoodVibes", "JSON exception", e);
            finish();
        }

        // Set up Location Request to periodically update currentLocation
        locationRequest = new LocationRequest();
        locationRequest.setInterval(2500);
        locationRequest.setFastestInterval(2000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);



        // Manage text views
        textView0 = (TextView) findViewById(R.id.direction_output);
        textView1 = (TextView) findViewById(R.id.direction_output1);
        textView2 = (TextView) findViewById(R.id.direction_output2);
        textView3 = (TextView) findViewById(R.id.direction_output3);
        textView4 = (TextView) findViewById(R.id.direction_output4);
        textView5 = (TextView) findViewById(R.id.direction_output5);
        textView6 = (TextView) findViewById(R.id.direction_output6);
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
        azimuthList = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            azimuthList.add(0f);
        }
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

        //retrieve interval from settings, default value of 5 if not defined
        //vibrate every $_INTERVAL seconds
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("preferences", 0);
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
                        if (!paused) {
                            try {
                                vibrateBeltTowardNextPoint();
                            }
                            catch (Exception e) {
                                // TODO Auto-generated catch block
                            }
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
                            "Oops! Your device doesn't support Speech to Text",
                            Toast.LENGTH_SHORT);
                    t.show();
                }
            }
        });

        //initialize Arduino class
        arduino = new Arduino(getApplicationContext());
        arduino.write("hello");
    }

    public void vibrateBeltTowardNextPoint() {
        int vibeDir;
        synchronized (directionLock) {
            direction = bearing - getAvgAzimuth(azimuthList); // Todo: True vs. magnetic north
            if (direction < 0) {
                direction += 2 * (float) Math.PI;
            }
            vibeDir = (int) ((direction + (Math.PI / 8)) / (Math.PI / 4));
            if (vibeDir < 0 || vibeDir > 8) vibrateBelt(Values.ALL_DIRECTIONS);
            textView5.setText("Angle to point: " + Float.toString(direction));
        }
        switch (vibeDir) {
            case (0):
                vibrateBelt(Values.FRONT);
                break;
            case (1):
                vibrateBelt(Values.FRONT_RIGHT);
                break;
            case (2):
                vibrateBelt(Values.RIGHT);
                break;
            case (3):
                vibrateBelt(Values.BACK_RIGHT);
                break;
            case (4):
                vibrateBelt(Values.BACK);
                break;
            case (5):
                vibrateBelt(Values.BACK_LEFT);
                break;
            case (6):
                vibrateBelt(Values.LEFT);
                break;
            case (7):
                vibrateBelt(Values.FRONT_LEFT);
                break;
            case (8):
                vibrateBelt(Values.FRONT);
                break;
        }
    }

    // Vibrate belt in specific direction
    public void vibrateBelt(Integer vibeDir) {
        vibrate();// Todo: delete
        arduino.write(vibeDir.toString());
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
        double endLat, endLng;

        Step (JSONObject step) throws JSONException {
            htmlInstructions = step.getString("html_instructions");
            JSONObject object = step.getJSONObject("end_location");
            endLat = object.getDouble("lat");
            endLng = object.getDouble("lng");
        }
    }

    // Information for the route to the destination
    // Set to the first route returned by the API call
    // Assumes no waypoints (single leg)
    private class Route {
        String endAddress;
        String durationText;
        String distanceText;
        JSONArray steps;
        Step step;
        int numSteps;
        int currentStep;

        Route (JSONObject response) throws JSONException {
            endAddress = response.getString("end_address");
            durationText = response.getJSONObject("duration").getString("text");
            distanceText = response.getJSONObject("distance").getString("text");
            steps = response.getJSONArray("steps");
            step = new Step(steps.getJSONObject(0));
            numSteps = steps.length();
            currentStep = 0;
        }

        Location getTargetLocation () {
            if (step != null) {
                Location location = new Location("");
                location.setLatitude(step.endLat);
                location.setLongitude(step.endLng);
                return location;
            }
            return null;
        }

        Step getTargetStep () {
            return step;
        }

        void incrementTargetLocation () {
            try {
                currentStep++;
                if (currentStep < numSteps) {
                    step = new Step(steps.getJSONObject(currentStep));
                } else {
                    step = null;
                }
            } catch (JSONException e) {
                Log.e("GoodVibes", "JSON exception", e);
                Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
                t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
                toast.show();
            }

        }

        boolean arrivedAtDestination () {
            return currentStep >= numSteps;
        }
    }

    private void changeCurrentLocation(Location newLocation) {
        // Update location
        currentLocation = newLocation;
        Location targetLocation = route.getTargetLocation();

        textView2.setText("Distance: " + newLocation.distanceTo(targetLocation));
        float distanceToNextPoint = newLocation.distanceTo(targetLocation);
        if (distanceToNextPoint < minDistanceToNextPoint) {
            minDistanceToNextPoint = distanceToNextPoint;
        }

        // Check if reached target location
        if (distanceToNextPoint < Values.LOCATION_BUFFER) {
            route.incrementTargetLocation();
            if (route.arrivedAtDestination()) { // Arrived at final location
                timer.cancel();
                if (googleApiClient.isConnected() && requestingLocationUpdates) {
                    stopLocationUpdates();
                }
                sensorManager.unregisterListener(this);
                Toast toast = Toast.makeText(context, Values.ARRIVE_AT_DESTINATION, Toast.LENGTH_SHORT);
                toast.show();
                vibrateBelt(Values.ALL_DIRECTIONS);
                finished = true;
                return;
            } else { // Did not arrive at final location
                targetLocation = route.getTargetLocation();
                textView2.setText("Distance: " + newLocation.distanceTo(targetLocation));
                textView1.setText(Html.fromHtml(route.getTargetStep().htmlInstructions));
                minDistanceToNextPoint = Float.MAX_VALUE;
                vibrateBeltTowardNextPoint();
            }
        }

        // Update bearing
        updateBearing(targetLocation);

        // Make new API request if necessary
        if (bestAccuracy == null) {
            bestAccuracy = newLocation.getAccuracy();
        } else if (newLocation.getAccuracy() < bestAccuracy - 10) {
            bestAccuracy = newLocation.getAccuracy();
            // Todo: MAKE NEW HTTP REQUEST HERE ALIE
            redoRequest();
            // Make sure it has time to finish request and construct data structures, or else
            //   multithreaded access to shared data = must synchronize
            // Also there are a shitton of state variables you have to update
        } else if (distanceToNextPoint - minDistanceToNextPoint > 50) {
            // Todo: MAKE NEW HTTP REQUEST HERE TOO ALIE
            redoRequest();
        }
    }

    private void updateBearing(Location targetLocation) {
        // Determine bearings
        synchronized (directionLock) {
            bearing = currentLocation.bearingTo(targetLocation);
            if (bearing < 0) {
                bearing += 360;
            }
            bearing = bearing * (float) Math.PI / 180;
            textView4.setText("Bearing: " + Float.toString(bearing));
        }
    }

    void redoRequest() {
        try {
            GoogleApi.getInstance(this).makeDirectionsHttpRequest(currentLocation, destination, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject newResponse) {
                    String status = "";
                    try {
                        status = newResponse.getString("status");
                    } catch(JSONException e) {
                        Log.e("GoodVibes", "JSON exception", e);
                        Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
                        t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
                        toast.show();
                    }
                    switch (status) {
                        case "OK":
                            response = newResponse;
                            try {
                                route = new Route(response);
                            }
                            catch(JSONException e) {
                                Log.e("GoodVibes", "JSON exception", e);
                                Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
                                t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
                                toast.show();
                            }
                            updateBearing(route.getTargetLocation());
                            bestAccuracy = null;
                            minDistanceToNextPoint = Float.MAX_VALUE;
                            information = new String();
                            information += "Location: " + route.endAddress + "." + "\n";
                            information += "Distance: " + addUnit(route.distanceText) + "." + "\n";
                            information += "Time: " + addUnit(route.durationText) + "." + "\n";
                            textView0.setText(information);
                            break;
                        case "NOT_FOUND":
                        default:
                            Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT).show();
                            t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
                            break;
                    }
                }
            }, new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
                    t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
                    toast.show();
                }
            });
        } catch (UnsupportedEncodingException e) {
            Log.e("GoodVibes", "Unsupported Encoding exception", e);
            Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
            t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
            toast.show();
        }
    }

    float getAvgAzimuth(ArrayList<Float> azimuthList) {
        synchronized (azimuthList) {
            float avg = azimuthList.get(0);
            float num = 1;
            for(int i = 1; i < azimuthList.size(); i++) {
                float azimuth = azimuthList.get(i);
                if ((avg > Math.PI * 3/2 || avg < Math.PI * 1/2)
                        && (azimuth > Math.PI * 3/2 || azimuth < Math.PI * 1/2)) {
                    if (avg > Math.PI * 3/2) avg -= Math.PI * 2;
                    if (azimuth > Math.PI * 3/2) azimuth -= Math.PI * 2;
                }
                avg = num * avg / (num + 1) + azimuth / (num + 1);
                num += 1;
            }
            if (avg < 0) {
                avg += Math.PI * 2;
            }
            textView6.setText("Avg Compass Orientation: " + Float.toString(avg));
            return avg;
        }
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
        t1.stop();
        googleApiClient.disconnect();
    }


    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
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
        paused = false;
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
                float azimuth = 0f;
                if (orientation[0] < 0) {
                    azimuth = 2 * (float)Math.PI + orientation[0];
                } else {
                    azimuth = orientation[0];
                }
                //if change in orientation is big vibrate
                //TODO: make accurate for final release
                synchronized (azimuthList) {
                    azimuthList.remove(azimuthList.size() - 1);
                    azimuthList.add(0, azimuth);
                    textView3.setText("Compass Orientation: " + Float.toString(azimuth));
                }
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
