package com.samourai.wallet.ricochet;

import static java.util.Objects.nonNull;

import android.content.Context;
import android.util.Log;

import com.google.common.collect.Lists;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.SendNotifTxFactory;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.constants.WALLET_INDEX;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.segwit.BIP84Util;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import com.samourai.wallet.send.FeeUtil;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.SendFactory;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.Triple;
import com.samourai.wallet.util.func.AddressFactory;
import com.samourai.wallet.whirlpool.WhirlpoolMeta;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class RicochetMeta {

    private  String SAMOURAI_RICOCHET_TX_FEE_ADDRESS = "bc1qsc887pxce0r3qed50e8he49a3amenemgptakg2";
    private  String TESTNET_SAMOURAI_RICOCHET_TX_FEE_ADDRESS = "tb1q6qkv397xf3j48mz4sdh9r3a38r22df5ddct587";

    private final static int RICOCHET_ACCOUNT = Integer.MAX_VALUE;

    public final static BigInteger samouraiFeeAmountV1 = BigInteger.valueOf(100000L);
    public final static BigInteger samouraiFeeAmountV2 = BigInteger.valueOf(100000L);

    public final static int defaultNbHops = 4;

    private static RicochetMeta instance = null;

    private static int index = 0;
    private static LinkedList<JSONObject> fifo = null;
    private static List<JSONObject> staggered = null;
    private static JSONObject lastRicochet = null;

    private static Context context = null;

    private RicochetMeta() { ; }

    public static RicochetMeta getInstance(Context ctx) {

        context = ctx;

        if(instance == null) {
            fifo = new LinkedList<JSONObject>();
            staggered = new ArrayList<JSONObject>();

            instance = new RicochetMeta();
        }

        return instance;
    }

    public Iterator<JSONObject> getIterator() {
        return fifo.iterator();
    }

    public LinkedList<JSONObject> getQueue() {
        return fifo;
    }

    public List<JSONObject> getStaggered() {
        return staggered;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        RicochetMeta.index = index;
    }

    public void setRicochetFeeAddress(String address) {
        if (SamouraiWallet.getInstance().isTestNet()) {
            TESTNET_SAMOURAI_RICOCHET_TX_FEE_ADDRESS = address;
        } else {
            SAMOURAI_RICOCHET_TX_FEE_ADDRESS = address;
        }
        Log.i("TAG", "address".concat(address));
    }

    public void add(JSONObject jObj) {
        fifo.add(jObj);
    }

    public void addStaggered(JSONObject jObj) {
        staggered.add(jObj);
    }

    public JSONObject peek() {
        if (!fifo.isEmpty()) {
            return fifo.peek();
        } else {
            return null;
        }
    }

    public JSONObject get(int pos) {
        if (!fifo.isEmpty() && pos < fifo.size()) {
            return fifo.get(pos);
        } else {
            return null;
        }
    }

    public JSONObject remove() {
        if (!fifo.isEmpty()) {
            return fifo.remove();
        } else {
            return null;
        }
    }

    public void empty() {
        fifo.clear();
    }

    public int size() {
        return fifo.size();
    }

    public JSONObject getLastRicochet() {
        return lastRicochet;
    }

    public void setLastRicochet(JSONObject lastRicochet) {
        RicochetMeta.lastRicochet = lastRicochet;
    }

    public int getRicochetAccount() {
        return RICOCHET_ACCOUNT;
    }

    public JSONObject toJSON() {

        JSONObject jsonPayload = new JSONObject();
        try {

            String zpub = BIP84Util.getInstance(context).getWallet().getAccount(RICOCHET_ACCOUNT).zpubstr();
            jsonPayload.put("xpub", zpub);

            jsonPayload.put("index", index);

            if (lastRicochet != null) {
                jsonPayload.put("last_ricochet", lastRicochet);
            }

            JSONArray array = new JSONArray();
            Iterator<JSONObject> itr = getIterator();
            while (itr.hasNext()) {
                JSONObject obj = itr.next();
                array.put(obj);
            }
            jsonPayload.put("queue", array);

            JSONArray _staggered = new JSONArray();
            for (JSONObject obj : staggered) {
                _staggered.put(obj);
            }
            jsonPayload.put("staggered", _staggered);

        } catch (JSONException je) {
            ;
        }

//        Log.i("RicochetMeta", jsonPayload.toString());

        return jsonPayload;
    }

    public void fromJSON(JSONObject jsonPayload) {

//        Log.i("RicochetMeta", jsonPayload.toString());

        try {

            if (jsonPayload.has("index")) {
                index = jsonPayload.getInt("index");
            }
            if (jsonPayload.has("last_ricochet")) {
                lastRicochet = jsonPayload.getJSONObject("last_ricochet");
            }
            if (jsonPayload.has("queue")) {

                fifo.clear();

                JSONArray array = jsonPayload.getJSONArray("queue");
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    fifo.add(obj);
                }

            }
            if (jsonPayload.has("staggered")) {
                staggered.clear();

                JSONArray _staggered = jsonPayload.getJSONArray("staggered");
                for (int i = 0; i < _staggered.length(); i++) {
                    JSONObject obj = _staggered.getJSONObject(i);
                    staggered.add(obj);
                }

            }

        } catch (JSONException ex) {
            throw new RuntimeException(ex);
        }

    }

    public RicochetTransactionInfo script(
            final long spendAmount,
            final long feePerKBAmount,
            final String strDestination,
            final int nbHops,
            final String strPCode,
            final boolean samouraiFeeViaBIP47,
            final boolean useTimeLock,
            final int account) {

        return script(
                spendAmount,
                feePerKBAmount,
                strDestination,
                nbHops,
                strPCode,
                samouraiFeeViaBIP47,
                useTimeLock,
                account,
                null,
                false);
    }

    public RicochetTransactionInfo script(
            final long spendAmount,
            final long feePerKBAmount,
            final String strDestination,
            final int nbHops,
            final String strPCode,
            final boolean samouraiFeeViaBIP47,
            final boolean useTimeLock,
            final int account,
            final List<UTXO> utxoSelectionList,
            final boolean freeUtxoSelection) {

        if (CollectionUtils.isEmpty(utxoSelectionList)) {
            return RicochetTransactionInfo.createEmpty();
        }

        final JSONObject jObj = new JSONObject();
        final List<MyTransactionOutPoint> outPoints = Lists.newArrayList();

        try {

            BigInteger biSpend = BigInteger.valueOf(spendAmount);
            BigInteger biSamouraiFee = BigInteger.valueOf(samouraiFeeAmountV2.longValue() * ((nbHops - defaultNbHops) + 1));    // default 4 hops min. for base fee, each additional hop 0.001
            BigInteger biFeePerKB = BigInteger.valueOf(feePerKBAmount);

            long latestBlock = APIFactory.getInstance(context).getLatestBlockHeight();
            long nTimeLock = 0L;
            if (useTimeLock && latestBlock > 0L) {
                nTimeLock = latestBlock;
            }

            jObj.put("ts", System.currentTimeMillis() / 1000L);
            jObj.put("hops", nbHops);
            jObj.put("spend_account", account);
            jObj.put("spend_amount", biSpend.longValue());
            jObj.put("samourai_fee", biSamouraiFee.longValue());
            jObj.put("samourai_fee_via_bip47", samouraiFeeViaBIP47);
            jObj.put("feeKB", biFeePerKB.longValue());
            jObj.put("destination", strDestination);
            if (strPCode != null) {
                jObj.put("pcode", strPCode);
            }
            if (useTimeLock) {
                jObj.put("nTimeLock", nTimeLock);
            }

            JSONObject jHop = new JSONObject();
            JSONArray jHops = new JSONArray();

            int hopSz = 0;
            if (samouraiFeeViaBIP47) {
                hopSz = FeeUtil.getInstance().estimatedSize(1, 2);
            } else {
                hopSz = FeeUtil.getInstance().estimatedSize(1, 1);
            }
            BigInteger biFeePerHop = FeeUtil.getInstance().calculateFee(hopSz, biFeePerKB);

            final Pair<List<UTXO>, BigInteger> pair = getHop0UTXO(
                    spendAmount,
                    nbHops,
                    biFeePerHop.longValue(),
                    samouraiFeeViaBIP47,
                    account,
                    utxoSelectionList,
                    freeUtxoSelection);

            final List<UTXO> utxos = pair.getLeft();
//            long totalValueSelected = 0L;
//            for (UTXO u : utxos) {
//                totalValueSelected += u.getValue();
//            }
//            Log.d("RicochetMeta", "totalValueSelected (return):" + totalValueSelected);

            // hop0 'leaves' wallet, change returned to wallet
            BigInteger hop0 = biSpend.add(biSamouraiFee).add(biFeePerHop.multiply(BigInteger.valueOf((long) nbHops)));

            //            BigInteger hop0Fee = FeeUtil.getInstance().calculateFee(hop0sz, biFeePerKB);
            BigInteger hop0Fee = pair.getRight();
//            Log.d("RicochetMeta", "hop0Fee (return):" + hop0Fee.longValue());

            final Triple<Transaction, List<MyTransactionOutPoint>, Long> txHop0Pair = getHop0Tx(
                    utxos,
                    hop0.longValue(),
                    getDestinationAddress(index),
                    hop0Fee.longValue(),
                    samouraiFeeViaBIP47,
                    nTimeLock,
                    account);

            jObj.put("change", txHop0Pair.getRight());
            outPoints.addAll(txHop0Pair.getMiddle());
            final Transaction txHop0 = txHop0Pair.getLeft();
            if (txHop0 == null) {
                return RicochetTransactionInfo.createEmpty();
            }

//            Log.d("RicochetMeta", "searching for:" + getDestinationAddress(index));
            int prevTxN = 0;
            for (int i = 0; i < txHop0.getOutputs().size(); i++) {
                Script script = txHop0.getOutputs().get(i).getScriptPubKey();
//                Log.d("RicochetMeta", "script:" + Hex.toHexString(script.getProgram()));
                String address = null;
                if (Hex.toHexString(script.getProgram()).startsWith("0014")) {
                    String hrp = null;
                    if (SamouraiWallet.getInstance().getCurrentNetworkParams() instanceof TestNet3Params) {
                        hrp = "tb";
                    } else {
                        hrp = "bc";
                    }
                    try {
                        String _script = Hex.toHexString(script.getProgram());
                        address = Bech32Segwit.encode(hrp, (byte) 0x00, Hex.decode(_script.substring(4).getBytes()));
                    } catch (Exception e) {
                        ;
                    }
//                    Log.d("RicochetMeta", "bech32:" + address);
                } else {
                    address = new Script(script.getProgram()).getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
//                    Log.d("RicochetMeta", "address from script:" + address);
                }
                if (address.equals(getDestinationAddress(index))) {
                    prevTxN = i;
//                    Log.d("RicochetMeta", "tx output n:" + prevTxN);
                    break;
                }
            }

            jHop.put("seq", 0);
            jHop.put("spend_amount", hop0.longValue());
            jHop.put("fee", hop0Fee.longValue());
            jHop.put("fee_per_hop", biFeePerHop.longValue());
            jHop.put("index", index);
            jHop.put("destination", getDestinationAddress(index));
//            Log.d("RicochetMeta", "destination:" + getDestinationAddress(index));
            int prevIndex = index;
            index++;
            jHop.put("tx", new String(Hex.encode(txHop0.bitcoinSerialize())));
            jHop.put("hash", txHop0.getHash().toString());
            if (useTimeLock) {
                jHop.put("nTimeLock", nTimeLock);
            }

            jHops.put(jHop);

            List<Pair<String, Long>> samouraiFees = new ArrayList<Pair<String, Long>>();
            if (samouraiFeeViaBIP47) {

                long baseVal = samouraiFeeAmountV2.longValue() / 4L;
                long totalVal = 0L;
                SecureRandom random = new SecureRandom();

                int _outgoingIdx = BIP47Meta.getInstance().getOutgoingIdx(BIP47Meta.strSamouraiDonationPCode);

                for (int i = 0; i < 4; i++) {
                    int val = random.nextInt((int)(samouraiFeeAmountV2.longValue() / 8L));
                    int sign = random.nextInt(1);
                    if (sign == 0) {
                        val *= -1L;
                    }
                    long feeVal = 0L;
                    if (i == 3) {
                        feeVal = samouraiFeeAmountV2.longValue() - totalVal;
                    } else {
                        feeVal = baseVal + val;
                        totalVal += feeVal;
                    }

                    //
                    // put address here
                    //
                    try {
                        PaymentCode pcode = new PaymentCode(BIP47Meta.strSamouraiDonationPCode);
                        SegwitAddress segwitAddress = BIP47Util.getInstance(context).getSendAddress(pcode, _outgoingIdx + i);
//                        String strAddress = paymentAddress.getSendECKey().toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                        //
                        // derive as bech32
                        //
                        String strAddress = segwitAddress.getBech32AsString();

                        samouraiFees.add(Pair.of(strAddress, feeVal));
//                        samouraiFees.add(Pair.of(strAddress, 200000L / 4L));
                    } catch (Exception e) {
                        samouraiFees.add(Pair.of(SendNotifTxFactory.getInstance().SAMOURAI_NOTIF_TX_FEE_ADDRESS, feeVal));
                    }

                }

            }

            Transaction txHop = null;
            String prevTxHash = txHop0.getHash().toString();
            String prevScriptPubKey = Hex.toHexString(txHop0.getOutput(prevTxN).getScriptPubKey().getProgram());

            BigInteger remainingSamouraiFee = BigInteger.ZERO;
            long prevSpendValue = hop0.longValue();
            if (!samouraiFeeViaBIP47) {
                prevSpendValue -= biSamouraiFee.longValue();
            } else {
                remainingSamouraiFee = samouraiFeeAmountV2;
            }
            int _hop = 0;
            for (int i = (nbHops - 1); i >= 0; i--) {
                _hop++;
                BigInteger hopx = null;
                if (samouraiFeeViaBIP47) {
                    remainingSamouraiFee = remainingSamouraiFee.subtract(BigInteger.valueOf(samouraiFees.get(_hop - 1).getRight()));
                    hopx = biSpend.add(biFeePerHop.multiply(BigInteger.valueOf((long) i))).add(remainingSamouraiFee);
                } else {
                    hopx = biSpend.add(biFeePerHop.multiply(BigInteger.valueOf((long) i)));
                }

                if (useTimeLock && latestBlock > 0L) {
                    nTimeLock = latestBlock + _hop;
                }
                //                Log.d("RicochetMeta", "doing hop:" + _hop);
                if (samouraiFeeViaBIP47 && ((_hop - 1) < 4)) {
                    txHop = getHopTx(prevTxHash, prevTxN, prevIndex, prevSpendValue, hopx.longValue(), _hop < nbHops ? getDestinationAddress(index) : strDestination, samouraiFees.get(_hop - 1), nTimeLock);
                } else {
                    txHop = getHopTx(prevTxHash, prevTxN, prevIndex, prevSpendValue, hopx.longValue(), _hop < nbHops ? getDestinationAddress(index) : strDestination, null, nTimeLock);
                }

                if (txHop == null) {
                    return RicochetTransactionInfo.createEmpty();
                }

                jHop = new JSONObject();
                jHop.put("seq", (nbHops - i));
                jHop.put("spend_amount", hopx.longValue());
                jHop.put("fee", biFeePerHop.longValue());
                jHop.put("prev_tx_hash", prevTxHash);
                jHop.put("prev_tx_n", prevTxN);
                jHop.put("prev_spend_value", prevSpendValue);
                jHop.put("script", prevScriptPubKey);
                jHop.put("tx", new String(Hex.encode(txHop.bitcoinSerialize())));
                jHop.put("hash", txHop.getHash().toString());
                if (useTimeLock) {
                    jHop.put("nTimeLock", nTimeLock);
                }
                if (_hop < nbHops) {
                    jHop.put("index", index);
                    jHop.put("destination", getDestinationAddress(index));
//                    Log.d("RicochetMeta", "destination:" + getDestinationAddress(index));
                    prevIndex = index;
                    index++;
                } else {
                    jHop.put("destination", strDestination);
//                    Log.d("RicochetMeta", "destination:" + strDestination);
                }

                if (samouraiFeeViaBIP47) {
                    jObj.put("samourai_fee_address", samouraiFees.get(_hop - 1).getLeft());
                    jObj.put("samourai_fee_amount", samouraiFees.get(_hop - 1).getRight());
                }

                jHops.put(jHop);

                prevTxHash = txHop.getHash().toString();
                prevTxN = 0;
                prevSpendValue = hopx.longValue();
                prevScriptPubKey = Hex.toHexString(txHop.getOutputs().get(0).getScriptPubKey().getProgram());
            }

            jObj.put("hops", jHops);

            BigInteger totalAmount = hop0.add(hop0Fee);

            jObj.put("total_spend", totalAmount.longValue());

        } catch (JSONException je) {
            return RicochetTransactionInfo.createEmpty();
        }

        System.out.println("RicochetMeta:" + jObj.toString());

        return RicochetTransactionInfo.create(jObj, outPoints);
    }

    private String getDestinationAddress(int idx) {

        HD_Address hd_addr = BIP84Util.getInstance(context).getWallet().getAccount(RICOCHET_ACCOUNT).getChain(AddressFactory.RECEIVE_CHAIN).getAddressAt(idx);
        SegwitAddress segwitAddress = new SegwitAddress(hd_addr.getECKey().getPubKey(), SamouraiWallet.getInstance().getCurrentNetworkParams());
        String address = segwitAddress.getBech32AsString();

        return address;
    }

    public static Pair<List<UTXO>, BigInteger> getHop0UTXO(
            final long spendAmount,
            final int nbHops,
            final long feePerHop,
            final boolean samouraiFeeViaBIP47,
            final int account,
            final List<UTXO> customSelectionUTXOList,
            final boolean freeUtxoSelection) {

        final List<UTXO> utxos;
        if (nonNull(customSelectionUTXOList)) {
            utxos = customSelectionUTXOList;
        } else if (account == WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()) {
            utxos = APIFactory.getInstance(context).getUtxosPostMix(true);
        } else {
            utxos = APIFactory.getInstance(context).getUtxos(true);
        }

        final List<UTXO> selectedUTXO = new ArrayList<UTXO>();
        long totalValueSelected = 0L;
        long totalSpendAmount = 0L;
        int selected = 0;

        // sort in ascending order by value
        Collections.sort(utxos, new UTXO.UTXOComparator());

        for (final UTXO u : utxos) {
            selectedUTXO.add(u);
            totalValueSelected += u.getValue();
            selected += u.getOutpoints().size();
//            Log.d("RicochetMeta", "selected:" + u.getValue());

            if (samouraiFeeViaBIP47) {
                totalSpendAmount = spendAmount + samouraiFeeAmountV2.longValue() + (feePerHop * nbHops) + SamouraiWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFee(selected, 3).longValue();
            } else {
                totalSpendAmount = spendAmount + samouraiFeeAmountV1.longValue() + (feePerHop * nbHops) + SamouraiWallet.bDust.longValue() + FeeUtil.getInstance().estimatedFee(selected, 3).longValue();
            }
//            Log.d("RicochetMeta", "totalSpendAmount:" + totalSpendAmount);
//            Log.d("RicochetMeta", "totalValueSelected:" + totalValueSelected);
            if (!freeUtxoSelection && totalValueSelected >= totalSpendAmount) {
//                Log.d("RicochetMeta", "breaking");
                break;
            }
        }

        if (selectedUTXO.size() < 1) {
            return Pair.of(Lists.newArrayList(), BigInteger.valueOf(0L));
        } else {
            return Pair.of(selectedUTXO, FeeUtil.getInstance().estimatedFee(selected, 3));
        }
    }

    private Triple<Transaction, List<MyTransactionOutPoint>, Long> getHop0Tx(
            final List<UTXO> utxos,
            final long spendAmount,
            final String destination,
            final long fee,
            final boolean samouraiFeeViaBIP47,
            final long nTimeLock,
            final int account) {

        final List<MyTransactionOutPoint> unspent = new ArrayList<>();
        long totalValueSelected = 0L;
        for (UTXO u : utxos) {
            totalValueSelected += u.getValue();
            unspent.addAll(u.getOutpoints());
        }

//        Log.d("RicochetMeta", "spendAmount:" + spendAmount);
//        Log.d("RicochetMeta", "fee:" + fee);
//        Log.d("RicochetMeta", "totalValueSelected:" + totalValueSelected);

        final BigInteger samouraiFeeAmount = samouraiFeeAmountV2;

        final long changeAmount = totalValueSelected - (spendAmount + fee);
//        Log.d("RicochetMeta", "changeAmount:" + changeAmount);
        final Map<String, BigInteger> receivers = new HashMap<>();

        if (changeAmount > 0L) {
            WALLET_INDEX walletIndex = (account == WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix() ? WALLET_INDEX.POSTMIX_CHANGE : WALLET_INDEX.BIP84_CHANGE);
            String change_address = AddressFactory.getInstance(context).getAddressAndIncrement(walletIndex).getRight();
            receivers.put(change_address, BigInteger.valueOf(changeAmount));
        }

        if (samouraiFeeViaBIP47) {
            // Samourai fee paid in the hops
            receivers.put(destination, BigInteger.valueOf(spendAmount));
        } else {
            receivers.put(SamouraiWallet.getInstance().isTestNet() ? TESTNET_SAMOURAI_RICOCHET_TX_FEE_ADDRESS : SAMOURAI_RICOCHET_TX_FEE_ADDRESS, samouraiFeeAmount);
            receivers.put(destination, BigInteger.valueOf(spendAmount - samouraiFeeAmount.longValue()));
        }

        Transaction tx = SendFactory.getInstance(context).makeTransaction(unspent, receivers);
        if (nTimeLock > 0L) {
            tx.setLockTime(nTimeLock);
        }
        tx = SendFactory.getInstance(context).signTransaction(tx, account);

        return Triple.of(tx, unspent, changeAmount);
    }

    private Transaction getHopTx(String prevTxHash, int prevTxN, int prevIndex, long prevSpendAmount, long spendAmount, String destination, Pair<String, Long> samouraiFeePair, long nTimeLock) {

        TransactionOutput output = null;
        if (destination.toLowerCase().startsWith("tb") || destination.toLowerCase().startsWith("bc")) {

            byte[] bScriptPubKey = null;

            try {
                Pair<Byte, byte[]> pair = Bech32Segwit.decode(SamouraiWallet.getInstance().isTestNet() ? "tb" : "bc", destination);
                bScriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
            } catch (Exception e) {
                return null;
            }
            output = new TransactionOutput(SamouraiWallet.getInstance().getCurrentNetworkParams(), null, Coin.valueOf(spendAmount), bScriptPubKey);
        } else {
            Script outputScript = ScriptBuilder.createOutputScript(org.bitcoinj.core.Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), destination));
            output = new TransactionOutput(SamouraiWallet.getInstance().getCurrentNetworkParams(), null, Coin.valueOf(spendAmount), outputScript.getProgram());
        }

        HD_Address address = BIP84Util.getInstance(context).getWallet().getAccount(RICOCHET_ACCOUNT).getChain(AddressFactory.RECEIVE_CHAIN).getAddressAt(prevIndex);
        ECKey ecKey = address.getECKey();
        SegwitAddress p2wpkh = new SegwitAddress(ecKey.getPubKey(), SamouraiWallet.getInstance().getCurrentNetworkParams());
        Script redeemScript = p2wpkh.segwitRedeemScript();

        Transaction tx = new Transaction(SamouraiWallet.getInstance().getCurrentNetworkParams());
        if (nTimeLock > 0L) {
            tx.setLockTime(nTimeLock);
        }
        tx.addOutput(output);

        if (samouraiFeePair != null) {

            byte[] bScriptPubKey = null;

            try {
                Pair<Byte, byte[]> pair = Bech32Segwit.decode(SamouraiWallet.getInstance().isTestNet() ? "tb" : "bc", samouraiFeePair.getLeft());
                bScriptPubKey = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
            } catch (Exception e) {
                return null;
            }
            TransactionOutput _output = new TransactionOutput(SamouraiWallet.getInstance().getCurrentNetworkParams(), null, Coin.valueOf(samouraiFeePair.getRight()), bScriptPubKey);
            tx.addOutput(_output);
        }

