package com.example.hajalie.goodvibes2;

import android.app.ActionBar;
import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Locale;


/*Google Maps API KEY
*  AIzaSyBYxrhTq9VBSEHp3OHR1VUH9BN5sAtMGXY
*
* Example API Request
*/
public class MainActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private ImageButton mainImageButton;
    private TextView txtText;
    Arduino arduino;
    TextToSpeech t1;
    protected static final int RESULT_SPEECH = 1;
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private boolean noErrors; //determine what to say on startup
    private BluetoothAdapter mBluetoothAdapter;
    private final Context context = this;
    private EditText editText;
    private GoogleApiClient googleApiClient = null;
    private boolean connected = false;
    private float originalLocationAccuracy;
    private boolean fromSpeaking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

        arduino = new Arduino(getApplicationContext());
        LinearLayout ll = (LinearLayout) findViewById(R.id.testButtonsLayout);
        LinearLayout ll2 = (LinearLayout) findViewById(R.id.testLine);
        LinearLayout mainlayout = (LinearLayout) findViewById(R.id.mainLayout);
        ll.setVisibility(LinearLayout.GONE);
        ll2.setVisibility(LinearLayout.GONE);
        mainlayout.setVisibility(LinearLayout.GONE);
        mainlayout.setVisibility(LinearLayout.VISIBLE);

