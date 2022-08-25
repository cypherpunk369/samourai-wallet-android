package com.samourai.wallet.send.cahoots;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.samourai.soroban.client.cahoots.OnlineCahootsMessage;
import com.samourai.wallet.R;
import com.samourai.wallet.SamouraiActivity;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.cahoots.AndroidSorobanCahootsService;
import com.samourai.wallet.cahoots.CahootsMode;
import com.samourai.wallet.cahoots.CahootsType;
import com.samourai.wallet.cahoots.CahootsTypeUser;
import com.samourai.soroban.cahoots.CahootsContext;
import com.samourai.soroban.client.SorobanMessage;
import com.samourai.wallet.cahoots.multi.MultiCahoots;
import com.samourai.wallet.send.FeeUtil;
import com.samourai.wallet.util.AppUtil;

import org.spongycastle.util.encoders.Hex;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class SorobanCahootsActivity extends SamouraiActivity {
    private static final String TAG = "SorobanCahootsActivity";
    private static final int TIMEOUT_MS = 60000;

    private SorobanCahootsUi cahootsUi;

    // intent
    private PaymentCode paymentCode;

    private Disposable sorobanDisposable;

    public static Intent createIntentSender(Context ctx, int account, CahootsType type, long sendAmount, long fees, String address, String pcode) {
        Intent intent = ManualCahootsUi.createIntent(ctx, SorobanCahootsActivity.class, account, type, CahootsTypeUser.SENDER);
        intent.putExtra("sendAmount", sendAmount);
        intent.putExtra("fees", fees);
        intent.putExtra("sendAddress", address);
        intent.putExtra("pcode", pcode);
        return intent;
    }

    public static Intent createIntentCounterparty(Context ctx, int account, CahootsType type, String pcode) {
        Intent intent = ManualCahootsUi.createIntent(ctx, SorobanCahootsActivity.class, account, type, CahootsTypeUser.COUNTERPARTY);
        intent.putExtra("pcode", pcode);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_cahoots);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null)
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        try {
            cahootsUi = new SorobanCahootsUi(
                findViewById(R.id.step_view),
                findViewById(R.id.view_flipper),
                getIntent(),
                getSupportFragmentManager(),
                i -> SorobanCahootsStepFragment.newInstance(i),
                this
            );
            this.account = cahootsUi.getAccount();
            setTitle(cahootsUi.getTitle(CahootsMode.SOROBAN));

            if (getIntent().hasExtra("pcode")) {
                String pcode = getIntent().getStringExtra("pcode");
                paymentCode = new PaymentCode(pcode);
            }
            if (paymentCode == null) {
                throw new Exception("Invalid paymentCode");
            }

            // start cahoots
            switch(cahootsUi.getTypeUser()) {
                case SENDER:
                    startSender();
                    break;
                case COUNTERPARTY:
                    startReceiver();
                    break;
                default:
                    throw new Exception("Unknown typeUser");
            }
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    private void startSender() throws Exception {
        long feePerB = getIntent().getLongExtra("fees", FeeUtil.getInstance().getSuggestedFeeDefaultPerB()) ;
        long sendAmount = getIntent().getLongExtra("sendAmount", 0);
        if (sendAmount <=0) {
            throw new Exception("Invalid sendAmount");
        }
        String sendAddress = getIntent().getStringExtra("sendAddress");
        String paynymDestination = null; // TODO Sarath set destination pCode
        // send cahoots
        AndroidSorobanCahootsService sorobanCahootsService = cahootsUi.getSorobanCahootsService();
        CahootsContext cahootsContext = cahootsUi.setCahootsContextInitiator(account, feePerB, sendAmount, sendAddress, paynymDestination);
        Observable<SorobanMessage> sorobanListener = sorobanCahootsService.initiator(cahootsContext, paymentCode, TIMEOUT_MS);

        // listen for cahoots progress
        subscribeOnMessage(sorobanListener);
    }

    private void startReceiver() throws Exception {
        AndroidSorobanCahootsService sorobanCahootsService = cahootsUi.getSorobanCahootsService();
        CahootsContext cahootsContext = cahootsUi.setCahootsContextCounterparty(account);
        Observable<SorobanMessage> sorobanListener = sorobanCahootsService.contributor(cahootsContext, paymentCode, TIMEOUT_MS);
        subscribeOnMessage(sorobanListener);
        Toast.makeText(this, "Waiting for online Cahoots", Toast.LENGTH_SHORT).show();
    }

    private void subscribeOnMessage(Observable<SorobanMessage> onMessage) {
        sorobanDisposable = onMessage.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(sorobanMessage -> {
                    OnlineCahootsMessage cahootsMessage = (OnlineCahootsMessage)sorobanMessage;
                    if (cahootsMessage != null) {
                        if(cahootsMessage.isDone()) {
                            if(cahootsMessage.getCahoots() instanceof MultiCahoots) {
                                MultiCahoots multiCahoots = (MultiCahoots) cahootsMessage.getCahoots();
                                System.out.println(Hex.toHexString(multiCahoots.getStowawayTransaction().bitcoinSerialize()));
                                System.out.println(Hex.toHexString(multiCahoots.getStonewallTransaction().bitcoinSerialize()));
                            }
                        }
                        cahootsUi.setCahootsMessage(cahootsMessage);
                    }
                },
                sorobanError -> {
                    Toast.makeText(getApplicationContext(), "Cahoots error: " + sorobanError.getMessage(), Toast.LENGTH_SHORT).show();
                    sorobanError.printStackTrace();
                    finish();
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppUtil.getInstance(this).setIsInForeground(true);
        AppUtil.getInstance(this).checkTimeOut();
    }

    private void clearDisposable() {
        if (sorobanDisposable != null && !sorobanDisposable.isDisposed()) {
            sorobanDisposable.dispose();
            sorobanDisposable = null;
        }
    }

    @Override
    public void finish() {
        clearDisposable();
        super.finish();
    }

    @Override
    public void onBackPressed() {// cancel cahoots request
        clearDisposable();
        super.onBackPressed();
    }
}
