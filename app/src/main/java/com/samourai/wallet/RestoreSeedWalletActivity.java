package com.samourai.wallet;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.google.android.material.snackbar.Snackbar;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.crypto.AESUtil;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.fragments.ImportWalletFragment;
import com.samourai.wallet.fragments.PinEntryFragment;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.network.dojo.DojoUtil;
import com.samourai.wallet.onboard.OfflineDojoActivityScreen;
import com.samourai.wallet.onboard.SetDojoActivity;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.pin.PinChooserManager;
import com.samourai.wallet.util.func.AddressFactory;
import com.samourai.wallet.util.tech.AppUtil;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.TimeOutUtil;
import com.samourai.wallet.widgets.MnemonicSeedEditText;
import com.samourai.wallet.widgets.ViewPager;

import org.apache.commons.codec.DecoderException;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import static com.samourai.wallet.R.id.dots;
import static com.samourai.wallet.R.id.start;


public class RestoreSeedWalletActivity extends AppCompatActivity implements
        PinChooserManager.OnPinEntryListener,
        ImportWalletFragment.onRestoreDataSets {
    private ViewPager wallet_create_viewpager;

    private List<String> validWordList = null;

    public enum Action {
        CREATE, RESTORE
    }

    private PagerAdapter adapter;
    private LinearLayout pagerIndicatorContainer;
    private LinearLayout forwardButton, backwardButton;
    private ImageView[] indicators;
    private String passPhrase39 = null;
    private String passphrase = "", mBackupData = "";
    private boolean checkedDisclaimer = false;
    private String pinCode = "", pinCodeConfirm = "";
    private ProgressDialog progressDialog = null;
    private Action currentAction = Action.CREATE;
    private String restoreMode = "mnemonic";
    private boolean isSamouraiImport = false;
    private static final String TAG = "RestoreSeedWalletActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_restore_wallet_activity);
        wallet_create_viewpager = (ViewPager) findViewById(R.id.wallet_create_viewpager);
        wallet_create_viewpager.enableSwipe(false);
        pagerIndicatorContainer = (LinearLayout) findViewById(dots);
        forwardButton = (LinearLayout) findViewById(R.id.wizard_forward);
        backwardButton = (LinearLayout) findViewById(R.id.wizard_backward);
        if (getActionBar() != null)
            getActionBar().hide();
        if (getIntent().hasExtra("mode")) {
            restoreMode = getIntent().getStringExtra("mode");
            setUpAdapter();
        }
        if (getIntent().hasExtra("type")) {
            if(getIntent().getExtras().getString("type").equals("samourai")){
                isSamouraiImport = true;
            }
        }
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.networking));

        String BIP39_EN = null;
        StringBuilder sb = new StringBuilder();
        String mLine = null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.getAssets().open("BIP39/en.txt")));
            mLine = reader.readLine();
            while (mLine != null) {
                sb.append("\n".concat(mLine));
                mLine = reader.readLine();
            }
            reader.close();
            BIP39_EN = sb.toString();
            validWordList = Arrays.asList(BIP39_EN.split("\\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Viewpager adapter for wizard
     */
    private void setUpAdapter() {
        adapter = new PagerAdapter(getSupportFragmentManager(), currentAction);
        if (restoreMode.equals("mnemonic")) {
            setForwardButtonEnable(true);
        } else {
            setForwardButtonEnable(true);
            pagerIndicatorContainer.setVisibility(View.INVISIBLE);
        }
        wallet_create_viewpager.setAdapter(adapter);
        wallet_create_viewpager.setCurrentItem(0);
        setPagerIndicators();
    }

    /**
     * Creates pager indicator dynamically using number of fragments present in the adapter
     */
    private void setPagerIndicators() {
        indicators = new ImageView[adapter.getCount()];
        //Creating circle dot ImageView based on adapter size
        for (int i = 0; i < adapter.getCount(); i++) {
            indicators[i] = new ImageView(this);
            indicators[i].setImageDrawable(getResources().getDrawable(R.drawable.pager_indicator_dot));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(8, 0, 8, 0);
            pagerIndicatorContainer.addView(indicators[i], params);
        }
        //Setting first ImageView as active indicator
        indicators[0].setImageDrawable(getResources().getDrawable(R.drawable.pager_indicator_dot));
        indicators[0].getDrawable().setColorFilter(getResources().getColor(R.color.accent), PorterDuff.Mode.ADD);
        // Viewpager listener is responsible for changing indicator color
        wallet_create_viewpager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                for (int i = 0; i < adapter.getCount(); i++) {
                    indicators[i].setImageDrawable(getResources().getDrawable(R.drawable.pager_indicator_dot));
                }
                // here we using PorterDuff mode to overlay color over ImageView to set Active indicator
                // we don't have to create multiple asset for showing active and inactive states of indicators
                indicators[position].getDrawable().setColorFilter(getResources().getColor(R.color.accent), PorterDuff.Mode.ADD);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

    }

    /**
     * Accepts Forward navigation clicks
     *
     * @param view
     */
    public void wizardNavigationForward(View view) {
        int count = wallet_create_viewpager.getCurrentItem();
        switch (count) {
            case 0: {
                if (restoreMode.equals("backup")) {
                    String decrypted = null;
                    try {
                        decrypted = PayloadUtil.getInstance(RestoreSeedWalletActivity.this).getDecryptedBackupPayload(mBackupData, new CharSequenceX(passphrase));
                    } catch (Exception e) {
                        Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                    }

                    if (decrypted == null || decrypted.length() < 1) {
                        Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                    } else {
                        try {
                            JSONObject json = new JSONObject(decrypted);
                            boolean existDojo = false;
                            if (json.has("meta") && json.getJSONObject("meta").has("dojo")) {
                                if (json.getJSONObject("meta").getJSONObject("dojo").has("pairing")) {
                                    existDojo = true;
                                }
                            }

                            if (existDojo && DojoUtil.getInstance(getApplication()).getDojoParams() != null)
                                RestoreWalletFromSamouraiBackup(decrypted,true);
                            else
                                RestoreWalletFromSamouraiBackup(decrypted,false);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                    }
                } else {
                    if (isSamouraiImport && passphrase.trim().isEmpty()) {
                        Snackbar.make(findViewById(R.id.wizard_nav_container), getText(R.string.passphrase_is_required_for_samourai), Snackbar.LENGTH_LONG)
                                .setAnimationMode(Snackbar.ANIMATION_MODE_FADE)
                                .setAnchorView(findViewById(R.id.wizard_nav_container))
                                .show();
                        return;
                    }

                    MnemonicSeedEditText etMnemonic = (MnemonicSeedEditText) wallet_create_viewpager.findViewById(R.id.mnemonic_code_edittext);
                    String data = etMnemonic.getText().toString().trim();
                    String[] s = data.split("\\s+");

                    if (!validWordList.contains(s[s.length - 1])) {
                        Toast.makeText(this, "Invalid BIP39 word \"" + s[s.length - 1] + "\"", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Pressing "next" on import seed screen
                    if (s.length >= 12 && s.length <= 24 && s.length % 3 == 0 && isValidMnemonic(Arrays.asList(s))) {
                        wallet_create_viewpager.setCurrentItem(count + 1);
                        setForwardButtonEnable(false);
                    } else {
                        Toast.makeText(RestoreSeedWalletActivity.this, R.string.invalid_mnemonic, Toast.LENGTH_SHORT).show();
                    }

                }
                break;
            }
            case 1: {
                if (restoreMode.equals("mnemonic")) {
                    wallet_create_viewpager.setCurrentItem(count + 1);
                }
                break;
            }
            case 2: {
                if (restoreMode.equals("mnemonic")) {
                    if (pinCode.equals(pinCodeConfirm)) {
                        String wordLists[] = mBackupData.trim().split(" ");
                        for (int i = 0; i < wordLists.length; i++) {
                            if (!validWordList.contains(wordLists[i])) {
                                Toast.makeText(this, "Invalid BIP39 word \"".concat(wordLists[i]).concat("\""), Toast.LENGTH_SHORT).show();
                                return;
                            }
                        }
                        RestoreFromMnemonic(false, pinCode, passphrase, mBackupData);
                    } else {
                        Toast.makeText(this, R.string.pin_error, Toast.LENGTH_SHORT).show();
                    }

                }
            }

        }
    }

    /**
     * Accepts backward navigation clicks
     *
     * @param view
     */
    public void wizardNavigationBackward(View view) {
        int count = wallet_create_viewpager.getCurrentItem();
        switch (count) {
            case 0: {
                finish();
                break;
            }
            case 1: {
                wallet_create_viewpager.setCurrentItem(count - 1);
            }
            case 2: {
                ((TextView) forwardButton.getChildAt(0)).setText(R.string.next);
                wallet_create_viewpager.setCurrentItem(count - 1);
            }
        }
    }

    /**
     * Helper method to enable and disable forward navigation button
     *
     * @param enable
     */
    private void setForwardButtonEnable(boolean enable) {
        forwardButton.setClickable(enable);
        forwardButton.setAlpha(enable ? 1 : 0.2f);
    }

    /**
     * this interface method is responsible for receiving data from fragment {@link ImportWalletFragment}
     * backupData can be Mnemonic seed or Samourai Backup data
     * based on the restore mode  we can use these for both restore options
     *
     * @param password
     * @param backupData
     */
    @Override
    public void onRestoreData(String password, String backupData) {
        passphrase = password;
        mBackupData = backupData;
    }


    /**
     * Callback method for receiving pin code from child fragment
     * pin entry and confirm entry fragment invoke this method
     * based on current active viewpager item we will set pin code
     *
     * @param pin
     */
    @Override
    public void pinEntry(final String pin) {
        if (wallet_create_viewpager.getCurrentItem() == 1) {
            pinCode = pin;
            if (pinCode.length() >= AccessFactory.MIN_PIN_LENGTH && pinCode.length() <= AccessFactory.MAX_PIN_LENGTH) {
                setForwardButtonEnable(true);
            } else {
                setForwardButtonEnable(false);
            }
        } else {
            pinCodeConfirm = pin;
            if (pinCodeConfirm.equals(pinCode)) {
                setForwardButtonEnable(true);
                ((TextView) forwardButton.getChildAt(0)).setText(R.string.finish);
            }
        }
    }

    /**
     * Pager adapter for viewpager
     */
    private class PagerAdapter extends FragmentPagerAdapter {

        PagerAdapter(FragmentManager fm, Action action) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: {
                    return ImportWalletFragment.newInstance(restoreMode);
                }
                case 1: {
                    return new PinEntryFragment()
                            .setOnPinEntryListener(RestoreSeedWalletActivity.this);
                }
                case 2: {
                    return PinEntryFragment
                            .newInstance(getString(R.string.pin_5_8_confirm), getString(R.string.re_enter_your_pin_code))
                            .setOnPinEntryListener(RestoreSeedWalletActivity.this);
                }
                default: {
                    return null;
                }
            }
        }

        @Override
        public int getCount() {
            return restoreMode.equals("backup") ? 1 : 3;
        }
    }

    synchronized private void toggleLoading() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
            progressDialog.setTitle(R.string.app_name);
            progressDialog.setMessage(getString(R.string.please_wait));
            progressDialog.show();
        } else {
            if (progressDialog.isShowing()) {
                progressDialog.dismiss();
            } else {
                progressDialog.show();
            }
        }
    }

    private void RestoreWalletFromSamouraiBackup(final String decrypted, boolean skipDojo) {
        toggleLoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                try {
                    JSONObject json = new JSONObject(decrypted);
                    PayloadUtil.getInstance(RestoreSeedWalletActivity.this).restoreWalletfromJSON(json, skipDojo);
                    String guid = AccessFactory.getInstance(RestoreSeedWalletActivity.this).createGUID();
                    String hash = AccessFactory.getInstance(RestoreSeedWalletActivity.this).getHash(guid, new CharSequenceX(AccessFactory.getInstance(RestoreSeedWalletActivity.this).getPIN()), AESUtil.DefaultPBKDF2Iterations);
                    PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.ACCESS_HASH, hash);
                    PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.ACCESS_HASH2, hash);
                    PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.WALLET_SCAN_COMPLETE, false);
                    PayloadUtil.getInstance(RestoreSeedWalletActivity.this).saveWalletToJSON(new CharSequenceX(guid + AccessFactory.getInstance().getPIN()));
                    //If dojo is enabled turn on tor automatically
                    if(json.has("meta") && json.getJSONObject("meta").has("dojo")){
                        if( json.getJSONObject("meta").getJSONObject("dojo").has("pairing")){
                            PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.ENABLE_TOR,true);
                        }
                    }
                } catch (MnemonicException.MnemonicLengthException mle) {
                    mle.printStackTrace();
                    Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                } catch (DecoderException de) {
                    de.printStackTrace();
                    Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                } catch (JSONException je) {
                    je.printStackTrace();
                    Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                    Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                } catch (NullPointerException npe) {
                    npe.printStackTrace();
                    Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                } catch (DecryptionException de) {
                    de.printStackTrace();
                    Toast.makeText(RestoreSeedWalletActivity.this, R.string.decryption_error, Toast.LENGTH_SHORT).show();
                } finally {
                    HashMap<String, String> dojoCredsMap = getDojoCredentialsFromBackup(decrypted);

                    Intent intent;

                    if (AppUtil.getInstance(getApplicationContext()).isOfflineMode())
                        intent = new Intent(getApplicationContext(), OfflineDojoActivityScreen.class);
                    else
                        intent = new Intent(getApplicationContext(), SetDojoActivity.class);

                    if (dojoCredsMap != null) {
                        intent.putExtra("dojoURL", dojoCredsMap.get("dojoURL"));
                        intent.putExtra("explorerURL", dojoCredsMap.get("explorerURL"));
                        intent.putExtra("apikey", dojoCredsMap.get("apikey"));
                    }

                    startActivity(intent);
                }

                Looper.loop();
                toggleLoading();

            }
        }).start();
    }

    private HashMap<String, String> getDojoCredentialsFromBackup (String decryptedString) {
        HashMap<String, String> dojoCredsMap = new HashMap<>();
        try {
            JSONObject decryptedJson = new JSONObject(decryptedString);
            JSONObject dojoJson = decryptedJson.getJSONObject("meta").getJSONObject("dojo").getJSONObject("pairing");
            String explorerURL = decryptedJson.getJSONObject("meta").has("explorer_url")
                    ? decryptedJson.getJSONObject("meta").getString("explorer_url")
                    : "";

            dojoCredsMap.put("dojoURL", dojoJson.getString("url"));
            dojoCredsMap.put("apikey", dojoJson.getString("apikey"));
            dojoCredsMap.put("explorerURL", explorerURL);
        } catch (JSONException e) {
            return null;
        }
        return dojoCredsMap;
    }

    private void RestoreFromMnemonic(final boolean create, final String pin, final String passphrase, final String seed) {
        toggleLoading();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                String guid = AccessFactory.getInstance(RestoreSeedWalletActivity.this).createGUID();
                String hash = AccessFactory.getInstance(RestoreSeedWalletActivity.this).getHash(guid, new CharSequenceX(pin), AESUtil.DefaultPBKDF2Iterations);
                PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.ACCESS_HASH, hash);
                PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.ACCESS_HASH2, hash);

                if (create) {

                    try {
                        HD_WalletFactory.getInstance(RestoreSeedWalletActivity.this).newWallet(12, passphrase);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    } catch (MnemonicException.MnemonicLengthException mle) {
                        mle.printStackTrace();
                    } finally {
                        ;
                    }

                } else if (seed == null) {
                    ;
                } else {

                    try {
                        HD_WalletFactory.getInstance(RestoreSeedWalletActivity.this).restoreWallet(seed, passphrase);
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    } catch (DecoderException de) {
                        de.printStackTrace();
                    } catch (AddressFormatException afe) {
                        afe.printStackTrace();
                    } catch (MnemonicException.MnemonicLengthException mle) {
                        mle.printStackTrace();
                    } catch (MnemonicException.MnemonicChecksumException mce) {
                        mce.printStackTrace();
                    } catch (MnemonicException.MnemonicWordException mwe) {
                        mwe.printStackTrace();
                    } finally {
                        ;
                    }

                }

                PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.SCRAMBLE_PIN, true);

                try {

                    String msg = null;

                    if (HD_WalletFactory.getInstance(RestoreSeedWalletActivity.this).get() != null) {

                        if (create) {
                            msg = getString(R.string.wallet_created_ok);
                        } else {
                            msg = getString(R.string.wallet_restored_ok);
                        }

                        try {
                            AccessFactory.getInstance(RestoreSeedWalletActivity.this).setPIN(pin);
                            PayloadUtil.getInstance(RestoreSeedWalletActivity.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(RestoreSeedWalletActivity.this).getGUID() + pin));

                            if (create) {
                                PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.WALLET_ORIGIN, "new");
                                PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.WALLET_SCAN_COMPLETE, false);
                            } else {
                                PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.WALLET_ORIGIN, "restored");
                                PrefsUtil.getInstance(RestoreSeedWalletActivity.this).setValue(PrefsUtil.WALLET_SCAN_COMPLETE, false);
                            }

                        } catch (JSONException je) {
                            je.printStackTrace();
                        } catch (IOException ioe) {
                            ioe.printStackTrace();
                        } catch (DecryptionException de) {
                            de.printStackTrace();
                        } finally {
                            ;
                        }

                        AddressFactory.getInstance().account2xpub().put(0, HD_WalletFactory.getInstance(RestoreSeedWalletActivity.this).get().getAccount(0).xpubstr());
                        AddressFactory.getInstance().xpub2account().put(HD_WalletFactory.getInstance(RestoreSeedWalletActivity.this).get().getAccount(0).xpubstr(), 0);
                        //
                        // backup wallet for alpha
                        //
                        if (create) {

                            String seed = HD_WalletFactory.getInstance(RestoreSeedWalletActivity.this).get().getMnemonic();
                            Intent intent = new Intent(RestoreSeedWalletActivity.this, RecoveryWordsActivity.class);
                            intent.putExtra( RecoveryWordsActivity.WORD_LIST, seed);
                            intent.putExtra(RecoveryWordsActivity.PASSPHRASE, passphrase);
                            startActivity(intent);
                            finish();

                        } else {
                            if (AppUtil.getInstance(getApplicationContext()).isOfflineMode())
                                startActivity(new Intent(getApplicationContext(), OfflineDojoActivityScreen.class));
                            else
                                startActivity(new Intent(getApplicationContext(), SetDojoActivity.class));

                            /*
                            AccessFactory.getInstance(RestoreSeedWalletActivity.this).setIsLoggedIn(true);
                            TimeOutUtil.getInstance().updatePin();
                            AppUtil.getInstance(RestoreSeedWalletActivity.this).restartApp();
                             */
                        }

                    } else {
                        if (create) {
                            msg = getString(R.string.wallet_created_ko);
                        } else {
                            msg = getString(R.string.wallet_restored_ko);
                        }
                    }

                    Toast.makeText(RestoreSeedWalletActivity.this, msg, Toast.LENGTH_SHORT).show();

                } catch (MnemonicException.MnemonicLengthException mle) {
                    mle.printStackTrace();
                } finally {
                    ;
                }
                toggleLoading();
                Looper.loop();

            }
        }).start();

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        String tag = "android:switcher:" + R.id.wallet_create_viewpager + ":" + 0;
        ImportWalletFragment fragment = (ImportWalletFragment) getSupportFragmentManager().findFragmentByTag(tag);
        fragment.onActivityResult(requestCode, resultCode, data);
    }

    private boolean isValidMnemonic(List<String> words) {
        try {
            MnemonicCode.INSTANCE.check(words);
            return true;
        } catch (MnemonicException e) {
            return false;
        }
    }
}