//        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        preferences = getApplicationContext().getSharedPreferences("preferences", 0);
        editor = preferences.edit();
        mainImageButton = (ImageButton) findViewById(R.id.main_image_button);
        txtText = (TextView) findViewById(R.id.direction_input);
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                    noErrors = true;
//                    checkBluetooth();
                    if(!isNetworkConnected()) {
                        noErrors = false;
                        t1.speak("Please enable WI-FI or network connectivity and try again.", TextToSpeech.QUEUE_ADD, null);
                    }
                    if(!isGPSEnabled()) {
                        noErrors = false;
                        t1.speak("GPS is disabled. Please enable GPS.", TextToSpeech.QUEUE_ADD, null);
                    }
                    if(noErrors) {
                        t1.speak("Please tap the screen and speak your destination. Ask for help for more details.", TextToSpeech.QUEUE_ADD, null);
                    }

                }
            }
        });

        View.OnClickListener listener = new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                fromSpeaking = true;
                Intent intent = new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");

                try {
                    t1.speak("", TextToSpeech.QUEUE_FLUSH, null);
                    startActivityForResult(intent, RESULT_SPEECH);
                    txtText.setText("");
                } catch (ActivityNotFoundException a) {
                    Toast t = Toast.makeText(getApplicationContext(),
                            "Oops! Your device doesn't support Speech to Text",
                            Toast.LENGTH_SHORT);
                    t.show();
                }
            }
        };
        mainImageButton.setOnClickListener(listener);
        mainlayout.setOnClickListener(listener);
    }

    // Vibrate the belt in a specific direction
    public void vibrateBelt(View view) {
        Integer vibeDir = -1;
        switch (view.getId()) {
            case (R.id.vib0):
                vibeDir = Values.FRONT;
                break;
            case (R.id.vib1):
                vibeDir = Values.FRONT_RIGHT;
                break;
            case (R.id.vib2):
                vibeDir = Values.RIGHT;
                break;
            case (R.id.vib3):
                vibeDir = Values.BACK_RIGHT;
                break;
            case (R.id.vib4):
                vibeDir = Values.BACK;
                break;
            case (R.id.vib5):
                vibeDir = Values.BACK_LEFT;
                break;
            case (R.id.vib6):
                vibeDir = Values.LEFT;
                break;
            case (R.id.vib7):
                vibeDir = Values.FRONT_LEFT;
                break;
        }
        int temp = arduino.write(vibeDir.toString());
        if(temp == -1) {
            arduino = new Arduino(getApplicationContext());
            t1.speak("Arduino failure", TextToSpeech.QUEUE_ADD, null);
        }
        t1.speak(Integer.toString(temp), TextToSpeech.QUEUE_ADD, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case RESULT_SPEECH: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> text = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    String response = text.get(0);
                    txtText.setText(text.get(0));
                    processVoice(response);
                }
                break;
            }

        }
    }

    private boolean isNetworkConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        return (cm.getActiveNetworkInfo() != null) && cm.getActiveNetworkInfo().isConnectedOrConnecting();
    }

    public boolean isInternetAvailable() {
        try {
            InetAddress ipAddr = InetAddress.getByName("google.com"); //You can replace it with your name

            if (ipAddr.equals("")) {
                return false;
            } else {
                return true;
            }

        } catch (Exception e) {
            return false;
        }

    }

    private boolean isGPSEnabled (){
        LocationManager locationManager = (LocationManager)
                getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void checkBluetooth() {
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
            noErrors = false;
            t1.speak("Your device does not support blue tooth,, please use another device", TextToSpeech.QUEUE_ADD, null);
        } else {
            if (!mBluetoothAdapter.isEnabled()) {
                // Bluetooth is not enable :)
                noErrors = false;
                t1.speak("Blue tooth is not enabled. Please enable blue tooth", TextToSpeech.QUEUE_ADD, null);
            }
        }
    }

    private void processVoice(String str) {
        String string = str.toLowerCase();
        string = string.replaceAll("[!?,]", "");
        String[] strings = string.split("\\s+");
        if(containsWord(str, "interval")) {
            float interval = containsNumber(str);
            if(interval >= 0) {
                if(interval > 50) {
                    interval = 50;
                }
                if(interval < 1) {
                    interval = 1;
                }
                editor.putFloat("interval", interval);
                editor.commit();
                t1.speak("Interval set to " + Float.toString(interval) + "seconds", TextToSpeech.QUEUE_FLUSH, null);
            }
            else {
                t1.speak("Invalid interval value, please try again", TextToSpeech.QUEUE_FLUSH, null);
            }
        }
//        else if(containsWord(str, "intensity")) {
//            float intensity = containsNumber(str);
//            if(intensity >= 0) {
//                editor.putFloat("intensity", intensity);
//                editor.commit();
//                t1.speak("Intensity set to " + Float.toString(intensity), TextToSpeech.QUEUE_FLUSH, null);
//            }
//            else {
//                t1.speak("Invalid intensity value, please try again", TextToSpeech.QUEUE_FLUSH, null);
//            }
//        }
        else if(containsWord(str, "help")) {
            t1.speak("Tap the screen and say your destination", TextToSpeech.QUEUE_FLUSH, null);
            t1.speak("You can also change the interval time between vibrations", TextToSpeech.QUEUE_ADD, null);
            t1.speak("Once the directions have loaded, you can tap the screen and say cancel to cancel directions", TextToSpeech.QUEUE_ADD, null);

        }
        else if(containsWord(str, "debug") && containsWord(str, "screen")) {
            LinearLayout ll = (LinearLayout) findViewById(R.id.testButtonsLayout);
            LinearLayout ll2 = (LinearLayout) findViewById(R.id.testLine);
            if(ll.getVisibility() == LinearLayout.GONE) {
                ll.setVisibility(LinearLayout.VISIBLE);
                ll2.setVisibility(LinearLayout.VISIBLE);
            }
            else {
                ll.setVisibility(LinearLayout.GONE);
                ll2.setVisibility(LinearLayout.GONE);
            }
        }
        else {
            t1.speak("Your Destination is" + str, TextToSpeech.QUEUE_FLUSH, null);
            txtText.setText(str);
            Log.d("PROCESSVOICE", "hello!");
            checkDestination(str);
        }
    }

    //Quick test to see if android usb is working
    public void arduinoTest(View view) {
        int temp = arduino.write(Integer.toString(Values.ALL_DIRECTIONS));
        if(temp == -1) {
            arduino = new Arduino(getApplicationContext());
            t1.speak("Arduino failure", TextToSpeech.QUEUE_ADD, null);
        }
        t1.speak(Integer.toString(temp), TextToSpeech.QUEUE_ADD, null);
    }

    /*
    * Checks if string statement contains a number and returns that number
    * If it doesn't contain a number, returns -1.0*/
    private float containsNumber(String str) {
        String string = str.toLowerCase();
        string = string.replaceAll("[!?,]", "");
        String[] strings = string.split("\\s+");
        for(int i = 0; i < strings.length; ++i) {
            try {
                Float val = Float.valueOf(strings[i]);
                if (val != null)
                    return val.floatValue();
            } catch (NumberFormatException e) {
                //do nothing
            }
        }
        return -1.0f;
    }

    public static boolean containsWord(String str, String word) {
        String string = str.toLowerCase();
        word = word.toLowerCase();
        string = string.replaceAll("[!?,]", "");
        String[] strings = string.split("\\s+");
        for(int i = 0; i < strings.length; ++i) {
            if(strings[i].equals(word)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        return super.onOptionsItemSelected(item);
    }

    public void vibrate(View view) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(800);

    }

    public void sendDestination(View view) {
        Intent intent = new Intent(this, Directions.class);
        TextView editText = (TextView) findViewById(R.id.direction_input);
        String destination = editText.getText().toString();
        intent.putExtra("destination", destination);
        startActivity(intent);
    }
    @Override
    public void onConnected(Bundle bundle) {
        connected = true;
    }

    @Override
    public void onConnectionSuspended(int i) {
        connected = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        connected = false;
        Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
        t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
        toast.show();
    }

    // Checks if the input destination is valid
    // If so, starts Directions activity
    // If not, request the user to try again
    public void checkDestination(String str) {
        final Location location = connected ?
                LocationServices.FusedLocationApi.getLastLocation(googleApiClient): null;
        if (location == null) {
            Toast toast = Toast.makeText(context, Values.LOCATION_ERROR, Toast.LENGTH_SHORT);
            toast.show();
            return;
        }
        Log.d("CHECKDESTINATION", "hello!");
        final String destination = str;
        originalLocationAccuracy = location.getAccuracy();
        placesRequest(location, destination);
        //Make Places API Request
        // Make API request

    }

    public void placesRequest(final Location location, final String destination) {
        Log.d("PLACESREQUEST", "hello!");
        try {
            GoogleApi.getInstance(this).makePlacesAPIRequest(location, destination, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    String coordinates = "";
                    String status = "";
                    try {
                        status = response.getString("status");
                        JSONObject loc = response.getJSONArray("results").getJSONObject(0).getJSONObject("geometry").getJSONObject("location");
                        coordinates = loc.getString("lat") + "," + loc.getString("lng");
                        directionsRequest(location, coordinates);

                    } catch(JSONException e) {
                        Log.e("placesRequest", "JSON exception", e);
                        Toast toast = Toast.makeText(context, Values.PLACES_ERROR + ", " + status, Toast.LENGTH_SHORT);
                        t1.speak(Values.PLACES_ERROR + ", " + status, TextToSpeech.QUEUE_ADD, null);
                        toast.show();
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

    public void directionsRequest(final Location location, final String destination) {
        Log.d("DIRECTIONSREQUEST", "hello!");
        try {
            GoogleApi.getInstance(this).makeDirectionsHttpRequest(location, destination, new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    String status = "";
                    try {
                        status = response.getString("status");
                    } catch(JSONException e) {
                        Log.e("GoodVibes", "JSON exception", e);
                        Toast toast = Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT);
                        t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
                        toast.show();
                    }
                    switch (status) {
                        case "OK":
                            try {
                                Intent intent = new Intent(context, Directions.class);
                                JSONObject leg = response.getJSONArray("routes").getJSONObject(0)
                                        .getJSONArray("legs").getJSONObject(0);
                                Integer numSteps = leg.getJSONArray("steps").length();
                                if (numSteps > 400) {
                                    Toast.makeText(context, Values.ROUTE_TOO_LONG, Toast.LENGTH_SHORT).show();
                                    t1.speak(Values.ROUTE_TOO_LONG, TextToSpeech.QUEUE_ADD, null);
                                    break;
                                } else {
                                    intent.putExtra("location", location);
                                    intent.putExtra("destination", destination);
                                    intent.putExtra("accuracy", originalLocationAccuracy);
                                    intent.putExtra("response", leg.toString());
                                    fromSpeaking = false;
                                    startActivity(intent);
                                }
                            } catch (JSONException e) {
                                Toast.makeText(context, Values.UNKNOWN_ERROR, Toast.LENGTH_SHORT).show();
                                t1.speak(Values.UNKNOWN_ERROR, TextToSpeech.QUEUE_ADD, null);
                                break;
                            }
                            break;
                        case "NOT_FOUND":
                            Toast.makeText(context, Values.DESTINATION_ERROR, Toast.LENGTH_SHORT).show();
                            t1.speak(Values.DESTINATION_ERROR, TextToSpeech.QUEUE_ADD, null);
                            break;
                        case "ZERO_RESULTS":
                            Toast.makeText(context, Values.PATH_ERROR, Toast.LENGTH_SHORT).show();
                            t1.speak(Values.PATH_ERROR, TextToSpeech.QUEUE_ADD, null);
                            break;
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

    @Override
    public void onStart() {
        super.onStart();
        googleApiClient.connect();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!fromSpeaking) {
            t1.speak("Please tap the screen and speak your destination. Ask for help for more details.", TextToSpeech.QUEUE_ADD, null);
        } else {
            fromSpeaking = false;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        fromSpeaking = false;
        googleApiClient.disconnect();
    }
}
