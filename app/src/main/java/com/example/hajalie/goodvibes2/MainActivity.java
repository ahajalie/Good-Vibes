package com.example.hajalie.goodvibes2;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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

        btnSpeak = (ImageButton) findViewById(R.id.btnSpeak);
        txtText = (EditText) findViewById(R.id.direction_input);
        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
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


    private void processVoice(String str) {
        String string = str.toLowerCase();
        string = string.replaceAll("[!?,]", "");
        String[] strings = string.split("\\s+");
        if(strings.length >= 2 && strings[0].equals("interval")) {
            String value = strings[1];
            float interval = Float.parseFloat(value);
            t1.speak("Interval set to " + value, TextToSpeech.QUEUE_FLUSH, null);
        }
        else if(strings.length >= 2 && strings[0].equals("intensity")) {
            String value = strings[1];
            float intensity = Float.parseFloat(value);
            t1.speak("Intensity set to " + value, TextToSpeech.QUEUE_FLUSH, null);
        }
        else {
            t1.speak("Your Destination is" + str + ". Is this correct?", TextToSpeech.QUEUE_FLUSH, null);
        }
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
