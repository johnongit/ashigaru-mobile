package com.samourai.wallet.api;

import static com.samourai.wallet.util.network.WebUtil.SAMOURAI_API2;
import static com.samourai.wallet.util.tech.LogUtil.debug;
import static com.samourai.wallet.util.tech.LogUtil.info;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.auth0.android.jwt.JWT;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Multimaps;
import com.samourai.wallet.BuildConfig;
import com.samourai.wallet.R;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.api.fee.FeeClient;
import com.samourai.wallet.api.fee.RawFees;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.constants.WALLET_INDEX;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.network.dojo.DojoUtil;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.ricochet.RicochetMeta;
import com.samourai.wallet.segwit.BIP49Util;
import com.samourai.wallet.segwit.BIP84Util;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.send.BlockedUTXO;
import com.samourai.wallet.send.FeeUtil;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.RBFUtil;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.tor.SamouraiTorManager;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.func.AddressFactory;
import com.samourai.wallet.util.func.EnumAddressType;
import com.samourai.wallet.util.func.FormatsUtil;
import com.samourai.wallet.util.func.SentToFromBIP47Util;
import com.samourai.wallet.util.network.BackendApiAndroid;
import com.samourai.wallet.util.network.WebUtil;
import com.samourai.wallet.util.tech.AppUtil;
import com.samourai.wallet.utxos.UTXOUtil;
import com.samourai.wallet.whirlpool.WhirlpoolMeta;
import com.samourai.whirlpool.client.wallet.WhirlpoolUtils;

import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.script.Script;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.math.BigInteger;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import io.reactivex.Observable;
import io.reactivex.subjects.BehaviorSubject;

public class APIFactory {

    public static final String TAG = APIFactory.class.getSimpleName();

    private static String APP_TOKEN = null;         // API app token
    private static String ACCESS_TOKEN = null;      // API access token
    private static long ACCESS_TOKEN_REFRESH = 300L;  // in seconds

    private static long xpub_balance = 0L;
    private static long xpub_premix_balance = 0L;
    private static long xpub_postmix_balance = 0L;
    private static long xpub_badbank_balance = 0L;
    private static Map<String, Long> xpub_amounts = null;
    private static Map<String,List<Tx>> xpub_txs = null;
    private static Map<String,List<Tx>> premix_txs = null;
    private static Map<String,List<Tx>> postmix_txs = null;
    private static Map<String,List<Tx>> badbank_txs = null;
    private static Map<String,Integer> unspentAccounts = null;
    private static Map<String,Integer> unspentBIP49 = null;
    private static Map<String,Integer> unspentBIP84 = null;
    private static Map<String,Integer> unspentBIP84PreMix = null;
    private static Map<String,Integer> unspentBIP84PostMix = null;
    private static Map<String,Integer> unspentBIP84BadBank = null;
    private static Map<String,String> unspentPaths = null;
    private static Map<String,UTXO> utxos = null;
    private static Map<String, UTXO> utxosP2PKH = null;
    private static Map<String, UTXO> utxosP2SH_P2WPKH = null;
    private static Map<String, UTXO> utxosP2WPKH = null;
    private static Map<String,UTXO> utxosPreMix = null;
    private static Map<String,UTXO> utxosPostMix = null;
    private static Map<String,UTXO> utxosBadBank = null;

    private static HashMap<String, Long> bip47_amounts = null;
    public boolean walletInit = false;

    //Broadcast balance changes to the application, this will be a timestamp,
    //Balance will be recalculated when the change is broadcasted
    public BehaviorSubject<Long> walletBalanceObserver = BehaviorSubject.create();
    public MutableLiveData<Long> walletBalanceObserverLiveData = new MutableLiveData(System.currentTimeMillis());
    private static long latest_block_height = -1L;
    private static String latest_block_hash = null;
    private static long latest_block_time = -1L;

    private int lastRicochetReceiveIdx = 0;

    private static int XPUB_PREMIX = 1;
    private static int XPUB_POSTMIX = 2;
    private static int XPUB_BADBANK = 3;

    private static APIFactory instance = null;

    private static Context context = null;

    private static AlertDialog alertDialog = null;

    private APIFactory()	{
        reset();
        walletBalanceObserver.onNext(System.currentTimeMillis());
        walletBalanceObserverLiveData.postValue(System.currentTimeMillis());
    }

    public static APIFactory getInstance(Context ctx) {
        context = ctx;

        if(instance == null) {
            instance = new APIFactory();
        }
        return instance;
    }

    public synchronized void reset() {
        xpub_balance = 0L;
        xpub_premix_balance = 0L;
        xpub_postmix_balance = 0L;
        xpub_badbank_balance = 0L;
        xpub_amounts = new LinkedHashMap<>();
        bip47_amounts = new LinkedHashMap<>();
        xpub_txs = new LinkedHashMap<>();
        premix_txs = new LinkedHashMap<>();
        postmix_txs = new LinkedHashMap<>();
        badbank_txs = new LinkedHashMap<>();
        unspentPaths = new LinkedHashMap<>();
        unspentAccounts = new LinkedHashMap<>();
        unspentBIP49 = new LinkedHashMap<>();
        unspentBIP84 = new LinkedHashMap<>();
        unspentBIP84PostMix = new LinkedHashMap<>();
        unspentBIP84PreMix = new LinkedHashMap<>();
        unspentBIP84BadBank = new LinkedHashMap<>();
        utxos = new LinkedHashMap<>();
        utxosP2PKH = new LinkedHashMap<>();
        utxosP2SH_P2WPKH = new LinkedHashMap<>();
        utxosP2WPKH = new LinkedHashMap<>();
        utxosPostMix = new LinkedHashMap<>();
        utxosPreMix = new LinkedHashMap<>();
        utxosBadBank = new LinkedHashMap<>();
        onUtxoChange();
    }

    public String getAccessTokenNotExpired() throws Exception {
        boolean setupDojo = DojoUtil.getInstance(context).getDojoParams() != null;

        String currentAccessToken = getAccessToken();
        if(currentAccessToken == null)    {
            // no current token => request new token
            Log.v("APIFactory", "getAccessTokenNotExpired => requesting new, setupDojo="+setupDojo);
            getToken(setupDojo, false);
            currentAccessToken = getAccessToken();
        }

        // still no token => not available
        if (StringUtils.isEmpty(currentAccessToken)) {
            Log.v("APIFactory", "getAccessTokenNotExpired => not available");
            return currentAccessToken;
        }

        // check current token not expired
        JWT jwt = new JWT(currentAccessToken);
        if(jwt.isExpired(getAccessTokenRefresh())) {
            // expired => request new token
            Log.v("APIFactory", "getAccessTokenNotExpired => expired, request new");
            getToken(setupDojo, false);
            currentAccessToken = getAccessToken();
        }
        return currentAccessToken;
    }

