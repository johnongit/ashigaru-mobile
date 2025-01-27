package com.samourai.wallet.send;

import static com.samourai.wallet.send.batch.InputBatchSpendHelper.canParseAsBatchSpend;
import static com.samourai.wallet.send.cahoots.JoinbotHelper.UTXO_COMPARATOR_BY_VALUE;
import static com.samourai.wallet.send.cahoots.JoinbotHelper.isJoinbotPossibleWithCurrentUserUTXOs;
import static com.samourai.wallet.util.func.SatoshiBitcoinUnitHelper.getBtcValue;
import static com.samourai.wallet.util.func.SatoshiBitcoinUnitHelper.getSatValue;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.trim;
import static java.lang.Math.max;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.Group;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Splitter;
import com.samourai.http.client.AndroidHttpClient;
import com.samourai.wallet.R;
import com.samourai.wallet.SamouraiActivity;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.TxAnimUIActivity;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.api.fee.EnumFeeBlockCount;
import com.samourai.wallet.api.fee.EnumFeeRate;
import com.samourai.wallet.api.fee.EnumFeeRepresentation;
import com.samourai.wallet.api.fee.RawFees;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.SendNotifTxFactory;
import com.samourai.wallet.cahoots.Cahoots;
import com.samourai.wallet.cahoots.CahootsMode;
import com.samourai.wallet.cahoots.CahootsType;
import com.samourai.wallet.cahoots.psbt.PSBTUtil;
import com.samourai.wallet.constants.SamouraiAccountIndex;
import com.samourai.wallet.constants.WALLET_INDEX;
import com.samourai.wallet.explorer.ExplorerActivity;
import com.samourai.wallet.fragments.CameraFragmentBottomSheet;
import com.samourai.wallet.fragments.PaynymSelectModalFragment;
import com.samourai.wallet.httpClient.IHttpClient;
import com.samourai.wallet.network.dojo.DojoUtil;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.paynym.paynymDetails.PayNymDetailsActivity;
import com.samourai.wallet.ricochet.RicochetActivity;
import com.samourai.wallet.ricochet.RicochetMeta;
import com.samourai.wallet.ricochet.RicochetTransactionInfo;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.send.batch.BatchSpendActivity;
import com.samourai.wallet.send.cahoots.ManualCahootsActivity;
import com.samourai.wallet.send.cahoots.SelectCahootsType;
import com.samourai.wallet.send.cahoots.SorobanCahootsActivity;
import com.samourai.wallet.send.review.ReviewTxActivity;
import com.samourai.wallet.send.review.ReviewTxModel;
import com.samourai.wallet.send.review.broadcast.SpendJoinbotTxBroadcaster;
import com.samourai.wallet.tor.SamouraiTorManager;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.FormatsUtilGeneric;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.func.AddressFactory;
import com.samourai.wallet.util.func.FormatsUtil;
import com.samourai.wallet.util.func.SatoshiBitcoinUnitHelper;
import com.samourai.wallet.util.func.SendAddressUtil;
import com.samourai.wallet.util.network.BackendApiAndroid;
import com.samourai.wallet.util.network.WebUtil;
import com.samourai.wallet.util.tech.AppUtil;
import com.samourai.wallet.util.tech.DecimalDigitsInputFilter;
import com.samourai.wallet.utxos.PreSelectUtil;
import com.samourai.wallet.utxos.UTXOSActivity;
import com.samourai.wallet.utxos.models.UTXOCoin;
import com.samourai.wallet.whirlpool.WhirlpoolConst;
import com.samourai.wallet.whirlpool.WhirlpoolMeta;
import com.samourai.wallet.widgets.SendTransactionDetailsView;
import com.samourai.wallet.xmanagerClient.XManagerClient;
import com.samourai.xmanager.protocol.XManagerService;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


public class SendActivity extends SamouraiActivity {

    private final static int SCAN_QR = 2012;
    private final static int RICOCHET = 2013;
    private static final String TAG = "SendActivity";

    private SendTransactionDetailsView sendTransactionDetailsView;
    private ViewSwitcher amountViewSwitcher;
    private EditText toAddressEditText, btcEditText, satEditText;
    private TextView tvMaxAmount, tvReviewSpendAmount, tvReviewSpendAmountInSats, tvTotalFee,
            tvToAddress, tvEstimatedBlockWait, tvSelectedFeeRate, tvSelectedFeeRateLayman,
            ricochetTitle, ricochetDesc, satbText, joinbotTitle, joinbotDesc;
    private ImageButton btnReview;
    private MaterialButton btnSend;
    private SwitchCompat joinbotSwitch, ricochetHopsSwitch, ricochetStaggeredDelivery;
    private ViewGroup totalMinerFeeLayout;
    private Slider feeSeekBar;
    private Group ricochetStaggeredOptionGroup;
    private boolean shownWalletLoadingMessage = false;
    private long balance = 0L;
    private long selectableBalance = 0L;
    private String strDestinationBTCAddress = null;
    private ProgressBar progressBar;
    private Disposable entropyDisposable = null;

    private final static int FEE_LOW = 0;
    public final static int FEE_NORMAL = 1;
    private final static int FEE_PRIORITY = 2;
    private final static int FEE_CUSTOM = 3;
    private int FEE_TYPE = FEE_LOW;

    public final static int SPEND_SIMPLE = 0;
    public final static int SPEND_BOLTZMANN = 1;
    public final static int SPEND_RICOCHET = 2;
    public final static int SPEND_JOINBOT = 3;
    private int SPEND_TYPE = SPEND_BOLTZMANN;
    private boolean openedPaynym = false;

    private String strPCode = null;
    private String strPcodeCounterParty = null;
    private long feeLow, feeMed, feeHigh;
    private String strPrivacyWarning;
    private String strCannotDoBoltzmann;
    private ArrayList<UTXO> selectedUTXO;
    private long _change;
    private HashMap<String, BigInteger> receivers;
    private int changeType;
    private ConstraintLayout premiumAddons;
    private ConstraintLayout premiumAddonsRicochet;
    private ConstraintLayout premiumAddonsJoinbot;
    private TextView addonsNotAvailableMessage;
    private String address;
    private String message;
    private long amount;
    private int change_index;
    private String ricochetMessage;
    private JSONObject ricochetJsonObj = null;
    private boolean stoneWallChecked = true;

    private int idxBIP44Internal = 0;
    private int idxBIP49Internal = 0;
    private int idxBIP84Internal = 0;
    private int idxBIP84PostMixInternal = 0;

    //stub address for entropy calculation
    public static String[] stubAddress = {"1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa", "12c6DSiU4Rq3P4ZxziKxzrL5LmMBrzjrJX", "1HLoD9E4SDFFPDiYfNYnkBLQ85Y51J3Zb1", "1FvzCLoTPGANNjWoUo6jUGuAG3wg1w4YjR", "15ubicBBWFnvoZLT7GiU2qxjRaKJPdkDMG", "1JfbZRwdDHKZmuiZgYArJZhcuuzuw2HuMu", "1GkQmKAmHtNfnD3LHhTkewJxKHVSta4m2a", "16LoW7y83wtawMg5XmT4M3Q7EdjjUmenjM", "1J6PYEzr4CUoGbnXrELyHszoTSz3wCsCaj", "12cbQLTFMXRnSzktFkuoG3eHoMeFtpTu3S", "15yN7NPEpu82sHhB6TzCW5z5aXoamiKeGy ", "1dyoBoF5vDmPCxwSsUZbbYhA5qjAfBTx9", "1PYELM7jXHy5HhatbXGXfRpGrgMMxmpobu", "17abzUBJr7cnqfnxnmznn8W38s9f9EoXiq", "1DMGtVnRrgZaji7C9noZS3a1QtoaAN2uRG", "1CYG7y3fukVLdobqgUtbknwWKUZ5p1HVmV", "16kktFTqsruEfPPphW4YgjktRF28iT8Dby", "1LPBetDzQ3cYwqQepg4teFwR7FnR1TkMCM", "1DJkjSqW9cX9XWdU71WX3Aw6s6Mk4C3TtN", "1P9VmZogiic8d5ZUVZofrdtzXgtpbG9fop", "15ubjFzmWVvj3TqcpJ1bSsb8joJ6gF6dZa"};
    private CompositeDisposable compositeDisposables = new CompositeDisposable();
    private SelectCahootsType.type selectedCahootsType = SelectCahootsType.type.NONE;
    private final DecimalFormat decimalFormatSatPerByte = new DecimalFormat("##");

    public final static String DONATION_ADDRESS_MAINNET = "bc1SamouraiDonationAddress_Mainnet";
    public final static String DONATION_ADDRESS_TESTNET = "tb1q930wg92ymtz7a56c5ppfcgcpvphcrzcan53sx5";

    private List<UTXOCoin> preselectedUTXOs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Switch themes based on accounts (blue theme for whirlpool account)
        super.onCreate(savedInstanceState);
        setupThemes();
        setContentView(R.layout.activity_send);
        setSupportActionBar(findViewById(R.id.toolbar_send));
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        setTitle("");

        //CustomView for showing and hiding body of th UI
        sendTransactionDetailsView = findViewById(R.id.sendTransactionDetailsView);

        //ViewSwitcher Element for toolbar section of the UI.
        //we can switch between Form and review screen with this element
        amountViewSwitcher = findViewById(R.id.toolbar_view_switcher);

        //Input elements from toolbar section of the UI
        toAddressEditText = findViewById(R.id.edt_send_to);
        btcEditText = findViewById(R.id.amountBTC);
        satEditText = findViewById(R.id.amountSat);
        tvToAddress = findViewById(R.id.to_address_review);
        tvReviewSpendAmount = findViewById(R.id.send_review_amount);
        tvReviewSpendAmountInSats = findViewById(R.id.send_review_amount_in_sats);
        tvMaxAmount = findViewById(R.id.totalBTC);


        //view elements from review segment and transaction segment can be access through respective
        //methods which returns root viewGroup
        btnReview = sendTransactionDetailsView.getTransactionView().findViewById(R.id.review_button);

//        joinbotSwitch = sendTransactionDetailsView.getTransactionView().findViewById(R.id.joinbot_switch);
//        joinbotTitle = sendTransactionDetailsView.getTransactionView().findViewById(R.id.joinbot_title);
//        joinbotDesc = sendTransactionDetailsView.getTransactionView().findViewById(R.id.joinbot_desc);

        ricochetHopsSwitch = sendTransactionDetailsView.getTransactionView().findViewById(R.id.ricochet_hops_switch);
        ricochetTitle = sendTransactionDetailsView.getTransactionView().findViewById(R.id.ricochet_desc);
        ricochetDesc = sendTransactionDetailsView.getTransactionView().findViewById(R.id.ricochet_title);
        ricochetStaggeredDelivery = sendTransactionDetailsView.getTransactionView().findViewById(R.id.ricochet_staggered_option);
        ricochetStaggeredOptionGroup = sendTransactionDetailsView.getTransactionView().findViewById(R.id.ricochet_staggered_option_group);

        tvSelectedFeeRate = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.selected_fee_rate);
        tvSelectedFeeRateLayman = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.selected_fee_rate_in_layman);
        tvTotalFee = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.total_fee);
        btnSend = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.send_btn);
        tvEstimatedBlockWait = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.est_block_time);
        feeSeekBar = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.fee_seekbar);
        premiumAddons = sendTransactionDetailsView.findViewById(R.id.premium_addons);
        premiumAddonsRicochet = sendTransactionDetailsView.findViewById(R.id.premium_addons_ricochet);
