package com.samourai.wallet.service;

import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.samourai.wallet.BuildConfig;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.access.AccessFactory;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.crypto.AESUtil;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.ricochet.RicochetMeta;
import com.samourai.wallet.segwit.BIP49Util;
import com.samourai.wallet.segwit.BIP84Util;
import com.samourai.wallet.util.AddressFactory;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.CharSequenceX;
import com.samourai.wallet.util.LogUtil;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.whirlpool.WhirlpoolMeta;

import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.crypto.MnemonicException;
import org.json.JSONException;

import java.io.IOException;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;


public class JobRefreshService extends JobIntentService {

    static final int JOB_ID = 56;
    private static final String TAG = "JobRefreshService";

    public static void enqueueWork(Context context, Intent intent) {
        JobRefreshService.enqueueWork(context, JobRefreshService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {


        boolean dragged = intent.getBooleanExtra("dragged", false);
        boolean launch = intent.getBooleanExtra("launch", false);
        boolean notifTx = intent.getBooleanExtra("notifTx", false);

        Log.d("JobRefreshService", "doInBackground()");

        APIFactory.getInstance(this.getApplicationContext()).stayingAlive();
        APIFactory.getInstance(this.getApplicationContext()).initWallet();

        Intent _intentDisplay = new Intent("com.samourai.wallet.BalanceFragment.DISPLAY");
        LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(_intentDisplay);

        PrefsUtil.getInstance(this.getApplicationContext()).setValue(PrefsUtil.FIRST_RUN, false);

        if (notifTx && !AppUtil.getInstance(this.getApplicationContext()).isOfflineMode()) {
            //
            // check for incoming payment code notification tx
            //
            try {
                PaymentCode pcode = BIP47Util.getInstance(this.getApplicationContext()).getPaymentCode();
//                    Log.i("BalanceFragment", "payment code:" + pcode.toString());
//                    Log.i("BalanceFragment", "notification address:" + pcode.notificationAddress().getAddressString());
                APIFactory.getInstance(this.getApplicationContext()).getNotifAddress(pcode.notificationAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).getAddressString());
            } catch (AddressFormatException afe) {
                afe.printStackTrace();
                Toast.makeText(this.getApplicationContext(), "HD wallet error", Toast.LENGTH_SHORT).show();
            } catch (Exception ex){
                ex.printStackTrace();
            }

            //
            // check on outgoing payment code notification tx
            //
            List<Pair<String, String>> outgoingUnconfirmed = BIP47Meta.getInstance().getOutgoingUnconfirmed();
//                Log.i("BalanceFragment", "outgoingUnconfirmed:" + outgoingUnconfirmed.size());
            for (Pair<String, String> pair : outgoingUnconfirmed) {
//                    Log.i("BalanceFragment", "outgoing payment code:" + pair.getLeft());
//                    Log.i("BalanceFragment", "outgoing payment code tx:" + pair.getRight());
                int confirmations = APIFactory.getInstance(this.getApplicationContext()).getNotifTxConfirmations(pair.getRight());
                if (confirmations > 0) {
                    BIP47Meta.getInstance().setOutgoingStatus(pair.getLeft(), BIP47Meta.STATUS_SENT_CFM);
                }
                if (confirmations == -1) {
                    BIP47Meta.getInstance().setOutgoingStatus(pair.getLeft(), BIP47Meta.STATUS_NOT_SENT);
                }
            }

            Intent _intent = new Intent("com.samourai.wallet.MainActivity2.RESTART_SERVICE");
            LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(_intent);
        }

        if (launch) {

            if (PrefsUtil.getInstance(this.getApplicationContext()).getValue(PrefsUtil.GUID_V, 0) < 4) {
                Log.i("JobIntentService", "guid_v < 4");
                try {
                    String _guid = AccessFactory.getInstance(this.getApplicationContext()).createGUID();
                    String _hash = AccessFactory.getInstance(this.getApplicationContext()).getHash(_guid, new CharSequenceX(AccessFactory.getInstance(this.getApplicationContext()).getPIN()), AESUtil.DefaultPBKDF2Iterations);

                    PayloadUtil.getInstance(this.getApplicationContext()).saveWalletToJSON(new CharSequenceX(_guid + AccessFactory.getInstance().getPIN()));

                    PrefsUtil.getInstance(this.getApplicationContext()).setValue(PrefsUtil.ACCESS_HASH, _hash);
                    PrefsUtil.getInstance(this.getApplicationContext()).setValue(PrefsUtil.ACCESS_HASH2, _hash);

                    Log.i("JobIntentService", "guid_v == 4");
                } catch (MnemonicException.MnemonicLengthException | IOException | JSONException | DecryptionException e) {
                    ;
                }
            }

            if (!PrefsUtil.getInstance(this.getApplicationContext().getApplicationContext()).getValue(PrefsUtil.XPUB44LOCK, false)) {

                String[] s = HD_WalletFactory.getInstance(this.getApplicationContext()).get().getXPUBs();
                APIFactory.getInstance(this.getApplicationContext()).lockXPUB(s[0], 44, null);

            }

            try {
                if (!PrefsUtil.getInstance(this.getApplicationContext()).getValue(PrefsUtil.XPUB49LOCK, false)) {
                    String ypub = BIP49Util.getInstance(this.getApplicationContext()).getWallet().getAccount(0).ypubstr();
                    APIFactory.getInstance(this.getApplicationContext()).lockXPUB(ypub, 49, null);
                }

                if (!PrefsUtil.getInstance(this.getApplicationContext()).getValue(PrefsUtil.XPUB84LOCK, false)) {
                    String zpub = BIP84Util.getInstance(this.getApplicationContext()).getWallet().getAccount(0).zpubstr();
                    APIFactory.getInstance(this.getApplicationContext()).lockXPUB(zpub, 84, null);
                }

                if (!PrefsUtil.getInstance(this.getApplicationContext()).getValue(PrefsUtil.XPUBPRELOCK, false)) {
                    String zpub = BIP84Util.getInstance(this.getApplicationContext()).getWallet().getAccountAt(WhirlpoolMeta.getInstance(this.getApplicationContext()).getWhirlpoolPremixAccount()).zpubstr();
                    APIFactory.getInstance(this.getApplicationContext()).lockXPUB(zpub, 84, PrefsUtil.XPUBPRELOCK);
                }

                if (!PrefsUtil.getInstance(this.getApplicationContext()).getValue(PrefsUtil.XPUBPOSTLOCK, false)) {
                    String zpub = BIP84Util.getInstance(this.getApplicationContext()).getWallet().getAccountAt(WhirlpoolMeta.getInstance(this.getApplicationContext()).getWhirlpoolPostmix()).zpubstr();
                    APIFactory.getInstance(this.getApplicationContext()).lockXPUB(zpub, 84, PrefsUtil.XPUBPOSTLOCK);
                }

                if (!PrefsUtil.getInstance(this.getApplicationContext()).getValue(PrefsUtil.XPUBBADBANKLOCK, false)) {
                    String zpub = BIP84Util.getInstance(this.getApplicationContext()).getWallet().getAccountAt(WhirlpoolMeta.getInstance(this.getApplicationContext()).getWhirlpoolBadBank()).zpubstr();
                    APIFactory.getInstance(this.getApplicationContext()).lockXPUB(zpub, 84, PrefsUtil.XPUBBADBANKLOCK);
                }

                if (!PrefsUtil.getInstance(this.getApplicationContext()).getValue(PrefsUtil.XPUBRICOCHETLOCK, false)) {
                    String zpub = BIP84Util.getInstance(this.getApplicationContext()).getWallet().getAccountAt(RicochetMeta.getInstance(JobRefreshService.this).getRicochetAccount()).zpubstr();
                    APIFactory.getInstance(this.getApplicationContext()).lockXPUB(zpub, 84, PrefsUtil.XPUBRICOCHETLOCK);
                }
            } catch (Exception e) {
                LogUtil.error(TAG,e.getMessage());
                if(BuildConfig.DEBUG){
                    e.printStackTrace();
                }
            }

            try {
                int prevIdx = RicochetMeta.getInstance(JobRefreshService.this).getIndex();
                APIFactory.getInstance(JobRefreshService.this).parseRicochetXPUB();
                if(prevIdx > RicochetMeta.getInstance(JobRefreshService.this).getIndex())    {
                    RicochetMeta.getInstance(JobRefreshService.this).setIndex(prevIdx);
                }
            } catch (JSONException je) {
                ;
            }

        } else {

            try {
                PayloadUtil.getInstance(this.getApplicationContext()).saveWalletToJSON(new CharSequenceX(AccessFactory.getInstance(this.getApplicationContext()).getGUID() + AccessFactory.getInstance(this.getApplicationContext()).getPIN()));
            } catch (Exception ignored) {
                ;
            }

        }

        Intent _intent = new Intent("com.samourai.wallet.BalanceFragment.DISPLAY");
        LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(_intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        LogUtil.debug(TAG, "JobRefreshService Destroy");
    }


}