    public String getAccessToken() {
        if(ACCESS_TOKEN == null && APIFactory.getInstance(context).APITokenRequired())    {
            try {
                getToken(true, false);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return DojoUtil.getInstance(context).getDojoParams() == null ? "" : ACCESS_TOKEN;
    }

    public void setAccessToken(String accessToken) {
        ACCESS_TOKEN = accessToken;
    }

    public void setAppToken(String token)   {
        if (!StringUtils.equals(token, APP_TOKEN)) {
            BackendApiAndroid.reset(); // reset BackendApi on change
        }
        APP_TOKEN = token;
    }

    public String getAppToken()  {

        if(APP_TOKEN != null)    {
            return APP_TOKEN;
        }
        else    {
            return new String(getXORKey());
        }

    }

    public byte[] getXORKey() {

        if(APP_TOKEN != null)    {
            return APP_TOKEN.getBytes();
        }

        if(BuildConfig.XOR_1.length() > 0 && BuildConfig.XOR_2.length() > 0)    {
            try {
                byte[] xorSegments0 = Base64.decode(BuildConfig.XOR_1);
                byte[] xorSegments1 = Base64.decode(BuildConfig.XOR_2);
                return xor(xorSegments0, xorSegments1);
            } catch (final Exception e) {
                return null;
            }
        } else    {
            return null;
        }
    }

    private byte[] xor(byte[] b0, byte[] b1) {

        byte[] ret = new byte[b0.length];

        for(int i = 0; i < b0.length; i++){
            ret[i] = (byte)(b0[i] ^ b1[i]);
        }

        return ret;

    }

    public long getAccessTokenRefresh() {
        return ACCESS_TOKEN_REFRESH;
    }

    public boolean stayingAlive()   {

        if(!AppUtil.getInstance(context).isOfflineMode() && APITokenRequired())    {

            if(APIFactory.getInstance(context).getAccessToken() == null)    {
                try {
                    APIFactory.getInstance(context).getToken(false, false);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(APIFactory.getInstance(context).getAccessToken() != null)    {
                JWT jwt = new JWT(APIFactory.getInstance(context).getAccessToken());
                if(jwt != null && jwt.isExpired(APIFactory.getInstance(context).getAccessTokenRefresh()))    {
                    try {
                        if(APIFactory.getInstance(context).getToken(false, false))  {
                            return true;
                        }
                        else    {
                            return false;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }

            return false;

        }
        else    {
            return true;
        }

    }

    public boolean APITokenRequired()  {
        return DojoUtil.getInstance(context).getDojoParams() == null ? false : true;
    }

    public boolean getToken(boolean setupDojo, boolean reauth401) {

        if(!APITokenRequired())    {
            return true;
        }

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : SAMOURAI_API2;

        if(DojoUtil.getInstance(context).getDojoParams() != null || setupDojo)    {
            _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET_TOR :  WebUtil.SAMOURAI_API2_TOR;
        }

        debug("APIFactory", "getToken() url:" + _url);

        JSONObject jsonObject  = null;

        try {

            String response = null;

            if(AppUtil.getInstance(context).isOfflineMode())    {
                return true;
            }
            else if(!SamouraiTorManager.INSTANCE.isRequired())    {
                // use POST
                StringBuilder args = new StringBuilder();
                args.append("apikey=");
                args.append(new String(getXORKey()));
                response = WebUtil.getInstance(context).postURL(_url + "auth/login?", args.toString());
                info("APIFactory", "API token response:" + response);
            }
            else    {
                HashMap<String,String> args = new HashMap<String,String>();
                args.put("apikey", new String(getXORKey()));
                info("APIFactory", "API key (XOR):" + new String(getXORKey()));
                info("APIFactory", "API key url:" + _url);
                response = WebUtil.getInstance(context).tor_postURL(_url + "auth/login", args);
                info("APIFactory", "API token response:" + response);
            }

            try {
                jsonObject = new JSONObject(response);
                if(jsonObject != null && jsonObject.has("authorizations"))    {
                    JSONObject authObj = jsonObject.getJSONObject("authorizations");
                    if(authObj.has("access_token"))    {
                        info("APIFactory", "setting access token:" + authObj.getString("access_token"));
                        setAccessToken(authObj.getString("access_token"));
                        if (reauth401) {
                            initWalletAmounts();
                        }
                        return true;
                    }
                }else{
                    return  false;
                }
            } catch(JSONException je) {
                je.printStackTrace();
                return false;
            }
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public synchronized JSONObject getXPUB(
            final Collection<String> addresses,
            final boolean parse) {

        return getXPUB(
                ImmutableListMultimap.<EnumAddressType, String>builder()
                        .putAll(EnumAddressType.BIP44_LEGACY, addresses)
                        .build(),
                parse);
    }

    private synchronized JSONObject getXPUB(
            final ListMultimap<EnumAddressType, String> addressesByType,
            final boolean parse) {

        final String walletapiService = BackendApiAndroid.getApiServiceUrl("wallet?");

        JSONObject jsonObject  = null;

        try {

            String response = null;

            if(AppUtil.getInstance(context).isOfflineMode())    {
                response = PayloadUtil.getInstance(context).deserializeMultiAddr().toString();
            }
            else if(!SamouraiTorManager.INSTANCE.isRequired())    {
                // use POST
                final StringBuilder args = new StringBuilder();
                for (final EnumAddressType type : addressesByType.keySet()) {
                    switch (type) {
                        case BIP44_LEGACY:
                            args.append("active=");
                            args.append(StringUtils.join(addressesByType.get(type), URLEncoder.encode("|", "UTF-8")));
                            args.append("&");
                            break;
                        case BIP49_SEGWIT_COMPAT:
                            args.append("bip49=");
                            args.append(StringUtils.join(addressesByType.get(type), URLEncoder.encode("|", "UTF-8")));
                            args.append("&");
                            break;
                        case BIP84_SEGWIT_NATIVE:
                            args.append("bip84=");
                            args.append(StringUtils.join(addressesByType.get(type), URLEncoder.encode("|", "UTF-8")));
                            args.append("&");
                            break;
                        default:
                            info("APIFactory", "unknown EnumAddressType:" + type);
                            break;
                    }
                }

                info("APIFactory", "XPUB:" + args.toString());
                args.append("at=");
                args.append(getAccessToken());
                response = WebUtil.getInstance(context).postURL(walletapiService, args.toString());
                //info("APIFactory", "XPUB response:" + response);
            }
            else    {
                final Map<String,String> args = Maps.newHashMap();

                for (final EnumAddressType type : addressesByType.keySet()) {
                    switch (type) {
                        case BIP44_LEGACY:
                            args.put("active", StringUtils.join(addressesByType.get(type), "|"));
                            break;
                        case BIP49_SEGWIT_COMPAT:
                            args.put("bip49", StringUtils.join(addressesByType.get(type), "|"));
                            break;
                        case BIP84_SEGWIT_NATIVE:
                            args.put("bip84", StringUtils.join(addressesByType.get(type), "|"));
                            break;
                        default:
                            info("APIFactory", "unknown EnumAddressType:" + type);
                            break;
                    }
                }

                info("APIFactory", "XPUB:" + args.toString());
                args.put("at", getAccessToken());
                info("APIFactory", "XPUB access token:" + getAccessToken());
                response = WebUtil.getInstance(context).tor_postURL(walletapiService, args);
                //info("APIFactory", "XPUB response:" + response);
            }

            try {
                jsonObject = new JSONObject(response);
                if(!parse)    {
                    return jsonObject;
                }

                xpub_txs.put(ImmutableList.copyOf(addressesByType.values()).get(0), new ArrayList<Tx>());
                parseXPUB(jsonObject);
                parseDynamicFees_bitcoind(jsonObject);
                parse1DollarFeesEstimator(jsonObject);
                xpub_amounts.put(HD_WalletFactory.getInstance(context).get().getAccount(0).xpubstr(), xpub_balance - BlockedUTXO.getInstance().getTotalValueBlocked0());
                walletBalanceObserver.onNext( System.currentTimeMillis());
                walletBalanceObserverLiveData.postValue(System.currentTimeMillis());
            }
            catch(JSONException je) {
                je.printStackTrace();
                jsonObject = null;
            }
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    public synchronized JSONObject registerXPUB(
            final String xpub,
            final int purpose,
            final String tag) {

        JSONObject jsonObject  = null;

        try {

            String response = null;

            if(!SamouraiTorManager.INSTANCE.isRequired())    {
                // use POST
                StringBuilder args = new StringBuilder();
                args.append("xpub=");
                args.append(xpub);
                args.append("&type=");
                if(Objects.equals(PrefsUtil.getInstance(context).getValue(PrefsUtil.WALLET_ORIGIN, "restored"), "restored"))   {
                    args.append("restore");
                    args.append("&force=true");
                }
                else    {
                    args.append("new");
                }
                if(purpose == 49)    {
                    args.append("&segwit=");
                    args.append("bip49");
                }
                else if(purpose == 84)   {
                    args.append("&segwit=");
                    args.append("bip84");
                }
                else    {
                    ;
                }
                info("APIFactory", "XPUB:" + args.toString());
                args.append("&at=");
                args.append(getAccessToken());
                final String xpubApiService = BackendApiAndroid.getApiServiceUrl("xpub?");
                response = WebUtil.getInstance(context).postURL(xpubApiService, args.toString());
                //info("APIFactory", "XPUB response:" + response);
            } else  {
                HashMap<String,String> args = new HashMap<String,String>();
                args.put("xpub", xpub);
                if(Objects.equals(PrefsUtil.getInstance(context).getValue(PrefsUtil.WALLET_ORIGIN, "restored"), "restored"))   {
                    args.put("type", "restore");
                    args.put("force", "true");
                }
                else    {
                    args.put("type", "new");
                }
                if(purpose == 49)    {
                    args.put("segwit", "bip49");
                }
                else if(purpose == 84)   {
                    args.put("segwit", "bip84");
                }
                else    {
                    ;
                }
                info("APIFactory", "XPUB:" + args.toString());
                args.put("at", getAccessToken());
                final String xpubApiService = BackendApiAndroid.getApiServiceUrl("xpub");
                response = WebUtil.getInstance(context).tor_postURL(xpubApiService, args);
                //info("APIFactory", "XPUB response:" + response);
            }

            try {
                jsonObject = new JSONObject(response);
                //info("APIFactory", "XPUB response:" + jsonObject.toString());
                if(jsonObject.has("status") && jsonObject.getString("status").equals("ok"))    {
                    if(tag != null)    {
                        PrefsUtil.getInstance(context).setValue(tag, true);
                        if(tag.equals(PrefsUtil.XPUBPOSTREG))    {
                            PrefsUtil.getInstance(context).removeValue(PrefsUtil.IS_RESTORE);
                        }
                    }
                    else if(purpose == 44)    {
                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB44REG, true);
                    }
                    else if(purpose == 49)    {
                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB49REG, true);
                    }
                    else if(purpose == 84)    {
                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB84REG, true);
                    }
                    else    {
                        ;
                    }

                }
            }
            catch(JSONException je) {
                je.printStackTrace();
                jsonObject = null;
            }
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    private synchronized boolean parseXPUB(JSONObject jsonObject) throws JSONException  {

        if(jsonObject != null)  {

            HashMap<String,Integer> pubkeys = new HashMap<String,Integer>();

            if(jsonObject.has("wallet"))  {
                JSONObject walletObj = (JSONObject)jsonObject.get("wallet");
                if(walletObj.has("final_balance"))  {
                    xpub_balance = walletObj.getLong("final_balance");
                    debug("APIFactory", "xpub_balance:" + xpub_balance);
                }
            }

            if(jsonObject.has("info"))  {
                JSONObject infoObj = (JSONObject)jsonObject.get("info");
                if(infoObj.has("latest_block"))  {
                    JSONObject blockObj = (JSONObject)infoObj.get("latest_block");
                    if(blockObj.has("height"))  {
                        setLatestBlockHeight(blockObj.getLong("height"));
                    }
                    if(blockObj.has("hash"))  {
                        latest_block_hash = blockObj.getString("hash");
                    }
                    if(blockObj.has("time"))  {
                        latest_block_time = blockObj.getLong("time");
                    }
                }
            }

            if(jsonObject.has("addresses"))  {

                JSONArray addressesArray = (JSONArray)jsonObject.get("addresses");
                JSONObject addrObj = null;
                for(int i = 0; i < addressesArray.length(); i++)  {
                    addrObj = (JSONObject)addressesArray.get(i);
                    if(addrObj != null && addrObj.has("final_balance") && addrObj.has("address"))  {
                        if(FormatsUtil.getInstance().isValidXpub((String)addrObj.get("address")))    {
                            xpub_amounts.put((String)addrObj.get("address"), addrObj.getLong("final_balance"));

                            WALLET_INDEX walletIndexReceive = null;
                            WALLET_INDEX walletIndexChange = null;
                            if(addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(0).xpubstr()) ||
                                    addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(0).zpubstr()))    {
                                walletIndexReceive = WALLET_INDEX.BIP84_RECEIVE;
                                walletIndexChange = WALLET_INDEX.BIP84_CHANGE;
                            }
                            else if(addrObj.getString("address").equals(BIP49Util.getInstance(context).getWallet().getAccount(0).xpubstr()) ||
                                    addrObj.getString("address").equals(BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr()))    {
                                walletIndexReceive = WALLET_INDEX.BIP49_RECEIVE;
                                walletIndexChange = WALLET_INDEX.BIP49_CHANGE;
                            }
                            else if(AddressFactory.getInstance().xpub2account().get((String) addrObj.get("address")) != null)    {
                                walletIndexReceive = WALLET_INDEX.BIP44_RECEIVE;
                                walletIndexChange = WALLET_INDEX.BIP44_CHANGE;
                            }
                            else    {
                                ;
                            }
                            if (walletIndexReceive != null) {
                                AddressFactory.getInstance().setHighestIdx(walletIndexReceive, addrObj.has("account_index") ? addrObj.getInt("account_index") : 0);
                            }
                            if (walletIndexChange != null) {
                                AddressFactory.getInstance().setHighestIdx(walletIndexChange, addrObj.has("change_index") ? addrObj.getInt("change_index") : 0);
                            }
                        }
                        else    {
                            long amount = 0L;
                            String addr = null;
                            addr = (String)addrObj.get("address");
                            amount = addrObj.getLong("final_balance");
                            String pcode = BIP47Meta.getInstance().getPCode4Addr(addr);

                            if(addrObj.has("pubkey"))    {
                                bip47Lookahead(pcode, addrObj.getString("pubkey"));
                            }

                            if(addr != null && addr.length() > 0 && pcode != null && pcode.length() > 0 && BIP47Meta.getInstance().getIdx4Addr(addr) != null)    {
                                int idx = BIP47Meta.getInstance().getIdx4Addr(addr);
                                if(amount > 0L)    {
                                    BIP47Meta.getInstance().addUnspent(pcode, idx);
                                    if(idx > BIP47Meta.getInstance().getIncomingIdx(pcode))    {
                                        BIP47Meta.getInstance().setIncomingIdx(pcode, idx);
                                    }
                                }
                                else    {
                                    if(addrObj.has("pubkey"))    {
                                        String pubkey = addrObj.getString("pubkey");
                                        if(pubkeys.containsKey(pubkey))    {
                                            int count = pubkeys.get(pubkey);
                                            count++;
                                            if(count == BIP47Meta.INCOMING_LOOKAHEAD)    {
                                                BIP47Meta.getInstance().removeUnspent(pcode, Integer.valueOf(idx));
                                            }
                                            else    {
                                                pubkeys.put(pubkey, count + 1);
                                            }
                                        }
                                        else    {
                                            pubkeys.put(pubkey, 1);
                                        }
                                    }
                                    else    {
                                        BIP47Meta.getInstance().removeUnspent(pcode, Integer.valueOf(idx));
                                    }
                                }
                                if(addr != null)  {
                                    bip47_amounts.put(addr, amount);
                                }
                            }

                        }
                    }
                }
            }

            if(jsonObject.has("txs"))  {
                xpub_txs.clear();
                List<String> seenHashes = new ArrayList<String>();

                JSONArray txArray = (JSONArray)jsonObject.get("txs");
                JSONObject txObj = null;
                for(int i = 0; i < txArray.length(); i++)  {

                    txObj = (JSONObject)txArray.get(i);
                    long height = 0L;
                    long amount = 0L;
                    long ts = 0L;
                    String hash = null;
                    String addr = null;
                    String _addr = null;

                    if(txObj.has("block_height"))  {
                        height = txObj.getLong("block_height");
                    }
                    else  {
                        height = -1L;  // 0 confirmations
                    }
                    if(txObj.has("hash"))  {
                        hash = (String)txObj.get("hash");
                    }
                    if(txObj.has("result"))  {
                        amount = txObj.getLong("result");
                    }
                    if(txObj.has("time"))  {
                        ts = txObj.getLong("time");
                    }

                    if(!seenHashes.contains(hash))  {
                        seenHashes.add(hash);
                    }

                    if(txObj.has("inputs"))  {
                        JSONArray inputArray = (JSONArray)txObj.get("inputs");
                        JSONObject inputObj = null;
                        for(int j = 0; j < inputArray.length(); j++)  {
                            inputObj = (JSONObject)inputArray.get(j);
                            if(inputObj.has("prev_out"))  {
                                JSONObject prevOutObj = (JSONObject)inputObj.get("prev_out");
                                if(prevOutObj.has("xpub"))  {
                                    JSONObject xpubObj = (JSONObject)prevOutObj.get("xpub");
                                    addr = (String)xpubObj.get("m");
                                }
                                else if(prevOutObj.has("addr") && BIP47Meta.getInstance().getPCode4Addr((String)prevOutObj.get("addr")) != null)  {
                                    _addr = (String)prevOutObj.get("addr");
                                }
                                else  {
                                    _addr = (String)prevOutObj.get("addr");
                                }
                            }
                        }
                    }

                    if(txObj.has("out"))  {
                        JSONArray outArray = (JSONArray)txObj.get("out");
                        JSONObject outObj = null;
                        for(int j = 0; j < outArray.length(); j++)  {
                            outObj = (JSONObject)outArray.get(j);
                            if(outObj.has("xpub"))  {
                                JSONObject xpubObj = (JSONObject)outObj.get("xpub");
                                addr = (String)xpubObj.get("m");
                            }
                            else  {
                                _addr = (String)outObj.get("addr");
                            }
                        }
                    }

                    if(addr != null || _addr != null)  {

                        if(addr == null)    {
                            addr = _addr;
                        }

                        Tx tx = new Tx(hash, addr, amount, ts, (latest_block_height > 0L && height > 0L) ? (latest_block_height - height) + 1 : 0);
                        if(SentToFromBIP47Util.getInstance().getByHash(hash) != null)    {
                            tx.setPaymentCode(SentToFromBIP47Util.getInstance().getByHash(hash));
                        }
                        if(BIP47Meta.getInstance().getPCode4Addr(addr) != null)    {
                            tx.setPaymentCode(BIP47Meta.getInstance().getPCode4Addr(addr));
                        }
                        if(!xpub_txs.containsKey(addr))  {
                            xpub_txs.put(addr, new ArrayList<Tx>());
                        }
                        if(FormatsUtil.getInstance().isValidXpub(addr))    {
                            xpub_txs.get(addr).add(tx);
                        }
                        else    {
                            if(!xpub_txs.containsKey(AddressFactory.getInstance().account2xpub().get(0)))    {
                                xpub_txs.put(AddressFactory.getInstance().account2xpub().get(0), new ArrayList<Tx>());

                            }
                            xpub_txs.get(AddressFactory.getInstance().account2xpub().get(0)).add(tx);
                        }

                        if(height > 0L)    {
                            RBFUtil.getInstance().remove(hash);
                        }

                    }
                }

                List<String> hashesSentToViaBIP47 = SentToFromBIP47Util.getInstance().getAllHashes();
                if(hashesSentToViaBIP47.size() > 0)    {
                    for(String s : hashesSentToViaBIP47)    {
                        if(!seenHashes.contains(s)) {
                            SentToFromBIP47Util.getInstance().removeHash(s);
                        }
                    }
                }

            }

            if(isWellFormedMultiAddr(jsonObject))    {
                try {
                    PayloadUtil.getInstance(context).serializeMultiAddr(jsonObject);
                }
                catch(Exception e) {
                    ;
                }
            }

            return true;

        }

        return false;

    }

    private synchronized void bip47Lookahead(String pcode, String addr) {
        if (addr == null || pcode == null) {
            return;
        }
        debug("APIFactory", "bip47Lookahead():" + addr);
        debug("APIFactory", "bip47Lookahead():" + pcode);
        debug("APIFactory", "bip47Lookahead():" + BIP47Meta.getInstance().getPCode4Addr(addr));
        int idx = BIP47Meta.getInstance().getIdx4Addr(addr);
        try {
            idx++;
            for (int i = idx; i < (idx + BIP47Meta.INCOMING_LOOKAHEAD); i++) {
                info("APIFactory", "receive from " + i + ":" + BIP47Util.getInstance(context).getReceivePubKey(new PaymentCode(pcode), i));
                BIP47Meta.getInstance().getIdx4AddrLookup().put(BIP47Util.getInstance(context).getReceivePubKey(new PaymentCode(pcode), i), i);
                BIP47Meta.getInstance().getPCode4AddrLookup().put(BIP47Util.getInstance(context).getReceivePubKey(new PaymentCode(pcode), i), pcode.toString());
            }

            idx--;
            if (idx >= 2) {
                for (int i = idx; i >= (idx - (BIP47Meta.INCOMING_LOOKAHEAD - 1)); i--) {
                    info("APIFactory", "receive from " + i + ":" + BIP47Util.getInstance(context).getReceivePubKey(new PaymentCode(pcode), i));
                    BIP47Meta.getInstance().getIdx4AddrLookup().put(BIP47Util.getInstance(context).getReceivePubKey(new PaymentCode(pcode), i), i);
                    BIP47Meta.getInstance().getPCode4AddrLookup().put(BIP47Util.getInstance(context).getReceivePubKey(new PaymentCode(pcode), i), pcode.toString());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized JSONObject lockXPUB(String xpub, int purpose, String tag) {

        JSONObject jsonObject  = null;

        try {

            String response = null;
            ECKey ecKey = null;

            if(AddressFactory.getInstance(context).xpub2account().get(xpub) != null ||
                    xpub.equals(BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr()) ||
                    xpub.equals(BIP84Util.getInstance(context).getWallet().getAccount(0).zpubstr()) ||
                    xpub.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).zpubstr()) ||
                    xpub.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).zpubstr()) ||
                    xpub.equals(BIP84Util.getInstance(context).getWallet().getAccount(RicochetMeta.getInstance(context).getRicochetAccount()).zpubstr())
            )    {

                HD_Address addr = null;
                switch(purpose)    {
                    case 49:
                        addr = BIP49Util.getInstance(context).getWallet().getAccount(0).getChange().getAddressAt(0);
                        break;
                    case 84:
                        if(tag != null && tag.equals(PrefsUtil.XPUBPRELOCK))    {
                            addr = BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).getChange().getAddressAt(0);
                        }
                        else if(tag != null && tag.equals(PrefsUtil.XPUBPOSTLOCK))   {
                            addr = BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).getChange().getAddressAt(0);
                        }
                        else if(tag != null && tag.equals(PrefsUtil.XPUBRICOCHETLOCK))   {
                            addr = BIP84Util.getInstance(context).getWallet().getAccount(RicochetMeta.getInstance(context).getRicochetAccount()).getChange().getAddressAt(0);
                        }
                        else    {
                            addr = BIP84Util.getInstance(context).getWallet().getAccount(0).getChange().getAddressAt(0);
                        }
                        break;
                    default:
                        addr = HD_WalletFactory.getInstance(context).get().getAccount(0).getChain(AddressFactory.CHANGE_CHAIN).getAddressAt(0);
                        break;
                }
                ecKey = addr.getECKey();

                if(ecKey != null && ecKey.hasPrivKey())    {

                    String sig = ecKey.signMessage("lock");
                    String address = null;
                    switch(purpose)    {
                        case 49:
                            SegwitAddress p2shp2wpkh = new SegwitAddress(ecKey.getPubKey(), SamouraiWallet.getInstance().getCurrentNetworkParams());
                            address = p2shp2wpkh.getAddressAsString();
                            break;
                        case 84:
                            SegwitAddress segwitAddress = new SegwitAddress(ecKey.getPubKey(), SamouraiWallet.getInstance().getCurrentNetworkParams());
                            address = segwitAddress.getBech32AsString();
                            break;
                        default:
                            address = ecKey.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                            break;
                    }

                    if(!SamouraiTorManager.INSTANCE.isRequired())    {
                        StringBuilder args = new StringBuilder();
                        args.append("address=");
                        args.append(address);
                        args.append("&signature=");
                        args.append(Uri.encode(sig));
                        args.append("&message=");
                        args.append("lock");
//                        info("APIFactory", "lock XPUB:" + args.toString());
                        args.append("&at=");
                        args.append(getAccessToken());
                        final String xpubApiService = BackendApiAndroid.getApiServiceUrl("xpub/" + xpub + "/lock/");
                        response = WebUtil.getInstance(context).postURL(xpubApiService , args.toString());
//                        info("APIFactory", "lock XPUB response:" + response);
                    } else {
                        HashMap<String,String> args = new HashMap<String,String>();
                        args.put("address", address);
                        args.put("signature", sig);
                        args.put("message", "lock");
                        args.put("at", getAccessToken());
                        info("APIFactory", "lock XPUB:" + BackendApiAndroid.getApiBaseUrl());
                        info("APIFactory", "lock XPUB:" + args.toString());
                        final String xpubApiService = BackendApiAndroid.getApiServiceUrl("xpub/" + xpub + "/lock/");
                        response = WebUtil.getInstance(context).tor_postURL(xpubApiService, args);
                        //info("APIFactory", "lock XPUB response:" + response);
                    }

                    try {
                        jsonObject = new JSONObject(response);

                        if(jsonObject.has("status") && jsonObject.getString("status").equals("ok"))    {

                            if(tag != null)    {
                                PrefsUtil.getInstance(context).setValue(tag, true);
                            }
                            else    {
                                switch(purpose)    {
                                    case 49:
                                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB49LOCK, true);
                                        break;
                                    case 84:
                                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB84LOCK, true);
                                        break;
                                    default:
                                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB44LOCK, true);
                                        break;
                                }
                            }

                        }

                    }
                    catch(JSONException je) {
                        je.printStackTrace();
                        jsonObject = null;
                    }

                }
            }

        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    public long getLatestBlockHeight()  {
        return latest_block_height;
    }

    public synchronized void setLatestBlockHeight(long blockHeight)  {
        debug("APIFactory", "setLatestBlockHeight: " + blockHeight);
        latest_block_height = blockHeight;
    }

    public String getLatestBlockHash() {
        return latest_block_hash;
    }

    public long getLatestBlockTime() {
        return latest_block_time;
    }

    public Pair<String, JSONObject> getNotifTx(String hash, String addr) {

        JSONObject jsonObject  = null;
        String pcode  = null;

        try {
            StringBuilder url = new StringBuilder(BackendApiAndroid.getApiServiceUrl("tx/" + hash));
            url.append("?fees=1");
//            info("APIFactory", "Notif tx:" + url.toString());
            url.append("&at=");
            url.append(getAccessToken());
            String response = WebUtil.getInstance(null).getURL(url.toString());
//            info("APIFactory", "Notif tx:" + response);
            try {
                jsonObject = new JSONObject(response);
                pcode = parseNotifTx(jsonObject, addr, hash);
            }
            catch(JSONException je) {
                je.printStackTrace();
                jsonObject = null;
            }
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return isNull(jsonObject) ? null : new Pair<>(pcode, jsonObject);
    }

    public JSONObject getNotifAddress(String addr) {

        JSONObject jsonObject  = null;

        try {
            StringBuilder url = new StringBuilder(BackendApiAndroid.getApiServiceUrl("wallet"));
            url.append("?active=");
            url.append(addr);
//            info("APIFactory", "Notif address:" + url.toString());
            url.append("&at=");
            url.append(getAccessToken());
            String response = WebUtil.getInstance(null).getURL(url.toString());
//            info("APIFactory", "Notif address:" + response);
            try {
                jsonObject = new JSONObject(response);
                parseNotifAddress(jsonObject, addr);
            }
            catch(JSONException je) {
                je.printStackTrace();
                jsonObject = null;
            }
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    public void parseNotifAddress(JSONObject jsonObject, String addr) throws JSONException  {

        if(jsonObject != null && jsonObject.has("txs"))  {

            JSONArray txArray = jsonObject.getJSONArray("txs");
            JSONObject txObj = null;
            for(int i = 0; i < txArray.length(); i++)  {
                txObj = (JSONObject)txArray.get(i);

                if(!txObj.has("block_height") || (txObj.has("block_height") && txObj.getLong("block_height") < 1L))    {
                    return;
                }

                String hash = null;

                if(txObj.has("hash"))  {
                    hash = (String)txObj.get("hash");
                    if(BIP47Meta.getInstance().getIncomingStatus(hash) == null)    {
                        getNotifTx(hash, addr);
                    }
                }

            }

        }

    }

    public String parseNotifTx(JSONObject jsonObject, String addr, String hash) throws JSONException  {

        info("APIFactory", "notif address:" + addr);
        info("APIFactory", "hash:" + hash);

        String pcodeAsString = null;

        if(jsonObject != null)  {

            byte[] mask = null;
            byte[] payload = null;
            PaymentCode pcode = null;

            if(jsonObject.has("inputs"))    {

                JSONArray inArray = (JSONArray)jsonObject.get("inputs");

                if(inArray.length() > 0)    {
                    JSONObject objInput = (JSONObject)inArray.get(0);
                    byte[] pubkey = null;
                    String strScript = objInput.getString("sig");
                    info("APIFactory", "scriptsig:" + strScript);
                    if((strScript == null || strScript.length() == 0 || strScript.startsWith("160014")) && objInput.has("witness"))    {
                        JSONArray witnessArray = (JSONArray)objInput.get("witness");
                        if(witnessArray.length() == 2)    {
                            pubkey = Hex.decode((String)witnessArray.get(1));
                        }
                    }
                    else    {
                        Script script = new Script(Hex.decode(strScript));
                        info("APIFactory", "pubkey from script:" + Hex.toHexString(script.getPubKey()));
                        pubkey = script.getPubKey();
                    }
                    ECKey pKey = new ECKey(null, pubkey, true);
                    info("APIFactory", "address from script:" + pKey.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString());

                    if(((JSONObject)inArray.get(0)).has("outpoint"))    {
                        JSONObject received_from = ((JSONObject) inArray.get(0)).getJSONObject("outpoint");

                        String strHash = received_from.getString("txid");
                        int idx = received_from.getInt("vout");

                        byte[] hashBytes = Hex.decode(strHash);
                        Sha256Hash txHash = new Sha256Hash(hashBytes);
                        TransactionOutPoint outPoint = new TransactionOutPoint(SamouraiWallet.getInstance().getCurrentNetworkParams(), idx, txHash);
                        byte[] outpoint = outPoint.bitcoinSerialize();
                        info("APIFactory", "outpoint:" + Hex.toHexString(outpoint));

                        try {
                            mask = BIP47Util.getInstance(context).getIncomingMask(pubkey, outpoint);
                            info("APIFactory", "mask:" + Hex.toHexString(mask));
                        }
                        catch(Exception e) {
                            e.printStackTrace();
                        }

                    }

                }

            }

            if(jsonObject.has("outputs"))  {
                JSONArray outArray = (JSONArray)jsonObject.get("outputs");
                JSONObject outObj = null;
                boolean isIncoming = false;
                String _addr = null;
                String script = null;
                String op_return = null;
                for(int j = 0; j < outArray.length(); j++)  {
                    outObj = (JSONObject)outArray.get(j);
                    if(outObj.has("address"))  {
                        _addr = outObj.getString("address");
                        if(addr.equals(_addr))    {
                            isIncoming = true;
                        }
                    }
                    if(outObj.has("scriptpubkey"))  {
                        script = outObj.getString("scriptpubkey");
                        if(script.startsWith("6a4c50"))    {
                            op_return = script;
                        }
                    }
                }
                if(isIncoming && op_return != null && op_return.startsWith("6a4c50"))    {
                    payload = Hex.decode(op_return.substring(6));
                }

            }

            if(mask != null && payload != null)    {
                try {
                    byte[] xlat_payload = PaymentCode.blind(payload, mask);
                    info("APIFactory", "xlat_payload:" + Hex.toHexString(xlat_payload));

                    pcode = new PaymentCode(xlat_payload);
                    pcodeAsString = pcode.toString();
                    info("APIFactory", "incoming payment code:" + pcodeAsString);

                    if(!pcodeAsString.equals(BIP47Util.getInstance(context).getPaymentCode().toString()) &&
                            pcode.isValid() && !BIP47Meta.getInstance().incomingExists(pcodeAsString))    {
                        BIP47Meta.getInstance().setLabel(pcodeAsString, "");
                        BIP47Meta.getInstance().setIncomingStatus(hash);
                    }

                }
                catch(AddressFormatException afe) {
                    afe.printStackTrace();
                }

            }

            //
            // get receiving addresses for spends from decoded payment code
            //
            if(pcode != null)    {
                try {

                    //
                    // initial lookup
                    //
                    for(int i = 0; i < BIP47Meta.INCOMING_LOOKAHEAD; i++)   {
                        final String receivePubKey = BIP47Util.getInstance(context).getReceivePubKey(pcode, i);
                        info("APIFactory", "receive from " + i + ":" + receivePubKey);
                        BIP47Meta.getInstance().getIdx4AddrLookup().put(receivePubKey, i);
                        BIP47Meta.getInstance().getPCode4AddrLookup().put(receivePubKey, pcodeAsString);
                    }
                }
                catch(Exception e) {
                    ;
                }
            }

        }

        return pcodeAsString;

    }

    public synchronized int getNotifTxConfirmations(String hash) {

        if(hash==null){
            return  0;
        }
        if(hash.isEmpty()){
            return 0;
        }

        JSONObject jsonObject  = null;

        try {
            StringBuilder url = new StringBuilder(BackendApiAndroid.getApiServiceUrl("tx/" + hash));
            url.append("?fees=1");
            url.append("&at=");
            url.append(getAccessToken());
            String response = WebUtil.getInstance(null).getURL(url.toString());
//            info("APIFactory", "Notif tx:" + response);
            jsonObject = new JSONObject(response);
//            info("APIFactory", "Notif tx json:" + jsonObject.toString());

            return parseNotifTx(jsonObject);
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return 0;
    }

    public synchronized int parseNotifTx(JSONObject jsonObject) throws JSONException  {

        int cf = 0;

        if(jsonObject != null && jsonObject.has("block") && jsonObject.getJSONObject("block").has("height"))  {

            long latestBlockHeght = getLatestBlockHeight();
            long height = jsonObject.getJSONObject("block").getLong("height");

            cf = (int)((latestBlockHeght - height) + 1);

            if(cf < 0)    {
                cf = 0;
            }

        }

        return cf;
    }

    public static synchronized boolean parseUnspentOutputs(String unspents)   {

        if(unspents != null)    {

            try {
                JSONObject jsonObj = new JSONObject(unspents);

                if(jsonObj == null || !jsonObj.has("unspent_outputs"))    {
                    return false;
                }
                JSONArray utxoArray = jsonObj.getJSONArray("unspent_outputs");
                if(utxoArray == null || utxoArray.length() == 0) {
                    return false;
                }

                for (int i = 0; i < utxoArray.length(); i++) {

                    JSONObject outDict = utxoArray.getJSONObject(i);

                    byte[] hashBytes = Hex.decode((String)outDict.get("tx_hash"));
                    Sha256Hash txHash = Sha256Hash.wrap(hashBytes);
                    int txOutputN = ((Number)outDict.get("tx_output_n")).intValue();
                    BigInteger value = BigInteger.valueOf(((Number)outDict.get("value")).longValue());
                    String script = (String)outDict.get("script");
                    byte[] scriptBytes = Hex.decode(script);
                    int confirmations = ((Number)outDict.get("confirmations")).intValue();
                    String path = null;

                    try {
                        String address = null;
                        if(Bech32Util.getInstance().isBech32Script(script))    {
                            address = Bech32Util.getInstance().getAddressFromScript(script);
                        }
                        else    {
                            address = new Script(scriptBytes).getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                        }

                        String xpubM = null;
                        if(outDict.has("xpub"))    {
                            JSONObject xpubObj = (JSONObject)outDict.get("xpub");
                            path = (String)xpubObj.get("path");
                            xpubM = (String)xpubObj.get("m");
                            unspentPaths.put(address, path);
                            if(xpubM.equals(BIP49Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                unspentBIP49.put(address, 0);   // assume account 0
                            }
                            else if(xpubM.equals(BIP84Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                unspentBIP84.put(address, 0);   // assume account 0
                            }
                            else    {
                                unspentAccounts.put(address, AddressFactory.getInstance(context).xpub2account().get(xpubM));
                            }
                        }
                        else if(outDict.has("pubkey"))    {
                            int idx = BIP47Meta.getInstance().getIdx4AddrLookup().get(outDict.getString("pubkey"));
                            BIP47Meta.getInstance().getIdx4AddrLookup().put(address, idx);
                            String pcode = BIP47Meta.getInstance().getPCode4AddrLookup().get(outDict.getString("pubkey"));
                            BIP47Meta.getInstance().getPCode4AddrLookup().put(address, pcode);

                            debug("APIFactory", outDict.getString("pubkey") + "," + pcode);
                            debug("APIFactory", outDict.getString("pubkey") + "," + idx);
                        }
                        else    {
                            ;
                        }

                        // Construct the output
                        MyTransactionOutPoint outPoint = new MyTransactionOutPoint(SamouraiWallet.getInstance().getCurrentNetworkParams(), txHash, txOutputN, value, scriptBytes, address, confirmations);

                        if(utxos.containsKey(script))    {
                            utxos.get(script).getOutpoints().add(outPoint);
                        }
                        else    {
                            UTXO utxo = new UTXO(path, xpubM);
                            utxo.getOutpoints().add(outPoint);
                            utxos.put(script, utxo);
                        }

                        if(Bech32Util.getInstance().isBech32Script(script))    {
                            utxosP2WPKH.put(script, utxos.get(script));
                        }
                        else if(Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress())    {
                            utxosP2SH_P2WPKH.put(script, utxos.get(script));
                        }
                        else    {
                            utxosP2PKH.put(script, utxos.get(script));
                        }
                        onUtxoChange();

                    }
                    catch(Exception e) {
                        e.printStackTrace();
                    }

                }

                long amount = 0L;
                for(String key : utxos.keySet())   {
                    for(MyTransactionOutPoint out : utxos.get(key).getOutpoints())    {
                        debug("APIFactory", "utxo:" + out.getAddress() + "," + out.getValue() + "," + utxos.get(key).getPath());
                        amount += out.getValue().longValue();
                    }
                }
                debug("APIFactory", "utxos by value (post-parse):" + amount);

                return true;

            }
            catch(JSONException je) {
                ;
            }

        }

        return false;

    }

    public synchronized JSONObject getAddressInfo(final Pair<EnumAddressType, String> addrByType) {

        final ListMultimap<EnumAddressType, String> addressesByType =
                ImmutableListMultimap.<EnumAddressType, String>builder()
                        .put(addrByType.first, addrByType.second)
                        .build();
        return getXPUB(
                addressesByType,
                false);
    }

    public synchronized JSONObject getTxInfo(final String hash) {


        JSONObject jsonObject  = null;

        try {
            StringBuilder url = new StringBuilder(BackendApiAndroid.getApiServiceUrl("tx/" + hash));
            url.append("?fees=1");
            url.append("&at=");
            url.append(getAccessToken());

            debug("APIFactory", "url:" + url);

            String response = WebUtil.getInstance(context).getURL(url.toString());
            jsonObject = new JSONObject(response);
        } catch(final Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    public Boolean loadFees() throws Exception {

        try {
            final RawFees fees = load1DollarFeesEstimator();
            if (isNull(fees) || !fees.hasFee()) {
                Log.w(TAG, "will load fees fromm bitcoind because no get fees from 1$ fee estimator");
                loadDynamicFeesBitcoind();
            }
        } catch (final Exception e) {
            Log.e(TAG, e.getMessage(), e);
            loadDynamicFeesBitcoind();
        }

        return Boolean.TRUE;
    }

    private RawFees load1DollarFeesEstimator() throws Exception {
        if(AppUtil.getInstance(context).isOfflineMode()) {
            if(BuildConfig.FLAVOR.equals("staging") && SamouraiWallet.MOCK_FEE) {
                return putMock1DollarFeesEstimator();
            } else {
                final JSONObject multiAddr = PayloadUtil.getInstance(context).deserializeMultiAddr();
                return parse1DollarFeesEstimator(new JSONObject(multiAddr.toString()));
            }
        } else if(BuildConfig.FLAVOR.equals("staging") && SamouraiWallet.MOCK_FEE) {
            RawFees fees = FeeClient.createFeeClient(context, false).getFees();
            if (isNull(fees) || ! fees.hasFee()) {
                Log.w(TAG, "will load hard coded 1$fees values because no get fees from mock 1$fee estimator");
                fees = putMock1DollarFeesEstimator();
            }
            FeeUtil.getInstance().putRawFees(fees);
            return fees;
        } else {
            final boolean testNet = SamouraiWallet.getInstance().isTestNet();
            final RawFees fees = FeeClient.createFeeClient(context, testNet).getFees();
            FeeUtil.getInstance().putRawFees(fees);
            return fees;
        }
    }

    private static RawFees putMock1DollarFeesEstimator() {
        final RawFees rawFees = RawFees.createFromMap(ImmutableMap.of(
                "0.999", 26,
                "0.99", 18,
                "0.9", 14,
                "0.5", 13,
                "0.2", 10,
                "0.1", 9));
        FeeUtil.getInstance().putRawFees(rawFees);
        return rawFees;
    }

    private void loadDynamicFeesBitcoind() throws Exception {
        final String response;
        if(!AppUtil.getInstance(context).isOfflineMode()) {
            final String feesApiService = BackendApiAndroid.getApiServiceUrl("fees");
            response = WebUtil.getInstance(null).getURL(feesApiService + "?at=" + getAccessToken());
        } else {
            response = PayloadUtil.getInstance(context).deserializeMultiAddr().toString();
        }
        final JSONObject jsonObject = new JSONObject(response);
        parseDynamicFees_bitcoind(jsonObject);
    }

    public static RawFees parse1DollarFeesEstimator(final JSONObject payload) throws JSONException  {

        if(isNull(payload)) return null;

        if(BuildConfig.FLAVOR.equals("staging") && SamouraiWallet.MOCK_FEE) {
            return putMock1DollarFeesEstimator();
        }

        JSONObject jsonObject = null;

        if(payload.has("info")){
            if(payload.getJSONObject("info").has("estimatorFees")){
                jsonObject = payload.getJSONObject("info").getJSONObject("estimatorFees");
            }
        } else if(payload.has("0.99")) {
            jsonObject = payload;
        }

        if (nonNull(jsonObject)) {
            info(TAG, "1$ fees estimator:" + jsonObject.toString(2));
            final RawFees rawFees = toRawFees(jsonObject);
            FeeUtil.getInstance().putRawFees(rawFees);
            return rawFees;
        }
        return null;
    }

    public static void parseDynamicFees_bitcoind(JSONObject payload) throws JSONException  {

        if(isNull(payload)) return;

        if(BuildConfig.FLAVOR.equals("staging") && SamouraiWallet.MOCK_FEE ){
                payload = new JSONObject("{\n" +
                        "\"2\": 101,\n" +
                        "\"4\": 91,\n" +
                        "\"6\": 62,\n" +
                        "\"12\": 38,\n" +
                        "\"24\": 38\n" +
                        "}");
        }

        JSONObject jsonObject = new JSONObject();
        if(payload.has("info")){
            if(payload.getJSONObject("info").has("fees")){
                jsonObject = payload.getJSONObject("info").getJSONObject("fees");
             }
        } else if(payload.has("2")){
            jsonObject = payload;
        }

        if (nonNull(jsonObject))  {
            info(TAG, "Dynamic fees:" + jsonObject.toString(2));
            FeeUtil.getInstance().putRawFees(toRawFees(jsonObject));
        }

    }

    private static RawFees toRawFees(final JSONObject jsonObject) throws JSONException {
        if (isNull(jsonObject)) return null;
        final Map<String, Integer> feesMap = Maps.newLinkedHashMap();
        final Iterator<String> feeIt = jsonObject.keys();
        while(feeIt.hasNext()) {
            final String blockCount = feeIt.next();
            feesMap.put(blockCount, jsonObject.getInt(blockCount));
        }
        return RawFees.createFromMap(feesMap);
    }

    public synchronized void validateAPIThread() {

        final Handler handler = new Handler();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                if(!AppUtil.getInstance(context).isOfflineMode()) {

                    try {
                        String response = WebUtil.getInstance(context).getURL(WebUtil.SAMOURAI_API_CHECK);

                        JSONObject jsonObject = new JSONObject(response);
                        if(!jsonObject.has("process"))    {
                            showAlertDialog(context.getString(R.string.api_error), false);
                        }

                    }
                    catch(Exception e) {
                        showAlertDialog(context.getString(R.string.cannot_reach_api), false);
                    }

                } else {
                    showAlertDialog(context.getString(R.string.no_internet), false);
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        ;
                    }
                });

                Looper.loop();

            }
        }).start();
    }

    private void showAlertDialog(final String message, final boolean forceExit){

        if (!((Activity) context).isFinishing()) {

            if(alertDialog != null)alertDialog.dismiss();

            final AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(message);
            builder.setCancelable(false);

            if(!forceExit) {
                builder.setPositiveButton(R.string.retry,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface d, int id) {
                                d.dismiss();
                                //Retry
                                validateAPIThread();
                            }
                        });
            }

            builder.setNegativeButton(R.string.exit,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int id) {
                            d.dismiss();
                            ((Activity) context).finish();
                        }
                    });

            alertDialog = builder.create();
            alertDialog.show();
        }
    }

    public synchronized void initWallet()    {

        info("APIFactory", "initWallet()");

        initWalletAmounts();

    }

    private synchronized void initWalletAmounts() {

        lastRicochetReceiveIdx =  RicochetMeta.getInstance(context).getIndex();

        reset();


        String[] s = null;

        try {

            xpub_txs.put(HD_WalletFactory.getInstance(context).get().getAccount(0).xpubstr(), new ArrayList<Tx>());

            final List<String> bip47Addresses = BIP47Util.getBip47Addresses(context);
            if(bip47Addresses.size() > 0)    {
                s = bip47Addresses.toArray(new String[0]);
            }

            HD_Wallet hdw = HD_WalletFactory.getInstance(context).get();
            if(hdw != null && hdw.getXPUBs() != null)    {
                final ListMultimap<EnumAddressType, String> addressesByType = MultimapBuilder.linkedHashKeys().arrayListValues().build();
                addressesByType.put(EnumAddressType.BIP44_LEGACY, BIP49Util.getInstance(context).getWallet().getAccount(0).xpubstr());
                addressesByType.put(EnumAddressType.BIP44_LEGACY, BIP84Util.getInstance(context).getWallet().getAccount(0).xpubstr());
                addressesByType.putAll(EnumAddressType.BIP44_LEGACY, ImmutableList.copyOf(hdw.getXPUBs()));
                if(s != null && s.length > 0)    {
                    addressesByType.putAll(EnumAddressType.BIP44_LEGACY, ImmutableList.copyOf(s));
                }
                JSONObject jObj = APIFactory.getInstance(context).getXPUB(addressesByType, true);
                if(jObj != null)    {
                    // parseXPUB is included in getXPUB() above
                    parseUnspentOutputs(jObj.toString());
                    parseDynamicFees_bitcoind(jObj);
                    parse1DollarFeesEstimator(jObj);
                }

             }

            //
            //
            //
            final List<String> seenOutputs = new ArrayList<>();
            final List<UTXO> _utxos = getUtxos(false);
            for (final UTXO _u : _utxos)   {
                for (final MyTransactionOutPoint _o : _u.getOutpoints())   {
                    seenOutputs.add(_o.getTxHash().toString() + "-" + _o.getTxOutputN());
                }
            }
            for (final String _s : ImmutableList.copyOf(BlockedUTXO.getInstance().getNotDustedUTXO())) {
//                debug("APIFactory", "not dusted:" + _s);
                if(!seenOutputs.contains(_s))    {
                    BlockedUTXO.getInstance().removeNotDusted(_s);
//                    debug("APIFactory", "not dusted removed:" + _s);
                }
            }
            for (final String _s : ImmutableList.copyOf(BlockedUTXO.getInstance().getBlockedUTXO().keySet())) {
//                debug("APIFactory", "blocked:" + _s);
                if(!seenOutputs.contains(_s))    {
                    BlockedUTXO.getInstance().remove(_s);
//                    debug("APIFactory", "blocked removed:" + _s);
                }
            }

            final String strPreMix = BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).xpubstr();
            final String strPostMix = BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).xpubstr();
//            String strBadBank = BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolBadBank()).xpubstr();

            final ListMultimap<EnumAddressType, String> addressesByType = ImmutableListMultimap.<EnumAddressType, String>builder()
                    .putAll(EnumAddressType.BIP44_LEGACY, strPreMix, strPostMix)
                    .build();

            final JSONObject mixMultiAddrObj = getRawXPUB(addressesByType);
            if (mixMultiAddrObj != null)    {
                parseMixXPUB(mixMultiAddrObj);
                parseMixUnspentOutputs(mixMultiAddrObj.toString());
            }

            //
            //
            //
            final List<String> seenOutputsPostMix = new ArrayList<String>();
            final List<UTXO> _utxosPostMix = getUtxosPostMix(false);
            for (final UTXO _u : _utxosPostMix)   {
                for(MyTransactionOutPoint _o : _u.getOutpoints())   {
                    seenOutputsPostMix.add(_o.getTxHash().toString() + "-" + _o.getTxOutputN());
                }
            }

            for(String _s : ImmutableList.copyOf(UTXOUtil.getInstance().getTags().keySet())) {
                if(!seenOutputsPostMix.contains(_s) && !seenOutputs.contains(_s))    {
                    try {
                        UTXOUtil.getInstance().remove(_s);
                        UTXOUtil.getInstance().removeNote(_s);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            final List<String> seenOutputsBadBank = new ArrayList<String>();
            final List<UTXO> _utxosBadBank = getUtxosBadBank(false);
            for (UTXO _u : _utxosBadBank)   {
                for(MyTransactionOutPoint _o : _u.getOutpoints())   {
                    seenOutputsBadBank.add(_o.getTxHash().toString() + "-" + _o.getTxOutputN());
                }
            }

            for (String _s : ImmutableList.copyOf(UTXOUtil.getInstance().getTags().keySet()))   {
                if(!seenOutputsBadBank.contains(_s) && !seenOutputs.contains(_s))    {
                    try {
                        UTXOUtil.getInstance().remove(_s);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            for (final String _s : ImmutableList.copyOf(BlockedUTXO.getInstance().getNotDustedUTXOPostMix())) {
//                debug("APIFactory", "not dusted postmix:" + _s);
                if(!seenOutputsPostMix.contains(_s))    {
                    BlockedUTXO.getInstance().removeNotDustedPostMix(_s);
//                    debug("APIFactory", "not dusted postmix removed:" + _s);
                }
            }

            for (final String _s : ImmutableList.copyOf(BlockedUTXO.getInstance().getBlockedUTXOPostMix().keySet()))   {
                debug("APIFactory", "blocked post-mix:" + _s);
                if(!seenOutputsPostMix.contains(_s))    {
                    BlockedUTXO.getInstance().removePostMix(_s);
                    debug("APIFactory", "blocked removed:" + _s);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();

            RicochetMeta.getInstance(context).setIndex(lastRicochetReceiveIdx);
        }
        walletInit = true;

    }

    public synchronized int syncBIP47Incoming(final String[] addresses) {

        final ListMultimap<EnumAddressType, String> addressesByType = MultimapBuilder
                .linkedHashKeys()
                .arrayListValues()
                .build();
        addressesByType.putAll(EnumAddressType.BIP44_LEGACY, ImmutableList.copyOf(addresses));
        JSONObject jsonObject = getXPUB(addressesByType, false);
        debug("APIFactory", String.format("sync BIP47 incoming:%s", jsonObject));
        int ret = 0;

        try {

            if(jsonObject != null && jsonObject.has("addresses"))  {

                HashMap<String,Integer> pubkeys = new HashMap<String,Integer>();

                JSONArray addressArray = (JSONArray)jsonObject.get("addresses");
                JSONObject addrObj = null;
                for(int i = 0; i < addressArray.length(); i++)  {
                    addrObj = (JSONObject)addressArray.get(i);
                    long amount = 0L;
                    int nbTx = 0;
                    String addr = null;
                    String pcode = null;
                    int idx = -1;
                    if(addrObj.has("address"))  {

                        if(addrObj.has("pubkey"))    {
                            addr = (String)addrObj.get("pubkey");
                            pcode = BIP47Meta.getInstance().getPCode4Addr(addr);
                            idx = BIP47Meta.getInstance().getIdx4Addr(addr);

                            BIP47Meta.getInstance().getIdx4AddrLookup().put(addrObj.getString("address"), idx);
                            BIP47Meta.getInstance().getPCode4AddrLookup().put(addrObj.getString("address"), pcode);
                        }
                        else    {
                            addr = (String)addrObj.get("address");
                            pcode = BIP47Meta.getInstance().getPCode4Addr(addr);
                            idx = BIP47Meta.getInstance().getIdx4Addr(addr);
                        }

                        if(addrObj.has("final_balance"))  {
                            amount = addrObj.getLong("final_balance");
                            if(amount > 0L)    {
                                BIP47Meta.getInstance().addUnspent(pcode, idx);
                                info("APIFactory", "BIP47 incoming amount:" + idx + ", " + addr + ", " + amount);
                            }
                            else    {
                                if(addrObj.has("pubkey"))    {
                                    String pubkey = addrObj.getString("pubkey");
                                    if(pubkeys.containsKey(pubkey))    {
                                        int count = pubkeys.get(pubkey);
                                        count++;
                                        if(count == 3)    {
                                            BIP47Meta.getInstance().removeUnspent(pcode, Integer.valueOf(idx));
                                            info("APIFactory", "BIP47 remove unspent:" + pcode + ":" + idx);
                                        }
                                        else    {
                                            pubkeys.put(pubkey, count + 1);
                                        }
                                    }
                                    else    {
                                        pubkeys.put(pubkey, 1);
                                    }
                                }
                                else    {
                                    BIP47Meta.getInstance().removeUnspent(pcode, Integer.valueOf(idx));
                                }
                            }
                        }
                        if(addrObj.has("n_tx"))  {
                            nbTx = addrObj.getInt("n_tx");
                            if(nbTx > 0)    {
                                if(idx > BIP47Meta.getInstance().getIncomingIdx(pcode))    {
                                    BIP47Meta.getInstance().setIncomingIdx(pcode, idx);
                                }
                                info("APIFactory", "sync receive idx:" + idx + ", " + addr);
                                ret++;
                            }
                        }

                    }
                }

            }

        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return ret;
    }

    public synchronized int syncBIP47Outgoing(final String[] addresses) {

        final ListMultimap<EnumAddressType, String> addressesByType = MultimapBuilder
                .linkedHashKeys()
                .arrayListValues()
                .build();
        addressesByType.putAll(EnumAddressType.BIP44_LEGACY, ImmutableList.copyOf(addresses));
        final JSONObject jsonObject = getXPUB(addressesByType, false);
        int ret = 0;

        try {

            if(jsonObject != null && jsonObject.has("addresses"))  {

                JSONArray addressArray = (JSONArray)jsonObject.get("addresses");
                JSONObject addrObj = null;
                for(int i = 0; i < addressArray.length(); i++)  {
                    addrObj = (JSONObject)addressArray.get(i);
                    int nbTx = 0;
                    String addr = null;
                    String pcode = null;
                    int idx = -1;
                    info("APIFactory", "address object:" + addrObj.toString());

                    if(addrObj.has("pubkey"))    {
                        addr = (String)addrObj.get("pubkey");
                        pcode = BIP47Meta.getInstance().getPCode4Addr(addr);
                        idx = BIP47Meta.getInstance().getIdx4Addr(addr);

                        BIP47Meta.getInstance().getIdx4AddrLookup().put(addrObj.getString("address"), idx);
                        BIP47Meta.getInstance().getPCode4AddrLookup().put(addrObj.getString("address"), pcode);
                    }
                    else    {
                        addr = (String)addrObj.get("address");
                        pcode = BIP47Meta.getInstance().getPCode4Addr(addr);
                        idx = BIP47Meta.getInstance().getIdx4Addr(addr);
                    }

                    if(addrObj.has("n_tx"))  {
                        nbTx = addrObj.getInt("n_tx");
                        if(nbTx > 0)    {
                            if(idx >= BIP47Meta.getInstance().getOutgoingIdx(pcode))    {
                                info("APIFactory", "sync send idx:" + idx + ", " + addr);
                                BIP47Meta.getInstance().setOutgoingIdx(pcode, idx + 1);
                            }
                            ret++;
                        }

                    }

                }

            }

        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return ret;
    }

    public long getXpubBalance()  {
        long ret = xpub_balance - BlockedUTXO.getInstance().getTotalValueBlocked0();
        return (ret < 0L) ? 0L : ret;
    }

    public void setXpubBalance(long value)  {
        xpub_balance = value;
        walletBalanceObserver.onNext(System.currentTimeMillis());
    }

    public long getXpubPreMixBalance()  {
        return xpub_premix_balance;
    }

    public long getXpubPostMixBalance()  {
        long ret = xpub_postmix_balance - BlockedUTXO.getInstance().getTotalValueBlockedPostMix();
        return (ret < 0L) ? 0L : ret;
    }

    public long getXpubBadBankBalance()  {
        long ret = xpub_badbank_balance - BlockedUTXO.getInstance().getTotalValueBlockedBadBank();
        return (ret < 0L) ? 0L : ret;
    }

    public void setXpubPostMixBalance(long value)  {
        xpub_postmix_balance = value;
    }

    public void setXpubBadBankBalance(long value)  {
        xpub_badbank_balance = value;
    }

    public Map<String, Long> getXpubAmounts()  {
        return xpub_amounts;
    }

    public Map<String, List<Tx>> getXpubTxs()  {
        return xpub_txs;
    }

    public Map<String, List<Tx>> getPremixXpubTxs()  {
        return premix_txs;
    }

    public Map<String, String> getUnspentPaths() {
        return unspentPaths;
    }

    public Map<String, Integer> getUnspentAccounts() {
        return unspentAccounts;
    }

    public Map<String, Integer> getUnspentBIP49() {
        return unspentBIP49;
    }

    public Map<String, Integer> getUnspentBIP84() {
        return unspentBIP84;
    }

    private static List<UTXO> getUtxosFiltered(Collection<UTXO> utxos, boolean filter, Function<MyTransactionOutPoint,Boolean> checkBlocked) {
        List<UTXO> unspents = new ArrayList<>();

        if(filter)    {
            for(UTXO utxo : utxos)   {
                UTXO u = new UTXO(utxo.getPath(), utxo.getXpub());
                for(MyTransactionOutPoint out : utxo.getOutpoints())    {
                    boolean blocked = checkBlocked.apply(out);
                    if(!blocked)    {
                        u.getOutpoints().add(out);
                        u.setPath(utxo.getPath());
                    }
                }
                if(u.getOutpoints().size() > 0)    {
                    unspents.add(u);
                }
            }
        }
        else    {
            unspents.addAll(utxos);
        }
        return unspents;
    }

    synchronized public List<UTXO> getUtxos(boolean filter) {
        Function<MyTransactionOutPoint,Boolean> checkBlocked = out -> BlockedUTXO.getInstance().contains(out.getTxHash().toString(), out.getTxOutputN());
        return getUtxosFiltered(utxos.values(), filter, checkBlocked);
    }

    synchronized public List<UTXO> getUtxosP2PKH(boolean filter) {
        Function<MyTransactionOutPoint,Boolean> checkBlocked = out -> BlockedUTXO.getInstance().contains(out.getTxHash().toString(), out.getTxOutputN());
        return getUtxosFiltered(utxosP2PKH.values(), filter, checkBlocked);
    }

    synchronized public List<UTXO> getUtxosP2SH_P2WPKH(boolean filter) {
        Function<MyTransactionOutPoint,Boolean> checkBlocked = out -> BlockedUTXO.getInstance().contains(out.getTxHash().toString(), out.getTxOutputN());
        return getUtxosFiltered(utxosP2SH_P2WPKH.values(), filter, checkBlocked);
    }

    synchronized public List<UTXO> getUtxosP2WPKH(boolean filter) {
        Function<MyTransactionOutPoint,Boolean> checkBlocked = out -> BlockedUTXO.getInstance().contains(out.getTxHash().toString(), out.getTxOutputN());
        return getUtxosFiltered(utxosP2WPKH.values(), filter, checkBlocked);
    }

    public List<UTXO> getUtxosPostMix(boolean filter) {
        Function<MyTransactionOutPoint,Boolean> checkBlocked = out -> BlockedUTXO.getInstance().containsPostMix(out.getTxHash().toString(), out.getTxOutputN());
        return getUtxosFiltered(utxosPostMix.values(), filter, checkBlocked);
    }
    public List<UTXO> getUtxosPreMix() {
        // premixs cannot be blocked
        return new ArrayList<>(utxosPreMix.values());
    }

    public List<UTXO> getUtxosBadBank(boolean filter) {
        Function<MyTransactionOutPoint,Boolean> checkBlocked = out -> BlockedUTXO.getInstance().containsBadBank(out.getTxHash().toString(), out.getTxOutputN());
        return getUtxosFiltered(utxosBadBank.values(), filter, checkBlocked);
    }

    public synchronized List<Tx> getAllXpubTxs()  {

        List<Tx> ret = new ArrayList<Tx>();
        for(String key : xpub_txs.keySet())  {
            List<Tx> txs = xpub_txs.get(key);
            for(Tx tx : txs)   {
                ret.add(tx);
            }
        }

        Collections.sort(ret, new TxMostRecentDateComparator());

        return ret;
    }

    public synchronized List<Tx> getAllPostMixTxs()  {

        List<Tx> ret = new ArrayList<Tx>();
        for(String key : postmix_txs.keySet())  {
            List<Tx> txs = postmix_txs.get(key);
            if(txs!=null){
                for(Tx tx : txs)   {
                    ret.add(tx);
                }
            }
        }

        Collections.sort(ret, new TxMostRecentDateComparator());
        return ret;
    }
    public synchronized List<Tx> getAllPremixTx()  {

        List<Tx> ret = new ArrayList<Tx>();
        for(String key : premix_txs.keySet())  {
            List<Tx> txs = premix_txs.get(key);
            if(txs!=null){
                for(Tx tx : txs)   {
                    ret.add(tx);
                }
            }
        }

        Collections.sort(ret, new TxMostRecentDateComparator());
        return ret;
    }

    private synchronized JSONObject getRawXPUB(
            final ListMultimap<EnumAddressType, String> addressesByType) {

        JSONObject jsonObject  = null;

        try {

            String response = null;

            if(AppUtil.getInstance(context).isOfflineMode())    {
                response = PayloadUtil.getInstance(context).deserializeMultiAddrMix().toString();
            }
            else if(!SamouraiTorManager.INSTANCE.isRequired())    {
                // use POST
                final StringBuilder args = new StringBuilder();

                for (final EnumAddressType type : addressesByType.keySet()) {
                    switch (type) {
                        case BIP44_LEGACY:
                            args.append("active=");
                            args.append(StringUtils.join(addressesByType.get(type), URLEncoder.encode("|", "UTF-8")));
                            args.append("&");
                            break;
                        case BIP49_SEGWIT_COMPAT:
                            args.append("bip49=");
                            args.append(StringUtils.join(addressesByType.get(type), URLEncoder.encode("|", "UTF-8")));
                            args.append("&");
                            break;
                        case BIP84_SEGWIT_NATIVE:
                            args.append("bip84=");
                            args.append(StringUtils.join(addressesByType.get(type), URLEncoder.encode("|", "UTF-8")));
                            args.append("&");
                            break;
                        default:
                            info("APIFactory", "unknown EnumAddressType:" + type);
                            break;
                    }
                }

                info("APIFactory", "XPUB:" + args.toString());
                args.append("&at=");
                args.append(getAccessToken());
                if(DojoUtil.getInstance(context).getDojoParams() == null)    {
                    if(PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUBPOSTXREG, false) == false)    {
                        args.append("&importPostmixLikeTypeChange=1");
                    }
                } else {
                    if(DojoUtil.getInstance(context).isLikeType() && PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUBPOSTXREG, false) == false)    {
                        args.append("&importPostmixLikeTypeChange=1");
                    }
                }
                final String walletApiService = BackendApiAndroid.getApiServiceUrl("wallet?");
                response = WebUtil.getInstance(context).postURL(walletApiService, args.toString());
                //info("APIFactory", "XPUB response:" + response);
            } else {

                final Map<String,String> args = Maps.newHashMap();
                for (final EnumAddressType type : addressesByType.keySet()) {
                    switch (type) {
                        case BIP44_LEGACY:
                            args.put("active", StringUtils.join(addressesByType.get(type), "|"));
                            break;
                        case BIP49_SEGWIT_COMPAT:
                            args.put("bip49", StringUtils.join(addressesByType.get(type), "|"));
                            break;
                        case BIP84_SEGWIT_NATIVE:
                            args.put("bip84", StringUtils.join(addressesByType.get(type), "|"));
                            break;
                        default:
                            info("APIFactory", "unknown EnumAddressType:" + type);
                            break;
                    }
                }

                info("APIFactory", "XPUB:" + args.toString());
                args.put("at", getAccessToken());
                if(DojoUtil.getInstance(context).getDojoParams() == null)    {
                    if(PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUBPOSTXREG, false) == false)    {
                        args.put("importPostmixLikeTypeChange", "1");
                    }
                }
                else    {
                    if(DojoUtil.getInstance(context).isLikeType() && PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUBPOSTXREG, false) == false)    {
                        args.put("importPostmixLikeTypeChange", "1");
                    }
                }
                final String walletApiService = BackendApiAndroid.getApiServiceUrl("wallet");
                response = WebUtil.getInstance(context).tor_postURL(walletApiService, args);
                //info("APIFactory", "XPUB response:" + response);
            }

            try {
                jsonObject = new JSONObject(response);
                return jsonObject;
            }
            catch(JSONException je) {
                je.printStackTrace();
                jsonObject = null;
            }
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    private synchronized boolean parseMixXPUB(JSONObject jsonObject) throws JSONException  {

        if(jsonObject != null)  {

            if(jsonObject.has("info"))  {
                JSONObject infoObj = (JSONObject)jsonObject.get("info");
                if(infoObj.has("latest_block"))  {
                    JSONObject blockObj = (JSONObject)infoObj.get("latest_block");
                    if(blockObj.has("height"))  {
                        setLatestBlockHeight(blockObj.getLong("height"));
                    }
                    if(blockObj.has("hash"))  {
                        latest_block_hash = blockObj.getString("hash");
                    }
                }
            }

            if(jsonObject.has("addresses"))  {

                JSONArray addressesArray = (JSONArray)jsonObject.get("addresses");
                JSONObject addrObj = null;
                for(int i = 0; i < addressesArray.length(); i++)  {
                    addrObj = (JSONObject)addressesArray.get(i);
                    if(addrObj != null && addrObj.has("final_balance") && addrObj.has("address"))  {
                        if(FormatsUtil.getInstance().isValidXpub((String)addrObj.get("address")))    {
                            WALLET_INDEX walletIndexReceive = null;
                            WALLET_INDEX walletIndexChange = null;
                            if(addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).xpubstr()) ||
                                    addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).zpubstr()))    {

                                xpub_postmix_balance = addrObj.getLong("final_balance");

                                walletIndexReceive=WALLET_INDEX.POSTMIX_RECEIVE;
                                walletIndexChange=WALLET_INDEX.POSTMIX_CHANGE;
                            }
                            else if(addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).xpubstr()) ||
                                    addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).zpubstr()))    {

                                xpub_premix_balance = addrObj.getLong("final_balance");

                                walletIndexReceive=WALLET_INDEX.PREMIX_RECEIVE;
                                walletIndexChange=WALLET_INDEX.PREMIX_CHANGE;
                            }
                            else if(addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolBadBank()).xpubstr()) ||
                                    addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolBadBank()).zpubstr()))    {

                                xpub_badbank_balance = addrObj.getLong("final_balance");

                                walletIndexReceive=WALLET_INDEX.BADBANK_RECEIVE;
                                walletIndexChange=WALLET_INDEX.BADBANK_CHANGE;
                            }
                            else    {
                                ;
                            }
                            if (walletIndexReceive != null) {
                                AddressFactory.getInstance().setHighestIdx(walletIndexReceive, addrObj.has("account_index") ? addrObj.getInt("account_index") : 0);
                            }
                            if (walletIndexChange != null) {
                                AddressFactory.getInstance().setHighestIdx(walletIndexChange, addrObj.has("change_index") ? addrObj.getInt("change_index") : 0);
                            }
                        }
                    }
                }
            }