//        premiumAddonsJoinbot = sendTransactionDetailsView.findViewById(R.id.premium_addons_joinbot);
        addonsNotAvailableMessage = sendTransactionDetailsView.findViewById(R.id.addons_not_available_message);
        totalMinerFeeLayout = sendTransactionDetailsView.getTransactionReview().findViewById(R.id.total_miner_fee_group);
        progressBar = findViewById(R.id.send_activity_progress);

        btcEditText.addTextChangedListener(BTCWatcher);
        btcEditText.setFilters(new InputFilter[]{new DecimalDigitsInputFilter(8, 8)});
        satEditText.addTextChangedListener(satWatcher);
        satEditText.setOnEditorActionListener(satKeyboardListener);
        toAddressEditText.addTextChangedListener(AddressWatcher);

        btnReview.setOnClickListener(v -> reviewTransactionSafely());
        btnSend.setOnClickListener(v -> initiateSpend());

        View.OnClickListener clipboardCopy = view -> {
            ClipboardManager cm = (ClipboardManager) this.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = android.content.ClipData
                    .newPlainText("Miner fee", tvTotalFee.getText());
            if (cm != null) {
                cm.setPrimaryClip(clipData);
                Toast.makeText(this, getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
            }
        };

        tvTotalFee.setOnClickListener(clipboardCopy);
        tvSelectedFeeRate.setOnClickListener(clipboardCopy);

        SPEND_TYPE = SPEND_BOLTZMANN;

        saveChangeIndexes();

        //setUpJoinBot();
        setUpRicochet();

        setBalance();

        enableReviewButton(false);

        setUpBoltzman();

        if (getIntent().getExtras().containsKey("preselected")) {
            setUpPreselecteUtxoCoins();
        } else {
            setUpCompositeDisposables();
        }

        if (getIntent().getExtras().containsKey("isDonation") && getIntent().getExtras().getBoolean("isDonation")) {
            if (SamouraiWallet.getInstance().isTestNet())
                setToAddress(DONATION_ADDRESS_TESTNET);
            else
                setToAddress(DONATION_ADDRESS_MAINNET);
            toAddressEditText.setEnabled(false);
        }

        validateSpend();

        checkDeepLinks();

        if (AppUtil.getInstance(SendActivity.this).isBroadcastDisabled()) {
            SPEND_TYPE = sendTransactionDetailsView.getStoneWallSwitch().isChecked() ? SPEND_BOLTZMANN : SPEND_SIMPLE;
            premiumAddons.setVisibility(View.GONE);
            addonsNotAvailableMessage.setVisibility(View.VISIBLE);
        }
    }

    private boolean isEnabledJoinbot() {
        return !AppUtil.getInstance(SendActivity.this).isBroadcastDisabled(); /*&&
                joinbotSwitch.isChecked();*/
    }

    private boolean isEnabledRicochet() {
        return !AppUtil.getInstance(SendActivity.this).isBroadcastDisabled() &&
                ricochetHopsSwitch.isChecked();
    }

    private boolean isEnabledRicochetStaggered() {
        return !AppUtil.getInstance(SendActivity.this).isBroadcastDisabled() &&
                ricochetStaggeredDelivery.isChecked();
    }

    private void setUpCompositeDisposables() {

        final Disposable disposable = APIFactory.getInstance(getApplicationContext())
                .walletBalanceObserver
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(aLong -> setBalance(), Throwable::printStackTrace);
        compositeDisposables.add(disposable);

        // Update fee
        final Disposable feeDisposable = Observable.fromCallable(() -> APIFactory
                        .getInstance(getApplicationContext()).loadFees())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(t -> {}, Throwable::printStackTrace);
        compositeDisposables.add(feeDisposable);

        if (getIntent().getExtras() != null) {
            if (!getIntent().getExtras().containsKey("balance")) {
                return;
            }
            balance = getIntent().getExtras().getLong("balance");
        }
    }

    private void setUpPreselecteUtxoCoins() {
        preselectedUTXOs = PreSelectUtil.getInstance().getPreSelected(getIntent().getExtras().getString("preselected"));
        setBalance();

        boolean premiumAddonsRicochetVisible = true;
        boolean premiumAddonsJoinbotVisible = true;

        if (CollectionUtils.isNotEmpty(preselectedUTXOs) && balance < 1_000_000l) {
            premiumAddonsRicochetVisible = false;
        }
        if (! isJoinbotPossibleWithCurrentUserUTXOs(
                this,
                isPostmixAccount(),
                amount,
                preselectedUTXOs)) {
            premiumAddonsJoinbotVisible = false;
        }

        if (!premiumAddonsRicochetVisible && !premiumAddonsJoinbotVisible) {
            addonsNotAvailableMessage.setVisibility(View.VISIBLE);
            addonsNotAvailableMessage.setText(R.string.note_privacy_addons_are_not_available_for_selected_utxo_s);
            premiumAddons.setVisibility(View.GONE);
            if (SPEND_TYPE == SPEND_RICOCHET || isEnabledRicochet()) {
                autoUncheckRicochetSwitch();
            }
            if (SPEND_TYPE == SPEND_JOINBOT || isEnabledJoinbot()) {
                autoUncheckJoinbotSwitch();
            }
        } else if (!premiumAddonsRicochetVisible) {
            addonsNotAvailableMessage.setVisibility(View.VISIBLE);
            addonsNotAvailableMessage.setText(R.string.note_some_privacy_addons_are_not_available_for_selected_utxo_s);
            premiumAddonsRicochet.setVisibility(View.GONE);
            if (SPEND_TYPE == SPEND_RICOCHET || isEnabledRicochet()) {
                autoUncheckRicochetSwitch();
            }
        } else if (!premiumAddonsJoinbotVisible) {
            addonsNotAvailableMessage.setVisibility(View.VISIBLE);
            addonsNotAvailableMessage.setText(R.string.note_some_privacy_addons_are_not_available_for_selected_utxo_s);
            try {
                premiumAddonsJoinbot.setVisibility(View.GONE);
            } catch (Exception ignored) {}
            if (SPEND_TYPE == SPEND_JOINBOT || isEnabledJoinbot()) {
                autoUncheckJoinbotSwitch();
            }
        }
    }

    public View createTag(String text) {
        float scale = getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams lparams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView textView = new TextView(getApplicationContext());
        textView.setText(text);
        textView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.white));
        textView.setLayoutParams(lparams);
        textView.setBackgroundResource(R.drawable.tag_round_shape);
        textView.setPadding((int) (8 * scale + 0.5f), (int) (6 * scale + 0.5f), (int) (8 * scale + 0.5f), (int) (6 * scale + 0.5f));
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        return textView;
    }


    @Override
    protected void onResume() {
        super.onResume();

        AppUtil.getInstance(SendActivity.this).setIsInForeground(true);

        AppUtil.getInstance(SendActivity.this).checkTimeOut();

        try {
            new Handler().postDelayed(this::setBalance, 300);
        } catch (Exception ex) {

        }

    }

    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener = (compoundButton, checked) -> {
        if (compoundButton.isPressed()) {
            if (account == WhirlpoolMeta.getInstance(getApplicationContext()).getWhirlpoolPostmix() && !checked) {
                compoundButton.setChecked(true);
                return;
            }
            SPEND_TYPE = checked ? SPEND_BOLTZMANN : SPEND_SIMPLE;
            stoneWallChecked = checked;
            compoundButton.setChecked(checked);
            new Handler().postDelayed(this::prepareSpend, 100);
        }
    };

    private void setUpBoltzman() {
        sendTransactionDetailsView.getStoneWallSwitch().setChecked(true);
        sendTransactionDetailsView.getStoneWallSwitch().setEnabled(true);
        sendTransactionDetailsView.enableStonewall(true);
        sendTransactionDetailsView.getStoneWallSwitch().setOnCheckedChangeListener(onCheckedChangeListener);
    }

    private void checkRicochetPossibility() {
        amount = getSatValue(getBtcAmountFromWidget());
        if (amount < max(0, balance - (RicochetMeta.samouraiFeeAmountV2.add(BigInteger.valueOf(50000L))).longValue()))  {
            ricochetDesc.setAlpha(1f);
            ricochetTitle.setAlpha(1f);
            ricochetHopsSwitch.setAlpha(1f);
            ricochetHopsSwitch.setEnabled(true);
            if (isEnabledRicochet()) {
                ricochetStaggeredOptionGroup.setVisibility(View.VISIBLE);
                SPEND_TYPE = SPEND_RICOCHET;
            }
        } else {
            ricochetStaggeredOptionGroup.setVisibility(View.GONE);
            ricochetDesc.setAlpha(.6f);
            ricochetTitle.setAlpha(.6f);
            ricochetHopsSwitch.setAlpha(.6f);
            ricochetHopsSwitch.setEnabled(false);
            SPEND_TYPE = sendTransactionDetailsView.getStoneWallSwitch().isChecked() ? SPEND_BOLTZMANN : SPEND_SIMPLE;
        }
    }

    private void enableReviewButton(boolean enable) {
        btnReview.setEnabled(enable);
        final ColorStateList colorStateList = ContextCompat.getColorStateList(
                this,
                enable ? R.color.white : R.color.disabled_grey);
        btnReview.setBackgroundTintList(colorStateList);
    }

    private void setFeeLabels() {
        float sliderValue = (((float) feeSeekBar.getValue()) / feeSeekBar.getValueTo());

        float sliderInPercentage = sliderValue * 100;

        if (sliderInPercentage < 33) {
            tvSelectedFeeRateLayman.setText(R.string.low);
        } else if (sliderInPercentage > 33 && sliderInPercentage < 66) {
            tvSelectedFeeRateLayman.setText(R.string.normal);
        } else if (sliderInPercentage > 66) {
            tvSelectedFeeRateLayman.setText(R.string.urgent);

        }
    }

    private void setFee(double fee) {

        double sanitySat = FeeUtil.getInstance().getHighFee().getDefaultPerKB().doubleValue() / 1000.0;
        final long sanityValue;
        if (sanitySat < 10.0) {
            sanityValue = 15L;
        } else {
            sanityValue = (long) (sanitySat * 1.5);
        }

        //        String val  = null;
        double d = FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().doubleValue() / 1000.0;
        NumberFormat decFormat = NumberFormat.getInstance(Locale.US);
        decFormat.setMaximumFractionDigits(3);
        decFormat.setMinimumFractionDigits(0);
        double customValue = 0.0;


        try {
            customValue = (double) fee;
        } catch (Exception e) {
            Toast.makeText(this, R.string.custom_fee_too_low, Toast.LENGTH_SHORT).show();
            return;
        }


        SuggestedFee suggestedFee = new SuggestedFee();
        suggestedFee.setStressed(false);
        suggestedFee.setOK(true);
        suggestedFee.setDefaultPerKB(BigInteger.valueOf((long) (customValue * 1000.0)));
        FeeUtil.getInstance().setSuggestedFee(suggestedFee);
        prepareSpend();

    }

    private void setUpJoinBot() {

        joinbotSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            hideKeyboard();

            if (AppUtil.getInstance(SendActivity.this).isBroadcastDisabled()) {
                return;
            }

            if (isChecked) {
                strPcodeCounterParty = BIP47Meta.getMixingPartnerCode();
                ricochetHopsSwitch.setChecked(false);
                SPEND_TYPE = SPEND_JOINBOT;
                selectedCahootsType = SelectCahootsType.type.MULTI_SOROBAN;
                PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_JOINBOT, true);
            } else {
                strPcodeCounterParty = null;
                SPEND_TYPE = sendTransactionDetailsView.getStoneWallSwitch().isChecked() ? SPEND_BOLTZMANN : SPEND_SIMPLE;
                selectedCahootsType = SelectCahootsType.type.NONE;
                PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_JOINBOT, false);
            }
        });
        joinbotSwitch.setChecked(PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_JOINBOT, false));
        //checkValidForJoinbot();
    }

    private void autoUncheckJoinbotSwitch() {
        final boolean checked = PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_JOINBOT, false);
        //joinbotSwitch.setChecked(false);
        PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_JOINBOT, checked);
    }

    private void autoUncheckRicochetSwitch() {
        final boolean checked = PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_RICOCHET, false);
        ricochetHopsSwitch.setChecked(false);
        PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_RICOCHET, checked);
    }

    private void setUpRicochet() {
        ricochetHopsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            hideKeyboard();

            if (AppUtil.getInstance(SendActivity.this).isBroadcastDisabled()) {
                return;
            }

            /*
            if (isChecked) {
                joinbotSwitch.setChecked(false);
            }
             */
            sendTransactionDetailsView.enableForRicochet(isChecked);
            ricochetStaggeredOptionGroup.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (isChecked) {
                SPEND_TYPE = SPEND_RICOCHET;
                PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_RICOCHET, true);
            } else {
                SPEND_TYPE = sendTransactionDetailsView.getStoneWallSwitch().isChecked() ? SPEND_BOLTZMANN : SPEND_SIMPLE;
                PrefsUtil.getInstance(this).setValue(PrefsUtil.USE_RICOCHET, false);
            }

            if (isChecked) {
                ricochetStaggeredOptionGroup.setVisibility(View.VISIBLE);
            } else {
                ricochetStaggeredOptionGroup.setVisibility(View.GONE);
            }
        });
        ricochetHopsSwitch.setChecked(PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_RICOCHET, false));

        if (isEnabledRicochet()) {
            ricochetStaggeredOptionGroup.setVisibility(View.VISIBLE);
        } else {
            ricochetStaggeredOptionGroup.setVisibility(View.GONE);

        }
        ricochetStaggeredDelivery.setChecked(PrefsUtil.getInstance(this).getValue(PrefsUtil.RICOCHET_STAGGERED, false));

        ricochetStaggeredDelivery.setOnCheckedChangeListener((compoundButton, isChecked) -> {

            hideKeyboard();

            if (AppUtil.getInstance(SendActivity.this).isBroadcastDisabled()) {
                return;
            }
            PrefsUtil.getInstance(this).setValue(PrefsUtil.RICOCHET_STAGGERED, isChecked);
            // Handle staggered delivery option
        });
    }

    private Completable setUpRicochetFees() {
        IHttpClient httpClient = new AndroidHttpClient(WebUtil.getInstance(getApplicationContext()));
            XManagerClient xManagerClient = new XManagerClient(httpClient, SamouraiWallet.getInstance().isTestNet(), SamouraiTorManager.INSTANCE.isConnected());
        if (PrefsUtil.getInstance(this).getValue(PrefsUtil.USE_RICOCHET, false)) {
            Completable completable = Completable.fromCallable(() -> {
                String feeAddress = xManagerClient.getAddressOrDefault(XManagerService.RICOCHET);
                RicochetMeta.getInstance(getApplicationContext()).setRicochetFeeAddress(feeAddress);
                return true;
            });
            //Set BIP47 Fee address if the tx is
            if (strPCode != null) {
                Completable pcode = Completable.fromCallable(() -> {
                    String address = xManagerClient.getAddressOrDefault(XManagerService.BIP47);
                    SendNotifTxFactory.getInstance().setAddress(address);
                    return true;
                });
                return Completable.concatArray(completable, pcode);
            } else {
                return completable;
            }
        } else {
            return Completable.complete();
        }
    }

    public boolean isPostmixAccount() {
        return account == WhirlpoolMeta.getInstance(SendActivity.this).getWhirlpoolPostmix();
    }

    private void setBalance() {

        try {
            if (isPostmixAccount()) {
                balance = APIFactory.getInstance(SendActivity.this).getXpubPostMixBalance();
                selectableBalance = balance;
            } else {
                balance = APIFactory.getInstance(SendActivity.this).getXpubBalance();
                selectableBalance = balance;
            }
        } catch (java.lang.NullPointerException npe) {
            npe.printStackTrace();
        }

        if (getIntent().getExtras().containsKey("preselected")) {
            //Reloads preselected utxo's if it changed on last call
            preselectedUTXOs = PreSelectUtil.getInstance().getPreSelected(getIntent().getExtras().getString("preselected"));

            if (preselectedUTXOs != null && preselectedUTXOs.size() > 0) {

                //Checks utxo's state, if the item is blocked it will be removed from preselectedUTXOs
                for (int i = preselectedUTXOs.size()-1; i >= 0; --i) {
                    final UTXOCoin coin = preselectedUTXOs.get(i);
                    if (BlockedUTXO.getInstance().containsAny(coin.hash, coin.idx)) {
                        preselectedUTXOs.remove(i);
                    }
                }
                long amount = 0;
                for (final UTXOCoin utxo : preselectedUTXOs) {
                    amount += utxo.amount;
                }
                balance = amount;
            } else {
                ;
            }

        }


        final String strAmount;
        strAmount = FormatsUtil.formatBTC(balance);

        if (account == 0) {
            tvMaxAmount.setOnClickListener(view -> {
                btcEditText.setText(strAmount.replace("BTC", "").trim());
            });
        }
        tvMaxAmount.setOnLongClickListener(view -> {
            setBalance();
            return true;
        });

        tvMaxAmount.setText(strAmount);

        if (!AppUtil.getInstance(getApplication()).isOfflineMode())
            if (balance == 0L && !APIFactory.getInstance(getApplicationContext()).walletInit) {
                //some time, user may navigate to this activity even before wallet initialization completes
                //so we will set a delay to reload balance info
                Disposable disposable = Completable.timer(700, TimeUnit.MILLISECONDS, AndroidSchedulers.mainThread())
                        .subscribe(this::setBalance);
                compositeDisposables.add(disposable);
                if (!shownWalletLoadingMessage) {
                    Snackbar.make(tvMaxAmount.getRootView(), "Please wait... your wallet is still loading ", Snackbar.LENGTH_LONG).show();
                    shownWalletLoadingMessage = true;
                }

            }
    }

    private void checkDeepLinks() {
        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            String strUri = extras.getString("uri");
            if (extras.containsKey("amount")) {
                DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
                format.setMaximumFractionDigits(8);
                btcEditText.setText(format.format(getBtcValue(extras.getDouble("amount"))));
            }

            if (extras.getString("pcode") != null)
                strPCode = extras.getString("pcode");

            if (strPCode != null && strPCode.length() > 0) {
                processPCode(strPCode, null);
            } else if (strUri != null && strUri.length() > 0) {
                processScan(strUri);
            }
            new Handler().postDelayed(this::validateSpend, 800);
        }
    }

    private TextWatcher BTCWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            satEditText.removeTextChangedListener(satWatcher);
            btcEditText.removeTextChangedListener(this);

            try {
                if (editable.toString().length() == 0) {
                    satEditText.setText("0");
                    btcEditText.setText("");
                    satEditText.setSelection(satEditText.getText().length());
                    satEditText.addTextChangedListener(satWatcher);
                    btcEditText.addTextChangedListener(this);
                    return;
                }

                Double btc = Double.parseDouble(String.valueOf(editable));

                if (btc > SatoshiBitcoinUnitHelper.MAX_POSSIBLE_BTC) {
                    btcEditText.setText("0.00");
                    btcEditText.setSelection(btcEditText.getText().length());
                    satEditText.setText("0");
                    satEditText.setSelection(satEditText.getText().length());
                    Toast.makeText(SendActivity.this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
                } else {
                    DecimalFormat format = (DecimalFormat) DecimalFormat.getInstance(Locale.US);
                    DecimalFormatSymbols symbols = format.getDecimalFormatSymbols();
                    String defaultSeparator = Character.toString(symbols.getDecimalSeparator());
                    int max_len = 8;
                    NumberFormat btcFormat = NumberFormat.getInstance(Locale.US);
                    btcFormat.setMaximumFractionDigits(max_len + 1);

                    try {
                        double d = NumberFormat.getInstance(Locale.US).parse(editable.toString()).doubleValue();
                        String s1 = btcFormat.format(d);
                        if (s1.indexOf(defaultSeparator) != -1) {
                            String dec = s1.substring(s1.indexOf(defaultSeparator));
                            if (dec.length() > 0) {
                                dec = dec.substring(1);
                                if (dec.length() > max_len) {
                                    btcEditText.setText(s1.substring(0, s1.length() - 1));
                                    btcEditText.setSelection(btcEditText.getText().length());
                                    editable = btcEditText.getEditableText();
                                    btc = Double.parseDouble(btcEditText.getText().toString());
                                }
                            }
                        }
                    } catch (NumberFormatException nfe) {
                        ;
                    } catch (ParseException pe) {
                        ;
                    }

                    final Long sats = getSatValue(Double.valueOf(btc));
                    satEditText.setText(formattedSatValue(sats));

                    checkRicochetPossibility();
                }

//
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
            satEditText.addTextChangedListener(satWatcher);
            btcEditText.addTextChangedListener(this);
            validateSpend();


        }
    };

    private TextWatcher AddressWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            if (editable.toString().length() != 0) {
                validateSpend();
            } else {
                setToAddress("");
            }
        }
    };

    private String formattedSatValue(Object number) {
        NumberFormat nformat = NumberFormat.getNumberInstance(Locale.US);
        DecimalFormat decimalFormat = (DecimalFormat) nformat;
        decimalFormat.applyPattern("#,###");
        return decimalFormat.format(number).replace(",", " ");
    }

    private TextWatcher satWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            satEditText.removeTextChangedListener(this);
            btcEditText.removeTextChangedListener(BTCWatcher);

            try {
                if (editable.toString().length() == 0) {
                    btcEditText.setText("0.00");
                    satEditText.setText("");
                    satEditText.addTextChangedListener(this);
                    btcEditText.addTextChangedListener(BTCWatcher);
                    return;
                }
                String cleared_space = editable.toString().replace(" ", "")
                        .replace(" ", "").replace(String.valueOf(new DecimalFormatSymbols(Locale.getDefault()).getGroupingSeparator()), "");

                Double sats = Double.parseDouble(cleared_space);
                Double btc = getBtcValue(sats);
                String formatted = formattedSatValue(sats);


                satEditText.setText(formatted);
                satEditText.setSelection(formatted.length());
                btcEditText.setText(String.format(Locale.ENGLISH, "%.8f", btc));
                if (btc > SatoshiBitcoinUnitHelper.MAX_POSSIBLE_BTC) {
                    btcEditText.setText("0.00");
                    btcEditText.setSelection(btcEditText.getText().length());
                    satEditText.setText("0");
                    satEditText.setSelection(satEditText.getText().length());
                    Toast.makeText(SendActivity.this, R.string.invalid_amount, Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();

            }
            satEditText.addTextChangedListener(this);
            btcEditText.addTextChangedListener(BTCWatcher);

            checkRicochetPossibility();
            validateSpend();

        }
    };

    private void setToAddress(String string) {
        tvToAddress.setText(string);
        toAddressEditText.removeTextChangedListener(AddressWatcher);
        toAddressEditText.setText(string);
        toAddressEditText.setSelection(toAddressEditText.getText().length());
        toAddressEditText.addTextChangedListener(AddressWatcher);
    }

    private String getToAddress() {
        if (toAddressEditText.getText().toString().trim().length() != 0) {
            return toAddressEditText.getText().toString();
        }
        if (tvToAddress.getText().toString().length() != 0) {
            return tvToAddress.getText().toString();
        }
        return "";
    }

    private void reviewTransaction() {
        setUpBoltzman();
        if (validateSpend()) {
            launchReviewTxActivity();
        }
    }

    private void launchReviewTxActivity() {

        if (amount == balance) {
            SPEND_TYPE = SPEND_SIMPLE;
        }

        final Intent intent = new Intent(SendActivity.this, ReviewTxActivity.class);
        intent.putExtra("_account", account);
        intent.putExtra("sendAmount", getSatValue(getBtcAmountFromWidget()));
        intent.putExtra("sendAddress", nonNull(strDestinationBTCAddress) ? strDestinationBTCAddress : getToAddress());
        if (nonNull(strDestinationBTCAddress)) {
            intent.putExtra("sendAddressLabel", getToAddress());
        }
        intent.putExtra("sendType", SPEND_TYPE);
        intent.putExtra("ricochetStaggeredDelivery", ricochetStaggeredDelivery.isChecked());

        if (getIntent().hasExtra("preselected")) {
            intent.putExtra("preselected", getIntent().getStringExtra("preselected"));
        }

        startActivity(intent);
    }

    private void reviewTransactionSafely() {
/*
        if (! checkValidForJoinbot()) {
            return;
        }
*/
        amount = SatoshiBitcoinUnitHelper.getSatValue(getBtcAmountFromWidget());

        if (amount == balance && balance == selectableBalance) {

            int warningMessage = R.string.full_spend_warning;
            if (account == WhirlpoolMeta.getInstance(getApplicationContext()).getWhirlpoolPostmix()) {
                warningMessage = R.string.postmix_full_spend;
            }
            MaterialAlertDialogBuilder dlg = new MaterialAlertDialogBuilder(SendActivity.this)
                    .setTitle(R.string.app_name)
                    .setMessage(warningMessage)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            dialog.dismiss();

                            reviewTransaction();

                        }

                    }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            dialog.dismiss();

                        }
                    });
            if (!isFinishing()) {
                dlg.show();
            }

        } else {
            reviewTransaction();
        }
    }

    private double getBtcAmountFromWidget() {
        try {
            return NumberFormat.getInstance(Locale.US)
                    .parse(btcEditText.getText().toString().trim())
                    .doubleValue();
        } catch (Exception e) {
            return 0d;
        }
    }

    private void hideKeyboard() {
        final InputMethodManager imm = (InputMethodManager) this.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(amountViewSwitcher.getWindowToken(), 0);
        }
    }

    private void hideMenus(boolean hide) {
        Toolbar toolbar = findViewById(R.id.toolbar_send);
        toolbar.getMenu().findItem(R.id.action_scan_qr).setVisible(!hide);
        toolbar.getMenu().findItem(R.id.select_paynym).setVisible(!hide);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        PreSelectUtil.getInstance().clear();
        if (compositeDisposables != null && !compositeDisposables.isDisposed())
            compositeDisposables.dispose();
    }

    synchronized private boolean prepareSpend() {

        try {

            btnSend.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.green_ui_2));

            if (SPEND_TYPE == SPEND_SIMPLE && stoneWallChecked) {
                SPEND_TYPE = SPEND_BOLTZMANN;
            }

            restoreChangeIndexes();

            amount = getSatValue(getBtcAmountFromWidget());

            if (selectedCahootsType.getCahootsType() == CahootsType.STOWAWAY) {
                setButtonForStowaway(true);
                return true;
            } else {
                setButtonForStowaway(false);
            }


            address = strDestinationBTCAddress == null ? toAddressEditText.getText().toString().trim() : strDestinationBTCAddress;

            boolean useLikeType = PrefsUtil.getInstance(SendActivity.this).getValue(PrefsUtil.USE_LIKE_TYPED_CHANGE, true);
            if (DojoUtil.getInstance(SendActivity.this).getDojoParams() != null && !DojoUtil.getInstance(SendActivity.this).isLikeType()) {
                useLikeType = false;
            }

            if (!useLikeType) {
                changeType = 84;
            } else if (FormatsUtil.getInstance().isValidBech32(address) || Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
                changeType = FormatsUtil.getInstance().isValidBech32(address) ? 84 : 49;
            } else {
                changeType = 44;
            }

            receivers = new HashMap<>();
            receivers.put(address, BigInteger.valueOf(amount));

            int countP2WSH_P2TR = 0;
            if(FormatsUtilGeneric.getInstance().isValidP2WSH_P2TR(address))    {
                countP2WSH_P2TR = 1;
            }

            if (isPostmixAccount()) {
                change_index = idxBIP84PostMixInternal;
            } else if (changeType == 84) {
                change_index = idxBIP84Internal;
            } else if (changeType == 49) {
                change_index = idxBIP49Internal;
            } else {
                change_index = idxBIP44Internal;
            }

            // if possible, get UTXO by input 'type': p2pkh, p2sh-p2wpkh or p2wpkh, else get all UTXO
            long neededAmount = 0L;
            if (FormatsUtil.getInstance().isValidBech32(address) || isPostmixAccount()) {
                neededAmount += FeeUtil.getInstance().estimatedFeeSegwit(0, 0, UTXOFactory.getInstance().getCountP2WPKH(), 4 - countP2WSH_P2TR, countP2WSH_P2TR).longValue();
//                    Log.d("SendActivity", "segwit:" + neededAmount);
            } else if (Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
                neededAmount += FeeUtil.getInstance().estimatedFeeSegwit(0, UTXOFactory.getInstance().getCountP2SH_P2WPKH(), 0, 4 - countP2WSH_P2TR, countP2WSH_P2TR).longValue();
//                    Log.d("SendActivity", "segwit:" + neededAmount);
            } else {
                neededAmount += FeeUtil.getInstance().estimatedFeeSegwit(UTXOFactory.getInstance().getCountP2PKH(), 0, 0, 4 - countP2WSH_P2TR, countP2WSH_P2TR).longValue();
//                    Log.d("SendActivity", "p2pkh:" + neededAmount);
            }
            neededAmount += amount;
            neededAmount += SamouraiWallet.bDust.longValue();

            // get all UTXO
            List<UTXO> utxos = new ArrayList<>();
            if (preselectedUTXOs != null && preselectedUTXOs.size() > 0) {
//            List<UTXO> utxos = preselectedUTXOs;
                // sort in descending order by value
                for (UTXOCoin utxoCoin : preselectedUTXOs) {
                    UTXO u = new UTXO();
                    List<MyTransactionOutPoint> outs = new ArrayList<MyTransactionOutPoint>();
                    outs.add(utxoCoin.getOutPoint());
                    u.setOutpoints(outs);
                    utxos.add(u);
                }
            } else {
                utxos = UTXOFactory.getInstance().getUTXOS(address, neededAmount, account);
            }

            List<UTXO> utxosP2WPKH = new ArrayList<>(APIFactory.getInstance(SendActivity.this).getUtxosP2WPKH(true));
            List<UTXO> utxosP2SH_P2WPKH = new ArrayList<>(APIFactory.getInstance(SendActivity.this).getUtxosP2SH_P2WPKH(true));
            List<UTXO> utxosP2PKH = new ArrayList<>(APIFactory.getInstance(SendActivity.this).getUtxosP2PKH(true));
            if ((preselectedUTXOs == null || preselectedUTXOs.size() == 0) && isPostmixAccount()) {
                utxos = new ArrayList<>(APIFactory.getInstance(SendActivity.this).getUtxosPostMix(true));
                utxosP2WPKH = new ArrayList<>(APIFactory.getInstance(SendActivity.this).getUtxosPostMix(true));
                utxosP2PKH.clear();
                utxosP2SH_P2WPKH.clear();
            }

            selectedUTXO = new ArrayList<>();
            long totalValueSelected = 0L;
            long change = 0L;
            BigInteger fee = null;
            boolean canDoBoltzmann = true;

