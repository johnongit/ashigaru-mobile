package com.samourai.wallet;

import static com.samourai.wallet.payload.ExternalBackupManager.backupAvailable;
import static com.samourai.wallet.payload.ExternalBackupManager.hasBackUpURI;
import static com.samourai.wallet.util.func.WalletUtil.ASHIGARU_PUB_KEY;
import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.commons.lang3.StringUtils.strip;
import static org.apache.commons.lang3.StringUtils.stripStart;
import static org.apache.commons.lang3.StringUtils.substringBetween;
import static java.lang.Math.max;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.auth0.android.jwt.JWT;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.crypto.AESUtil;
import com.samourai.wallet.home.BalanceActivity;
import com.samourai.wallet.onboard.OnBoardSlidesActivity;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.pin.PinEntryActivity;
import com.samourai.wallet.service.BackgroundManager;
import com.samourai.wallet.service.WebSocketService;
import com.samourai.wallet.sync.SyncWalletActivity;
import com.samourai.wallet.tor.EnumTorState;
import com.samourai.wallet.tor.SamouraiTorManager;
import com.samourai.wallet.tor.TorState;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.TimeOutUtil;
import com.samourai.wallet.util.Util;
import com.samourai.wallet.util.func.WalletUtil;
import com.samourai.wallet.util.network.ConnectivityStatus;
import com.samourai.wallet.util.network.WebUtil;
import com.samourai.wallet.util.tech.AppUtil;
import com.samourai.wallet.util.tech.LogUtil;
import com.samourai.wallet.util.tech.SimpleCallback;
import com.samourai.wallet.util.tech.SimpleTaskRunner;
import com.samourai.wallet.util.tech.ThreadHelper;
import com.samourai.wallet.util.tech.VerifyPGPSignedClearMessageUtil;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.io.ThreadUtils;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.json.JSONObject;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class MainActivity2 extends SamouraiActivity {

    private ProgressDialog progress = null;
    public static final String ACTION_RESTART = "com.samourai.wallet.MainActivity2.RESTART_SERVICE";
    private AlertDialog.Builder dlg;
    private static final String TAG = "MainActivity2";
    private TextView loaderTxView;
    private LinearProgressIndicator progressIndicator;
    private CompositeDisposable compositeDisposables = new CompositeDisposable();
    private SwitchCompat netSwitch;
    private TextView mainnetText;
    private TextView testnetText;
    private AtomicBoolean torStateObserverInitialized = new AtomicBoolean(false);
    private Observer<TorState> torStateObserver;

    protected BroadcastReceiver receiver_restart = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (ACTION_RESTART.equals(intent.getAction())) {

//                ReceiversUtil.getInstance(MainActivity2.this).initReceivers();

                WebSocketService.restartService(MainActivity2.this);

            }

        }
    };

    protected BackgroundManager.Listener bgListener = new BackgroundManager.Listener() {

        public void onBecameForeground() {
//
//            Intent intent = new Intent("com.samourai.wallet.BalanceFragment.REFRESH");
//            intent.putExtra("notifTx", false);
//            LocalBroadcastManager.getInstance(MainActivity2.this.getApplicationContext()).sendBroadcast(intent);
//
//            Intent _intent = new Intent("com.samourai.wallet.MainActivity2.RESTART_SERVICE");
//            LocalBroadcastManager.getInstance(MainActivity2.this.getApplicationContext()).sendBroadcast(_intent);

        }

        public void onBecameBackground() {

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        try {
                            PayloadUtil.getInstance(MainActivity2.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(MainActivity2.this).getGUID() + AccessFactory.getInstance(MainActivity2.this).getPIN()));
                        } catch (Exception e) {
                            ;
                        }

                    }
                }).start();
            }

        }

    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

