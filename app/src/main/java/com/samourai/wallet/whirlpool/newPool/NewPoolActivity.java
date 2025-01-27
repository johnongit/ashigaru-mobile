package com.samourai.wallet.whirlpool.newPool;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.samourai.wallet.R;
import com.samourai.wallet.SamouraiActivity;
import com.samourai.wallet.api.backend.beans.UnspentOutput;
import com.samourai.wallet.constants.SamouraiAccount;
import com.samourai.wallet.send.BlockedUTXO;
import com.samourai.wallet.send.FeeUtil;
import com.samourai.wallet.util.func.FormatsUtil;
import com.samourai.wallet.util.tech.LogUtil;
import com.samourai.wallet.util.view.CustomProgressIndicatorFragment;
import com.samourai.wallet.utxos.PreSelectUtil;
import com.samourai.wallet.utxos.UTXOUtil;
import com.samourai.wallet.utxos.models.UTXOCoin;
import com.samourai.wallet.whirlpool.WhirlpoolMeta;
import com.samourai.wallet.whirlpool.WhirlpoolTx0;
import com.samourai.wallet.whirlpool.models.PoolCyclePriority;
import com.samourai.wallet.whirlpool.newPool.fragments.ChooseUTXOsFragment;
import com.samourai.wallet.whirlpool.newPool.fragments.ReviewPoolFragment;
import com.samourai.wallet.whirlpool.newPool.fragments.SelectPoolFragment;
import com.samourai.wallet.whirlpool.service.WhirlpoolNotificationService;
import com.samourai.wallet.widgets.ViewPager;
import com.samourai.whirlpool.client.tx0.Tx0;
import com.samourai.whirlpool.client.tx0.Tx0Config;
import com.samourai.whirlpool.client.tx0.Tx0Info;
import com.samourai.whirlpool.client.wallet.AndroidWhirlpoolWalletService;
import com.samourai.whirlpool.client.wallet.WhirlpoolUtils;
import com.samourai.whirlpool.client.wallet.WhirlpoolWallet;
import com.samourai.whirlpool.client.wallet.beans.Tx0FeeTarget;

import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.TransactionOutput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import kotlin.Unit;

import static android.graphics.Typeface.BOLD;

public class NewPoolActivity extends SamouraiActivity {

    private static final String TAG = "NewPoolActivity";
    public static final String CONNECTION_ERROR_TAP_TO_TRY_AGAIN = "Connection Error.\nTap to try again";
    private static final int TX0_LOADING_DURATION_UNIT = 9000;

    private WhirlpoolTx0 tx0 = null;
    private boolean blockChangeOutput = false;

    private TextView stepperMessage1, stepperMessage2, stepperMessage3, cycleTotalAmount;
    private View stepperLine1, stepperLine2;
    private ImageView stepperPoint1, stepperPoint2, stepperPoint3;
    private ChooseUTXOsFragment chooseUTXOsFragment;
    private SelectPoolFragment selectPoolFragment;
    private ReviewPoolFragment reviewPoolFragment;
    private ViewPager newPoolViewPager;
    private int account = 0;
    private MaterialButton confirmButton;
    private CompositeDisposable disposables = new CompositeDisposable();
    private  NewPoolViewModel newPoolViewModel;
    private List<UTXOCoin> selectedCoins = new ArrayList<>();
    private LinearLayout tx0Progress;

    private Float tx0LoadingProgess = 0f;
    private MutableLiveData<Float> liveTx0LoadingProgess = new MutableLiveData<>(tx0LoadingProgess);
    private MutableLiveData<Boolean> liveTx0LoadingViewVisible = new MutableLiveData<>(false);

