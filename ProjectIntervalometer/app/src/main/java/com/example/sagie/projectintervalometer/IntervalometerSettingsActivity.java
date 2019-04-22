package com.example.sagie.projectintervalometer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v7.app.AppCompatDelegate;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;


public class IntervalometerSettingsActivity extends Activity {
    public static final String TAG = "tag";
    final String ticks = "TICKS";
    final String intervals = "INTERVALS";
    final String bulb = "BULB";
    final String WifiAP = "Intervalometer 9000";
    final String intervalometerConfirmation = "OK";
    final int minValueTicks = 1;
    final int minValueLongTimes = 4;
    final int maxValue = 999;
    final int WAIT_TIME = 7000;
    final int RESP_TIMEOUT = 20000;
    final String[] shortTimes = {
            "1/1000", "1/500", "1/400", "1/300", "1/250", "1/200", "1/160", "1/125", "1/100",
            "1/80", "1/60", "1/50", "1/40", "1/30", "1/25", "1/20", "1/15", "1/13", "1/10", "1/8", "1/6",
            "1/5", "1/4", "1/3", "0.5", "0.6", "0.8", "1", "1.3", "1.6", "2", "2.5", "3", "3.2"};
    String[] times;
    WifiManager wifiManager;
    boolean isConnected = false;
    TextView connStatus;
    SharedPreferences prefs;
    NumberPicker bulbPressTime, intervalTime, numTicks;
    Button startBtn;
    WifiConfiguration wifiConfiguration;
    ConnectivityManager connectivityManager;
    int netId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_disp);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        initTimes();

        // Do this only once
        if (savedInstanceState == null) {
            prefs = getSharedPreferences("Prefs", MODE_PRIVATE);
            connStatus = findViewById(R.id.connectionStatus);
            bulbPressTime = findViewById(R.id.BulbPressTimeSeekBar);
            intervalTime = findViewById(R.id.IntervalTimeSeekBar);
            numTicks = findViewById(R.id.NumTicksSeekBar);
            startBtn = findViewById(R.id.StartBtn);

            final TextView bulbPressDisplay = findViewById(R.id.BulbPressTimeDisplay);
            final TextView intervalTimeDisplay = findViewById(R.id.IntervalTimeDisplay);
            final TextView numTicksDisplay = findViewById(R.id.NumTicksDisplay);

            if (wifiManager != null) {
                // Enable before checking configurations
                if (!wifiManager.isWifiEnabled()) {
                    wifiManager.setWifiEnabled(true);
                }

                Optional<WifiConfiguration> intervalConf = wifiManager.getConfiguredNetworks().stream().filter(conf ->
                        conf.SSID.equals("\"" + WifiAP + "\"")).findFirst();

                // Add configuration ONLY IF NOT ALREADY ADDED!!
                if (!intervalConf.isPresent()) {
                    wifiConfiguration = new WifiConfiguration();
                    wifiConfiguration.SSID = "\"" + WifiAP + "\"";   // Please note the quotes. String should contain ssid in quotes
                    wifiConfiguration.preSharedKey = "\"" + WifiAP + "\""; // WPA-PSK type encryption
                    wifiConfiguration.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
                    netId = wifiManager.addNetwork(wifiConfiguration);
                } else {
                    netId = intervalConf.get().networkId;
                }
            }

            startBtn.setOnClickListener(view -> {
                // Validate data before sending!
                if (intervalTime.getValue() < bulbPressTime.getValue()) {
                    Toast.makeText(IntervalometerSettingsActivity.this,
                            "Interval time must be same length as bulb press or longer!!",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!isConnected) {
                    Toast.makeText(IntervalometerSettingsActivity.this,
                            "Not connected to device!", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Formulate the request and handle the response.
                final StringRequest stringRequest =
                        new StringRequest(Request.Method.GET, String.format(Locale.US,
                                "http://10.10.10.1:80/start?intervalTime=%.4f&numTicks=%d&bulbPressTime=%.4f",
                                getValueFromPicker(intervalTime), numTicks.getValue(), getValueFromPicker(bulbPressTime)),
                                response -> {
                                    if (response.contains(intervalometerConfirmation)) {
                                        disableInputs();

                                        // Re-enable inputs after expected interval time is over
                                        new Handler().postDelayed(() -> {
                                            enableInputs();
                                            connectToServer();
                                        }, (long) (getValueFromPicker(intervalTime) * 1000 * numTicks.getValue()));
                                    }
                                },
                                error -> {
                                    // Handle error
                                    Log.e(TAG, "An error has occurred sending request to device");
                                    Toast.makeText(this,
                                            "Failed to start intervals.\nMake sure phone is connected to WiFi network: Intervalometer 9000\n" +
                                                    "and network has been configured correctly", Toast.LENGTH_SHORT).show();
                                });

                stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                        RESP_TIMEOUT,
                        DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                        DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

                RequestHandler.getInstance(IntervalometerSettingsActivity.this).
                        addToRequestQueue(stringRequest);
            });

            Log.d(TAG, String.valueOf(times.length - 1));
            bulbPressTime.setDisplayedValues(null);
            bulbPressTime.setMinValue(0);
            bulbPressTime.setMaxValue(times.length - 1);
            bulbPressTime.setWrapSelectorWheel(true);
            bulbPressTime.setDisplayedValues(times);
            bulbPressTime.setOnValueChangedListener((picker, oldVal, newVal) -> bulbPressDisplay.setText(getString(R.string.bulb, times[newVal])));
            bulbPressTime.setOnClickListener(v -> {}); // Do nothing on click!

            intervalTime.setDisplayedValues(null);
            intervalTime.setMinValue(0);
            intervalTime.setMaxValue(times.length - 1);
            intervalTime.setWrapSelectorWheel(true);
            intervalTime.setDisplayedValues(times);
            intervalTime.setOnValueChangedListener((picker, oldVal, newVal) -> intervalTimeDisplay.setText(getString(R.string.intervals, times[newVal])));
            intervalTime.setOnClickListener(v -> {}); // Do nothing on click!

            numTicks.setMinValue(minValueTicks);
            numTicks.setMaxValue(maxValue);
            numTicks.setWrapSelectorWheel(true);
            numTicks.setOnValueChangedListener((picker, oldVal, newVal) -> numTicksDisplay.setText(getString(R.string.ticks, newVal)));

            // Set saved values - load indices of values! Not the actual displayed values
            numTicks.setValue(prefs.getInt(ticks, minValueTicks));
            intervalTime.setValue(prefs.getInt(intervals, 0));
            bulbPressTime.setValue(prefs.getInt(bulb, 0));

            // Set initial texts...
            bulbPressDisplay.setText(getString(R.string.bulb, times[bulbPressTime.getValue()]));
            numTicksDisplay.setText(getString(R.string.ticks, numTicks.getValue()));
            intervalTimeDisplay.setText(getString(R.string.intervals, times[intervalTime.getValue()]));

            connectToServer();

            // This hack is required due to an unholy bug which displays the wrong displayed value
            // even though the getValue() (index) is correct. A simple touch event somehow fixes it.
            // It may not be called in onCreate though!
            // So much arbitrary buggy shit I hate it..
            new Handler().postDelayed(() -> {
                simulateTouchHack(bulbPressTime);
                simulateTouchHack(intervalTime);
            }, 100);
        }
    }

    private void simulateTouchHack(View toTouch) {
        MotionEvent motionEventDown = MotionEvent.obtain(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis() + 100,
                MotionEvent.ACTION_DOWN,
                toTouch.getWidth() / 2,
                toTouch.getHeight() / 2,
                0
        );

        toTouch.dispatchTouchEvent(motionEventDown);

        MotionEvent motionEventUp = MotionEvent.obtain(
                SystemClock.uptimeMillis() + 200,
                SystemClock.uptimeMillis() + 300,
                MotionEvent.ACTION_UP,
                toTouch.getWidth() / 2,
                toTouch.getHeight() / 2,
                0
        );

        toTouch.dispatchTouchEvent(motionEventUp);
    }

    private float getValueFromPicker(NumberPicker picker) {
        return fromStringFraction(times[picker.getValue()]);
    }

    private float fromStringFraction(String fraction) {
        String[] fractionArray = fraction.split("/");
        try {
            if (fractionArray.length != 2) {
                // Standard whole number or decimal point float
                if (fractionArray.length == 1) {
                    return Float.parseFloat(fractionArray[0]);
                } else {
                    return 0f;
                }
            }

            float b = Float.parseFloat(fractionArray[1]);
            if (b == 0d) {
                return 0f;
            }

            float a = Float.parseFloat(fractionArray[0]);
            return a / b;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Shit! problem with dividing:\n" + e.getMessage());
            return 0f;
        }
    }

    private void initTimes() {
        times = Arrays.copyOf(shortTimes, shortTimes.length +
                (maxValue - minValueLongTimes + 1));

        // Add a second for each whole number between maxValue and minValueLongTimes, inclusive
        for (int i = minValueLongTimes; i <= maxValue; i++) {
            times[shortTimes.length + i - minValueLongTimes] = String.valueOf(i);
        }
    }

    private void disableInputs() {
        // Disable pressing anything again
        bulbPressTime.setEnabled(false);
        intervalTime.setEnabled(false);
        numTicks.setEnabled(false);
        startBtn.setEnabled(false);
        connStatus.setText(getString(R.string.StatusRunning));
        connStatus.setTextColor(getColor(R.color.green));
    }

    private void enableInputs() {
        // Enable
        bulbPressTime.setEnabled(true);
        intervalTime.setEnabled(true);
        numTicks.setEnabled(true);
        startBtn.setEnabled(true);
        connStatus.setText(getString(R.string.not_connected));
        connStatus.setTextColor(getColor(R.color.red));
    }

    private void connectToServer() {
        final Handler connectionHandler = new Handler();

        connectionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (wifiManager == null) {
//                    wifiManager = (WifiManager) IntervalometerSettingsActivity.this.getSystemService(Context.WIFI_SERVICE);
                    Log.e(TAG, "wifiManager is null!!");
                }

                // Do stuff
                if (wifiManager != null) {
                    if (!wifiManager.isWifiEnabled()) {
                        wifiManager.setWifiEnabled(true);
                    }

                    // If connection succeeded
                    if (wifiManager.getConnectionInfo() != null &&
                            wifiManager.getConnectionInfo().getNetworkId() == netId) {
                        isConnected = true;
                        connStatus.setText(R.string.connected);
                        connStatus.setTextColor(getColor(R.color.green));
                    } else {
                        // Disconnect and try to reconnect to correct network
                        wifiManager.disconnect();
                        wifiManager.enableNetwork(netId, true);
//                        bindToNetwork();
                        wifiManager.reconnect();

                        // Queue handler again due to failure!
                        connectionHandler.postDelayed(this, WAIT_TIME);
                    }
                }
            }
        }, 0);
    }

//    private void bindToNetwork() {
//        NetworkRequest.Builder builder;
//        builder = new NetworkRequest.Builder();
//        //set the transport type do WIFI
//        builder.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
//        builder.setNetworkSpecifier(WifiAP);
//        connectivityManager.requestNetwork(builder.build(), new ConnectivityManager.NetworkCallback() {
//            @Override
//            public void onAvailable(Network network) {
//                connectivityManager.bindProcessToNetwork(null);
//                if (wifiManager.getConnectionInfo() != null &&
//                        wifiManager.getConnectionInfo().getNetworkId() == netId) {
//                    connectivityManager.bindProcessToNetwork(network);
//                }
//
//                connectivityManager.unregisterNetworkCallback(this);
//            }
//        });
//    }

    @Override
    protected void onPause() {
        super.onPause();

        // Save indices of values! Not the actual displayed values
        prefs.edit().putInt(ticks, numTicks.getValue()).
                putInt(intervals, intervalTime.getValue()).
                putInt(bulb, bulbPressTime.getValue()).apply();
    }
}
