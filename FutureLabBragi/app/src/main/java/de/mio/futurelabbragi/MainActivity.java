package de.mio.futurelabbragi;

import android.Manifest;
import android.content.ContentResolver;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tbouron.shakedetector.library.ShakeDetector;

import net.gotev.speech.GoogleVoiceTypingDisabledException;
import net.gotev.speech.Speech;
import net.gotev.speech.SpeechDelegate;
import net.gotev.speech.SpeechRecognitionNotAvailable;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class MainActivity extends FragmentActivity implements View.OnClickListener {

    private static String TAG = MainActivity.class.getSimpleName();
    Button btn_ttp;
    TextToSpeech tts;

    //STT GOOGLE CLOUD
    private Button startButton,stopButton;

    private static int PERMISSION_MICROPHONE_CODE = 101;
    private static int PERMISSION_EXTERNAL_STORAGE_CODE = 102;
    private static int PERMISSION_SEND_SMS_CODE = 103;
    private String absolutePath;

    private static MainActivity activity;

    private MediaPlayer mediaPlayer;
    private TextView tv_processinglies;

    private boolean askedForMoreInfo = false;

    public static MainActivity instance() {
        return activity;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tv_processinglies = (TextView) findViewById(R.id.tv_processinglies);

        btn_ttp = (Button) findViewById(R.id.btn_ttp);
        btn_ttp.setOnClickListener(this);

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    //Toast.makeText(MainActivity.this, "Text-To-Speech engine is initialized", Toast.LENGTH_LONG).show();
                } else if (status == TextToSpeech.ERROR) {
                    Toast.makeText(MainActivity.this, "Error occurred while initializing Text-To-Speech engine", Toast.LENGTH_LONG).show();
                }
            }
        });
        tts.setLanguage(Locale.US);


        //SPEECH TO TEXT

        ((Button) findViewById(R.id.btn_stt)).setOnClickListener(this);
        ((Button) findViewById(R.id.btn_stt_play)).setOnClickListener(this);


        //SPEECH TO TEXT
        startButton = (Button) findViewById(R.id.start_button);
        stopButton = (Button) findViewById(R.id.stop_button);

        startButton.setOnClickListener(startListener);
        stopButton.setOnClickListener(stopListener);



        String[] permissions = {String.valueOf(Manifest.permission.RECORD_AUDIO),
        String.valueOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                String.valueOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)};
        ActivityCompat.requestPermissions(this,permissions, PERMISSION_MICROPHONE_CODE);

        Speech.getInstance().setStopListeningAfterInactivity(9999);
        Speech.getInstance().setLocale(Locale.US);


        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        //audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
           // audioManager.adjustStreamVolume(AudioManager.STREAM_RING,AudioManager.ADJUST_MUTE,0);
            //audioManager.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION,AudioManager.ADJUST_MUTE,0);
            //audioManager.adjustStreamVolume(AudioManager.STREAM_ALARM,AudioManager.ADJUST_MUTE,0);
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
            Log.i(TAG, "init - mute - onCreate");

            //audioManager.adjustStreamVolume(AudioManager.STREAM_SYSTEM,AudioManager.ADJUST_MUTE,0);
        } else
        {
            //audioManager.setStreamMute(AudioManager.STREAM_NOTIFICATION, true);
            //audioManager.setStreamMute(AudioManager.STREAM_ALARM, true);
            audioManager.setStreamMute(AudioManager.STREAM_MUSIC, true);
            //audioManager.setStreamMute(AudioManager.STREAM_RING, true);
            //audioManager.setStreamMute(AudioManager.STREAM_SYSTEM, true);
        }


        //SHAKEDETECTOR

        ShakeDetector.create(this, new ShakeDetector.OnShakeListener() {
            @Override
            public void OnShake() {

                Log.i(TAG,"Shake Detected");
                if(askedForMoreInfo)
                {
                    //Toast.makeText(getApplicationContext(), "Shake it out!", Toast.LENGTH_SHORT).show();
                    Log.i(TAG,"ASKING FOR MORE INFO AFTER SHAKE");
                    giveMoreInfo();
                }
            }
        });

        ShakeDetector.updateConfiguration(1.0f, 1);

    }


    @Override
    public void onClick(View v) {

        //tts.speak("Trump is stupid", TextToSpeech.QUEUE_ADD, null);
        if(v.getId() == R.id.btn_ttp)
        {
            tts.speak("Fakenews! fakenews! fakenews!", TextToSpeech.QUEUE_ADD, null,"id");
        } else if(v.getId() == R.id.btn_stt)
        {
            if(!isRecording)
            {
                //startRecording();
            } else
            {
                //stopRecording();
            }
        } else if(v.getId() == R.id.btn_stt_play)
        {
            if(absolutePath != null)
            {
                mediaPlayer = MediaPlayer.create(this, Uri.parse(absolutePath));
                mediaPlayer.setLooping(false);
                mediaPlayer.start();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        activity = this;
    }

    @Override
    public void onResume() {
        super.onResume();

        ShakeDetector.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        ShakeDetector.stop();
    }

    private final View.OnClickListener stopListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            tv_processinglies.setVisibility(View.GONE);
            mStopHandler = true;
            Speech.getInstance().stopListening();
            triggeredFirstWord = false;
            triggeredSecondWord = false;
            triggeredThirdWord = false;
        }

    };

    private SpeechDelegate speechDelegate = new SpeechDelegate() {
        @Override
        public void onStartOfSpeech() {
            Log.i(TAG,"recording - startOfSpeech");
            AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
            if(!mediaPlayer.isPlaying())
            {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
                Log.i(TAG, "speechDelegate - mute - onStartOfSpeech");
            }
        }

        @Override
        public void onSpeechRmsChanged(float value) {
            //Log.d("speech", "rms is now: " + value);
        }

        @Override
        public void onSpeechPartialResults(List<String> results) {
            StringBuilder str = new StringBuilder();
            for (String res : results) {
                str.append(res).append(" ");
            }

            if(!triggeredFirstWord && (StringUtils.containsIgnoreCase(str.toString(),"50000") ||
                    StringUtils.containsIgnoreCase(str.toString(),"5000") ||
                    StringUtils.containsIgnoreCase(str.toString(),"fifty thousand") ||
                    StringUtils.containsIgnoreCase(str.toString(),"fifteen thousand")))
            {
                triggeredFirstWord = true;

                playFakeNews();
                //Speech.getInstance().say("Fakenews!");
            }
            if(!triggeredSecondWord && (StringUtils.containsIgnoreCase(str.toString(),"seventeen") ||
                    StringUtils.containsIgnoreCase(str.toString(), "17") ||
                    StringUtils.containsIgnoreCase(str.toString(), "seventy percent")))
            {
                triggeredSecondWord = true;

                playFakeNews();
                //Speech.getInstance().say("Fakenews!");
            }
            if(!triggeredThirdWord && (StringUtils.containsIgnoreCase(str.toString(),"green") ||
                    StringUtils.containsIgnoreCase(str.toString(),"massacker") ||
                    StringUtils.containsIgnoreCase(str.toString(),"massacre")))
            {
                triggeredThirdWord = true;

                playFakeNews();

                askforMoreInfo();
                //Speech.getInstance().say("Fakenews!");
            }

            Log.i("speech", "partial result: " + str.toString().trim());
        }

        @Override
        public void onSpeechResult(String result) {
            Log.i(TAG,"speechDelegate - onSpeechResult");
            Log.i(TAG, "result: " + result);
            if(!triggeredFirstWord && (StringUtils.containsIgnoreCase(result,"50000") ||
                    StringUtils.containsIgnoreCase(result,"5000") ||
                    StringUtils.containsIgnoreCase(result,"fifty thousand")
                    || StringUtils.containsIgnoreCase(result,"fifteen thousand")))
            {
                triggeredFirstWord = true;

                playFakeNews();
            }
            if(!triggeredSecondWord && (StringUtils.containsIgnoreCase(result,"seventeen") ||
                    StringUtils.containsIgnoreCase(result, "17") ||
                    StringUtils.containsIgnoreCase(result, "seventy percent")))
            {
                triggeredSecondWord = true;

                playFakeNews();
            }
            if(!triggeredThirdWord && (StringUtils.containsIgnoreCase(result,"green") ||
                    StringUtils.containsIgnoreCase(result,"massacker") ||
                    StringUtils.containsIgnoreCase(result,"massacre")))
            {
                triggeredThirdWord = true;

                playFakeNews();

                askforMoreInfo();

                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                if(!mediaPlayer.isPlaying())
                {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
                    Log.i(TAG, "speechDelegate - mute - onSpeechResult");

                }
            }
        }
    };

    private void askforMoreInfo() {

        askedForMoreInfo = true;

        /*final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                askedForMoreInfo = true;
                Speech.getInstance().say("Do you want more info?");
            }
        }, 4000);*/

    }

    private void giveMoreInfo() {
        final AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
        Log.i(TAG, "giveMireInfo - mute - start");


        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_UNMUTE,0);
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,0);
                Log.i(TAG, "giveMoreInfo - unmute - handler");

                tts.speak("More info, more info, more info. This is a slightly longer text just to test some limits for our fancy AI based news articles."
                        ,TextToSpeech.QUEUE_ADD, null,"moreinfo");
                //Speech.getInstance().say("More info, more info, more info. This is a slightly longer text just to test some limits for our fancy AI based news articles.");
            }
        }, 1500);
        askedForMoreInfo = false;

        final Handler handlermute = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(!mediaPlayer.isPlaying())
                {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
                    Log.i(TAG,"GiveMoreInfo - giveMoreInfo - handlermute");
                } else
                {
                    handlermute.postDelayed(this,50);
                }

            }
        }, 50);

    }

    private boolean triggeredFirstWord = false;
    private boolean triggeredSecondWord = false;
    private boolean triggeredThirdWord = false;

    private void playFakeNews()
    {

        final AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_UNMUTE,0);
                Log.i(TAG, "playFakeNews - unmute - handler");
                mediaPlayer = MediaPlayer.create(MainActivity.this,R.raw.fakenews);
                mediaPlayer.setLooping(false);
                mediaPlayer.start();

                Log.i(TAG,"played fake news");
            }
        }, 10);

        final Handler handlermute = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(!mediaPlayer.isPlaying())
                {
                    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_MUTE,0);
                    Log.i(TAG, "playFakeNews - mute - handlermute");

                    if(triggeredThirdWord)
                    {
                        mStopHandler = true;
                        Speech.getInstance().stopListening();
                    }
                } else
                {
                    handlermute.postDelayed(this,50);
                }

            }
        }, 50);


       // SmsManager smsManager = SmsManager.getDefault();
        //smsManager.sendTextMessage("+33674711769", null, "fakenews", null, null);
    }


    private void startSTT()
    {
        try {
            // you must have android.permission.RECORD_AUDIO granted at this point
            Speech.getInstance().startListening(speechDelegate);
        } catch (SpeechRecognitionNotAvailable exc) {
            Log.e("speech", "Speech recognition is not available on this device!");
            // You can prompt the user if he wants to install Google App to have
            // speech recognition, and then you can simply call:
            //
            // SpeechUtil.redirectUserToGoogleAppOnPlayStore(this);
            //
            // to redirect the user to the Google App page on Play Store
        } catch (GoogleVoiceTypingDisabledException exc) {
            Log.e("speech", "Google voice typing must be enabled!");
        }
    }
    boolean mStopHandler = false;

    private final View.OnClickListener startListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            tv_processinglies.setVisibility(View.VISIBLE);
            mStopHandler = false;
            startSTT();

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(!mStopHandler) {
                        if(!Speech.getInstance().isListening())
                        {
                            startSTT();
                        }
                        handler.postDelayed(this, 500);
                    }
                }
            }, 500);
        }
    };


    private final String AUDIO_FOLDER = "futurelabs/bragi/";


    private void startRecording() {
       //not needed in final product
    }

    private boolean isRecording = false;

    private void stopRecording() {
      // not needed in final product
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Speech.getInstance().shutdown();
        ShakeDetector.destroy();
    }


    private static final String INBOX_URI = "content://sms/inbox";

    //NOT USED IN FINAL PRODUCT
    public void readSMS() {
        ContentResolver contentResolver = getContentResolver();
        Cursor smsInboxCursor = contentResolver.query(Uri.parse(INBOX_URI), null, null, null, null);
        int senderIndex = smsInboxCursor.getColumnIndex("address");
        int messageIndex = smsInboxCursor.getColumnIndex("body");
        if (messageIndex < 0 || !smsInboxCursor.moveToFirst()) return;
        do {
            String sender = smsInboxCursor.getString(senderIndex);
            String message = smsInboxCursor.getString(messageIndex);
            String formattedText = String.format(getResources().getString(R.string.sms_message), sender, message);
        } while (smsInboxCursor.moveToNext());
    }

    public void setSMSText(String formattedText) {
        Toast.makeText(this,formattedText,Toast.LENGTH_SHORT);
    }
}