    private Handler handler = new Handler();
    private Runnable incrementProgressRunnable = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(this);
            tx0LoadingProgess += 0.1f;
            liveTx0LoadingProgess.postValue(tx0LoadingProgess);
            if (tx0LoadingProgess < 1.0f) {
                handler.postDelayed(this, TX0_LOADING_DURATION_UNIT);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_whirlpool_cycle);
        Toolbar toolbar = findViewById(R.id.toolbar_new_whirlpool);
        newPoolViewModel = new ViewModelProvider(this).get(NewPoolViewModel.class);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        cycleTotalAmount = findViewById(R.id.cycle_total_amount);
        cycleTotalAmount.setText(FormatsUtil.formatBTC(0L));

        String preselectId = null;
        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("preselected")) {
            preselectId = getIntent().getExtras().getString("preselected");
        }
        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("_account")) {
            account = getIntent().getExtras().getInt("_account");
        }

        chooseUTXOsFragment = ChooseUTXOsFragment.newInstance(preselectId);
        selectPoolFragment = new SelectPoolFragment();
        reviewPoolFragment = new ReviewPoolFragment();
        tx0Progress = findViewById(R.id.new_pool_tx0_progress);

        newPoolViewPager = findViewById(R.id.new_pool_viewpager);

        setUpStepper();

        newPoolViewPager.setAdapter(new NewPoolStepsPager(getSupportFragmentManager()));
        newPoolViewPager.enableSwipe(false);

        confirmButton = findViewById(R.id.utxo_selection_confirm_btn);

        confirmButton.setVisibility(View.VISIBLE);

        enableConfirmButton(false);

        // fetch tx0Info only once  (it should be refreshed after each TX0)
        // disable confirmButton until tx0Info is not loaded
        //confirmButton.setText(getString(R.string.loading));

        loadTx0InfoAsync();

        //Disable selection from fragment since post mix utxo's are populated by the activity
        if (account != WhirlpoolMeta.getInstance(getApplicationContext()).getWhirlpoolPostmix())
            chooseUTXOsFragment.setOnUTXOSelectionListener(this::onUTXOSelected);

        newPoolViewModel.getGetPoolLoadError().observe(this,exception -> {
            if(exception!=null){
                if(exception.getMessage()!=null){
                    Snackbar.make(findViewById(R.id.new_pool_snackbar_layout),exception.getMessage(),Snackbar.LENGTH_LONG).show();
                }
            }
        });
        newPoolViewModel.getGetPool().observe(this,(pool) -> {
            if (tx0 != null && pool != null) {
                tx0.setPool(pool.getDenomination());
            }
            enableConfirmButton(pool != null && tx0.getAmountAfterWhirlpoolFee() > pool.getDenomination());
        });

        newPoolViewModel.getGetPool().observe(this,poolViewModel -> {
            if(poolViewModel != null && newPoolViewPager.getCurrentItem() == 1 && poolViewModel.getTx0Preview().getNbPremix() != 0){
                enableConfirmButton(true);
            }
        });

        reviewPoolFragment.setLoadingListener((aBoolean, e) -> {
            if (e == null) {
                enableBroadcastButton(!aBoolean);
            }
            return Unit.INSTANCE;
        });

        confirmButton.setOnClickListener(view -> {
            if (StringUtils.equals(confirmButton.getText(), CONNECTION_ERROR_TAP_TO_TRY_AGAIN)) {
                loadTx0InfoAsync();
                return;
            }
            switch (newPoolViewPager.getCurrentItem()) {
                case 0: {
                    newPoolViewPager.setCurrentItem(1);
                    initUTXOReviewButton(selectedCoins);
                    newPoolViewModel.loadPools();
                    enableConfirmButton(newPoolViewModel.getGetPool().getValue() != null);
                    break;
                }
                case 1: {
                    newPoolViewPager.setCurrentItem(2);
                    confirmButton.setText(getString(R.string.begin_cycle));
                    confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.green_ui_2));
                    break;
                }
                case 2: {

                    MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
                    builder.setMessage(R.string.block_tx0_change).setCancelable(false);
                    builder.setTitle(R.string.doxxic_change_warning);
                    builder.setPositiveButton(getString(R.string.yes), (dialog, id) -> {
                        dialog.dismiss();
                        blockChangeOutput = true;
                        processWhirlPool();
                    });
                    builder.setNeutralButton(R.string.cancel, (dialog, which) -> {
                        dialog.cancel();
                    });
                    builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            blockChangeOutput = false;
                            processWhirlPool();
                        }
                    });
                    if (!isFinishing()) {
                        builder.show();
                    }

                    break;
                }
            }
        });

        setUpViewPager();

        if (getIntent().getExtras() != null && getIntent().getExtras().containsKey("_account")) {
            if (account == WhirlpoolMeta.getInstance(getApplication()).getWhirlpoolPostmix()) {
                selectedCoins.clear();
                List<UTXOCoin> coinList = PreSelectUtil.getInstance().getPreSelected(preselectId);
                try {
                    onUTXOSelected(coinList);
                    newPoolViewPager.setCurrentItem(1);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        final FragmentManager fragManager = getSupportFragmentManager();
        final FragmentTransaction fragTx = fragManager.beginTransaction();
        fragTx.replace(R.id.loading_indicator, new CustomProgressIndicatorFragment(
                liveTx0LoadingProgess,
                liveTx0LoadingViewVisible,
                "LOADING")
        );
        fragTx.commit();
    }

    private void loadTx0InfoAsync() {
        confirmButton.setVisibility(View.GONE);
        liveTx0LoadingViewVisible.postValue(true);
        tx0LoadingProgess = 0f;
        liveTx0LoadingProgess.postValue(tx0LoadingProgess);
        Completable.fromRunnable(() -> newPoolViewModel.loadTx0Info())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(() -> {
                // enable confirmButton once tx0Info is loaded
                Log.v(TAG, "loadTx0Info success");
                confirmButton.setVisibility(View.VISIBLE);
                confirmButton.setText(getString(R.string.next));
                enableConfirmButton(true);
                liveTx0LoadingViewVisible.postValue(false);
            }, e -> {
                // keep confirmButton disabled on tx0Info loading error
                confirmButton.setVisibility(View.VISIBLE);
                confirmButton.setText(CONNECTION_ERROR_TAP_TO_TRY_AGAIN);
                Toast.makeText(NewPoolActivity.this, "Error fetching Tx0 details", Toast.LENGTH_SHORT).show();
                confirmButton.setEnabled(true);
                confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.blue_ui_2));
                liveTx0LoadingViewVisible.postValue(false);
                Log.e(TAG, "loadTx0Info() failed", e);
        });
        handler.postDelayed(incrementProgressRunnable, TX0_LOADING_DURATION_UNIT);
    }

    void onUTXOSelected(List<UTXOCoin> coins) {

        selectedCoins = coins;

        if (coins.size() == 0) {
            cycleTotalAmount.setText(FormatsUtil.formatBTC(0L));
            enableConfirmButton(false);
        } else {
            long mediumFee = FeeUtil.getInstance().getNormalFee().getDefaultPerKB().longValue() / 1000L;

            cycleTotalAmount.setText(FormatsUtil.formatBTC(getCycleTotalAmount(coins)));
            // default set to lowest pool
            calculateTx0(WhirlpoolMeta.getInstance(NewPoolActivity.this).getMinimumPoolDenomination(), mediumFee);
        }

        newPoolViewModel.setUtxos(selectedCoins);
        newPoolViewModel.setPoolPriority(PoolCyclePriority.NORMAL);
        newPoolViewModel.loadPools();
    }

    private void calculateTx0(long denomination, long fee) {
        tx0 = new WhirlpoolTx0(denomination, fee, 0, selectedCoins);

        LogUtil.info(TAG, "calculateTx0: ".concat(String.valueOf(denomination)).concat(" fee").concat(String.valueOf(fee)));
        try {
            tx0.make();
        } catch (Exception ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            ex.printStackTrace();
        }

        if (tx0.getTx0() != null) {
            enableConfirmButton(true);
            selectPoolFragment.setTX0(selectedCoins);
        } else {
            enableConfirmButton(false);
        }
    }

    private void processWhirlPool() {

        try {
            if (AndroidWhirlpoolWalletService.getInstance().listenConnectionStatus().getValue() != AndroidWhirlpoolWalletService.ConnectionStates.CONNECTED) {
                WhirlpoolNotificationService.startService(getApplicationContext());
            } else {
                tx0Progress.setVisibility(View.VISIBLE);
                confirmButton.setEnabled(false);
                Disposable tx0Disposable = beginTx0(selectedCoins)
                        .delay(500, TimeUnit.MILLISECONDS)
                        .andThen(AndroidWhirlpoolWalletService.getInstance().whirlpoolWallet().refreshUtxosAsync())
                        .delay(100,TimeUnit.MILLISECONDS)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(() -> {
                            confirmButton.setEnabled(true);
                            Snackbar.make(findViewById(R.id.new_pool_snackbar_layout), "TX0 Successfully broadcasted", Snackbar.LENGTH_LONG).show();
                            tx0Progress.setVisibility(View.GONE);
                            finish();
                        }, error -> {
                            confirmButton.setEnabled(true);
                            tx0Progress.setVisibility(View.GONE);
                            Snackbar.make(findViewById(R.id.new_pool_snackbar_layout), "Error: ".concat(error.getMessage()), Snackbar.LENGTH_LONG).show();
                            error.printStackTrace();
                        });

                disposables.add(tx0Disposable);
            }


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private Completable beginTx0(List<UTXOCoin> coins) {
        return Completable.fromCallable(() -> {
            WhirlpoolWallet whirlpoolWallet = AndroidWhirlpoolWalletService.getInstance().whirlpoolWallet();
            if (whirlpoolWallet == null) {
                return true;
            }
            Collection<UnspentOutput> spendFroms = WhirlpoolUtils.getInstance().toUnspentOutputsCoins(coins);

            Tx0FeeTarget mixFeeTarget = Tx0FeeTarget.BLOCKS_2;

            if (newPoolViewModel.getGetTx0PoolPriority().getValue() == PoolCyclePriority.HIGH) {
                mixFeeTarget = Tx0FeeTarget.BLOCKS_2;
            } else if (newPoolViewModel.getGetTx0PoolPriority().getValue() == PoolCyclePriority.NORMAL) {
                mixFeeTarget = Tx0FeeTarget.BLOCKS_6;
            } else if (newPoolViewModel.getGetTx0PoolPriority().getValue() == PoolCyclePriority.LOW) {
                mixFeeTarget = Tx0FeeTarget.BLOCKS_24;
            }
            Tx0Info tx0Info = newPoolViewModel.getTx0Info();
            Tx0Config tx0Config = tx0Info.getTx0Config(mixFeeTarget, mixFeeTarget);
            if (account == WhirlpoolMeta.getInstance(getApplicationContext()).getWhirlpoolPostmix()) {
                tx0Config.setChangeWallet(SamouraiAccount.POSTMIX);
            } else {
                tx0Config.setChangeWallet(SamouraiAccount.DEPOSIT);
            }
            try {
                com.samourai.whirlpool.client.whirlpool.beans.Pool pool = whirlpoolWallet.getPoolSupplier().findPoolById(newPoolViewModel.getGetPool().getValue().getPoolId());

                Tx0 tx0 = tx0Info.tx0(whirlpoolWallet.getWalletSupplier(), whirlpoolWallet.getUtxoSupplier(), spendFroms, tx0Config, pool);
                final String txHash = tx0.getTx().getHashAsString();
                // tx0 success
                if (tx0.getChangeOutputs() != null && !tx0.getChangeOutputs().isEmpty()) {
                    TransactionOutput changeOutput = tx0.getChangeOutputs().iterator().next();
                    LogUtil.info("NewPoolActivity", "change:" + changeOutput.toString());
                    LogUtil.info("NewPoolActivity", "change index:" + changeOutput.getIndex());
                    UTXOUtil.getInstance().add(txHash + "-" + changeOutput.getIndex(), "\u2623 tx0 change\u2623");
                    UTXOUtil.getInstance().addNote(txHash, "tx0");
                    if (blockChangeOutput) {
                        if (account == WhirlpoolMeta.getInstance(getApplicationContext()).getWhirlpoolPostmix()) {
                            BlockedUTXO.getInstance().addPostMix(txHash, changeOutput.getIndex(), tx0.getChangeValue());
                        } else {
                            BlockedUTXO.getInstance().add(txHash, changeOutput.getIndex(), tx0.getChangeValue());
                        }
                    }
                }

                NewPoolActivity.this.runOnUiThread(() -> Toast.makeText(NewPoolActivity.this, txHash, Toast.LENGTH_SHORT).show());
                LogUtil.info("NewPoolActivity", "result:" + txHash);

            } catch (Exception e) {
                // tx0 failed
                throw  e;
            }

            return true;
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUpViewPager() {
        newPoolViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                switch (position) {
                    case 0: {
                        enableStep1(true);
                        confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.blue_ui_2));
                        confirmButton.setText(R.string.next);
                        break;
                    }
                    case 1: {
                        initUTXOReviewButton(selectedCoins);
                        enableStep2(true);
                        enableConfirmButton(newPoolViewModel.getGetPool().getValue() != null);
                        break;
                    }
                    case 2: {
                        //pass PoolViewModel information to @ReviewPoolFragment
                        enableStep3(true);
                        break;
                    }
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    private void initUTXOReviewButton(List<UTXOCoin> coins) {

        String reviewMessage = getString(R.string.review_cycle_details).concat("\n");
        String reviewAmountMessage = getString(R.string.total_whirlpool_balance).concat(" ");
        String amount = FormatsUtil.formatBTC(getCycleTotalAmount(coins));

        SpannableString spannable = new SpannableString(reviewMessage.concat(reviewAmountMessage).concat(amount));
        spannable.setSpan(
                new StyleSpan(BOLD),
                0, reviewMessage.length() - 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        spannable.setSpan(
                new RelativeSizeSpan(.8f),
                reviewMessage.length() - 1, spannable.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.blue_ui_2));

        confirmButton.setText(spannable);
    }

    private void setUpStepper() {
        stepperMessage1 = findViewById(R.id.stepper_1_message);
        stepperMessage2 = findViewById(R.id.stepper_2_message);
        stepperMessage3 = findViewById(R.id.stepper_3_message);

        stepperLine1 = findViewById(R.id.step_line_1);
        stepperLine2 = findViewById(R.id.step_line_2);

        stepperPoint1 = findViewById(R.id.stepper_point_1);
        stepperPoint2 = findViewById(R.id.stepper_point_2);
        stepperPoint3 = findViewById(R.id.stepper_point_3);

        enableStep3(false);
        enableStep2(false);

    }

    @Override
    public void onBackPressed() {
        switch (newPoolViewPager.getCurrentItem()) {
            case 0: {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.confirm)
                        .setMessage("Are you sure want to cancel?")
                        .setPositiveButton(R.string.yes, (dialogInterface, i) -> super.onBackPressed())
                        .setNegativeButton(R.string.no, (dialogInterface, i) -> {
                        })
                        .create().show();
                break;
            }
            case 1: {
                if (account == WhirlpoolMeta.getInstance(getApplicationContext()).getWhirlpoolPostmix()) {
                    new MaterialAlertDialogBuilder(this)
                            .setMessage("Are you sure want to cancel?")
                            .setPositiveButton(R.string.yes, (dialogInterface, i) -> super.onBackPressed())
                            .setNegativeButton(R.string.no, (dialogInterface, i) -> {
                            })
                            .create().show();
                    return;
                }
                newPoolViewPager.setCurrentItem(0);
                break;
            }
            case 2: {
                newPoolViewPager.setCurrentItem(1);
                break;
            }
        }
    }


    private void enableConfirmButton(boolean enable) {
        // require tx0Info to be loaded before continuation
        boolean tx0InfoLoaded = newPoolViewModel.getTx0Info()!=null;
        if (enable && tx0InfoLoaded) {
            confirmButton.setEnabled(true);
            confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.blue_ui_2));
            Log.v(TAG, "enableConfirmButton=true");
        } else {
            confirmButton.setEnabled(false);
            confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.disabled_grey));
            Log.v(TAG, "enableConfirmButton=false, tx0InfoLoaded="+tx0InfoLoaded);
        }
    }

    private void enableBroadcastButton(boolean enable) {
        if (enable) {
            confirmButton.setEnabled(true);
            confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.green_ui_2));

        } else {
            confirmButton.setEnabled(false);
            confirmButton.setBackgroundTintList(ContextCompat.getColorStateList(getApplicationContext(), R.color.disabled_grey));

        }
    }

    class NewPoolStepsPager extends FragmentPagerAdapter {


        NewPoolStepsPager(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case 0: {
                    return chooseUTXOsFragment;
                }
                case 1: {
                    return selectPoolFragment;

                }
                case 2: {
                    return reviewPoolFragment;
                }
            }
            return chooseUTXOsFragment;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "";
        }
    }

    private void enableStep1(boolean enable) {
        if (enable) {
            stepperMessage1.setTextColor(ContextCompat.getColor(this, R.color.white));
            stepperPoint1.setColorFilter(ContextCompat.getColor(this, R.color.whirlpoolGreen));
            stepperMessage1.setAlpha(1f);
            enableStep2(false);
            enableStep3(false);
        } else {
            stepperMessage1.setTextColor(ContextCompat.getColor(this, R.color.disabled_white));
            stepperPoint1.setColorFilter(ContextCompat.getColor(this, R.color.disabled_white));
            stepperPoint1.setAlpha(0.6f);

        }
    }

    private void enableStep2(boolean enable) {
        if (enable) {
            stepperMessage2.setTextColor(ContextCompat.getColor(this, R.color.white));
            stepperMessage2.setAlpha(1f);
            stepperPoint2.setColorFilter(ContextCompat.getColor(this, R.color.whirlpoolGreen));
            stepperPoint2.setAlpha(1f);
            stepperMessage1.setAlpha(0.6f);
            stepperLine1.setBackgroundResource(R.color.whirlpoolGreen);
            enableStep3(false);
        } else {
            stepperMessage2.setTextColor(ContextCompat.getColor(this, R.color.disabled_white));
            stepperPoint2.setColorFilter(ContextCompat.getColor(this, R.color.disabled_white));
            stepperPoint2.setAlpha(0.6f);
            stepperLine1.setBackgroundResource(R.color.disabled_white);
        }
    }

    private void enableStep3(boolean enable) {
        if (enable) {
            stepperMessage3.setTextColor(ContextCompat.getColor(this, R.color.white));
            stepperPoint3.setColorFilter(ContextCompat.getColor(this, R.color.whirlpoolGreen));
            stepperPoint3.setAlpha(1f);
            stepperMessage2.setAlpha(0.6f);
            stepperLine2.setBackgroundResource(R.color.whirlpoolGreen);
        } else {
            stepperMessage3.setTextColor(ContextCompat.getColor(this, R.color.disabled_white));
            stepperPoint3.setColorFilter(ContextCompat.getColor(this, R.color.disabled_white));
            stepperPoint3.setAlpha(0.6f);
            stepperLine2.setBackgroundResource(R.color.disabled_white);
        }
    }

    public static long getCycleTotalAmount(List<UTXOCoin> utxoCoinList) {

        long ret = 0L;

        for (UTXOCoin coin : utxoCoinList) {
            ret += coin.amount;
        }

        return ret;

    }

    @Override
    protected void onDestroy() {
        disposables.dispose();
        handler.removeCallbacks(incrementProgressRunnable);
        super.onDestroy();
    }

}