//                Log.d("SendActivity", "amount:" + amount);
//                Log.d("SendActivity", "balance:" + balance);

            // insufficient funds
            if (amount > balance) {
                Toast.makeText(SendActivity.this, R.string.insufficient_funds, Toast.LENGTH_SHORT).show();
            }

            if (preselectedUTXOs != null) {
                canDoBoltzmann = false;
                if (SPEND_TYPE == SPEND_BOLTZMANN) {
                    SPEND_TYPE = SPEND_SIMPLE;
                }
            }
            // entire balance (can only be simple spend)
            else if (amount == balance) {
                // make sure we are using simple spend
                SPEND_TYPE = SPEND_SIMPLE;
                canDoBoltzmann = false;

//                    Log.d("SendActivity", "amount == balance");
                // take all utxos, deduct fee
                selectedUTXO.addAll(utxos);

                for (UTXO u : selectedUTXO) {
                    totalValueSelected += u.getValue();
                }

//                    Log.d("SendActivity", "balance:" + balance);
//                    Log.d("SendActivity", "total value selected:" + totalValueSelected);

            } else {
                ;
            }

            org.apache.commons.lang3.tuple.Pair<List<MyTransactionOutPoint>, List<TransactionOutput>> pair = null;
            if (SPEND_TYPE == SPEND_RICOCHET) {
                if (AppUtil.getInstance(getApplicationContext()).isOfflineMode()) {
                    Toast.makeText(getApplicationContext(), "You won't able to compose ricochet when you're on offline mode", Toast.LENGTH_SHORT).show();
                    return false;
                }

                boolean samouraiFeeViaBIP47 = false;
                if (BIP47Meta.getInstance().getOutgoingStatus(BIP47Meta.strSamouraiDonationPCode) == BIP47Meta.STATUS_SENT_CFM) {
                    samouraiFeeViaBIP47 = true;
                }
                final RicochetTransactionInfo ricochetTransactionInfo = RicochetMeta.getInstance(SendActivity.this)
                        .script(amount,
                                FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().longValue(),
                                address,
                                RicochetMeta.defaultNbHops,
                                strPCode,
                                samouraiFeeViaBIP47,
                                isEnabledRicochetStaggered(),
                                account);
                ricochetJsonObj = ricochetTransactionInfo.getRicochetScriptAsJson();
                if (ricochetJsonObj != null) {

                    try {
                        long totalAmount = ricochetJsonObj.getLong("total_spend");
                        if (totalAmount > balance) {
                            Toast.makeText(SendActivity.this, R.string.insufficient_funds, Toast.LENGTH_SHORT).show();
                            autoUncheckRicochetSwitch();
                            return false;
                        }
                        long hop0Fee = ricochetJsonObj.getJSONArray("hops").getJSONObject(0).getLong("fee");
                        long perHopFee = ricochetJsonObj.getJSONArray("hops").getJSONObject(0).getLong("fee_per_hop");

                        long ricochetFee = hop0Fee + (RicochetMeta.defaultNbHops * perHopFee);

                        if (selectedCahootsType == SelectCahootsType.type.NONE) {
                            tvTotalFee.setText(FormatsUtil.formatBTC(ricochetFee));
                        } else {
                            tvTotalFee.setText("__");
                        }

                        ricochetMessage = getText(R.string.ricochet_spend1) + " " + address + " " + getText(R.string.ricochet_spend2) + " " + FormatsUtil.formatBTC(totalAmount) + " " + getText(R.string.ricochet_spend3);

                        btnSend.setText("send ".concat(FormatsUtil.formatBTC(totalAmount)));
                        return true;

                    } catch (JSONException je) {
                        return false;
                    }

                }

                return true;
            } else if (SPEND_TYPE == SPEND_BOLTZMANN) {

                Log.d("SendActivity", "needed amount:" + neededAmount);

                List<UTXO> _utxos1 = null;
                List<UTXO> _utxos2 = null;

                long valueP2WPKH = UTXOFactory.getInstance().getTotalP2WPKH();
                long valueP2SH_P2WPKH = UTXOFactory.getInstance().getTotalP2SH_P2WPKH();
                long valueP2PKH = UTXOFactory.getInstance().getTotalP2PKH();
                if (isPostmixAccount()) {

                    valueP2WPKH = UTXOFactory.getInstance().getTotalPostMix();
                    valueP2SH_P2WPKH = 0L;
                    valueP2PKH = 0L;

                    utxosP2SH_P2WPKH.clear();
                    utxosP2PKH.clear();
                }

                Log.d("SendActivity", "value P2WPKH:" + valueP2WPKH);
                Log.d("SendActivity", "value P2SH_P2WPKH:" + valueP2SH_P2WPKH);
                Log.d("SendActivity", "value P2PKH:" + valueP2PKH);

                boolean selectedP2WPKH = false;
                boolean selectedP2SH_P2WPKH = false;
                boolean selectedP2PKH = false;

                if ((valueP2WPKH > (neededAmount * 2)) && isPostmixAccount()) {
                    Log.d("SendActivity", "set 1 P2WPKH 2x");
                    _utxos1 = utxosP2WPKH;
                    selectedP2WPKH = true;
                } else if ((valueP2WPKH > (neededAmount * 2)) && FormatsUtil.getInstance().isValidBech32(address)) {
                    Log.d("SendActivity", "set 1 P2WPKH 2x");
                    _utxos1 = utxosP2WPKH;
                    selectedP2WPKH = true;
                } else if (!FormatsUtil.getInstance().isValidBech32(address) && (valueP2SH_P2WPKH > (neededAmount * 2)) && Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
                    Log.d("SendActivity", "set 1 P2SH_P2WPKH 2x");
                    _utxos1 = utxosP2SH_P2WPKH;
                    selectedP2SH_P2WPKH = true;
                } else if (!FormatsUtil.getInstance().isValidBech32(address) && (valueP2PKH > (neededAmount * 2)) && !Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) {
                    Log.d("SendActivity", "set 1 P2PKH 2x");
                    _utxos1 = utxosP2PKH;
                    selectedP2PKH = true;
                } else if (valueP2WPKH > (neededAmount * 2)) {
                    Log.d("SendActivity", "set 1 P2WPKH 2x");
                    _utxos1 = utxosP2WPKH;
                    selectedP2WPKH = true;
                } else if (valueP2SH_P2WPKH > (neededAmount * 2)) {
                    Log.d("SendActivity", "set 1 P2SH_P2WPKH 2x");
                    _utxos1 = utxosP2SH_P2WPKH;
                    selectedP2SH_P2WPKH = true;
                } else if (valueP2PKH > (neededAmount * 2)) {
                    Log.d("SendActivity", "set 1 P2PKH 2x");
                    _utxos1 = utxosP2PKH;
                    selectedP2PKH = true;
                } else {
                    ;
                }

                if (_utxos1 == null || _utxos1.size() == 0) {
                    if (valueP2SH_P2WPKH > neededAmount) {
                        Log.d("SendActivity", "set 1 P2SH_P2WPKH");
                        _utxos1 = utxosP2SH_P2WPKH;
                        selectedP2SH_P2WPKH = true;
                    } else if (valueP2WPKH > neededAmount) {
                        Log.d("SendActivity", "set 1 P2WPKH");
                        _utxos1 = utxosP2WPKH;
                        selectedP2WPKH = true;
                    } else if (valueP2PKH > neededAmount) {
                        Log.d("SendActivity", "set 1 P2PKH");
                        _utxos1 = utxosP2PKH;
                        selectedP2PKH = true;
                    } else {
                        ;
                    }

                }

                if (_utxos1 != null && _utxos1.size() > 0) {
                    if (!selectedP2SH_P2WPKH && valueP2SH_P2WPKH > neededAmount) {
                        Log.d("SendActivity", "set 2 P2SH_P2WPKH");
                        _utxos2 = utxosP2SH_P2WPKH;
                        selectedP2SH_P2WPKH = true;
                    }
                    if (!selectedP2SH_P2WPKH && !selectedP2WPKH && valueP2WPKH > neededAmount) {
                        Log.d("SendActivity", "set 2 P2WPKH");
                        _utxos2 = utxosP2WPKH;
                        selectedP2WPKH = true;
                    }
                    if (!selectedP2SH_P2WPKH && !selectedP2WPKH && !selectedP2PKH && valueP2PKH > neededAmount) {
                        Log.d("SendActivity", "set 2 P2PKH");
                        _utxos2 = utxosP2PKH;
                        selectedP2PKH = true;
                    } else {
                        ;
                    }
                }

                if ((_utxos1 == null || _utxos1.size() == 0) && (_utxos2 == null || _utxos2.size() == 0)) {
                    // can't do boltzmann, revert to SPEND_SIMPLE
                    canDoBoltzmann = false;
                    SPEND_TYPE = SPEND_SIMPLE;
                } else {

                    Log.d("SendActivity", "boltzmann spend");

                    Collections.shuffle(_utxos1);
                    if (_utxos2 != null && _utxos2.size() > 0) {
                        Collections.shuffle(_utxos2);
                    }

                    // boltzmann spend (STONEWALL)
                    pair = SendFactory.getInstance(SendActivity.this).boltzmann(_utxos1, _utxos2, BigInteger.valueOf(amount), address, account);

                    if (pair == null) {
                        // can't do boltzmann, revert to SPEND_SIMPLE
                        canDoBoltzmann = false;
                        restoreChangeIndexes();
                        SPEND_TYPE = SPEND_SIMPLE;
                    } else {
                        canDoBoltzmann = true;
                    }
                }

            } else {
                ;
            }

            if (SPEND_TYPE == SPEND_SIMPLE && amount == balance && preselectedUTXOs == null) {
                // do nothing, utxo selection handles above
                ;
            }
            // simple spend (less than balance)
            else if (SPEND_TYPE == SPEND_SIMPLE || SPEND_TYPE == SPEND_JOINBOT) {
                List<UTXO> _utxos = utxos;
                // sort in ascending order by value
                Collections.sort(_utxos, UTXO_COMPARATOR_BY_VALUE);
                Collections.reverse(_utxos);

                // get smallest 1 UTXO > than spend + fee + dust
                for (UTXO u : _utxos) {
                    final Triple<Integer, Integer, Integer> outpointTypes = FeeUtil.getInstance().getOutpointCount(new Vector(u.getOutpoints()));
                    if (u.getValue() >= (amount +
                            SamouraiWallet.bDust.longValue() +
                            FeeUtil.getInstance().estimatedFeeSegwit(
                                    outpointTypes.getLeft(),
                                    outpointTypes.getMiddle(),
                                    outpointTypes.getRight(),
                                    2 - countP2WSH_P2TR,
                                    countP2WSH_P2TR).longValue())) {
                        selectedUTXO.add(u);
                        totalValueSelected += u.getValue();
                        Log.d("SendActivity", "spend type:" + SPEND_TYPE);
                        Log.d("SendActivity", "single output");
                        Log.d("SendActivity", "amount:" + amount);
                        Log.d("SendActivity", "value selected:" + u.getValue());
                        Log.d("SendActivity", "total value selected:" + totalValueSelected);
                        Log.d("SendActivity", "nb inputs:" + u.getOutpoints().size());
                        break;
                    }
                }

                if (selectedUTXO.size() == 0) {
                    // sort in descending order by value
                    Collections.sort(_utxos, new UTXO.UTXOComparator());
                    int selected = 0;
                    int p2pkh = 0;
                    int p2sh_p2wpkh = 0;
                    int p2wpkh = 0;

                    // get largest UTXOs > than spend + fee + dust
                    for (UTXO u : _utxos) {

                        selectedUTXO.add(u);
                        totalValueSelected += u.getValue();
                        selected += u.getOutpoints().size();

//                            Log.d("SendActivity", "value selected:" + u.getValue());
//                            Log.d("SendActivity", "total value selected/threshold:" + totalValueSelected + "/" + (amount + SamouraiWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFee(selected, 2).longValue()));

                        final Triple<Integer, Integer, Integer> outpointTypes = FeeUtil.getInstance().getOutpointCount(new Vector<>(u.getOutpoints()));
                        p2pkh += outpointTypes.getLeft();
                        p2sh_p2wpkh += outpointTypes.getMiddle();
                        p2wpkh += outpointTypes.getRight();
                        if (totalValueSelected >= (amount + SamouraiWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFeeSegwit(p2pkh, p2sh_p2wpkh, p2wpkh, 2 - countP2WSH_P2TR, countP2WSH_P2TR).longValue())) {
                            Log.d("SendActivity", "spend type:" + SPEND_TYPE);
                            Log.d("SendActivity", "multiple outputs");
                            Log.d("SendActivity", "amount:" + amount);
                            Log.d("SendActivity", "total value selected:" + totalValueSelected);
                            Log.d("SendActivity", "nb inputs:" + selected);
                            break;
                        }
                    }
                }

            } else if (pair != null) {

                selectedUTXO.clear();
                receivers.clear();

                long inputAmount = 0L;
                long outputAmount = 0L;

                for (MyTransactionOutPoint outpoint : pair.getLeft()) {
                    UTXO u = new UTXO();
                    List<MyTransactionOutPoint> outs = new ArrayList<>();
                    outs.add(outpoint);
                    u.setOutpoints(outs);
                    totalValueSelected += u.getValue();
                    selectedUTXO.add(u);
                    inputAmount += u.getValue();
                }

                for (TransactionOutput output : pair.getRight()) {
                    try {
                        Script script = new Script(output.getScriptBytes());
                        if (Bech32Util.getInstance().isP2WPKHScript(Hex.toHexString(output.getScriptBytes())) || Bech32Util.getInstance().isP2TRScript(Hex.toHexString(output.getScriptBytes()))) {
                            receivers.put(Bech32Util.getInstance().getAddressFromScript(script), BigInteger.valueOf(output.getValue().longValue()));
                        } else {
                            receivers.put(script.getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString(), BigInteger.valueOf(output.getValue().longValue()));
                        }
                        outputAmount += output.getValue().longValue();
                    } catch (Exception e) {
                        Toast.makeText(SendActivity.this, R.string.error_bip126_output, Toast.LENGTH_SHORT).show();
                        return false;
                    }
                }

                fee = BigInteger.valueOf(inputAmount - outputAmount);

            } else {
                Toast.makeText(SendActivity.this, R.string.cannot_select_utxo, Toast.LENGTH_SHORT).show();
                return false;
            }

            if (selectedUTXO.size() > 0) {

                // estimate fee for simple spend, already done if boltzmann
                if (SPEND_TYPE == SPEND_SIMPLE || SPEND_TYPE == SPEND_JOINBOT) {

                    List<MyTransactionOutPoint> outpoints = new ArrayList<>();
                    for (UTXO utxo : selectedUTXO) {
                        outpoints.addAll(utxo.getOutpoints());
                    }
                    final Triple<Integer, Integer, Integer> outpointTypes = FeeUtil.getInstance().getOutpointCount(new Vector(outpoints));
                    if (amount == balance) {
                        fee = FeeUtil.getInstance().estimatedFeeSegwit(outpointTypes.getLeft(), outpointTypes.getMiddle(), outpointTypes.getRight(), 1 - countP2WSH_P2TR, countP2WSH_P2TR);
                        amount -= fee.longValue();
                        receivers.clear();
                        receivers.put(address, BigInteger.valueOf(amount));

                        //
                        // fee sanity check
                        //
                        restoreChangeIndexes();
                        Transaction tx = SendFactory.getInstance(SendActivity.this).makeTransaction(outpoints, receivers);
                        tx = SendFactory.getInstance(SendActivity.this).signTransaction(tx, account);
                        byte[] serialized = tx.bitcoinSerialize();
                        Log.d("SendActivity", "size:" + serialized.length);
                        Log.d("SendActivity", "vsize:" + tx.getVirtualTransactionSize());
                        Log.d("SendActivity", "fee:" + fee.longValue());
                        if ((tx.hasWitness() && (fee.longValue() < tx.getVirtualTransactionSize())) || (!tx.hasWitness() && (fee.longValue() < serialized.length))) {
                            Toast.makeText(SendActivity.this, R.string.insufficient_fee, Toast.LENGTH_SHORT).show();
                            return false;
                        }
                        //
                        //
                        //

                    } else {
                        fee = FeeUtil.getInstance().estimatedFeeSegwit(
                                outpointTypes.getLeft(),
                                outpointTypes.getMiddle(),
                                outpointTypes.getRight(),
                                2 - countP2WSH_P2TR,
                                countP2WSH_P2TR);
                    }
                }

                Log.d("SendActivity", "spend type:" + SPEND_TYPE);
                Log.d("SendActivity", "amount:" + amount);
                Log.d("SendActivity", "total value selected:" + totalValueSelected);
                Log.d("SendActivity", "fee:" + fee.longValue());
                Log.d("SendActivity", "nb inputs:" + selectedUTXO.size());

                change = totalValueSelected - (amount + fee.longValue());
//                    Log.d("SendActivity", "change:" + change);

                if (change > 0L && change < SamouraiWallet.bDust.longValue() && SPEND_TYPE == SPEND_SIMPLE) {
                    feeSeekBar.setEnabled(false);
                    MaterialAlertDialogBuilder dlg = new MaterialAlertDialogBuilder(SendActivity.this)
                            .setTitle(R.string.app_name)
                            .setMessage(R.string.change_is_dust)
                            .setCancelable(false)
                            .setOnDismissListener(dialog -> feeSeekBar.setEnabled(true))
                            .setPositiveButton(R.string.ok, (dialog, whichButton) -> dialog.dismiss());
                    if (!isFinishing()) {
                        dlg.show();
                    }

                    return false;
                }

                _change = change;
                final BigInteger _fee = fee;

                String dest = null;
                if (strPCode != null && strPCode.length() > 0) {
                    dest = BIP47Meta.getInstance().getDisplayLabel(strPCode);
                } else {
                    dest = address;
                }

                strCannotDoBoltzmann = "";
                if (SendAddressUtil.getInstance().get(address) == 1) {
                    strPrivacyWarning = getString(R.string.send_privacy_warning) + "\n\n";
                } else {
                    strPrivacyWarning = "";
                }

                if (SPEND_TYPE == SPEND_BOLTZMANN) {
                    sendTransactionDetailsView.enableStonewall(canDoBoltzmann);
                    sendTransactionDetailsView.getStoneWallSwitch().setChecked(canDoBoltzmann);
                } else {
                    sendTransactionDetailsView.enableStonewall(false);
                }

                if (!canDoBoltzmann) {
                    restoreChangeIndexes();
                    if (isPostmixAccount()) {
                        strCannotDoBoltzmann = getString(R.string.boltzmann_cannot) + "\n\n";
                    }
                }

                if (account == WhirlpoolConst.WHIRLPOOL_POSTMIX_ACCOUNT) {
                    if (SPEND_TYPE == SPEND_SIMPLE) {
                        strCannotDoBoltzmann = getString(R.string.boltzmann_cannot) + "\n\n";
                    }
                }
                message = strCannotDoBoltzmann + strPrivacyWarning + "Send " + FormatsUtil.formatBTCWithoutUnit(amount) + " to " + dest + " (fee:" + FormatsUtil.formatBTCWithoutUnit(_fee.longValue()) + ")?\n";

                if (selectedCahootsType == SelectCahootsType.type.NONE) {
                    boolean is_sat_prefs = PrefsUtil.getInstance(SendActivity.this).getValue(PrefsUtil.IS_SAT, true);
                    if (is_sat_prefs) {
                        tvTotalFee.setText(FormatsUtil.formatSats(fee.longValue()) + "s");
                    } else {
                        tvTotalFee.setText(FormatsUtil.formatBTC(fee.longValue()));
                    }
                    calculateTransactionSize(_fee);
                } else {
                    tvTotalFee.setText("__");
                }

                if (amount + fee.longValue() > balance) {
                    btnSend.setEnabled(false);
                    btnReview.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.disabled_grey));
                    btnSend.setText(R.string.send);
                    feeSeekBar.setEnabled(false);
                    MaterialAlertDialogBuilder dlg = new MaterialAlertDialogBuilder(SendActivity.this)
                            .setTitle(R.string.app_name)
                            .setMessage(R.string.insufficient_amount_for_fee)
                            .setCancelable(false)
                            .setOnDismissListener(dialog -> feeSeekBar.setEnabled(true))
                            .setPositiveButton(R.string.ok, (dialog, whichButton) -> dialog.dismiss());
                    if (!isFinishing()) {
                        dlg.show();
                    }

                    return false;
                }

                btnSend.setEnabled(true);

                btnSend.setText("send ".concat(FormatsUtil.formatBTC(_fee.add(BigInteger.valueOf(amount)).longValue())));

                switch (selectedCahootsType) {
                    case NONE: {
                        sendTransactionDetailsView.showStonewallx1Layout();
                        // for ricochet entropy will be 0 always
                        if (SPEND_TYPE == SPEND_RICOCHET) {
                            break;
                        }

                        if (receivers.size() <= 1) {
                            sendTransactionDetailsView.setEntropyBarStoneWallX1ZeroBits();
                            break;
                        }
                        if (receivers.size() > 8) {
                            sendTransactionDetailsView.setEntropyBarStoneWallX1(null);
                            break;
                        }

                        if(entropyDisposable != null) {
                            Log.d("SendActivity", "Disposing of observable...");
                            entropyDisposable.dispose();
                        }

                        if(entropyDisposable == null || entropyDisposable.isDisposed()) {
                            Log.d("SendActivity", "Creating new observable...");
                            entropyDisposable = ReviewTxModel.reactiveCalculateEntropy(selectedUTXO, receivers)
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribeOn(Schedulers.computation())
                                    .doOnSuccess(txProcessorResult -> {
                                        sendTransactionDetailsView.setEntropyBarStoneWallX1(txProcessorResult);
                                    })
                                    .doOnError(throwable -> {
                                        sendTransactionDetailsView.setEntropyBarStoneWallX1(null);
                                        throwable.printStackTrace();
                                    })
                                    .doOnDispose(() -> entropyDisposable = null)
                                    .subscribe();
                        }

                        break;
                    }
                    default: {
                        switch (selectedCahootsType.getCahootsType()) {
                            case MULTI:
                            case STONEWALLX2:
                                sendTransactionDetailsView.showStonewallX2Layout(
                                        getApplicationContext(),
                                        selectedCahootsType.getCahootsMode());
                                btnSend.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.blue_ui_2));
                                btnSend.setText(StringUtils.upperCase(getString(R.string.start_joinbot_transaction)));
                                btnSend.setPadding(0, 0, 0, 0);
                                sendTransactionDetailsView.getTransactionReview().findViewById(R.id.transaction_push_icon).setVisibility(View.INVISIBLE);
                                break;

                            case STOWAWAY:
                                sendTransactionDetailsView.showStowawayLayout(selectedCahootsType.getCahootsMode(), getParticipantLabel());
                                btnSend.setBackgroundResource(R.drawable.button_blue);
                                btnSend.setText(getString(R.string.begin_stowaway));
                                break;

                            default:
                                btnSend.setBackgroundResource(R.drawable.button_green);
                                btnSend.setText("send ".concat(FormatsUtil.formatBTC(amount)));
                        }
                    }
                }
                return true;
            }
            return false;
        } catch (Exception exception) {
            if (APIFactory.getInstance(getApplicationContext()).walletInit) return false;
            else if (exception.getMessage() != null) {
                new MaterialAlertDialogBuilder(this)
                        .setMessage("Exception ".concat(exception.getMessage()))
                        .setTitle("Error")
                        .setPositiveButton(R.string.ok, (dialog, which) -> dialog.dismiss())
                        .show();
            } else {
                Snackbar.make(amountViewSwitcher.getRootView(), "Error: unable to compose transaction", BaseTransientBottomBar.LENGTH_LONG).show();
            }
            return false;
        }
    }

    private String getParticipantLabel() {
        if (nonNull(strPcodeCounterParty)) {
            return BIP47Meta.getInstance().getDisplayLabel(strPcodeCounterParty);
        }
        return null;
    }

    private void setButtonForStowaway(boolean prepare) {
        if (prepare) {
            // Sets view with stowaway message
            // also hides overlay push icon from button
            sendTransactionDetailsView.showStowawayLayout(selectedCahootsType.getCahootsMode(), getParticipantLabel());
            btnSend.setBackgroundResource(R.drawable.button_blue);
            btnSend.setText(getString(R.string.begin_stowaway));
            sendTransactionDetailsView.getTransactionReview().findViewById(R.id.transaction_push_icon).setVisibility(View.INVISIBLE);
            btnSend.setPadding(0, 0, 0, 0);
        } else {
            // resets the changes made for stowaway
            int paddingDp = 12;
            float density = getResources().getDisplayMetrics().density;
            int paddingPixel = (int) (paddingDp * density);
            btnSend.setBackgroundResource(R.drawable.button_green);
            sendTransactionDetailsView.getTransactionReview().findViewById(R.id.transaction_push_icon).setVisibility(View.VISIBLE);
            btnSend.setPadding(0, paddingPixel, 0, 0);
        }

    }

    private void initiateSpend() {
        final long feeds = FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().longValue();
        if (CahootsMode.MANUAL.equals(selectedCahootsType.getCahootsMode())) {
            // Cahoots manual
            final Intent intent = ManualCahootsActivity.createIntentSender(
                    this,
                    account,
                    selectedCahootsType.getCahootsType(),
                    Math.round(feeds/1000d),
                    amount,
                    address,
                    strPCode,
                    EMPTY);
            startActivity(intent);
            return;
        }
        if (CahootsMode.SOROBAN.equals(selectedCahootsType.getCahootsMode())) {
            /*
            if (!checkValidForJoinbot()) {
                return;
            }
             */
            // Cahoots online
            final Intent intent = SorobanCahootsActivity.createIntentSender(
                    getApplicationContext(),
                    account,
                    selectedCahootsType.getCahootsType(),
                    amount,
                    Math.round(feeds/1000d),
                    address,
                    strPcodeCounterParty,
                    strPCode,
                    EMPTY);

            startActivity(intent);
            return;
        }
        if (SPEND_TYPE == SPEND_RICOCHET) {
            progressBar.setVisibility(View.VISIBLE);
            Disposable disposable = setUpRicochetFees()
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(() -> {
                        prepareSpend();
                        progressBar.setVisibility(View.INVISIBLE);
                        ricochetSpend(isEnabledRicochetStaggered());
                    }, er -> {
                        progressBar.setVisibility(View.INVISIBLE);
                        Toast.makeText(this, "Error ".concat(er.getMessage()), Toast.LENGTH_LONG).show();
                    });
            compositeDisposables.add(disposable);
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(SendActivity.this);
        builder.setTitle(R.string.app_name);
        builder.setMessage(message);
        final CheckBox cbShowAgain;
        if (strPrivacyWarning.length() > 0) {
            cbShowAgain = new CheckBox(SendActivity.this);
            cbShowAgain.setText(R.string.do_not_repeat_sent_to);
            cbShowAgain.setChecked(false);
            builder.setView(cbShowAgain);
        } else {
            cbShowAgain = null;
        }
        builder.setCancelable(false);
        builder.setPositiveButton(R.string.yes, (dialog, whichButton) -> {

            final List<MyTransactionOutPoint> outPoints = new ArrayList<MyTransactionOutPoint>();
            for (UTXO u : selectedUTXO) {
                outPoints.addAll(u.getOutpoints());
            }

            // add change
            final String changeAddress = SendParams.generateChangeAddress(
                    SendActivity.this,
                    _change,
                    SPEND_TYPE,
                    account,
                    changeType,
                    change_index,
                    true);
            if (changeAddress != null) {
                receivers.put(changeAddress, BigInteger.valueOf(_change));
            }

            SendParams.getInstance().setParams(
                    outPoints,
                    receivers,
                    strPCode,
                    SPEND_TYPE,
                    _change,
                    changeType,
                    account,
                    address,
                    strPrivacyWarning.length() > 0,
                    cbShowAgain != null ? cbShowAgain.isChecked() : false,
                    amount,
                    change_index
            );

            startActivity(new Intent(this, TxAnimUIActivity.class));

        });
        builder.setNegativeButton(R.string.no, (dialog, whichButton) -> SendActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                            btSend.setActivated(true);
//                            btSend.setClickable(true);
//                                        dialog.dismiss();
            }
        }));

        builder.create().show();

    }

    private boolean checkValidForJoinbot() {

        boolean valid = true;

        if (amount > SpendJoinbotTxBroadcaster.JOINNBOT_MAX_AMOUNT) {
            if (isEnabledJoinbot()) {
                Toast.makeText(this, getString(R.string.joinbot_max_amount_reached), Toast.LENGTH_SHORT).show();
            }
            valid = false;
        }

        if (! isJoinbotPossibleWithCurrentUserUTXOs(
                this,
                isPostmixAccount(),
                amount,
                preselectedUTXOs)) {

            if (isEnabledJoinbot()) {
                Toast.makeText(this, getString(R.string.joinbot_not_possible_with_current_utxo), Toast.LENGTH_SHORT).show();
            }
            valid = false;
        }

        if (valid) {
            joinbotDesc.setAlpha(1f);
            joinbotTitle.setAlpha(1f);
            joinbotSwitch.setAlpha(1f);
            joinbotSwitch.setEnabled(true);
        } else {
            joinbotDesc.setAlpha(.6f);
            joinbotTitle.setAlpha(.6f);
            joinbotSwitch.setAlpha(.6f);
            joinbotSwitch.setEnabled(false);
            autoUncheckJoinbotSwitch();
        }

        return SPEND_TYPE == SPEND_JOINBOT ? valid : true;

    }

    private void ricochetSpend(boolean staggered) {

        MaterialAlertDialogBuilder dlg = new MaterialAlertDialogBuilder(SendActivity.this)
                .setTitle(R.string.app_name)
                .setMessage(ricochetMessage)
                .setCancelable(false)
                .setPositiveButton(R.string.yes, (dialog, whichButton) -> {

                    dialog.dismiss();

                    if (staggered) {

//                            Log.d("SendActivity", "Ricochet staggered:" + ricochetJsonObj.toString());

                        try {
                            if (ricochetJsonObj.has("hops")) {
                                JSONArray hops = ricochetJsonObj.getJSONArray("hops");
                                if (hops.getJSONObject(0).has("nTimeLock")) {

                                    JSONArray nLockTimeScript = new JSONArray();
                                    for (int i = 0; i < hops.length(); i++) {
                                        JSONObject hopObj = hops.getJSONObject(i);
                                        int seq = i;
                                        long locktime = hopObj.getLong("nTimeLock");
                                        String hex = hopObj.getString("tx");
                                        JSONObject scriptObj = new JSONObject();
                                        scriptObj.put("hop", i);
                                        scriptObj.put("nlocktime", locktime);
                                        scriptObj.put("tx", hex);
                                        nLockTimeScript.put(scriptObj);
                                    }

                                    JSONObject nLockTimeObj = new JSONObject();
                                    nLockTimeObj.put("script", nLockTimeScript);
                                    if (APIFactory.getInstance(getApplicationContext()).APITokenRequired()) {
                                        nLockTimeObj.put("at", APIFactory.getInstance(getApplicationContext()).getAccessToken());
                                    }

//                                        Log.d("SendActivity", "Ricochet nLockTime:" + nLockTimeObj.toString());

                                    new Thread(new Runnable() {
                                        @Override
                                        public void run() {

                                            Looper.prepare();

                                            final String apiServiceUrl = BackendApiAndroid.getApiServiceUrl("pushtx/schedule");
                                            try {
                                                String result = "";
                                                result = WebUtil.getInstance(SendActivity.this).tor_postURL(apiServiceUrl, nLockTimeObj, null);
                                                JSONObject resultObj = new JSONObject(result);
                                                if (resultObj.has("status") && resultObj.getString("status").equalsIgnoreCase("ok")) {
                                                    Toast.makeText(SendActivity.this, R.string.ricochet_nlocktime_ok, Toast.LENGTH_LONG).show();
                                                    finish();
                                                } else {
                                                    Toast.makeText(SendActivity.this, R.string.ricochet_nlocktime_ko, Toast.LENGTH_LONG).show();
                                                    finish();
                                                }
                                            } catch (Exception e) {
                                                Log.d("SendActivity", e.getMessage());
                                                Toast.makeText(SendActivity.this, R.string.ricochet_nlocktime_ko, Toast.LENGTH_LONG).show();
                                                finish();
                                            }

                                            Looper.loop();

                                        }
                                    }).start();

                                }
                            }
                        } catch (JSONException je) {
                            Log.d("SendActivity", je.getMessage());
                        }

                    } else {
                        RicochetMeta.getInstance(SendActivity.this).add(ricochetJsonObj);

                        Intent intent = new Intent(SendActivity.this, RicochetActivity.class);
                        startActivityForResult(intent, RICOCHET);
                    }

                }).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        dialog.dismiss();

                    }
                });
        if (!isFinishing()) {
            dlg.show();
        }

    }

    private void backToTransactionView() {
        if (SPEND_TYPE == SPEND_SIMPLE)
            SPEND_TYPE = SPEND_BOLTZMANN;
        //Revert to default
        selectedUTXO = new ArrayList<>();
        receivers = new HashMap<>();
        amountViewSwitcher.showPrevious();
        sendTransactionDetailsView.showTransaction();
        hideMenus(false);
    }

    private void calculateTransactionSize(BigInteger _fee) {

        Disposable disposable = Single.fromCallable(() -> {

                    final List<MyTransactionOutPoint> outPoints = new ArrayList<>();
                    for (UTXO u : selectedUTXO) {
                        outPoints.addAll(u.getOutpoints());
                    }

                    HashMap<String, BigInteger> _receivers = SerializationUtils.clone(receivers);

                    // add change
                    if (_change > 0L) {
                        if (SPEND_TYPE == SPEND_SIMPLE) {
                            WALLET_INDEX walletIndex = WALLET_INDEX.findChangeIndex(account, changeType);
                            String change_address = AddressFactory.getInstance().getAddress(walletIndex).getRight();
                            _receivers.put(change_address, BigInteger.valueOf(_change));
                        }
                    }
                    final Transaction tx = SendFactory.getInstance(getApplication()).makeTransaction(outPoints, _receivers);
                    return SendFactory.getInstance(getApplication()).signTransaction(tx, account);
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((transaction, throwable) -> {
                    if (throwable == null && transaction != null) {
                        decimalFormatSatPerByte.setDecimalSeparatorAlwaysShown(false);
                        tvSelectedFeeRate.setText(decimalFormatSatPerByte.format((_fee.doubleValue()) / transaction.getVirtualTransactionSize()).concat(" sat/b"));
                    } else {
                        tvSelectedFeeRate.setText("_");
                    }
                });

        compositeDisposables.add(disposable);

    }

    @Override
    public void onBackPressed() {
        if (sendTransactionDetailsView.isReview()) {
            backToTransactionView();
        } else {
            super.onBackPressed();
        }
    }

    private void enableAmount(boolean enable) {
        btcEditText.setEnabled(enable);
        satEditText.setEnabled(enable);
    }

    private void processScan(final String inputData) {

        String data = inputData;

        strPCode = null;
        toAddressEditText.setEnabled(true);
        address = null;
        strDestinationBTCAddress = null;

        if (canParseAsBatchSpend(data)) {
            launchBatchSpend(data);
            return;
        }

        if (data.contains("https://bitpay.com")) {

            MaterialAlertDialogBuilder dlg = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(R.string.no_bitpay)
                    .setCancelable(false)
                    .setPositiveButton(R.string.learn_more, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://blog.samouraiwallet.com/post/169222582782/bitpay-qr-codes-are-no-longer-valid-important"));
                            startActivity(intent);

                        }
                    }).setNegativeButton(R.string.close, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {

                            dialog.dismiss();

                        }
                    });
            if (!isFinishing()) {
                dlg.show();
            }

            return;
        }

        if (Cahoots.isCahoots(trim(data))) {
            try {
                Intent cahootsIntent = ManualCahootsActivity.createIntentResume(this, account, trim(data));
                startActivity(cahootsIntent);
            } catch (Exception e) {
                Toast.makeText(this, R.string.cannot_process_cahoots, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
            return;
        }
        if (isPSBT(trim(data))) {
            try {
                PSBTUtil.getInstance(SendActivity.this).doPSBT(trim(data));
            } catch (Exception e) {
                Log.d(TAG, "cannot PSBT with this data");
            }
            return;
        }

        if (FormatsUtil.getInstance().isValidPaymentCode(data)) {
            processPCode(data, null);
            return;
        }

        if (data.startsWith("BITCOIN:")) {
            data = "bitcoin:" + data.substring(8);
        }

        if (FormatsUtil.getInstance().isBitcoinUri(data)) {
            String address = FormatsUtil.getInstance().getBitcoinAddress(data);
            String amount = FormatsUtil.getInstance().getBitcoinAmount(data);

            setToAddress(address);
            if (amount != null) {
                try {
                    NumberFormat btcFormat = NumberFormat.getInstance(Locale.US);
                    btcFormat.setMaximumFractionDigits(8);
                    btcFormat.setMinimumFractionDigits(1);
//                    setToAddress(btcFormat.format(Double.parseDouble(amount) / 1e8));
//                    Log.i(TAG, "------->: ".concat();
                    btcEditText.setText(btcFormat.format(Double.parseDouble(amount) / 1e8));
                } catch (NumberFormatException nfe) {
//                    setToAddress("0.0");
                }
            }

            final String strAmount = FormatsUtil.formatBTCWithoutUnit(balance);
            tvMaxAmount.setText(strAmount);

            try {
                if (amount != null && Double.parseDouble(amount) != 0.0) {
                    toAddressEditText.setEnabled(false);
//                    selectPaynymBtn.setEnabled(false);
//                    selectPaynymBtn.setAlpha(0.5f);
                    //                    Toast.makeText(this, R.string.no_edit_BIP21_scan, Toast.LENGTH_SHORT).show();
                    enableAmount(false);

                }
            } catch (NumberFormatException nfe) {
                enableAmount(true);
            }

        } else if (FormatsUtil.getInstance().isValidBitcoinAddress(data)) {

            if (FormatsUtil.getInstance().isValidBech32(data)) {
                setToAddress(data.toLowerCase());
            } else {
                setToAddress(data);
            }

        } else if (data.contains("?")) {

            String pcode = data.substring(0, data.indexOf("?"));
            // not valid BIP21 but seen often enough
            if (pcode.startsWith("bitcoin://")) {
                pcode = pcode.substring(10);
            }
            if (pcode.startsWith("bitcoin:")) {
                pcode = pcode.substring(8);
            }
            if (FormatsUtil.getInstance().isValidPaymentCode(pcode)) {
                processPCode(pcode, data.substring(data.indexOf("?")));
            }
        } else {
            Toast.makeText(this, R.string.scan_error, Toast.LENGTH_SHORT).show();
        }

        validateSpend();
    }

    public static boolean isPSBT(final String data) {
        try {
            return FormatsUtil.getInstance().isPSBT(data);
        } catch (final Exception e) {
            Log.d(TAG, "data is not PSBT");
            return false;
        }
    }


    private void processPCode(String pcode, String meta) {

        final Handler handler = new Handler();
        handler.postDelayed(this::setBalance, 2000);

        if (FormatsUtil.getInstance().isValidPaymentCode(pcode)) {

            if (BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_SENT_CFM) {
                try {

                    strDestinationBTCAddress = BIP47Util.getInstance(SendActivity.this)
                            .getSendAddressString(pcode);
                    strPCode = pcode;
                    setToAddress(BIP47Meta.getInstance().getDisplayLabel(strPCode));
                    toAddressEditText.setEnabled(false);
                    validateSpend();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.error_payment_code, Toast.LENGTH_SHORT).show();
                }
            } else {
//                Toast.makeText(SendActivity.this, "Payment must be added and notification tx sent", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, PayNymDetailsActivity.class);
                intent.putExtra("pcode", pcode);
                intent.putExtra("label", "");

                if (meta != null && meta.startsWith("?") && meta.length() > 1) {
                    meta = meta.substring(1);

                    if (meta.length() > 0) {
                        String _meta = null;
                        Map<String, String> map = new HashMap<String, String>();
                        meta.length();
                        try {
                            _meta = URLDecoder.decode(meta, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        map = Splitter.on('&').trimResults().withKeyValueSeparator("=").split(_meta);
                        intent.putExtra("label", map.containsKey("title") ? map.get("title").trim() : "");
                    }

                }
                if (!openedPaynym) {
                    startActivity(intent);
                    openedPaynym = true;
                }
            }

        } else {
            Toast.makeText(this, R.string.invalid_payment_code, Toast.LENGTH_SHORT).show();
        }

    }

    private boolean validateSpend() {

        /*
        if (! checkValidForJoinbot()) {
            enableReviewButton(false);
            return false;
        }
         */

        boolean insufficientFunds = false;

        String strBTCAddress = getToAddress();
        if (strBTCAddress.startsWith("bitcoin:")) {
            setToAddress(strBTCAddress.substring(8));
        }
        setToAddress(strBTCAddress);

        final long amount = getSatValue(getBtcAmountFromWidget());
        Log.i("SendFragment", "amount entered (converted to long):" + amount);
        Log.i("SendFragment", "balance:" + balance);
        if (amount > balance) {
            insufficientFunds = true;
        }

        if (selectedCahootsType != SelectCahootsType.type.NONE) {
            totalMinerFeeLayout.setVisibility(View.INVISIBLE);
        } else {
            totalMinerFeeLayout.setVisibility(View.VISIBLE);
        }

        if (insufficientFunds && amount != 0) {
            Toast.makeText(this, getString(R.string.insufficient_funds), Toast.LENGTH_SHORT).show();
            enableReviewButton(false);
            return false;
        }

        boolean isValid;
        if (amount >= SamouraiWallet.bDust.longValue()
                && FormatsUtil.getInstance().isValidBitcoinAddress(getToAddress())) {
            isValid = true;
        } else if (amount >= SamouraiWallet.bDust.longValue()
                && strDestinationBTCAddress != null
                && FormatsUtil.getInstance().isValidBitcoinAddress(strDestinationBTCAddress)) {
            isValid = true;
        } else {
            isValid = false;
        }

        final boolean hasEnoughFunds = !insufficientFunds;
        enableReviewButton(isValid && hasEnoughFunds);
        return isValid && hasEnoughFunds;

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_CANCELED && requestCode == SCAN_QR) {
            ;
        } else if (resultCode == Activity.RESULT_OK && requestCode == RICOCHET) {
            ;
        } else if (resultCode == Activity.RESULT_CANCELED && requestCode == RICOCHET) {
            ;
        } else {
            ;
        }

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.send_menu, menu);

        if (account != SamouraiAccountIndex.DEPOSIT) {
            if (account != SamouraiAccountIndex.POSTMIX) {
                menu.findItem(R.id.action_batch).setVisible(false);
            }
            menu.findItem(R.id.action_ricochet).setVisible(false);
            menu.findItem(R.id.action_empty_ricochet).setVisible(false);
        }

        if (preselectedUTXOs != null) {
            menu.findItem(R.id.action_batch).setVisible(false);
        }

        if (account == WhirlpoolMeta.getInstance(getApplication()).getWhirlpoolPostmix()) {
            MenuItem item = menu.findItem(R.id.action_send_menu_account);
            item.setVisible(true);
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            item.setActionView(createTag("POST-MIX"));
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (item.getItemId() == android.R.id.home) {
            this.onBackPressed();
            return true;
        }
        if (item.getItemId() == R.id.select_paynym) {
            PaynymSelectModalFragment paynymSelectModalFragment =
                    PaynymSelectModalFragment.newInstance(code -> processPCode(code, null), getString(R.string.paynym), false);
            paynymSelectModalFragment.show(getSupportFragmentManager(), "paynym_select");
            return true;
        }
        // noinspection SimplifiableIfStatement
        if (id == R.id.action_scan_qr) {
            doScan();
        } else if (id == R.id.action_ricochet) {
            if (RicochetMeta.getInstance(this).getQueue().isEmpty()) {
                Toast.makeText(this, getString(R.string.empty_ricochet_queue), Toast.LENGTH_SHORT).show();
            }
            else {
                Intent intent = new Intent(SendActivity.this, RicochetActivity.class);
                startActivity(intent);
            }
        } else if (id == R.id.action_empty_ricochet) {
            if (RicochetMeta.getInstance(this).getQueue().isEmpty())
                Toast.makeText(this, getString(R.string.empty_ricochet_queue), Toast.LENGTH_SHORT).show();
            else
                emptyRicochetQueue();
        } else if (id == R.id.action_utxo) {
            doUTXO();
        } else if (id == R.id.action_fees) {
            doFees(SendActivity.this);
        } else if (id == R.id.action_batch) {
            launchBatchSpend();
        } /*else if (id == R.id.action_support) {
            doSupport();
        } */ else {
            ;
        }

        return super.onOptionsItemSelected(item);
    }

    private void emptyRicochetQueue() {

        RicochetMeta.getInstance(this).setLastRicochet(null);
        RicochetMeta.getInstance(this).empty();

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    PayloadUtil.getInstance(SendActivity.this).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(SendActivity.this).getGUID() + AccessFactory.getInstance(SendActivity.this).getPIN()));
                } catch (Exception e) {
                    ;
                }

            }
        }).start();

        Toast.makeText(this, "Ricochet queue has been emptied", Toast.LENGTH_SHORT).show();
    }

    private void doScan() {

        final CameraFragmentBottomSheet cameraFragmentBottomSheet = new CameraFragmentBottomSheet();
        cameraFragmentBottomSheet.show(getSupportFragmentManager(), cameraFragmentBottomSheet.getTag());

        cameraFragmentBottomSheet.setQrCodeScanListener(code -> {
            cameraFragmentBottomSheet.dismissAllowingStateLoss();
            processScan(code);
        });
    }

    private void doSupport() {
        String url = null; //"https://samouraiwallet.com/support";
        if (SamouraiTorManager.INSTANCE.isConnected())
            url = null; //"http://72typmu5edrjmcdkzuzmv2i4zqru7rjlrcxwtod4nu6qtfsqegngzead.onion/support";
        Intent intent = new Intent(this, ExplorerActivity.class);
        intent.putExtra(ExplorerActivity.SUPPORT, url);
        startActivity(intent);
    }

    private void doUTXO() {
        Intent intent = new Intent(SendActivity.this, UTXOSActivity.class);
        if (account != 0) {
            intent.putExtra("_account", account);
        }
        startActivity(intent);
    }

    private void launchBatchSpend() {
        launchBatchSpend(null);
    }

    private void launchBatchSpend(final String inputBatchSpendAsJson) {
        final Intent intent = new Intent(SendActivity.this, BatchSpendActivity.class);
        intent.putExtra("_account", account);
        intent.putExtra("inputBatchSpend", inputBatchSpendAsJson);
        if (getIntent().hasExtra("preselected")) {
            intent.putExtra("preselected", getIntent().getStringExtra("preselected"));
        }
        startActivity(intent);
    }

    public static void doFees(final SamouraiActivity activity) {

        final StringBuilder sb = new StringBuilder();

        for (final EnumFeeRepresentation feeRepresentation : EnumFeeRepresentation.values()) {
            sb.append(feeRepresentation.name());
            sb.append(":");
            sb.append(System.lineSeparator());
            final RawFees rawFees = FeeUtil.getInstance().getRawFees(feeRepresentation);
            if (isNull(rawFees) || ! rawFees.hasFee()) {
                sb.append("none");
                sb.append(System.lineSeparator());
            } else {
                switch (feeRepresentation) {
                    case NEXT_BLOCK_RATE:
                        for (final EnumFeeRate feeRateType : EnumFeeRate.values()) {
                            final Integer fee = rawFees.getFee(feeRateType);
                            if (nonNull(fee)) {
                                sb.append(feeRateType.getRateAsString());
                                sb.append(": ");
                                sb.append(fee);
                                sb.append(" sat/vB");
                                sb.append(System.lineSeparator());
                            }
                        }
                        break;
                    case BLOCK_COUNT:
                        for (final EnumFeeBlockCount blockCount : EnumFeeBlockCount.values()) {
                            final Integer fee = rawFees.getFee(blockCount);
                            if (nonNull(fee)) {
                                sb.append(blockCount.getBlockCount());
                                sb.append(": ");
                                sb.append(fee);
                                sb.append(" sat/vB");
                                sb.append(System.lineSeparator());
                            }
                        }
                        break;
                }

            }
            sb.append(System.lineSeparator());
        }

        final MaterialAlertDialogBuilder dlg = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_name)
                .setMessage(sb.toString())
                .setCancelable(false)
                .setPositiveButton(R.string.ok, (dialog, whichButton) -> dialog.dismiss());

        if (!activity.isFinishing()) {
            dlg.show();
        }

    }

    private void saveChangeIndexes() {

        idxBIP84PostMixInternal = AddressFactory.getInstance(SendActivity.this).getIndex(WALLET_INDEX.POSTMIX_CHANGE);
        idxBIP84Internal = AddressFactory.getInstance(SendActivity.this).getIndex(WALLET_INDEX.BIP84_CHANGE);
        idxBIP49Internal = AddressFactory.getInstance(SendActivity.this).getIndex(WALLET_INDEX.BIP49_CHANGE);
        idxBIP44Internal = AddressFactory.getInstance(SendActivity.this).getIndex(WALLET_INDEX.BIP44_CHANGE);

    }

    private void restoreChangeIndexes() {

        AddressFactory.getInstance(SendActivity.this).setWalletIdx(WALLET_INDEX.POSTMIX_CHANGE, idxBIP84PostMixInternal, true);
        AddressFactory.getInstance(SendActivity.this).setWalletIdx(WALLET_INDEX.BIP84_CHANGE, idxBIP84Internal, true);
        AddressFactory.getInstance(SendActivity.this).setWalletIdx(WALLET_INDEX.BIP49_CHANGE, idxBIP49Internal, true);
        AddressFactory.getInstance(SendActivity.this).setWalletIdx(WALLET_INDEX.BIP44_CHANGE, idxBIP44Internal, true);

    }

    private TextView.OnEditorActionListener satKeyboardListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(final TextView v, final int actionId, final KeyEvent event) {
            if (isNull(event)) return false;
            if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                hideKeyboard();
                return true;
            }
            return false;
        }
    };

}

