package com.example.sagie.projectintervalometer;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;

import java.util.Locale;
import java.util.Optional;


public class IntervalometerSettingsActivity extends Activity {
    public static final String TAG = "tag";
    final String ticks = "TICKS";
    final String intervals = "INTERVALS";
    final String bulb = "BULB";
    final String WifiAP = "Intervalometer 9000";
    final String intervalometerConfirmation = "OK";
    final int minValue = 1; // For all right now
    final int maxValue = 999;
    final int WAIT_TIME = 7000;
    final int RESP_TIMEOUT = 20000;
    WifiManager wifiManager;
    boolean isConnected = false;
    TextView connStatus;
    SharedPreferences prefs;
    NumberPicker bulbPressTime, intervalTime, numTicks;
    Button startBtn;
    WifiConfiguration wifiConfiguration;
    int netId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_disp);
        wifiManager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);

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
                            "http://10.10.10.1:80/start?intervalTime=%d&numTicks=%d&bulbPressTime=%d",
                            intervalTime.getValue(), numTicks.getValue(), bulbPressTime.getValue()),
                                response -> {
                                    if (response.contains(intervalometerConfirmation)) {
                                        disableInputs();

                                        // Re-enable inputs after expected interval time is over
                                        new Handler().postDelayed(() -> {
                                            enableInputs();
                                            connectToServer();
                                        }, intervalTime.getValue() * numTicks.getValue() * 1000);
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

            bulbPressTime.setMinValue(minValue);
            bulbPressTime.setMaxValue(maxValue);
            bulbPressTime.setWrapSelectorWheel(true);
            bulbPressTime.setOnValueChangedListener((picker, oldVal, newVal) -> bulbPressDisplay.setText(getString(R.string.bulb, newVal)));

            intervalTime.setMinValue(minValue);
            intervalTime.setMaxValue(maxValue);
            intervalTime.setWrapSelectorWheel(true);
            intervalTime.setOnValueChangedListener((picker, oldVal, newVal) -> intervalTimeDisplay.setText(getString(R.string.intervals, newVal)));

            numTicks.setMinValue(minValue);
            numTicks.setMaxValue(maxValue);
            numTicks.setWrapSelectorWheel(true);
            numTicks.setOnValueChangedListener((picker, oldVal, newVal) -> numTicksDisplay.setText(getString(R.string.ticks, newVal)));

            // Set saved values
            numTicks.setValue(prefs.getInt(ticks, minValue));
            intervalTime.setValue(prefs.getInt(intervals, minValue));
            bulbPressTime.setValue(prefs.getInt(bulb, minValue));

            // Set initial texts...
            bulbPressDisplay.setText(getString(R.string.bulb, bulbPressTime.getValue()));
            numTicksDisplay.setText(getString(R.string.ticks, numTicks.getValue()));
            intervalTimeDisplay.setText(getString(R.string.intervals, intervalTime.getValue()));

            connectToServer();
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
                        wifiManager.reconnect();

                        // Queue handler again due to failure!
                        connectionHandler.postDelayed(this, WAIT_TIME);
                    }
                }
            }
        }, 0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        super.onPause();

        prefs.edit().putInt(ticks, numTicks.getValue()).
                putInt(intervals, intervalTime.getValue()).
                putInt(bulb, bulbPressTime.getValue()).apply();
    }
}
