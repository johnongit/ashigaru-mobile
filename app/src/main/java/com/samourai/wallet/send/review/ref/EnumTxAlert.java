package com.samourai.wallet.send.review.ref;

import static com.samourai.wallet.constants.SamouraiAccountIndex.DEPOSIT;
import static com.samourai.wallet.util.func.AddressHelper.isMyDepositOrPostmixAddress;
import static com.samourai.wallet.util.func.TransactionOutPointHelper.toUtxos;
import static com.samourai.wallet.util.tech.NumberHelper.numberOfTrailingZeros;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import android.util.Log;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.samourai.wallet.R;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.api.seen.RawSeenAddresses;
import com.samourai.wallet.send.BlockedUTXO;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.SendActivity;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.send.review.ReviewTxModel;
import com.samourai.wallet.send.review.TxAlertReview;
import com.samourai.wallet.send.review.TxData;
import com.samourai.wallet.util.func.AddressHelper;
import com.samourai.wallet.util.func.BatchSendUtil;
import com.samourai.wallet.util.func.EnumAddressType;
import com.samourai.wallet.util.func.MyTransactionOutPointAmountComparator;
import com.samourai.wallet.util.func.SendAddressUtil;

import org.apache.commons.collections4.CollectionUtils;
import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public enum EnumTxAlert {

    SENDING_TO_LEGAL_FUND_DONATION_ADDRESS {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_send_to_donation_address,
                    R.string.tx_alert_desc_send_to_donation_address,
                    null);
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, boolean forInit) {
            if (reviewTxModel.getAddress().equals(SendActivity.DONATION_ADDRESS_MAINNET)
                    || reviewTxModel.getAddress().equals(SendActivity.DONATION_ADDRESS_TESTNET)) {
                return createAlert(reviewTxModel);
            }
            return null;
        }
    },
    REUSED_SENDING_ADDRESS_LOCAL {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_reused_sending_address_local,
                    R.string.tx_alert_desc_reused_sending_address_local,
                    null);
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, final boolean forInit) {

            if (!forInit) {
                // avoid to call api several times
                return reviewTxModel.getAlertReviews().getValue().get(this);
            }

            if (reviewTxModel.getAddress().equals(SendActivity.DONATION_ADDRESS_MAINNET)
                    || reviewTxModel.getAddress().equals(SendActivity.DONATION_ADDRESS_TESTNET)) {
                return null;
            }

            final TxAlertReview alert = createAlert(reviewTxModel);

            if (reviewTxModel.getSendType().isBatchSpend()) {
                final BatchSendUtil batchSendUtil = BatchSendUtil.getInstance();
                for (final BatchSendUtil.BatchSend batchSend : CollectionUtils.emptyIfNull(batchSendUtil.getCopyOfBatchSends())) {
                    if (!batchSend.isPayNym()) {
                        try {
                            final String addr = batchSend.getAddr(reviewTxModel.getApplication());
                            if (SendAddressUtil.getInstance().get(addr) == 1) {
                                alert.addReusedAddress(addr);
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            } else {
                final String address = reviewTxModel.getAddress();
                if (SendAddressUtil.getInstance().get(address) == 1) {
                    alert.addReusedAddress(address);
                }
            }
            return alert.getReusedAddresses().isEmpty() ? null : alert;
        }
    },REUSED_SENDING_ADDRESS_GLOBAL {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_reused_sending_address_global,
                    R.string.tx_alert_desc_reused_sending_address_global,
                    null);
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, final boolean forInit) {

            final TxAlertReview localAlert = REUSED_SENDING_ADDRESS_LOCAL.checkForAlert(reviewTxModel, forInit);
            final Set<String> localReusedAddresses = nonNull(localAlert)
                    ? localAlert.getReusedAddresses()
                    : ImmutableSet.of();

            final RawSeenAddresses seenAddresses = reviewTxModel.getSeenAddresses();
            if (isNull(seenAddresses)) return null;

            if (reviewTxModel.getAddress().equals(SendActivity.DONATION_ADDRESS_MAINNET)
                    || reviewTxModel.getAddress().equals(SendActivity.DONATION_ADDRESS_TESTNET)) {
                return null;
            }

            final Set<String> allGlobalSeenAddresses = Sets.newHashSet(seenAddresses.allSeenAddresses());
            allGlobalSeenAddresses.removeAll(localReusedAddresses);

            final TxAlertReview alert = createAlert(reviewTxModel);

            if (reviewTxModel.getSendType().isBatchSpend()) {
                alert.addReusedAddresses(allGlobalSeenAddresses);
            } else {
                final String address = reviewTxModel.getAddress();
                if (allGlobalSeenAddresses.contains(address)) {
                    alert.addReusedAddress(address);
                }
            }
            return alert.getReusedAddresses().isEmpty() ? null : alert;
        }
    },
    UNNECESSARY_INPUTS {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_unnecessary_input,
                    R.string.tx_alert_desc_unnecessary_input,
                    () -> {
                        final List<MyTransactionOutPoint> toRemovePoints =
                                getUnnecessaryInputs(reviewTxModel);
                        if (CollectionUtils.isEmpty(toRemovePoints)) return null;
                        reviewTxModel.removeCustomSelectionUtxos(toUtxos(toRemovePoints));
                        reviewTxModel.refreshModel();
                        return "fix";
                    });
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, boolean forInit) {
            if (CollectionUtils.isNotEmpty(getUnnecessaryInputs(reviewTxModel))) {
                return createAlert(reviewTxModel);
            }
            return null;
        }

        private List<MyTransactionOutPoint> getUnnecessaryInputs(
                final ReviewTxModel reviewTxModel) {

            if (! reviewTxModel.getSendType().isCustomSelection()) return null;

            final long amountForDest = reviewTxModel.getImpliedAmount().getValue();
            final long fees = reviewTxModel.getFeeAggregated().getValue();
            final long neededAmount = amountForDest + fees;

            final List<MyTransactionOutPoint> selectedUTXOPoints = reviewTxModel.getTxData()
                    .getValue().getSelectedUTXOPoints();
            final List<MyTransactionOutPoint> orderedUTXOPoints = Ordering
                    .from(new MyTransactionOutPointAmountComparator(true))
                    .sortedCopy(selectedUTXOPoints);

            final List<MyTransactionOutPoint> toRemovePoints = Lists.newArrayList();
            long accuAmount = 0L;
            int i = 0;
            while(i < orderedUTXOPoints.size()) {
                if (accuAmount >= neededAmount) {
                    toRemovePoints.add(orderedUTXOPoints.get(i));
                }
                final Coin coin = orderedUTXOPoints.get(i).getValue();
                if (nonNull(coin)) {
                    accuAmount += coin.value;
                }
                ++ i;
            }
            return toRemovePoints;
        }
    },
    ROUNDED_SENDING_AMOUNT_HEURISTIC {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_rounded_sending_amount,
                    R.string.tx_alert_desc_rounded_sending_amount,
                    null);
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, boolean forInit) {
            final EnumSendType sendType = reviewTxModel.getSendType();
            if (sendType != EnumSendType.SPEND_SIMPLE &&
                    sendType != EnumSendType.SPEND_CUSTOM) return null;
            final TxData txData = reviewTxModel.getTxData().getValue();
            if (txData.getChange() <= 0L) return null;

            final long amountToDest = reviewTxModel.getImpliedAmount().getValue();
            final int trailingZerosLeft = numberOfTrailingZeros(amountToDest);
            final int trailingZerosRight = numberOfTrailingZeros(txData.getChange());
            if (min(trailingZerosLeft, trailingZerosRight) <= 2 &&
                    max(trailingZerosLeft, trailingZerosRight) >= 3) {
                return createAlert(reviewTxModel);
            }

            return null;
        }
    },
    SCRIPT_TYPE_HEURISTIC {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_scrypt_type_heuristic,
                    R.string.tx_alert_desc_scrypt_type_heuristic,
                    () -> {
                        reviewTxModel.setUseLikeRequestedAsFixForTx(true);
                        reviewTxModel.refreshModel();
                        return "not fixed !";
                    });
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, boolean forInit) {
            if (reviewTxModel.getSendType().isBatchSpend()) return null;
            if (reviewTxModel.getSendType() == EnumSendType.SPEND_BOLTZMANN) return null;
            if (EnumAddressType.fromAddress(reviewTxModel.getAddress()).isSegwitNative()) return null;
            if (! reviewTxModel.isChangeUseLikeTyped() &&
                    ! reviewTxModel.isUseLikeRequestedAsFixForTx()) {
                return createAlert(reviewTxModel);
            }
            return null;
        }
    },
    DUST_OUTPUT {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_spend_dust_output,
                    R.string.tx_alert_desc_spend_dust_output,
                    () -> {

                        final boolean postmixAccount = reviewTxModel.isPostmixAccount();
                        final List<MyTransactionOutPoint> toRemovePoints = Lists.newArrayList();

                        for (final MyTransactionOutPoint outPoint : reviewTxModel.getTxData().getValue().getSelectedUTXOPoints()) {
                            if (nonNull(outPoint.getValue())) {
                                final long amount = outPoint.getValue().value;
                                if (amount < SamouraiWallet.bDust.longValue()) {
                                    toRemovePoints.add(outPoint);
                                    if (postmixAccount) {
                                        BlockedUTXO.getInstance().addPostMix(
                                                outPoint.getHash().toString(),
                                                outPoint.getTxOutputN(),
                                                amount);
                                    } else {
                                        BlockedUTXO.getInstance().add(
                                                outPoint.getHash().toString(),
                                                outPoint.getTxOutputN(),
                                                amount);
                                    }
                                }
                            }
                        }

                        if (CollectionUtils.isNotEmpty(toRemovePoints)) {
                            reviewTxModel.removeCustomSelectionUtxos(toUtxos(toRemovePoints));
                            reviewTxModel.refreshModel();
                        }

                        return "fixed !";
                    });
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, boolean forInit) {
            try {
                reviewTxModel.getSendType().buildTx(reviewTxModel);

                for (final UTXO utxo : reviewTxModel.getTxData().getValue().getSelectedUTXO()) {
                    for (final MyTransactionOutPoint outPoint : utxo.getOutpoints()) {
                        if (nonNull(outPoint.getValue())) {
                            if (outPoint.getValue().value < SamouraiWallet.bDust.longValue()) {
                                return createAlert(reviewTxModel);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
                throw new RuntimeException(e);
            }
            return null;
        }
    },
    SENDING_POSTMIX_TO_DEPOSIT_ACCOUNT {
        @Override
        public TxAlertReview createAlert(final ReviewTxModel reviewTxModel) {
            return TxAlertReview.create(
                    R.string.tx_alert_title_spend_from_postmix_to_deposit,
                    R.string.tx_alert_desc_spend_from_postmix_to_deposit,
                    null);
        }

        @Override
        public TxAlertReview checkForAlert(final ReviewTxModel reviewTxModel, boolean forInit) {

            if (reviewTxModel.isPostmixAccount()) {

                final AddressHelper.AddressInfo myDepositOrPostmixAddress = isMyDepositOrPostmixAddress(
                        reviewTxModel.getApplication(),
                        reviewTxModel.getAddress(),
                        256);

                if (myDepositOrPostmixAddress.isFound() &&
                        Objects.equals(myDepositOrPostmixAddress.getAccountIdx(), DEPOSIT) ) {
                    return createAlert(reviewTxModel);
                }

            }
            return null;
        }
    };

    private static final String TAG = "EnumTxAlert";

    abstract public TxAlertReview createAlert(final ReviewTxModel reviewTxModel);

    abstract public TxAlertReview checkForAlert(
            final ReviewTxModel reviewTxModel,
            final boolean forInit);
}
