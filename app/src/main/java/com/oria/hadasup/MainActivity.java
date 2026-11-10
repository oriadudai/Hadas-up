package com.oria.hadasup;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Hosts the game page (assets/index.html) in a full-screen WebView and
 * turns phone tilts into answers, like the original Heads Up:
 * screen tilted toward the floor = correct, toward the ceiling = pass.
 */
public class MainActivity extends Activity implements SensorEventListener {

    private static final float TILT = 6.5f;     // ~40 degrees past vertical
    private static final float NEUTRAL = 3.0f;  // within ~18 degrees of vertical

    private WebView webView;
    private SensorManager sensorManager;
    private Sensor sensor;

    // Low-pass filtered z component of gravity (m/s^2). z points out of the screen.
    private float z = 0f;
    // A new tilt counts only after the phone has come back upright (on the forehead).
    private volatile boolean armed = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                 // keeps the edited word list
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (sensor == null) sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        hideSystemBars();
    }

    @Override
    protected void onResume() {
        super.onResume();
        armed = false;
        if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    // ---- tilt detection ----

    @Override
    public void onSensorChanged(SensorEvent event) {
        z = 0.8f * z + 0.2f * event.values[2];

        if (!armed) {
            // Upright on the forehead: the screen is vertical, so z is near 0.
            if (Math.abs(z) < NEUTRAL) armed = true;
            return;
        }
        if (z < -TILT) fire(true);        // screen faces the floor -> correct
        else if (z > TILT) fire(false);   // screen faces the ceiling -> pass
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void fire(boolean correct) {
        armed = false;
        webView.evaluateJavascript("window.onTilt && window.onTilt(" + correct + ")", null);
    }

    // ---- page <-> app ----

    private class Bridge {
        @JavascriptInterface
        public void vibrate(long ms) {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                v = ((VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE)).getDefaultVibrator();
            } else {
                v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v != null) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        /** Called when a round starts, so the first tilt needs the phone upright first. */
        @JavascriptInterface
        public void resetTilt() {
            armed = false;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Let the page handle Back (game -> menu). It returns true if it did.
        webView.evaluateJavascript("window.onBack ? window.onBack() : false", handled -> {
            if (!"true".equals(handled)) finish();
        });
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }
}