//        Log.d("RicochetMeta", "spending from:" + p2wpkh.getBech32AsString());
//        Log.d("RicochetMeta", "pubkey:" + Hex.toHexString(ecKey.getPubKey()));

        Sha256Hash txHash = Sha256Hash.wrap(prevTxHash);
        TransactionOutPoint outPoint = new TransactionOutPoint(SamouraiWallet.getInstance().getCurrentNetworkParams(), prevTxN, txHash, Coin.valueOf(prevSpendAmount));
        TransactionInput txInput = new TransactionInput(SamouraiWallet.getInstance().getCurrentNetworkParams(), null, new byte[]{}, outPoint, Coin.valueOf(prevSpendAmount));
        if (PrefsUtil.getInstance(context).getValue(PrefsUtil.RBF_OPT_IN, false) == true) {
            txInput.setSequenceNumber(SamouraiWallet.RBF_SEQUENCE_VAL.longValue());
        }
        tx.addInput(txInput);

        TransactionSignature sig = tx.calculateWitnessSignature(0, ecKey, redeemScript.scriptCode(), Coin.valueOf(prevSpendAmount), Transaction.SigHash.ALL, false);
        final TransactionWitness witness = new TransactionWitness(2);
        witness.setPush(0, sig.encodeToBitcoin());
        witness.setPush(1, ecKey.getPubKey());
        tx.setWitness(0, witness);

        assert (0 == tx.getInput(0).getScriptBytes().length);
//        Log.d("RicochetMeta", "script sig length:" + tx.getInput(0).getScriptBytes().length);

        tx.verify();

        return tx;
    }

    public static long getFeesFor1Inputs() {
        return SamouraiWallet.bDust.longValue() +
                RicochetMeta.samouraiFeeAmountV1.longValue() + computeHopFee() +
                FeeUtil.getInstance().estimatedFee(1, 3).longValue();
    }

    public static long computeHopFee() {
        return computeFeePerHop() * defaultNbHops;
    }

    public static long computeFeePerHop() {
        final boolean samouraiFeeViaBIP47 = BIP47Meta.getInstance()
                .getOutgoingStatus(BIP47Meta.strSamouraiDonationPCode) == BIP47Meta.STATUS_SENT_CFM;
        final int hopSz  = samouraiFeeViaBIP47
                ? FeeUtil.getInstance().estimatedSize(1, 2)
                : FeeUtil.getInstance().estimatedSize(1, 1);

        final long biFeePerKB = FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().longValue();
        final BigInteger biFeePerHop = FeeUtil.getInstance().calculateFee(hopSz, BigInteger.valueOf(biFeePerKB));
        return biFeePerHop.longValue();
    }

}
