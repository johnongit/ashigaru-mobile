package com.samourai.wallet.util.tech;

import static android.content.Context.ACTIVITY_SERVICE;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.samourai.wallet.MainActivity2;
import com.samourai.wallet.SamouraiActivity;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.network.dojo.DojoUtil;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.ricochet.RicochetMeta;
import com.samourai.wallet.segwit.BIP84Util;
import com.samourai.wallet.send.BlockedUTXO;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.TimeOutUtil;
import com.samourai.wallet.util.Util;
import com.samourai.wallet.util.func.BatchSendUtil;
import com.samourai.wallet.util.func.SendAddressUtil;
import com.samourai.wallet.util.func.SentToFromBIP47Util;
import com.samourai.wallet.util.network.ConnectivityStatus;
import com.samourai.wallet.utxos.UTXOUtil;
import com.samourai.whirlpool.client.wallet.WhirlpoolUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppUtil {

    public static final String TAG = "AppUtil";

    public static final int MIN_BACKUP_PW_LENGTH = 6;
    public static final int MAX_BACKUP_PW_LENGTH = 255;
    private MutableLiveData<Boolean> offlineLiveData = new MutableLiveData();

    public static final String TOR_PACKAGE_ID = "org.torproject.android";
    public static final String OPENVPN_PACKAGE_ID = "de.blinkt.openvpn";

    private boolean isInForeground = false;

    private static AppUtil instance = null;
    private static Context context = null;

    private static String strReceiveQRFilename = null;
    private static String strBackupFilename = null;

    private static boolean PRNG_FIXES = false;

    private static boolean CLIPBOARD_SEEN = false;

    private static boolean isOfflineMode = false;
    private static boolean isUserOfflineMode = false;
    private static MutableLiveData<Boolean> isWalletRefreshing = new MutableLiveData<>(false);
    private static MutableLiveData<Boolean> hasUpdateBeenShown = new MutableLiveData<>(false);

    private AppUtil() {
        ;
    }

    public static AppUtil getInstance(Context ctx) {

        context = ctx;

        if (instance == null) {
            strReceiveQRFilename = context.getExternalCacheDir() + File.separator + "qr.png";
            strBackupFilename = context.getCacheDir() + File.separator + "backup.asc";
            instance = new AppUtil();
        }

        return instance;
    }

    public boolean isOfflineMode() {

        isOfflineMode = isUserOfflineMode() || !ConnectivityStatus.hasConnectivity(context);

        return isOfflineMode;
    }

    public LiveData<Boolean> offlineStateLive() {
        return offlineLiveData;
    }

    public void checkOfflineState() {
        offlineLiveData.postValue(isOfflineMode());
    }

    public void setOfflineMode(boolean offline) {
        isOfflineMode = offline;
        checkOfflineState();
    }

    public void setWalletLoading(boolean loading) {
        isWalletRefreshing.postValue(loading);
    }

    public LiveData<Boolean> getWalletLoading() {
        return isWalletRefreshing;
    }

    public MutableLiveData<Boolean> getHasUpdateBeenShown() {
        return hasUpdateBeenShown;
    }

    public void setHasUpdateBeenShown(boolean beenShown) {
        hasUpdateBeenShown.postValue(beenShown);
    }

    public boolean isUserOfflineMode() {
        return isUserOfflineMode;
    }

    public void setUserOfflineMode(boolean offline) {
        isUserOfflineMode = offline;
        checkOfflineState();
    }

    public void wipeApp() {

        try {
            // wipe whirlpool files
            HD_Wallet bip84w = BIP84Util.getInstance(context).getWallet();
            WhirlpoolUtils.getInstance().wipe(bip84w, context);
        } catch (Exception e) {
            e.printStackTrace();
        }
/*
        try {
            HD_Wallet hdw = HD_WalletFactory.getInstance(context).get();
            String[] s = hdw.getXPUBs();
            for(int i = 0; i < s.length; i++)   {
//                APIFactory.getInstance(context).deleteXPUB(s[i], false);
            }
            String _s = BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr();
//            APIFactory.getInstance(context).deleteXPUB(_s, true);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
*/

        BIP47Meta.getInstance().clear();
        DojoUtil.getInstance(context).clear();

        try {
            PayloadUtil.getInstance(context).wipe();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }

        // reset HD_WalletFactory + BIP47Util + BIP49Util + BIP84Util + AddressFactory
        HD_WalletFactory.getInstance(context).clear();

        deleteBackup();
        deleteQR();

        final ComponentName component = new ComponentName(context.getApplicationContext().getPackageName(), "com.samourai.wallet.MainActivity");
        try {
            context.getPackageManager().setComponentEnabledSetting(component, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
            PrefsUtil.getInstance(context).setValue(PrefsUtil.ICON_HIDDEN, false);
        } catch (IllegalArgumentException iae) {
            ;
        }

        APIFactory.getInstance(context).setXpubBalance(0L);
        APIFactory.getInstance(context).reset();
        PrefsUtil.getInstance(context).setValue(PrefsUtil.ENABLE_TOR, false);
        PrefsUtil.getInstance(context).setValue(PrefsUtil.IS_RESTORE, false);
        PrefsUtil.getInstance(context).clear();
        BlockedUTXO.getInstance().clear();
        BlockedUTXO.getInstance().clearPostMix();
        RicochetMeta.getInstance(context).empty();
        RicochetMeta.getInstance(context).setIndex(0);
        SendAddressUtil.getInstance().reset();
        SentToFromBIP47Util.getInstance().reset();
        BatchSendUtil.getInstance().clear();
        UTXOUtil.getInstance().reset();
        AccessFactory.getInstance(context).setIsLoggedIn(false);

        try {
            clearApplicationData();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void restartApp() {
        Intent intent = new Intent(context, MainActivity2.class);
        if (PrefsUtil.getInstance(context).getValue(PrefsUtil.ICON_HIDDEN, false) == true) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        context.startActivity(intent);
    }

    public void restartAppFromActivity(final Bundle extras, final FragmentActivity fromActivity) {
        final Intent intent = new Intent(fromActivity, MainActivity2.class);
        if (extras != null) {
            intent.putExtras(extras);
        }
        fromActivity.startActivity(intent);
    }

    public boolean isServiceRunning(Class<?> serviceClass) {

        ActivityManager manager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.d("AppUtil", "service class name:" + serviceClass.getName() + " is running");
                return true;
            }
        }

        Log.d("AppUtil", "service class name:" + serviceClass.getName() + " is not running");
        return false;
    }

    public boolean isInForeground() {
        return isInForeground;
    }

    public void setIsInForeground(boolean foreground) {
        isInForeground = foreground;
    }

    public String getReceiveQRFilename() {
        return strReceiveQRFilename;
    }

    public String getBackupFilename() {
        return strBackupFilename;
    }

    public void deleteQR() {
        String strFileName = strReceiveQRFilename;
        File file = new File(strFileName);
        if (file.exists()) {
            file.delete();
        }
    }

    public void deleteBackup() {
        String strFileName = strBackupFilename;
        File file = new File(strFileName);
        if (file.exists()) {
            file.delete();
        }
    }

    public void checkTimeOut() {
        if (TimeOutUtil.getInstance().isTimedOut()) {
            AppUtil.getInstance(context).restartApp();
        } else {
            TimeOutUtil.getInstance().updatePin();
        }
    }

    public boolean isSideLoaded() {
        List<String> validInstallers = new ArrayList<>(Arrays.asList("com.android.vending", "com.google.android.feedback"));
        final String installer = context.getPackageManager().getInstallerPackageName(context.getPackageName());
        return installer == null || !validInstallers.contains(installer);
    }

    public boolean isClipboardSeen() {
        return CLIPBOARD_SEEN;
    }

    public void setClipboardSeen(boolean seen) {
        CLIPBOARD_SEEN = seen;
    }

    private void clearApplicationData() throws IOException {
        File cacheDirectory = context.getCacheDir();
        File applicationDirectory = new File(cacheDirectory.getParent());
        if (applicationDirectory.exists()) {
            String[] fileNames = applicationDirectory.list();
            for (String fileName : fileNames) {
                deleteFiles(new File(applicationDirectory, fileName));
            }
        }
    }

    private synchronized boolean deleteFiles(File file) throws IOException {
        boolean deletedAll = true;
        if (file != null) {
            if (file.isDirectory()) {
                String[] children = file.list();
                for (int i = 0; i < children.length; i++) {
                    deletedAll = deleteFiles(new File(file, children[i])) && deletedAll;
                }
            } else {
                deletedAll = file.delete();
            }
        }

        return deletedAll;
    }

    public boolean isBroadcastDisabled() {
        return PrefsUtil.getInstance(context).getValue(PrefsUtil.BROADCAST_TX, true) == false
                || isOfflineMode();
    }

    public String getApkSha256() {
        final ApplicationInfo appInfo = context.getApplicationContext().getApplicationInfo();
        final String apkPath = appInfo.sourceDir;
        final File file = new File(apkPath);
        try (final FileInputStream fis = new FileInputStream(file)) {
            byte[] fileBytes = new byte[(int) file.length()];
            fis.read(fileBytes);
            return Util.sha256ToString(fileBytes);
        } catch (final Exception e) {
            Log.e(TAG, "cannot getApkSha256()", e);
            return null;
        }
    }

}