//        if (BuildConfig.DEBUG) { // useful to detect bad usage of main thread for developers
//            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
//                    .detectCustomSlowCalls()
//                    .detectNetwork()
//                    .detectDiskReads()
//                    .penaltyLog()
//                    .build());
//            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
//                    .detectLeakedSqlLiteObjects()
//                    .detectLeakedClosableObjects()
//                    .penaltyLog()
//                    .build());
//        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.window));


        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        loaderTxView = findViewById(R.id.loader_text);
        progressIndicator = findViewById(R.id.loader);
        progressIndicator.setIndeterminate(true);
        progressIndicator.setMax(100);
        BalanceActivity.Companion.setAppUpdateShowed(false);
        SamouraiWallet.getInstance().releaseNotes = null;

        final Disposable disposable = Observable.fromCallable(() -> {
            return PrefsUtil.getInstance(MainActivity2.this).getValue(PrefsUtil.TESTNET, false);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(isTestNet -> {
                    if (isTestNet) {
                        SamouraiWallet.getInstance().setCurrentNetworkParams(TestNet3Params.get());
                    }
                    BackgroundManager.get(MainActivity2.this).addListener(bgListener);
                    startApp();
                });
        compositeDisposables.add(disposable);
    }

    private void startApp() {

        if (SamouraiTorManager.INSTANCE.isRequired() &&
                !AppUtil.getInstance(getApplicationContext()).isOfflineMode() &&
                ConnectivityStatus.hasConnectivity(getApplicationContext()) &&
                !SamouraiTorManager.INSTANCE.isConnected()) {

            progressIndicator.setProgress(0, false);
            loaderTxView.setText(getText(R.string.initializing_tor));
            initAppOnTorStart();
        } else {
            initAppOnCreate();
        }
    }

    @Override
    protected void onResume() {

        if (SamouraiTorManager.INSTANCE.isRequired() &&
                !AppUtil.getInstance(getApplicationContext()).isOfflineMode() &&
                ConnectivityStatus.hasConnectivity(getApplicationContext()) &&
                !SamouraiTorManager.INSTANCE.isConnected()) {

            progressIndicator.setIndeterminate(true);
            progressIndicator.setProgress(0, false);
            SamouraiTorManager.INSTANCE.start();
            initAppOnTorStart();
        } else {
            initAppOnResume();
        }
        super.onResume();

    }

    private void initAppOnCreate() {
        if (AppUtil.getInstance(MainActivity2.this).isOfflineMode() &&
                !(AccessFactory.getInstance(MainActivity2.this).getGUID().length() < 1 || !PayloadUtil.getInstance(MainActivity2.this).walletFileExists())) {
            Toast.makeText(MainActivity2.this, R.string.in_offline_mode, Toast.LENGTH_SHORT).show();
            doAppInit0(false, null);
        } else {
//            SSLVerifierThreadUtil.getInstance(MainActivity2.this).validateSSLThread();
//            APIFactory.getInstance(MainActivity2.this).validateAPIThread();

            String action = getIntent().getAction();
            String scheme = getIntent().getScheme();
            String strUri = null;
            boolean isDial = false;
//                String strUri = null;
            if (action != null && Intent.ACTION_VIEW.equals(action) && scheme.equals("bitcoin")) {
                strUri = getIntent().getData().toString();
            } else {
                Bundle extras = getIntent().getExtras();
                if (extras != null && extras.containsKey("dialed")) {
                    isDial = extras.getBoolean("dialed");
                }
                if (extras != null && extras.containsKey("uri")) {
                    strUri = extras.getString("uri");
                }
            }

            if ( scheme !=null && scheme.equals("auth47") && getIntent().getData()!=null) {
                strUri = getIntent().getData().toString();
            }
            doAppInit0(isDial, strUri);
        }

    }

    private void initAppOnTorStart() {
        if (torStateObserverInitialized.compareAndSet(false, true)) {
            if (torStateObserver == null) {
                torStateObserver = new Observer<TorState>() {
                    @Override
                    public void onChanged(TorState torState) {

                        SimpleTaskRunner.create().executeAsync(
                                true,
                                new Callable<Integer>() {
                                    @Override
                                    public Integer call() throws Exception {
                                        final int progressIndicatorValue = torState.getProgressIndicator();
                                        final int newProgressIndicatorValue = max(
                                                progressIndicatorValue,
                                                progressIndicator.getProgress());
                                        return newProgressIndicatorValue;
                                    }
                                }, new SimpleCallback<Integer>() {
                            @Override
                            public void onComplete(final Integer newProgressIndicatorValue) {

                                    if (newProgressIndicatorValue > 0 && progressIndicator.isIndeterminate()) {
                                        progressIndicator.setIndeterminate(false);
                                    }

                                    SimpleTaskRunner.create().executeAsync(true, new Callable<Boolean>() {
                                        @Override
                                        public Boolean call() throws Exception {
                                            if (progressIndicator.getProgress() <= 0 && newProgressIndicatorValue > 0) {
                                                ThreadHelper.pauseMillis(100L);
                                                return true;
                                            }
                                            return false;
                                        }
                                    }, new SimpleCallback<Boolean>() {
                                        @Override
                                        public void onComplete(final Boolean stateChanged) {
                                            if (newProgressIndicatorValue > 0) {
                                                progressIndicator.setProgressCompat(newProgressIndicatorValue, true);
                                            }
                                            if (torState.getState() == EnumTorState.ON) {
                                                SimpleTaskRunner.create().executeAsync(true, new Callable<Object>() {
                                                    @Override
                                                    public Object call() throws Exception {
                                                        // wait for progress bar to reach 100%
                                                        ThreadUtils.sleep(Duration.ofMillis(500));
                                                        return null;
                                                    }
                                                }, new SimpleCallback<Object>() {
                                                    @Override
                                                    public void onComplete(Object result) {
                                                        SimpleTaskRunner.create().executeAsyncAndShutdown(() -> initAppOnCreate());
                                                    }
                                                });
                                            }
                                        }
                                    });
                            }
                        });
                    }
                };
                SamouraiTorManager.INSTANCE.getTorStateLiveData().observe(this, torStateObserver);
            }
        }
    }

    private void initAppOnResume() {

        AppUtil.getInstance(MainActivity2.this).setIsInForeground(true);

        SimpleTaskRunner.create().executeAsync(
                true,
                new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        AppUtil.getInstance(MainActivity2.this).deleteQR();
                        AppUtil.getInstance(MainActivity2.this).deleteBackup();
                        return null;
                    }
                },
                new SimpleCallback<Void>() {
                    @Override
                    public void onComplete(Void result) {
                        IntentFilter filter_restart = new IntentFilter(ACTION_RESTART);
                        LocalBroadcastManager.getInstance(MainActivity2.this).registerReceiver(receiver_restart, filter_restart);
                        String strUri = null;
                        try {
                            String action = getIntent().getAction();
                            String scheme = getIntent().getScheme();
                            if (action != null && Intent.ACTION_VIEW.equals(action) && scheme.equals("bitcoin")) {
                                strUri = getIntent().getData().toString();
                            } else {
                                Bundle extras = getIntent().getExtras();

                                if (extras != null && extras.containsKey("uri")) {
                                    strUri = extras.getString("uri");
                                }
                            }

                            if ( scheme !=null && scheme.equals("auth47") && getIntent().getData()!=null) {
                                strUri = getIntent().getData().toString();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        doAppInit0(false, strUri);
                    }

                    @Override
                    public void onException(Throwable t) {
                        Log.e(TAG, "issue on initAppOnResume()", t);
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(MainActivity2.this).unregisterReceiver(receiver_restart);

        AppUtil.getInstance(MainActivity2.this).setIsInForeground(false);
    }

    @Override
    protected void onDestroy() {
        compositeDisposables.dispose();

        SimpleTaskRunner.create().executeAsync(
                true,
                () -> {
                    AppUtil.getInstance(MainActivity2.this).deleteQR();
                    AppUtil.getInstance(MainActivity2.this).deleteBackup();
                    return null;
                },
                new SimpleCallback<Void>() {
                    @Override
                    public void onComplete(Void result) {
                        SimpleCallback.super.onComplete(result);
                    }

                    @Override
                    public void onException(Throwable t) {
                        Log.e(TAG, "issue on delete resources", t);
                    }
                });

        BackgroundManager.get(this).removeListener(bgListener);

        super.onDestroy();
    }

    private void initDialog() {
        final Intent intent = new Intent(MainActivity2.this, OnBoardSlidesActivity.class);
        startActivity(intent);
        finish();
    }

    private void validatePIN(final String strUri) {
        if (AccessFactory.getInstance(MainActivity2.this).isLoggedIn() && !TimeOutUtil.getInstance().isTimedOut()) {
            return;
        }

        AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
        final Intent intent = new Intent(MainActivity2.this, PinEntryActivity.class);
        if (strUri != null) {
            intent.putExtra("uri", strUri);
            PrefsUtil.getInstance(MainActivity2.this).setValue("SCHEMED_URI", strUri);
        }
        if (getBundleExtras() != null) {
            intent.putExtras(getBundleExtras());
        }
        startActivity(intent);
        finish();
    }

    private void launchFromDialer(final String pin) {

        if (progress != null && progress.isShowing()) {
            progress.dismiss();
            progress = null;
        }

        progress = new ProgressDialog(MainActivity2.this);
        progress.setCancelable(false);
        progress.setTitle(R.string.app_name);
        progress.setMessage(getString(R.string.please_wait));
        progress.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                try {
                    PayloadUtil.getInstance(MainActivity2.this).restoreWalletfromJSON(new CharSequenceX(AccessFactory.getInstance(MainActivity2.this).getGUID() + pin));

                    if (progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }

                    AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(true);
                    TimeOutUtil.getInstance().updatePin();
                    AppUtil.getInstance(MainActivity2.this).restartApp();
                } catch (MnemonicException.MnemonicLengthException mle) {
                    mle.printStackTrace();
                } catch (DecoderException de) {
                    de.printStackTrace();
                } finally {
                    if (progress != null && progress.isShowing()) {
                        progress.dismiss();
                        progress = null;
                    }
                }
                Looper.loop();

            }
        }).start();

    }

    private void doAppInit0(final boolean isDial, final String strUri) {

        Disposable disposable = Completable.fromCallable(() -> {

            if (!APIFactory.getInstance(MainActivity2.this).APITokenRequired()) {
                doAppInit1(isDial, strUri);
                return false;
            }

            boolean needToken = false;
            if (APIFactory.getInstance(MainActivity2.this).getAccessToken() == null) {
                needToken = true;
            } else {
                JWT jwt = new JWT(APIFactory.getInstance(MainActivity2.this).getAccessToken());
                if (jwt.isExpired(APIFactory.getInstance(MainActivity2.this).getAccessTokenRefresh())) {
                    APIFactory.getInstance(MainActivity2.this).getToken(true, false);
                    needToken = true;
                }
            }
            if (needToken && !AppUtil.getInstance(MainActivity2.this).isOfflineMode()) {

                APIFactory.getInstance(MainActivity2.this).stayingAlive();

                doAppInit1(isDial, strUri);

                return true;

            } else {
                doAppInit1(isDial, strUri);
            }
            return true;
        }).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                }, e -> {
                    LogUtil.error(TAG, e.getMessage());
                    e.printStackTrace();
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
        compositeDisposables.add(disposable);


    }


    @Override
    protected void onNewIntent(Intent intent) {
        setIntent(intent);
        super.onNewIntent(intent);
    }

    private Bundle getBundleExtras() {
        Bundle bundle = getIntent().getExtras();
        if (bundle == null) {
            return null;
        }
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getScheme() != null && getIntent().getScheme().equals("bitcoin")) {
            bundle.putString("uri", getIntent().getData().toString());
        } else {
            if (bundle.containsKey("uri")) {
                bundle.putString("uri", bundle.getString("uri"));
            }
        }
        if (Intent.ACTION_VIEW.equals(getIntent().getAction()) && getIntent().getScheme() != null && getIntent().getScheme().equals("auth47")) {
            bundle.putString("auth47", getIntent().getData().toString());
        }
        return bundle;

    }

    synchronized private void doAppInit1(boolean isDial, final String strUri) {

        if (AccessFactory.getInstance(MainActivity2.this).getGUID().length() < 1 || !PayloadUtil.getInstance(MainActivity2.this).walletFileExists()) {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
            if (AppUtil.getInstance(MainActivity2.this).isSideLoaded()) {
                runOnUiThread(this::doSelectNet);
            } else {
                runOnUiThread(this::initDialog);
            }
        } else if (isDial && AccessFactory.getInstance(MainActivity2.this).validateHash(PrefsUtil.getInstance(MainActivity2.this).getValue(PrefsUtil.ACCESS_HASH, ""), AccessFactory.getInstance(MainActivity2.this).getGUID(), new CharSequenceX(AccessFactory.getInstance(MainActivity2.this).getPIN()), AESUtil.DefaultPBKDF2Iterations)) {
            TimeOutUtil.getInstance().updatePin();
            launchFromDialer(AccessFactory.getInstance(MainActivity2.this).getPIN());
        } else if (TimeOutUtil.getInstance().isTimedOut()) {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
            validatePIN(strUri);
        } else if (AccessFactory.getInstance(MainActivity2.this).isLoggedIn() && !TimeOutUtil.getInstance().isTimedOut()) {

            TimeOutUtil.getInstance().updatePin();

            boolean walletScanComplete = isWalletScanComplete();

            if (!walletScanComplete && hasBackUpURI() && !backupAvailable()) {
                WalletUtil.saveWallet(this);
            }

            if(!walletScanComplete && !AppUtil.getInstance(this).isOfflineMode()) {
                startSyncWalletActivity(strUri);
            } else {
                if (!AppUtil.getInstance(this).isOfflineMode()) {
                    checkForAppUpdates();
                }
                startBalanceActivity(strUri);
            }

        } else {
            AccessFactory.getInstance(MainActivity2.this).setIsLoggedIn(false);
            validatePIN(strUri == null ? null : strUri);
        }
    }

    private boolean isWalletScanComplete() {
        return PrefsUtil.getInstance(this).getValue(PrefsUtil.WALLET_SCAN_COMPLETE, false);
    }

    private void startSyncWalletActivity(final String strUri) {
        final Intent intent = new Intent(MainActivity2.this, SyncWalletActivity.class);
        intent.putExtra("notifTx", true);
        intent.putExtra("fetch", true);
        if(strUri != null) {
            intent.putExtra("uri", strUri);
        }
        if (getBundleExtras() != null) {
            intent.putExtras(getBundleExtras());
        }
        startActivity(intent);
    }

    private void startBalanceActivity(final String strUri) {
        final Intent intent = new Intent(MainActivity2.this, BalanceActivity.class);
        intent.putExtra("notifTx", true);
        intent.putExtra("fetch", true);
        if(strUri != null){
            intent.putExtra("uri", strUri);
        }
        if (getBundleExtras() != null) {
            intent.putExtras(getBundleExtras());
        }
        startActivity(intent);
    }

    private void doSelectNet() {
        if (dlg != null) {
            return;
        }
        dlg = new AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage(R.string.select_network)
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, whichButton) -> {
                    if(netSwitch.isChecked()) { //MAINNET SELECTION
                        dialog.dismiss();
                        PrefsUtil.getInstance(MainActivity2.this).removeValue(PrefsUtil.TESTNET);
                        SamouraiWallet.getInstance().setCurrentNetworkParams(MainNetParams.get());
                        initDialog();
                    }
                    else { // TESTNET SELECTION
                        dialog.dismiss();
                        doCheckTestnet();
                    }
                });
        if (!isFinishing()) {
            LayoutInflater inflater = this.getLayoutInflater();
            View view = inflater.inflate(R.layout.net_selection,null);
            netSwitch = view.findViewById(R.id.switch1);
            mainnetText = view.findViewById(R.id.text_mainnet);
            mainnetText.setTextColor(Color.parseColor("#0CA9F4"));
            testnetText = view.findViewById(R.id.text_testnet);
            netSwitch.setOnCheckedChangeListener((compoundButton, b) -> {
                if (b) {
                    mainnetText.setTextColor(Color.parseColor("#03A9F4"));
                    testnetText.setTextColor(getResources().getColor(R.color.white));
                }
                else {
                    testnetText.setTextColor(Color.parseColor("#00BFA5"));
                    mainnetText.setTextColor(getResources().getColor(R.color.white));
                }
            });
            dlg.setView(view);
            dlg.show();
        }

    }

    private void doCheckTestnet() {
        AlertDialog testnetDlg = new AlertDialog.Builder(this)
                .setTitle("Ashigaru")
                .setMessage(R.string.confirm_testnet_message)
                .setCancelable(false)
                .setNegativeButton("BACK", (dialog12, whichButton12) -> {
                    dialog12.dismiss();
                    dlg = null;
                    doSelectNet();
                })
                .setPositiveButton("YES", (dialog1, whichButton1) -> {
                    PrefsUtil.getInstance(MainActivity2.this).setValue(PrefsUtil.TESTNET, true);
                    SamouraiWallet.getInstance().setCurrentNetworkParams(TestNet3Params.get());
                    initDialog();
                }).show();
    }

    private void checkForAppUpdates() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                String latestVersionMessage = WebUtil.getInstance(null).getURL("http://lbpxfhbnfyhxmy3jl6a4q7dzpeobx7cvkghz2vvwygevq3k4ilo2v5ad.onion/Ashigaru/Ashigaru-Mobile/raw/branch/main/accompanying-release-files/ashigaru_mobile_latest.txt");
                // 1. If the message is signed with Ashigaru's Dev public key
                // If it's correct, proceed; otherwise, return and don't check anything else
                if (!VerifyPGPSignedClearMessageUtil.verifySignedMessage(
                        defaultString(latestVersionMessage),
                        ASHIGARU_PUB_KEY))
                    return;
                final String releaseNotes = WebUtil.getInstance(null).getURL("http://lbpxfhbnfyhxmy3jl6a4q7dzpeobx7cvkghz2vvwygevq3k4ilo2v5ad.onion/Ashigaru/Ashigaru-Mobile/raw/branch/main/accompanying-release-files/ashigaru_mobile_release_notes.txt");

                final String releaseNotesSha256 = Util.sha256Hex(defaultString(releaseNotes));
                final String releaseNoteSha256ToVerify = substringBetween(latestVersionMessage, "ashigaru_mobile_release_notes.txt_sha256hash=", "\n");
                if (! StringUtils.equals(releaseNotesSha256, releaseNoteSha256ToVerify)) {
                    return;
                }

                JSONObject releaseNotesJSON = null;
                if (isNotBlank(releaseNotes)) {
                    try {
                        releaseNotesJSON =  new JSONObject(releaseNotes);
                    } catch (final Exception e) {
                        Log.e(TAG, e.getMessage(), e);
                        releaseNotesJSON = null;
                    }
                }
                if (releaseNotesJSON == null) {
                    Log.w(TAG, "releaseNotesJSON is null");
                    return;
                }
                SamouraiWallet.getInstance().releaseNotes = releaseNotesJSON;

                final String latestVersion = strip(stripStart(substringBetween(latestVersionMessage, "latest_ashigaru_mobile_version=", "\n"), "v"));
                boolean noNeedToShow = latestVersion.equals(stripStart(BuildConfig.VERSION_NAME, "v"));
                runOnUiThread(() -> {
                    AppUtil.getInstance(this).setHasUpdateBeenShown(noNeedToShow);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    System.out.println("Failed to fetch app updated");
                });
            }
        });
    }
}
