package com.example.hajalie.goodvibes2;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Locale;


/*Google Maps API KEY
*  AIzaSyBYxrhTq9VBSEHp3OHR1VUH9BN5sAtMGXY
*
* Example API Request
*/
public class MainActivity extends AppCompatActivity {

    private ImageButton btnSpeak;
    private EditText txtText;
    TextToSpeech t1;
    protected static final int RESULT_SPEECH = 1;
    private SharedPreferences preferences;
    private SharedPreferences.Editor editor;
    private boolean noErrors; //determine what to say on startup
    private BluetoothAdapter mBluetoothAdapter;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        preferences = getApplicationContext().getSharedPreferences("preferences", 0);
        editor = preferences.edit();
        btnSpeak = (ImageButton) findViewById(R.id.btnSpeak);
        txtText = (EditText) findViewById(R.id.direction_input);
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                    noErrors = true;
                    checkBluetooth();
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


        btnSpeak.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {

                Intent intent = new Intent(
                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");

                try {
                    startActivityForResult(intent, RESULT_SPEECH);
                    txtText.setText("");
                } catch (ActivityNotFoundException a) {
                    Toast t = Toast.makeText(getApplicationContext(),
                            "Opps! Your device doesn't support Speech to Text",
                            Toast.LENGTH_SHORT);
                    t.show();
                }
            }
        });

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
                editor.putFloat("interval", interval);
                t1.speak("Interval set to " + Float.toString(interval) + "seconds", TextToSpeech.QUEUE_FLUSH, null);
            }
            else {
                t1.speak("Invalid interval value, please try again", TextToSpeech.QUEUE_FLUSH, null);
            }
        }
        else if(containsWord(str, "intensity")) {
            float intensity = containsNumber(str);
            if(intensity >= 0) {
                editor.putFloat("intensity", intensity);
                t1.speak("Intensity set to " + Float.toString(intensity), TextToSpeech.QUEUE_FLUSH, null);
            }
            else {
                t1.speak("Invalid intensity value, please try again", TextToSpeech.QUEUE_FLUSH, null);
            }
        }
        else if(containsWord(str, "help")) {
            t1.speak("Tap the screen and say your destination", TextToSpeech.QUEUE_FLUSH, null);
            t1.speak("You can also change the intensity value of the motor or the interval time between vibrations in seconds", TextToSpeech.QUEUE_ADD, null);
            t1.speak("Once the directions have loaded, you can tap the screen and say cancel to cancel directions", TextToSpeech.QUEUE_ADD, null);

        }
        else {
            t1.speak("Your Destination is" + str, TextToSpeech.QUEUE_FLUSH, null);
        }
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

    private boolean containsWord(String str, String word) {
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
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public void vibrate(View view) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(800);

    }

    public void sendDestination(View view) {
        Intent intent = new Intent(this, Directions.class);
        EditText editText = (EditText) findViewById(R.id.direction_input);
        String destination = editText.getText().toString();
        intent.putExtra("destination", destination);
        startActivity(intent);
    }
}