            if(jsonObject.has("txs"))  {
                postmix_txs.clear();
                premix_txs.clear();
                JSONArray txArray = (JSONArray)jsonObject.get("txs");
                JSONObject txObj = null;
                for(int i = 0; i < txArray.length(); i++)  {

                    boolean hasPreMix = false;
                    boolean hasPostMix = false;
                    boolean isPostMixTx0 = false;
                    long tx0value = 0L;

                    txObj = (JSONObject)txArray.get(i);
                    long height = 0L;
                    long amount = 0L;
                    long ts = 0L;
                    String hash = null;
                    String addr = null;
                    String _addr = null;

                    if(txObj.has("block_height"))  {
                        height = txObj.getLong("block_height");
                    }
                    else  {
                        height = -1L;  // 0 confirmations
                    }
                    if(txObj.has("hash"))  {
                        hash = (String)txObj.get("hash");
                    }
                    if(txObj.has("result"))  {
                        amount = txObj.getLong("result");
                    }
                    if(txObj.has("time"))  {
                        ts = txObj.getLong("time");
                    }
                    if(txObj.has("inputs"))  {
                        JSONArray inputArray = (JSONArray)txObj.get("inputs");
                        JSONObject inputObj = null;
                        for(int j = 0; j < inputArray.length(); j++)  {
                            inputObj = (JSONObject)inputArray.get(j);
                            if(inputObj.has("prev_out"))  {
                                JSONObject prevOutObj = (JSONObject)inputObj.get("prev_out");
                                if(prevOutObj.has("xpub"))  {
                                    JSONObject xpubObj = (JSONObject)prevOutObj.get("xpub");
                                    addr = (String)xpubObj.get("m");
                                    if(addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).xpubstr()) ||
                                            addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).zpubstr()))  {
                                        hasPreMix = true;
                                    }
                                    if(txObj.getLong("result") < 0L && addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).xpubstr()) ||
                                            addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).zpubstr()))  {
                                        hasPostMix = true;
                                        tx0value += prevOutObj.getLong("value");
                                    }
                                }
                                else  {
                                    _addr = (String)prevOutObj.get("addr");
                                }
                            }
                        }
                    }

                    if(txObj.has("out"))  {
                        JSONArray outArray = (JSONArray)txObj.get("out");
                        JSONObject outObj = null;
                        for(int j = 0; j < outArray.length(); j++)  {
                            outObj = (JSONObject)outArray.get(j);
                            if(outObj.has("xpub"))  {
                                JSONObject xpubObj = (JSONObject)outObj.get("xpub");
                                addr = (String)xpubObj.get("m");
                                if(hasPreMix && (addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).xpubstr()) ||
                                        addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).zpubstr())))  {
                                    amount = outObj.getLong("value");
                                }
                                if(hasPostMix && (addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).xpubstr()) ||
                                        addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()))))  {
                                    amount = -tx0value;
                                    isPostMixTx0 = true;
                                }
                            }
                            else  {
                                _addr = (String)outObj.get("addr");
                            }
                        }
                    }

                    if (addr != null || _addr != null) {
                        Tx tx = new Tx(hash, addr, amount, ts, (latest_block_height > 0L && height > 0L) ? (latest_block_height - height) + 1 : 0);

                        if (addr == null) {
                            addr = _addr;
                        }
                        if (isPostMixTx0 || addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).xpubstr()) ||
                                addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).zpubstr())) {
                            if (!postmix_txs.containsKey(addr)) {
                                postmix_txs.put(addr, new ArrayList<Tx>());
                            }
                            if (FormatsUtil.getInstance().isValidXpub(addr)) {
                                postmix_txs.get(addr).add(tx);
                            } else {
                                if(!xpub_txs.containsKey(AddressFactory.getInstance().account2xpub().get(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix())))    {
                                    xpub_txs.put(AddressFactory.getInstance().account2xpub().get(AddressFactory.getInstance().account2xpub().get(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix())), new ArrayList<Tx>());

                                }
                                xpub_txs.get(AddressFactory.getInstance().account2xpub().get(AddressFactory.getInstance().account2xpub().get(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()))).add(tx);
                            }

                        }

                        if(hasPreMix ||  addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).xpubstr()) ||
                                addr.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).zpubstr())  ) {
                            if (!premix_txs.containsKey(addr)) {
                                premix_txs.put(addr, new ArrayList<Tx>());
                            }
                            if (FormatsUtil.getInstance().isValidXpub(addr)) {
                                premix_txs.get(addr).add(tx);
                            } else {
                                if(!xpub_txs.containsKey(AddressFactory.getInstance().account2xpub().get(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount())))    {
                                    xpub_txs.put(AddressFactory.getInstance().account2xpub().get(AddressFactory.getInstance().account2xpub().get(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount())), new ArrayList<Tx>());
                                }
                                xpub_txs.get(AddressFactory.getInstance().account2xpub().get(AddressFactory.getInstance().account2xpub().get(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()))).add(tx);
                            }

                        }
                    }
                }

            }

            if(isWellFormedMultiAddr(jsonObject))    {
                try {
                    PayloadUtil.getInstance(context).serializeMultiAddrMix(jsonObject);
                    if(DojoUtil.getInstance(context).getDojoParams() == null)    {
                        if(PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUBPOSTXREG, false) == false)    {
                            PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUBPOSTXREG, true);
                        }
                    }
                    else    {
                        if(DojoUtil.getInstance(context).isLikeType() && PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUBPOSTXREG, false) == false)    {
                            PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUBPOSTXREG, true);
                        }
                    }
                }
                catch(Exception e) {
                    ;
                }
            }

            return true;

        }

        return false;

    }

    public  synchronized boolean parseMixUnspentOutputs(String unspents)   {

        int account_type = 0;

        if(unspents != null)    {

            try {
                JSONObject jsonObj = new JSONObject(unspents);

                if(jsonObj == null || !jsonObj.has("unspent_outputs"))    {
                    return false;
                }
                JSONArray utxoArray = jsonObj.getJSONArray("unspent_outputs");
                if(utxoArray == null || utxoArray.length() == 0) {
                    return false;
                }

                for (int i = 0; i < utxoArray.length(); i++) {

                    JSONObject outDict = utxoArray.getJSONObject(i);

                    byte[] hashBytes = Hex.decode((String)outDict.get("tx_hash"));
                    Sha256Hash txHash = Sha256Hash.wrap(hashBytes);
                    int txOutputN = ((Number)outDict.get("tx_output_n")).intValue();
                    BigInteger value = BigInteger.valueOf(((Number)outDict.get("value")).longValue());
                    String script = (String)outDict.get("script");
                    byte[] scriptBytes = Hex.decode(script);
                    int confirmations = ((Number)outDict.get("confirmations")).intValue();
                    String path = null;

                    try {
                        String address = outDict.getString("addr");
                        String xpubM = null;
                        if(outDict.has("xpub"))    {
                            JSONObject xpubObj = (JSONObject)outDict.get("xpub");
                            path = (String)xpubObj.get("path");
                            xpubM = (String)xpubObj.get("m");

                            unspentPaths.put(address, path);
                            if(xpubM.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix()).xpubstr()))    {
                                unspentBIP84PostMix.put(address, WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix());
                                account_type = XPUB_POSTMIX;
                            }
                            else if(xpubM.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount()).xpubstr()))    {
                                unspentBIP84PreMix.put(address, WhirlpoolMeta.getInstance(context).getWhirlpoolPremixAccount());
                                account_type = XPUB_PREMIX;
                            }
                            else if(xpubM.equals(BIP84Util.getInstance(context).getWallet().getAccount(WhirlpoolMeta.getInstance(context).getWhirlpoolBadBank()).xpubstr()))    {
                                unspentBIP84BadBank.put(address, WhirlpoolMeta.getInstance(context).getWhirlpoolBadBank());
                                account_type = XPUB_BADBANK;
                            }
                            else    {
                                ;
                            }
                        }
                        else    {
                            ;
                        }

                        // Construct the output
                        MyTransactionOutPoint outPoint = new MyTransactionOutPoint(SamouraiWallet.getInstance().getCurrentNetworkParams(), txHash, txOutputN, value, scriptBytes, address, confirmations);

                        if(account_type == XPUB_POSTMIX)    {
                            if(utxosPostMix.containsKey(script))    {
                                utxosPostMix.get(script).getOutpoints().add(outPoint);
                            }
                            else    {
                                UTXO utxo = new UTXO(path, xpubM);
                                utxo.getOutpoints().add(outPoint);
                                utxosPostMix.put(script, utxo);
                            }
                        }
                        else if(account_type == XPUB_PREMIX)    {
                            if(utxosPreMix.containsKey(script))    {
                                utxosPreMix.get(script).getOutpoints().add(outPoint);
                            }
                            else    {
                                UTXO utxo = new UTXO(path, xpubM);
                                utxo.getOutpoints().add(outPoint);
                                utxosPreMix.put(script, utxo);
                            }
                        } if(account_type == XPUB_BADBANK)    {
                            if(utxosBadBank.containsKey(script))    {
                                utxosBadBank.get(script).getOutpoints().add(outPoint);
                            }
                            else    {
                                UTXO utxo = new UTXO(path, xpubM);
                                utxo.getOutpoints().add(outPoint);
                                utxosBadBank.put(script, utxo);
                            }
                        }
                        onUtxoChange();

                    }
                    catch(Exception e) {
                        e.printStackTrace();
                        ;
                    }

                }

                return true;

            }
            catch(Exception j) {
                j.printStackTrace();
                ;
            }

        }

        return false;

    }

    private boolean isWellFormedMultiAddr(JSONObject jsonObject) {
        if(jsonObject.has("wallet") && jsonObject.has("info") && jsonObject.has("addresses") && jsonObject.has("txs"))    {
            return true;
        }
        else    {
            return false;
        }
    }

    public synchronized boolean parseRicochetXPUB() throws JSONException  {

        final String[] s = new String[] { BIP84Util.getInstance(context).getWallet().getAccount(RicochetMeta.getInstance(context).getRicochetAccount()).xpubstr() };

        final ListMultimap<EnumAddressType, String> addressesByType = ImmutableListMultimap.<EnumAddressType, String>builder()
                .putAll(EnumAddressType.BIP44_LEGACY, s)
                .build();
        JSONObject jsonObject = getRawXPUB(addressesByType);

        if(jsonObject != null)  {

            if(!jsonObject.has("wallet"))  {
                return false;
            }

            if(jsonObject.has("addresses"))  {

                JSONArray addressesArray = (JSONArray)jsonObject.get("addresses");
                JSONObject addrObj = null;
                for(int i = 0; i < addressesArray.length(); i++)  {
                    addrObj = (JSONObject)addressesArray.get(i);
                    if(addrObj != null && addrObj.has("address"))  {
                        if(FormatsUtil.getInstance().isValidXpub((String)addrObj.get("address")))    {

                            if(addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(RicochetMeta.getInstance(context).getRicochetAccount()).xpubstr()) ||
                                    addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(RicochetMeta.getInstance(context).getRicochetAccount()).zpubstr()))    {

                                if(addrObj.has("account_index"))    {
                                    RicochetMeta.getInstance(context).setIndex(addrObj.getInt("account_index"));
                                }

                            }
                            else    {
                                ;
                            }
                        }
                    }
                }
            }

        }

        return true;

    }

    public Observable<Pair<List<Tx>, Long>> parseXPUBObservable(JSONObject jsonObject) {
        return Observable.fromCallable(() -> {
            parseXPUB(jsonObject);
            List<Tx> txes = getAllXpubTxs();
            return new Pair<>(txes, xpub_balance);
        });
    }

    public Observable<Pair<List<Tx>, Long>> parseMixXPUBObservable(JSONObject jsonObject) {
        return Observable.fromCallable(() -> {
            parseMixXPUB(jsonObject);
            List<Tx> txes = getAllPostMixTxs();
            return new Pair<>(txes, xpub_postmix_balance);
        });
    }

    private static void onUtxoChange() {
        WhirlpoolUtils.getInstance().onUtxoChange(); // notify Whirlpool
    }

    public static class TxMostRecentDateComparator implements Comparator<Tx> {

        public int compare(Tx t1, Tx t2) {

            final int BEFORE = -1;
            final int EQUAL = 0;
            final int AFTER = 1;

            int ret = 0;

            if(t1.getTS() > t2.getTS()) {
                ret = BEFORE;
            }
            else if(t1.getTS() < t2.getTS()) {
                ret = AFTER;
            }
            else    {
                ret = EQUAL;
            }

            return ret;
        }

    }
}
