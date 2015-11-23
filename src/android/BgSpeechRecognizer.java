//
//
//  BgSpeechRecognizer.java
//
//  Created by Christopher Rohde on 2015-11-22.
//

package li.iti.cordova.plugin.bgspeechrecognizer;

import java.util.ArrayList;
import java.util.Locale;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CallbackContext;

import android.app.Activity;

import android.content.Context;
import android.content.Intent;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.media.AudioManager;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.os.Bundle;
import android.os.CountDownTimer;

public class BgSpeechRecognizer extends CordovaPlugin {

    private static final String LOG_TAG = BgSpeechRecognizer.class.getSimpleName();
    private static int REQUEST_CODE = 1001;

    private CallbackContext callbackContext;
    private LanguageDetailsChecker languageDetailsChecker;

    private Camera cam;
    private Parameters p;
    private boolean isFlashOn;

    private SpeechRecognizer sr;
    private Intent intent;
    private AudioManager mAudioManager;
    private int mStreamVolume = 0;

    private boolean pauseRecognition = false;

    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        Boolean isValidAction = true;
        this.callbackContext = callbackContext;
        if ("startRecognize".equals(action)) {
            startSpeechRecognitionActivity(args);     
        } else if ("getSupportedLanguages".equals(action)) {
            getSupportedLanguages();
        } else if("stopRecognize".equals(action)) {
            stopSpeechRecognitionActivity();
        } else {
            this.callbackContext.error("Unknown action: " + action);
            isValidAction = false;
        }       
        return isValidAction;
    }

    private void getSupportedLanguages() {
        if (languageDetailsChecker == null){
            languageDetailsChecker = new LanguageDetailsChecker(callbackContext);
        }
        Intent detailsIntent = new Intent(RecognizerIntent.ACTION_GET_LANGUAGE_DETAILS);
        cordova.getActivity().sendOrderedBroadcast(detailsIntent, null, languageDetailsChecker, null, Activity.RESULT_OK, null, null);
        
    }

    /**
     * Fire an intent to start the speech recognition activity.
     *
     * @param args Argument array with the following string args: [req code][number of matches]
     */
    private void startSpeechRecognitionActivity(JSONArray args) {
        int maxMatches = 0;
        String language = Locale.getDefault().toString();

        try {
            if (args.length() > 0) {
                String temp = args.getString(0);
                maxMatches = Integer.parseInt(temp);
            }
            if (args.length() > 1) {
                language = args.getString(1);
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, String.format("startSpeechRecognitionActivity exception: %s", e.toString()));
        }

        intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"voice.recognition.test");
        if (maxMatches > 0) {
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxMatches);
        }

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                sr = SpeechRecognizer.createSpeechRecognizer(cordova.getActivity().getBaseContext());
                sr.setRecognitionListener(new Listener());                    
                sr.startListening(intent);
            }
        });
        
        mAudioManager = (AudioManager) cordova.getActivity().getSystemService(Context.AUDIO_SERVICE);
        mStreamVolume = 15;//mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC); 
        muteStreamVolume();
    }

    /**
     * Stop the speech recognition.
     *
     */
    private void stopSpeechRecognitionActivity() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if(sr != null) {
                    sr.cancel();
                    sr.destroy();
                    sr = null;
                }
            }
        });
        setStreamVolumeBack();
        
    }

    @Override
    public void onResume(boolean b) {
        super.onResume(b);
        AppStatus.activityResumed();
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                sr.startListening(intent);
            }
        });
        muteStreamVolume();
    }

    @Override
    public void onPause(boolean b) {
        super.onPause(b);
        AppStatus.activityPaused();
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                sr.stopListening();
            }
        });
        setStreamVolumeBack();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if(sr != null) {
                    sr.cancel();
                    sr.destroy();
                    sr = null;
                }
            }
        });
        setStreamVolumeBack();
    }

    private void muteStreamVolume() {
        PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: Mute Volume " + mStreamVolume);
            progressResult.setKeepCallback(true);
            callbackContext.sendPluginResult(progressResult);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0);
    }

    private void setStreamVolumeBack() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: Reset Volume " + mStreamVolume);
                    progressResult.setKeepCallback(true);
                    callbackContext.sendPluginResult(progressResult);
                mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mStreamVolume, 0);
            }
        }, 500);
    }

    private void returnSpeechResults(ArrayList<String> matches) {
        JSONArray jsonMatches = new JSONArray(matches);
        this.callbackContext.success(jsonMatches);
    }

    private void returnProgressResults(ArrayList<String> matches) {
        JSONArray jsonMatches = new JSONArray(matches);
        JSONObject done = new JSONObject();
        try {
            done.put("results", jsonMatches);
        }
        catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        
        PluginResult progressResult = new PluginResult(PluginResult.Status.OK, done);
        progressResult.setKeepCallback(true);
        callbackContext.sendPluginResult(progressResult);
    }

    private void setupRecognition() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                sr.cancel();
                sr.destroy();
                muteStreamVolume();
                sr = SpeechRecognizer.createSpeechRecognizer(cordova.getActivity().getBaseContext());
                sr.setRecognitionListener(new Listener());                    
                sr.startListening(intent);
            }
        });
    }

    class Listener implements RecognitionListener {
        public void onReadyForSpeech(Bundle params) {
            PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: ReadyForSpeech");
            progressResult.setKeepCallback(true);
            callbackContext.sendPluginResult(progressResult);

            /*Log.d("Speech", "onReadyForSpeech: Cancel Timer");
            if(mTimer != null) {
                mTimer.cancel();
            }*/
        }
        public void onBeginningOfSpeech() {
            muteStreamVolume();
            PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: BeginOfSpeech");
            progressResult.setKeepCallback(true);
            callbackContext.sendPluginResult(progressResult);
        }
        public void onRmsChanged(float rmsdB) {
        }
        public void onBufferReceived(byte[] buffer) {
        }
        public void onEndOfSpeech() {
            PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: EndOfSpeech");
            progressResult.setKeepCallback(true);
            callbackContext.sendPluginResult(progressResult);
        }
        public void onError(int error) {
            PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: Error("+error+")");
            progressResult.setKeepCallback(true);
            callbackContext.sendPluginResult(progressResult);

            if(AppStatus.isActivityVisible()) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        setupRecognition();
                    }
                });
            }
        }
        public void onResults(Bundle results) {
            /*if(mTimer != null){
                mTimer.cancel();
            }*/

            ArrayList<String> matches = new ArrayList<String>();
            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            for (int i = 0; i < data.size(); i++) {
                matches.add((String) data.get(i));
            }            
            if(AppStatus.isActivityVisible()) {
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        setupRecognition();
                    }
                });
            }            
            returnProgressResults(matches);

            /*Log.d("Speech", "onResults: Start a timer");
            if(mTimer == null) {
                mTimer = new CountDownTimer(2000, 500) {
                    @Override
                    public void onTick(long l) {
                    }

                    @Override
                    public void onFinish() {
                        PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: Timer Finished, Restart recognizer");
                        progressResult.setKeepCallback(true);
                        callbackContext.sendPluginResult(progressResult);

                        Log.d("Speech", "Timer.onFinish: Timer Finished, Restart recognizer");
                        setupRecognition();
                    }
                };
            }
            mTimer.start();*/
        }
        public void onPartialResults(Bundle partialResults) {
            PluginResult progressResult = new PluginResult(PluginResult.Status.OK, "BgSpeechRecognizer: partialResults");
            progressResult.setKeepCallback(true);
            callbackContext.sendPluginResult(progressResult);
        }
        public void onEvent(int eventType, Bundle params) {
        }
    }

    static class AppStatus {
        private static boolean activityVisible = true;

        public static boolean isActivityVisible() {
            return activityVisible;
        }

        public static void activityResumed() {
            activityVisible = true;
        }

        public static void activityPaused() {
            activityVisible = false;
        }
    }
    
}