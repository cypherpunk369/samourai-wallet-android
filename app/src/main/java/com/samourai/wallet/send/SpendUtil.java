package com.samourai.wallet.send;

import android.content.Context;

import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.api.APIFactory;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.send.UTXOFactory;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.wallet.whirlpool.WhirlpoolMeta;

import org.bitcoinj.core.Address;

import java.util.ArrayList;
import java.util.List;

public class SpendUtil {

    public static List<UTXO> getUTXOS(Context context, String address, long neededAmount, int account) {
        List<UTXO> utxos = null;
        if(account == WhirlpoolMeta.getInstance(context).getWhirlpoolPostmix())    {
            if(UTXOFactory.getInstance().getTotalPostMix() > neededAmount)    {
                utxos = new ArrayList<UTXO>(UTXOFactory.getInstance().getAllPostMix().values());
            }
            else    {
                return null;
            }
        }
        else if (FormatsUtil.getInstance().isValidBech32(address) && (UTXOFactory.getInstance().getAllP2WPKH().size() > 0 && UTXOFactory.getInstance().getTotalP2WPKH() > neededAmount)) {
            utxos = new ArrayList<UTXO>(UTXOFactory.getInstance().getAllP2WPKH().values());
//                    Log.d("SendActivity", "segwit utxos:" + utxos.size());
        } else if (!FormatsUtil.getInstance().isValidBech32(address) && Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress() && (UTXOFactory.getInstance().getAllP2SH_P2WPKH().size() > 0 && UTXOFactory.getInstance().getTotalP2SH_P2WPKH() > neededAmount)) {
            utxos = new ArrayList<UTXO>(UTXOFactory.getInstance().getAllP2SH_P2WPKH().values());
//                    Log.d("SendActivity", "segwit utxos:" + utxos.size());
        } else if ((!FormatsUtil.getInstance().isValidBech32(address) && !Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress()) && (UTXOFactory.getInstance().getAllP2PKH().size() > 0) && (UTXOFactory.getInstance().getTotalP2PKH() > neededAmount)) {
            utxos = new ArrayList<UTXO>(UTXOFactory.getInstance().getAllP2PKH().values());
//                    Log.d("SendActivity", "p2pkh utxos:" + utxos.size());
        } else {
            utxos = APIFactory.getInstance(context).getUtxos(true);
//                    Log.d("SendActivity", "all filtered utxos:" + utxos.size());
        }
        return utxos;
    }
}
