package com.samourai.wallet.network;

import android.content.Intent;
import android.os.Bundle;
import android.support.transition.TransitionManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.samourai.wallet.R;
import com.samourai.wallet.network.dojo.DojoConfigureBottomSheet;
import com.samourai.wallet.tor.TorManager;
import com.samourai.wallet.tor.TorService;

import java.util.Objects;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class NetworkDashboard extends AppCompatActivity {

    enum CONNECTION_STATUS {ENABLED, DISABLED, CONFIGURE, WAITING}

    Button torButton, dataButton, dojoBtn;
    TextView dataConnectionStatus, torConnectionStatus, dojoConnectionStatus;
    ImageView dataConnectionIcon, torConnectionIcon, dojoConnectionIcon;
    LinearLayout offlineMessage;
    int activeColor, disabledColor, waiting;
    CompositeDisposable disposables = new CompositeDisposable();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_dashboard);
        setSupportActionBar(findViewById(R.id.toolbar));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        activeColor = ContextCompat.getColor(this, R.color.green_ui_2);
        disabledColor = ContextCompat.getColor(this, R.color.disabledRed);
        waiting = ContextCompat.getColor(this, R.color.warning_yellow);

        offlineMessage = findViewById(R.id.offline_message);

        dataButton = findViewById(R.id.networking_data_btn);
        torButton = findViewById(R.id.networking_tor_btn);
        dojoBtn = findViewById(R.id.networking_dojo_btn);

        dataConnectionStatus = findViewById(R.id.network_data_status);
        torConnectionStatus = findViewById(R.id.network_tor_status);
        dojoConnectionStatus = findViewById(R.id.network_dojo_status);

        dataConnectionIcon = findViewById(R.id.network_data_status_icon);
        torConnectionIcon = findViewById(R.id.network_tor_status_icon);
        dojoConnectionIcon = findViewById(R.id.network_dojo_status_icon);


        setDojoConnectionState(CONNECTION_STATUS.CONFIGURE);
        listenToTorStatus();

        dataButton.setOnClickListener(view -> {
            if (dataConnectionStatus.getText().equals("Disabled")) {
                setDataConnectionState(CONNECTION_STATUS.ENABLED);
            } else {
                setDataConnectionState(CONNECTION_STATUS.DISABLED);
            }
        });

        dojoBtn.setOnClickListener(view -> {
            DojoConfigureBottomSheet dojoConfigureBottomSheet = new DojoConfigureBottomSheet();
            dojoConfigureBottomSheet.show(getSupportFragmentManager(), dojoConfigureBottomSheet.getTag());


        });

        torButton.setOnClickListener(view -> {
            if (TorManager.getInstance(getApplicationContext()).isConnected()) {
                stopTor();
            } else {
                startTor();

            }
        });
    }

    private void listenToTorStatus() {
        Disposable disposable = TorManager.getInstance(getApplicationContext())
                .torStatus
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::setTorConnectionState);
        disposables.add(disposable);
    }

    private void setDataConnectionState(CONNECTION_STATUS enabled) {
        if (enabled == CONNECTION_STATUS.ENABLED) {
            showOfflineMessage(false);
            dataButton.setText("Disable");
            dataConnectionIcon.setColorFilter(activeColor);
            dataConnectionStatus.setText("Enabled");
        } else {
            dataButton.setText("Enable");
            showOfflineMessage(true);
            dataConnectionIcon.setColorFilter(disabledColor);
            dataConnectionStatus.setText("Disabled");
        }
    }

    private void setDojoConnectionState(CONNECTION_STATUS enabled) {
        if (enabled == CONNECTION_STATUS.ENABLED) {
            dojoBtn.setText("Disable");
            dojoConnectionIcon.setColorFilter(activeColor);
            dojoConnectionStatus.setText("Enabled");
        } else if (enabled == CONNECTION_STATUS.CONFIGURE) {
            dojoBtn.setText("configure");
            dojoConnectionIcon.setColorFilter(waiting);
            dojoConnectionStatus.setText("Not configured");
        } else {
            dojoBtn.setText("Enable");
            dojoConnectionIcon.setColorFilter(disabledColor);
            dojoConnectionStatus.setText("Disabled");
        }
    }

    private void setTorConnectionState(TorManager.CONNECTION_STATES enabled) {
        if (enabled == TorManager.CONNECTION_STATES.CONNECTED) {
            torButton.setText("Disable");
            torButton.setEnabled(true);
            torConnectionIcon.setColorFilter(activeColor);
            torConnectionStatus.setText("Enabled");
        } else if (enabled == TorManager.CONNECTION_STATES.CONNECTING) {
            torButton.setText("loading...");
            torButton.setEnabled(false);
            torConnectionIcon.setColorFilter(waiting);
            torConnectionStatus.setText("Tor initializing");
        } else {
            dojoBtn.setText("Enable");
            dojoBtn.setEnabled(true);
            torConnectionIcon.setColorFilter(disabledColor);
            torConnectionStatus.setText("Disabled");
        }
    }

    private void showOfflineMessage(boolean show) {
        TransitionManager.beginDelayedTransition((ViewGroup) offlineMessage.getRootView());
        offlineMessage.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void startTor() {

        Intent startIntent = new Intent(getApplicationContext(), TorService.class);
        startIntent.setAction(TorService.START_SERVICE);
        startService(startIntent);

    }

    private void stopTor() {
        Intent startIntent = new Intent(getApplicationContext(), TorService.class);
        startIntent.setAction(TorService.STOP_SERVICE);
        startService(startIntent);
    }

}
