package com.samourai.wallet.api;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.samourai.wallet.JSONRPC.JSONRPC;
import com.samourai.wallet.JSONRPC.TrustedNodeUtil;
import com.samourai.wallet.SamouraiWallet;
import com.samourai.wallet.bip47.BIP47Activity;
import com.samourai.wallet.bip47.BIP47Meta;
import com.samourai.wallet.bip47.BIP47Util;
import com.samourai.wallet.crypto.DecryptionException;
import com.samourai.wallet.hd.HD_Address;
import com.samourai.wallet.hd.HD_Wallet;
import com.samourai.wallet.hd.HD_WalletFactory;
import com.samourai.wallet.payload.PayloadUtil;
import com.samourai.wallet.segwit.BIP49Util;
import com.samourai.wallet.segwit.BIP84Util;
import com.samourai.wallet.segwit.SegwitAddress;
import com.samourai.wallet.segwit.bech32.Bech32Segwit;
import com.samourai.wallet.segwit.bech32.Bech32Util;
import com.samourai.wallet.send.BlockedUTXO;
import com.samourai.wallet.send.FeeUtil;
import com.samourai.wallet.send.MyTransactionOutPoint;
import com.samourai.wallet.send.RBFUtil;
import com.samourai.wallet.send.SuggestedFee;
import com.samourai.wallet.send.UTXO;
import com.samourai.wallet.send.UTXOFactory;
import com.samourai.wallet.util.AddressFactory;
import com.samourai.wallet.util.AppUtil;
import com.samourai.wallet.util.ConnectivityStatus;
import com.samourai.wallet.util.FormatsUtil;
import com.samourai.wallet.util.PrefsUtil;
import com.samourai.wallet.util.TorUtil;
import com.samourai.wallet.util.WebUtil;
import com.samourai.wallet.bip47.rpc.PaymentCode;
import com.samourai.wallet.bip47.rpc.PaymentAddress;
import com.samourai.wallet.R;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.Utils;
import org.bitcoinj.crypto.MnemonicException;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.Script;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static java.util.TimeZone.getTimeZone;

public class APIFactory	{

    private static long xpub_balance = 0L;
    private static HashMap<String, Long> xpub_amounts = null;
    private static HashMap<String,List<Tx>> xpub_txs = null;
    private static HashMap<String,Integer> unspentAccounts = null;
    private static HashMap<String,Integer> unspentBIP49 = null;
    private static HashMap<String,Integer> unspentBIP84 = null;
    private static HashMap<String,String> unspentPaths = null;
    private static HashMap<String, List<Tx>> bip47_txs = null;
    private static HashMap<String,UTXO> utxos = null;

    private static HashMap<String, Long> bip47_amounts = null;

    private static HashMap<String, String> xpub_pathaddressmap = null;

    private static long latest_block_height = -1L;
    private static String latest_block_hash = null;

    private static APIFactory instance = null;

    private static Context context = null;

    private static AlertDialog alertDialog = null;

    private APIFactory()	{ ; }

    public static APIFactory getInstance(Context ctx) {

        context = ctx;

        if(instance == null) {
            xpub_amounts = new HashMap<String, Long>();
            xpub_txs = new HashMap<String,List<Tx>>();
            xpub_balance = 0L;
            bip47_amounts = new HashMap<String, Long>();
            unspentPaths = new HashMap<String, String>();
            unspentAccounts = new HashMap<String, Integer>();
            unspentBIP49 = new HashMap<String, Integer>();
            unspentBIP84 = new HashMap<String, Integer>();
            utxos = new HashMap<String, UTXO>();
            xpub_pathaddressmap = new HashMap<>();
            instance = new APIFactory();
            bip47_txs = new HashMap<>();
        }

        return instance;
    }

    public synchronized void reset() {
        xpub_balance = 0L;
        xpub_amounts.clear();
        bip47_amounts.clear();
        xpub_txs.clear();
        unspentPaths = new HashMap<String, String>();
        unspentAccounts = new HashMap<String, Integer>();
        unspentBIP49 = new HashMap<String, Integer>();
        unspentBIP84 = new HashMap<String, Integer>();
        utxos = new HashMap<String, UTXO>();
        xpub_pathaddressmap.clear();
        bip47_txs.clear();

        UTXOFactory.getInstance().clear();
    }

    private synchronized JSONObject getXPUB(String[] xpubs, boolean parse) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        boolean regularAddress = false;
        if(xpubs[0].startsWith("F") || xpubs[0].startsWith("m") || xpubs[0].startsWith("2") || xpubs[0].startsWith("3") || xpubs[0].startsWith("n")) {
            regularAddress = true;
            StringBuilder addresses = new StringBuilder();
            for(String xpub : xpubs) {
                addresses.append(xpub);
            }
            xpubs = new String[1];
            xpubs[0] = addresses.substring(0, addresses.length()-1);
        } else if(xpubs[0].length() == 66) {
            HashMap<String, String> mapAddressToPubkey = new HashMap<>(xpubs.length * 2);
            StringBuilder addresses = new StringBuilder();
            regularAddress = true;
            for(String pubkey : xpubs) {
                ECKey key = ECKey.fromPublicOnly(Utils.HEX.decode(pubkey));
                String p2pkh = key.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toBase58();
                SegwitAddress segwitAddress = new SegwitAddress(key, SamouraiWallet.getInstance().getCurrentNetworkParams());
                String bech32 = segwitAddress.getBech32AsString();

                mapAddressToPubkey.put(p2pkh, pubkey);
                mapAddressToPubkey.put(bech32, pubkey);
                addresses.append(p2pkh).append("|").append(bech32).append("|");
                xpubs = new String[1];
                xpubs[0] = addresses.toString();
            }
        }

        try {

            String responses [] = new String[xpubs.length];

            for(int i = 0; i < xpubs.length; ++i) {

                if (AppUtil.getInstance(context).isOfflineMode()) {
                    responses[i] = PayloadUtil.getInstance(context).deserializeMultiAddr().toString();
                } else if (!TorUtil.getInstance(context).statusFromBroadcast()) {
                    // use POST
                    StringBuilder args = new StringBuilder();
                    if(regularAddress)
                        args.append("multiaddr&active=");
                    else args.append("xpub2&xpub=");
                    //args.append(StringUtils.join(xpubs, URLEncoder.encode("|", "UTF-8")));
                    args.append(xpubs[i]);
                    Log.i("APIFactory", "XPUB["+i+"]:" + args.toString());
                    responses[i] = WebUtil.getInstance(context).getURL(_url + args.toString());
                    Log.i("APIFactory", "XPUB["+i+"] response:" + responses[i]);
                } else {
                    HashMap<String, String> args = new HashMap<String, String>();
                    args.put("active", StringUtils.join(xpubs[i], "|"));
                    Log.i("APIFactory", "XPUB:" + args.toString());
                    responses[i] = WebUtil.getInstance(context).tor_postURL(_url, args);
                    Log.i("APIFactory", "XPUB response:" + responses[i]);
                }


                try {
                    jsonObject = new JSONObject(responses[i]);
                    if (!parse) {
                        return jsonObject;
                    }
                    xpub_txs.put(xpubs[i], new ArrayList<Tx>());
                    parseXPUB(jsonObject, xpubs[i]);
                    xpub_amounts.put(HD_WalletFactory.getInstance(context).get().getAccount(0).xpubstr(), xpub_balance - BlockedUTXO.getInstance().getTotalValueBlocked());
                } catch (JSONException je) {
                    je.printStackTrace();
                    jsonObject = null;
                }
            }

        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    private synchronized JSONObject registerXPUB(String xpub, int purpose) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {

            String response = null;

            if(!TorUtil.getInstance(context).statusFromBroadcast())    {
                // use POST
                StringBuilder args = new StringBuilder();
                args.append("xpub=");
                args.append(xpub);
                args.append("&type=");
                if(PrefsUtil.getInstance(context).getValue(PrefsUtil.IS_RESTORE, false) == true)    {
                    args.append("restore");
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
                Log.i("APIFactory", "XPUB:" + args.toString());
                response = WebUtil.getInstance(context).getURL(_url + "xpub2&"+ args.toString());
                Log.i("APIFactory", "XPUB response:" + response);
            }
            else    {
                HashMap<String,String> args = new HashMap<String,String>();
                args.put("xpub", xpub);
                if(PrefsUtil.getInstance(context).getValue(PrefsUtil.IS_RESTORE, false) == true)    {
                    args.put("type", "restore");
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
                Log.i("APIFactory", "XPUB:" + args.toString());
                response = WebUtil.getInstance(context).tor_postURL(_url + "xpub", args);
                Log.i("APIFactory", "XPUB response:" + response);
            }

            try {
                jsonObject = new JSONObject(response);
                Log.i("APIFactory", "XPUB response:" + jsonObject.toString());
                if(jsonObject.has("status") && jsonObject.getString("status").equals("ok"))    {
                    if(purpose == 49)    {
                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB49REG, true);
                        PrefsUtil.getInstance(context).removeValue(PrefsUtil.IS_RESTORE);
                    }
                    else if(purpose == 84)    {
                        PrefsUtil.getInstance(context).setValue(PrefsUtil.XPUB84REG, true);
                        PrefsUtil.getInstance(context).removeValue(PrefsUtil.IS_RESTORE);

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

    private synchronized void parseXPUB(JSONObject jsonObject, String xpub) throws JSONException  {

        if(jsonObject != null)  {

            HashMap<String,Integer> pubkeys = new HashMap<String,Integer>();

            if(jsonObject.has("wallet"))  {
                JSONObject walletObj = (JSONObject)jsonObject.get("wallet");
                if(walletObj.has("final_balance"))  {
                    xpub_balance = walletObj.getLong("final_balance");
                    Log.d("APIFactory", "xpub_balance:" + xpub_balance);
                }
            }

            if(jsonObject.has("info"))  {
                JSONObject infoObj = (JSONObject)jsonObject.get("info");
                if(infoObj.has("latest_block"))  {
                    JSONObject blockObj = (JSONObject)infoObj.get("latest_block");
                    if(blockObj.has("height"))  {
                        latest_block_height = blockObj.getLong("height");
                    }
                    if(blockObj.has("hash"))  {
                        latest_block_hash = blockObj.getString("hash");
                    }
                }
            }

            ArrayList<String> addressesFound = new ArrayList<>();

            if(jsonObject.has("addresses"))  {

                JSONArray addressesArray = (JSONArray)jsonObject.get("addresses");
                JSONObject addrObj = null;
                for(int i = 0; i < addressesArray.length(); i++)  {
                    addrObj = (JSONObject)addressesArray.get(i);
                    if(addrObj.has("address")) {
                        int count = addrObj.has("n_tx") ? addrObj.getInt("n_tx") : 1;
                        for(int j = 0; j < count; ++j) {
                            addressesFound.add(addrObj.getString("address"));
                        }
                    }
                    if(addrObj != null && addrObj.has("final_balance") && addrObj.has("address"))  {
                        if(FormatsUtil.getInstance().isValidXpub((String)addrObj.get("address")))    {
                            xpub_amounts.put((String)addrObj.get("address"), addrObj.getLong("final_balance"));
                            if(addrObj.getString("address").equals(BIP84Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                AddressFactory.getInstance().setHighestBIP84ReceiveIdx(addrObj.has("account_index") ? addrObj.getInt("account_index") : 0);
                                AddressFactory.getInstance().setHighestBIP84ChangeIdx(addrObj.has("change_index") ? addrObj.getInt("change_index") : 0);
                            }
                            else if(addrObj.getString("address").equals(BIP49Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                AddressFactory.getInstance().setHighestBIP49ReceiveIdx(addrObj.has("account_index") ? addrObj.getInt("account_index") : 0);
                                AddressFactory.getInstance().setHighestBIP49ChangeIdx(addrObj.has("change_index") ? addrObj.getInt("change_index") : 0);
                            }
                            else if(AddressFactory.getInstance().xpub2account().get((String) addrObj.get("address")) != null)    {
                                AddressFactory.getInstance().setHighestTxReceiveIdx(AddressFactory.getInstance().xpub2account().get((String) addrObj.get("address")), addrObj.has("account_index") ? addrObj.getInt("account_index") : 0);
                                AddressFactory.getInstance().setHighestTxChangeIdx(AddressFactory.getInstance().xpub2account().get((String)addrObj.get("address")), addrObj.has("change_index") ? addrObj.getInt("change_index") : 0);
                            }
                            else    {
                                ;
                            }
                        }
                        else    {
                            long amount = 0L;
                            String addr = null;
                            addr = (String)addrObj.get("address");
                            amount = addrObj.getLong("final_balance");
                            String pcode = BIP47Meta.getInstance().getPCode4Addr(addr);
                            if(addr != null && addr.length() > 0 && pcode != null && pcode.length() > 0 && BIP47Meta.getInstance().getIdx4Addr(addr) != null)    {
                                int idx = BIP47Meta.getInstance().getIdx4Addr(addr);
                                if(amount > 0L)    {
                                    BIP47Meta.getInstance().addUnspent(pcode, idx);
                                }
                                else    {
                                    if(addrObj.has("pubkey"))    {
                                        String pubkey = addrObj.getString("pubkey");
                                        if(pubkeys.containsKey(pubkey))    {
                                            int count = pubkeys.get(pubkey);
                                            count++;
                                            if(count == 3)    {
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
                    String path = null;
                    String input_xpub = null;
                    String output_xpub = null;
                    long move_amount = 0L;
                    long input_amount = 0L;
                    long output_amount = 0L;
                    long bip47_input_amount = 0L;
                    long xpub_input_amount = 0L;
                    long change_output_amount = 0L;
                    boolean hasBIP47Input = false;
                    boolean hasOnlyBIP47Input = true;
                    boolean hasChangeOutput = false;

                    boolean useManualAmount = true;
                    long manual_amount = 0;


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
                        if(amount == 0)
                            useManualAmount = true;
                    } else useManualAmount = true;
                    if(txObj.has("time"))  {
                        ts = txObj.getLong("time");
                        if(ts == 0)
                            ts = new Date().getTime();
                    }

                    if(txObj.has("inputs"))  {
                        JSONArray inputArray = (JSONArray)txObj.get("inputs");
                        JSONObject inputObj = null;
                        for(int j = 0; j < inputArray.length(); j++)  {
                            inputObj = (JSONObject)inputArray.get(j);
                            if(true/*inputObj.has("prev_out")*/)  {
                                JSONObject prevOutObj = inputObj;//(JSONObject)inputObj.get("prev_out");
                                input_amount += prevOutObj.getLong("value");
                                if(prevOutObj.has("xpub"))  {
                                    JSONObject xpubObj = (JSONObject)prevOutObj.get("xpub");
                                    addr = xpubObj.has("m") ? (String)xpubObj.get("m") : xpub;
                                    input_xpub = addr;
                                    xpub_input_amount -= prevOutObj.getLong("value");
                                    hasOnlyBIP47Input = false;
                                    if(useManualAmount) manual_amount-=prevOutObj.getLong("value");
                                }
                                else if(prevOutObj.has("addr") && BIP47Meta.getInstance().getPCode4Addr((String)prevOutObj.get("addr")) != null)  {
                                    _addr = (String)prevOutObj.get("addr");
									hasBIP47Input = true;
                                    bip47_input_amount -= prevOutObj.getLong("value");	
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
                                output_amount += outObj.getLong("value");
                                JSONObject xpubObj = (JSONObject)outObj.get("xpub");
                                addr = xpubObj.has("m") ? (String)xpubObj.get("m") : xpub;
                                if(xpubObj.has("path"))
                                    path = xpubObj.getString("path");
                                if(useManualAmount) manual_amount+=outObj.getLong("value");
                                if(outObj.has("addr")) {
                                    _addr = (String) outObj.get("addr");
                                }
                                if(_addr != null  && path != null)
                                    xpub_pathaddressmap.put(_addr, path);
                            }
                            else  {
                                _addr = (String)outObj.get("addr");
                            }
                        }

                        //we are not including BIP47 with the query, so don't do this.
                        /*if(hasOnlyBIP47Input && !hasChangeOutput)    {
                            amount = bip47_input_amount;
                            manual_amount += bip47_input_amount;
                        }
                        else if(hasBIP47Input)    {
                            amount = bip47_input_amount + xpub_input_amount + change_output_amount;
                            manual_amount += bip47_input_amount;
                        }
                        else    {
                            ;
                        }*/

                    }

                    //for multiaddr
                    if(!txObj.has("out") && !txObj.has("inputs")) {
                        amount = txObj.getLong("change");
                        try {
                            String date = txObj.getString("time_utc");
                            date = date.replace('T', ' ');
                            date = date.substring(0, date.length()-1);
                            ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(date).getTime()/1000;
                        } catch (ParseException x) {
                            ts = 0;
                        }
                        //address
                        addr = addressesFound.get(i);
                        manual_amount += amount;
                    }

                    if(addr != null || _addr != null)  {

                        if(addr == null)    {
                            addr = _addr;
                        }

                        if(useManualAmount) {
                            amount = manual_amount;

                        }
                        xpub_balance += amount;

                        //String pmtcode = null;

                        Iterator<Tx> txs = xpub_txs.get(xpub).iterator();
                        boolean foundIt = false;
                        Tx thisTx = null;
                        while(txs.hasNext())
                        {
                            thisTx = txs.next();
                            if(thisTx.getHash().equals(hash))
                            {
                                foundIt = true;
                                //pmtcode = thisTx.getPaymentCode();
                                //txs.remove();
                                //Log.i("APIFactory", "deleting BIP47 transaction: "+ hash);
                                break;
                            }

                        }

                        if(foundIt) {
                            amount = (int) thisTx.getAmount() + amount;
                            txs.remove();
                        }
                        Tx tx = new Tx(hash, addr, amount, ts, (latest_block_height > 0L && height > 0L) ? (latest_block_height - height) + 1 : 0);
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
                            xpub_txs.get(AddressFactory.getInstance().account2xpub().get(0)).add(tx);
                        }

                        if(height > 0L)    {
                            RBFUtil.getInstance().remove(hash);
                        }

                    }
                }

            }

            try {
                PayloadUtil.getInstance(context).serializeMultiAddr(jsonObject);
            }
            catch(IOException | DecryptionException e) {
                ;
            }

            return;

        }

        return;

    }
    /*
        public synchronized JSONObject deleteXPUB(String xpub, boolean bip49) {

            String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

            JSONObject jsonObject  = null;

            try {

                String response = null;
                ECKey ecKey = null;

                if(AddressFactory.getInstance(context).xpub2account().get(xpub) != null || xpub.equals(BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr()))    {

                    HD_Address addr = null;
                    if(bip49)    {
                        addr = BIP49Util.getInstance(context).getWallet().getAccountAt(0).getChange().getAddressAt(0);
                    }
                    else    {
                        addr = HD_WalletFactory.getInstance(context).get().getAccount(0).getChain(AddressFactory.CHANGE_CHAIN).getAddressAt(0);
                    }
                    ecKey = addr.getECKey();

                    if(ecKey != null && ecKey.hasPrivKey())    {

                        String sig = ecKey.signMessage(xpub);
                        String address = null;
                        if(bip49)    {
                            SegwitAddress segwitAddress = new SegwitAddress(ecKey.getPubKey(), SamouraiWallet.getInstance().getCurrentNetworkParams());
                            address = segwitAddress.getAddressAsString();
                        }
                        else    {
                            address = ecKey.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                        }

                        if(!TorUtil.getInstance(context).statusFromBroadcast())    {
                            StringBuilder args = new StringBuilder();
                            args.append("message=");
                            args.append(xpub);
                            args.append("address=");
                            args.append(address);
                            args.append("&signature=");
                            args.append(Uri.encode(sig));
                            Log.i("APIFactory", "delete XPUB:" + args.toString());
                            response = WebUtil.getInstance(context).deleteURL(_url + "delete/" + xpub, args.toString());
                            Log.i("APIFactory", "delete XPUB response:" + response);
                        }
                        else    {
                            HashMap<String,String> args = new HashMap<String,String>();
                            args.put("message", xpub);
                            args.put("address", address);
                            args.put("signature", Uri.encode(sig));
                            Log.i("APIFactory", "delete XPUB:" + args.toString());
                            response = WebUtil.getInstance(context).tor_deleteURL(_url + "delete", args);
                            Log.i("APIFactory", "delete XPUB response:" + response);
                        }

                        try {
                            jsonObject = new JSONObject(response);

                            if(jsonObject.has("status") && jsonObject.getString("status").equals("ok"))    {
                                ;
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
    */
    public synchronized JSONObject lockXPUB(String xpub, int purpose) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {

            String response = null;
            ECKey ecKey = null;

            if(AddressFactory.getInstance(context).xpub2account().get(xpub) != null || xpub.equals(BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr()) || xpub.equals(BIP84Util.getInstance(context).getWallet().getAccount(0).zpubstr()))    {

                HD_Address addr = null;
                switch(purpose)    {
                    case 49:
                        addr = BIP49Util.getInstance(context).getWallet().getAccountAt(0).getChange().getAddressAt(0);
                        break;
                    case 84:
                        addr = BIP84Util.getInstance(context).getWallet().getAccountAt(0).getChange().getAddressAt(0);
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

                    if(!TorUtil.getInstance(context).statusFromBroadcast())    {
                        StringBuilder args = new StringBuilder();
                        args.append("address=");
                        args.append(address);
                        args.append("&signature=");
                        args.append(Uri.encode(sig));
                        args.append("&message=");
                        args.append("lock");
//                        Log.i("APIFactory", "lock XPUB:" + args.toString());
                        //response = WebUtil.getInstance(context).postURL(_url + "xpub/" + xpub + "/lock/", args.toString());
//                        Log.i("APIFactory", "lock XPUB response:" + response);
                    }
                    else    {
                        HashMap<String,String> args = new HashMap<String,String>();
                        args.put("address", address);
                        args.put("signature", Uri.encode(sig));
                        args.put("message", "lock");
//                        Log.i("APIFactory", "lock XPUB:" + args.toString());
                        //chainz does not do this
                        //response = WebUtil.getInstance(context).tor_postURL(_url + "xpub" + xpub + "/lock/", args);
//                        Log.i("APIFactory", "lock XPUB response:" + response);
                    }
                    //try {
/*                        jsonObject = new JSONObject(response);

                        if(jsonObject.has("status") && jsonObject.getString("status").equals("ok"))    {
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
*/
  //                  }
  //                  catch(JSONException je) {
  //                      je.printStackTrace();
  //                      jsonObject = null;
  //                  }

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

    public String getLatestBlockHash()  {
        return latest_block_hash;
    }

    public JSONObject getNotifTx(String hash, String addr) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {
            StringBuilder url = new StringBuilder(_url);
            url.append("txinfo&t=");
            url.append(hash);
            //url.append("?fees=1");
//            Log.i("APIFactory", "Notif tx:" + url.toString());
            String response = WebUtil.getInstance(null).getURL(url.toString());
//            Log.i("APIFactory", "Notif tx:" + response);
            try {
                jsonObject = new JSONObject(response);
                parseNotifTx(jsonObject, addr, hash);
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

    public JSONObject getNotifAddress(String addr) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {
            StringBuilder url = new StringBuilder(_url);
            url.append("multiaddr&active=");
            url.append(addr);
//            Log.i("APIFactory", "Notif address:" + url.toString());
            String response = WebUtil.getInstance(null).getURL(url.toString());
//            Log.i("APIFactory", "Notif address:" + response);
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

                //Chainz doesn't provide the block height with multiaddr
                /*if(!txObj.has("block_height") || (txObj.has("block_height") && txObj.getLong("block_height") < 1L))    {
                    return;
                }*/

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

    public void parseNotifTx(JSONObject jsonObject, String addr, String hash) throws JSONException  {

        Log.i("APIFactory", "notif address:" + addr);
        Log.i("APIFactory", "hash:" + hash);

        if(jsonObject != null)  {

            byte[] mask = null;
            byte[] payload = null;
            PaymentCode pcode = null;

            if(jsonObject.has("inputs"))    {

                JSONArray inArray = (JSONArray)jsonObject.get("inputs");

                if(inArray.length() > 0)    {
                    JSONObject objInput = (JSONObject)inArray.get(0);
                    byte[] pubkey = null;
                    JSONObject received_from = ((JSONObject) inArray.get(0)).getJSONObject("received_from");
                    String strScript = received_from.getString("script");

                    Log.i("APIFactory", "scriptsig:" + strScript);
                    if((strScript == null || strScript.length() == 0 || strScript.startsWith("160014")) && received_from.has("txinwitness"))    {
                        JSONArray witnessArray = (JSONArray)received_from.get("txinwitness");
                        if(witnessArray.length() == 2)    {
                            pubkey = Hex.decode((String)witnessArray.get(1));
                        }
                    }
                    else    {
                        Script script = new Script(Hex.decode(strScript));
                        Log.i("APIFactory", "pubkey from script:" + Hex.toHexString(script.getPubKey()));
                        pubkey = script.getPubKey();
                    }
                    ECKey pKey = new ECKey(null, pubkey, true);
                    Log.i("APIFactory", "address from script:" + pKey.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString());
//                        Log.i("APIFactory", "uncompressed public key from script:" + Hex.toHexString(pKey.decompress().getPubKey()));

                    if(((JSONObject)inArray.get(0)).has("received_from"))    {
                        received_from = ((JSONObject) inArray.get(0)).getJSONObject("received_from");

                        String strHash = received_from.getString("tx");
                        int idx = received_from.getInt("n");

                        byte[] hashBytes = Hex.decode(strHash);
                        Sha256Hash txHash = new Sha256Hash(hashBytes);
                        TransactionOutPoint outPoint = new TransactionOutPoint(SamouraiWallet.getInstance().getCurrentNetworkParams(), idx, txHash);
                        byte[] outpoint = outPoint.bitcoinSerialize();
                        Log.i("APIFactory", "outpoint:" + Hex.toHexString(outpoint));

                        try {
                            mask = BIP47Util.getInstance(context).getIncomingMask(pubkey, outpoint);
                            Log.i("APIFactory", "mask:" + Hex.toHexString(mask));
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
                    if(outObj.has("addr"))  {
                        _addr = outObj.getString("addr");
                        if(addr.equals(_addr))    {
                            isIncoming = true;
                        }
                    }
                    if(outObj.has("script"))  {
                        script = outObj.getString("script");
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
                    Log.i("APIFactory", "xlat_payload:" + Hex.toHexString(xlat_payload));

                    pcode = new PaymentCode(xlat_payload);
                    Log.i("APIFactory", "incoming payment code:" + pcode.toString());

                    if(!pcode.toString().equals(BIP47Util.getInstance(context).getPaymentCode().toString()) && pcode.isValid() && !BIP47Meta.getInstance().incomingExists(pcode.toString()))    {
                        BIP47Meta.getInstance().setLabel(pcode.toString(), "");
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
                    for(int i = 0; i < 3; i++)   {
                        Log.i("APIFactory", "receive from " + i + ":" + BIP47Util.getInstance(context).getReceivePubKey(pcode, i));
                        BIP47Meta.getInstance().setIncomingIdx(pcode.toString(), i, BIP47Util.getInstance(context).getReceivePubKey(pcode, i));
                        BIP47Meta.getInstance().getIdx4AddrLookup().put(BIP47Util.getInstance(context).getReceivePubKey(pcode, i), i);
                        BIP47Meta.getInstance().getPCode4AddrLookup().put(BIP47Util.getInstance(context).getReceivePubKey(pcode, i), pcode.toString());
                    }

                }
                catch(Exception e) {
                    ;
                }
            }

        }

    }

    public synchronized int getNotifTxConfirmations(String hash) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

//        Log.i("APIFactory", "Notif tx:" + hash);

        JSONObject jsonObject  = null;

        try {
            StringBuilder url = new StringBuilder(_url);
            url.append("txinfo&t=");
            url.append(hash);
            //url.append("?fees=1");
//            Log.i("APIFactory", "Notif tx:" + url.toString());
            String response = WebUtil.getInstance(null).getURL(url.toString());
//            Log.i("APIFactory", "Notif tx:" + response);
            jsonObject = new JSONObject(response);
//            Log.i("APIFactory", "Notif tx json:" + jsonObject.toString());

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

        if(jsonObject != null && jsonObject.has("block"))  {

            long latestBlockHeght = getLatestBlockHeight();
            long height = jsonObject.getLong("block");

            cf = (int)((latestBlockHeght - height) + 1);

            if(cf < 0)    {
                cf = 0;
            }

        }

        return cf;
    }

    public synchronized JSONObject getUnspentOutputs(String[] xpubs) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {

            String response = null;

            if(AppUtil.getInstance(context).isOfflineMode())    {
                response = PayloadUtil.getInstance(context).deserializeUTXO().toString();
            }
            else if(!TorUtil.getInstance(context).statusFromBroadcast())    {
                StringBuilder args = new StringBuilder();
                args.append("xpub=");
                args.append(StringUtils.join(xpubs, URLEncoder.encode("|", "UTF-8")));
                Log.d("APIFactory", "UTXO args:" + args.toString());
                response = WebUtil.getInstance(context).getURL(_url + "unspent&" + args.toString());
                Log.d("APIFactory", "UTXO:" + response);
            }
            else    {
                HashMap<String,String> args = new HashMap<String,String>();
                args.put("active", StringUtils.join(xpubs, "|"));
                response = WebUtil.getInstance(context).tor_getURL(_url + "unspent" + args.toString());
            }

            parseUnspentOutputs(response);

        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    public synchronized JSONObject getUnspentOutputs_chainz_pubkeys(String[] pubkeys) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {

            String response = null;
            final int MAX_PER_REQUESTS = 15;
            int numRequests = pubkeys.length / MAX_PER_REQUESTS + 1;

            //first get a list of pubkeys

            HashMap<String, String> mapAddressToPubkey = new HashMap<>(pubkeys.length * 2);
            StringBuilder addresses = new StringBuilder();
            String [] addressArray = new String[numRequests];
            for(int i = 0; i < numRequests; ++i)
                addressArray[i] = "";
            int count = 0;
            for(String pubkey : pubkeys) {
                ECKey key = ECKey.fromPublicOnly(Utils.HEX.decode(pubkey));
                String p2pkh = key.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toBase58();
                SegwitAddress segwitAddress = new SegwitAddress(key, SamouraiWallet.getInstance().getCurrentNetworkParams());
                String bech32 = segwitAddress.getBech32AsString();

                mapAddressToPubkey.put(p2pkh, pubkey);
                mapAddressToPubkey.put(bech32, pubkey);
                addresses.append(p2pkh).append("|").append(bech32).append("|");
                count++;
                addressArray[count / MAX_PER_REQUESTS] += p2pkh + "|" + bech32 + "|";//addresses.toString();
            }

            pubkeys = addressArray;

            for(String _addresses : addressArray) {
                if (AppUtil.getInstance(context).isOfflineMode()) {
                    response = PayloadUtil.getInstance(context).deserializeUTXO().toString();
                } else if (!TorUtil.getInstance(context).statusFromBroadcast()) {
                    StringBuilder args = new StringBuilder();
                    args.append("active=");
                    //ECKey key = ECKey.fromPublicOnly(Utils.HEX.decode(pubkey));
                    //String p2pkh = key.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toBase58();
                    //SegwitAddress segwitAddress = new SegwitAddress(key, SamouraiWallet.getInstance().getCurrentNetworkParams());
                    //String bech32 = segwitAddress.getBech32AsString();
                    //args.append(p2pkh);
                    //args.append("|");
                    //args.append(bech32);
                    //args.append(StringUtils.join(pubkey, URLEncoder.encode("|", "UTF-8")));
                    args.append(_addresses);
                    Log.d("APIFactory", "UTXO args:" + args.toString());
                    response = WebUtil.getInstance(context).getURL(_url + "unspent&" + args.toString());
                    Log.d("APIFactory", "UTXO:" + response);
                } else {
                    HashMap<String, String> args = new HashMap<String, String>();
                    args.put("active", StringUtils.join(pubkeys, "|"));
                    response = WebUtil.getInstance(context).tor_getURL(_url + "unspent" + args.toString());
                }
            }

            //process the results

                parseUnspentOutputs_chainz_pubkeys(mapAddressToPubkey, response);
            }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    private synchronized boolean parseUnspentOutputs(String unspents)   {

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

                    try {
                        String address = null;
                        if(Bech32Util.getInstance().isBech32Script(script))    {
                            address = Bech32Util.getInstance().getAddressFromScript(script);
                        }
                        else    {
                            address = new Script(scriptBytes).getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                        }

                        if(outDict.has("xpub"))    {
                            JSONObject xpubObj = (JSONObject)outDict.get("xpub");
                            String path = (String)xpubObj.get("path");
                            String m = (String)xpubObj.get("m");
                            unspentPaths.put(address, path);
                            if(m.equals(BIP49Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                unspentBIP49.put(address, 0);   // assume account 0
                            }
                            else if(m.equals(BIP84Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                unspentBIP84.put(address, 0);   // assume account 0
                            }
                            else    {
                                unspentAccounts.put(address, AddressFactory.getInstance(context).xpub2account().get(m));
                            }
                        }
                        else if(outDict.has("pubkey"))    {
                            int idx = BIP47Meta.getInstance().getIdx4AddrLookup().get(outDict.getString("pubkey"));
                            BIP47Meta.getInstance().getIdx4AddrLookup().put(address, idx);
                            String pcode = BIP47Meta.getInstance().getPCode4AddrLookup().get(outDict.getString("pubkey"));
                            BIP47Meta.getInstance().getPCode4AddrLookup().put(address, pcode);
                        }
                        else    {
                            ;
                        }

                        // Construct the output
                        MyTransactionOutPoint outPoint = new MyTransactionOutPoint(txHash, txOutputN, value, scriptBytes, address);
                        outPoint.setConfirmations(confirmations);

                        if(utxos.containsKey(script))    {
                            utxos.get(script).getOutpoints().add(outPoint);
                        }
                        else    {
                            UTXO utxo = new UTXO();
                            utxo.getOutpoints().add(outPoint);
                            utxos.put(script, utxo);
                        }

                        if(!BlockedUTXO.getInstance().contains(txHash.toString(), txOutputN))    {

                            if(Bech32Util.getInstance().isBech32Script(script))    {
                                UTXOFactory.getInstance().addP2WPKH(script, utxos.get(script));
                            }
                            else if(Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress())    {
                                UTXOFactory.getInstance().addP2SH_P2WPKH(script, utxos.get(script));
                            }
                            else    {
                                UTXOFactory.getInstance().addP2PKH(script, utxos.get(script));
                            }

                        }

                    }
                    catch(Exception e) {
                        ;
                    }

                }

                try {
                    PayloadUtil.getInstance(context).serializeUTXO(jsonObj);
                }
                catch(IOException | DecryptionException e) {
                    ;
                }

                return true;

            }
            catch(JSONException je) {
                ;
            }

        }

        return false;

    }

    private synchronized boolean parseUnspentOutputs_chainz_pubkeys(HashMap<String, String> mapAddresses, String unspents) {

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
                    int txOutputN = ((Number)outDict.get("tx_ouput_n")).intValue();
                    BigInteger value = new BigInteger(outDict.get("value").toString(), 10);
                    String script = (String)outDict.get("script");
                    byte[] scriptBytes = Hex.decode(script);
                    int confirmations = ((Number)outDict.get("confirmations")).intValue();

                    try {
                        String address = null;
                        if(outDict.has("addr")) {
                            try {
                                JSONObject addr = outDict.getJSONObject("addr");
                                if (addr.has("derived"))
                                    address = addr.getString("derived");
                            } catch (Exception e) {
                                //swallow, get address in the next section from the script
                            }
                        }

                        if(address == null) {
                            if(Bech32Util.getInstance().isBech32Script(script))    {
                                address = Bech32Util.getInstance().getAddressFromScript(script);
                            }
                            else    {
                                address = new Script(scriptBytes).getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                                /*if(address.startsWith("3") || address.startsWith("2"))
                                    last3address = address;
                                else if(address.startsWith("F") || address.startsWith("m") || address.startsWith("n") && last3address != null)
                                {
                                    //determine if the hash160 of this address matches the previous 3 address.
                                    Address Faddress = Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address);
                                    byte [] hash160_F = Faddress.getHash160();
                                    Address last3addr = Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), last3address);
                                    byte [] hash160_3 = last3addr.getHash160();
                                    if(Arrays.equals(hash160_F, hash160_3)) {
                                        address = last3address;
                                        scriptBytes = SegwitAddress.segWitOutputScript(address).getProgram();
                                    }
                                }*/
                            }
                        }

                        String pubkey = mapAddresses.get(address);

                        if(pubkey.length() == 66)    {
                            int idx = BIP47Meta.getInstance().getIdx4AddrLookup().get(pubkey);
                            BIP47Meta.getInstance().getIdx4AddrLookup().put(address, idx);
                            String pcode = BIP47Meta.getInstance().getPCode4AddrLookup().get(pubkey);
                            BIP47Meta.getInstance().getPCode4AddrLookup().put(address, pcode);
                        }

                        //xpub_balance += value.longValue();


                        // Construct the output
                        MyTransactionOutPoint outPoint = new MyTransactionOutPoint(txHash, txOutputN, value, scriptBytes, address);
                        outPoint.setConfirmations(confirmations);

                        if(utxos.containsKey(script))    {
                            utxos.get(script).getOutpoints().add(outPoint);
                        }
                        else    {
                            UTXO utxo = new UTXO();
                            utxo.getOutpoints().add(outPoint);
                            utxos.put(script, utxo);
                        }

                        if(!BlockedUTXO.getInstance().contains(txHash.toString(), txOutputN))    {

                            if(Bech32Util.getInstance().isBech32Script(script))    {
                                UTXOFactory.getInstance().addP2WPKH(script, utxos.get(script));
                            }
                            else if(Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress())    {
                                UTXOFactory.getInstance().addP2SH_P2WPKH(script, utxos.get(script));
                            }
                            else    {
                                UTXOFactory.getInstance().addP2PKH(script, utxos.get(script));
                            }

                        }

                    }
                    catch(Exception e) {
                        Log.i("APIFactory", "Exception: "+e.getMessage());
                    }

                }

                try {
                    PayloadUtil.getInstance(context).serializeUTXO(jsonObj);
                }
                catch(IOException | DecryptionException e) {
                    ;
                }

                return true;

            }
            catch(JSONException je) {
                ;
            }

        }

        return false;

    }



    public synchronized JSONObject getAddressInfo(String addr) {

        return getXPUB(new String[] { addr }, false);

    }

    public synchronized JSONObject getTxInfo(String hash) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {

            StringBuilder url = new StringBuilder(_url);
            url.append("txinfo&t=");
            url.append(hash);
            //url.append("?fees=true");

            String response = WebUtil.getInstance(context).getURL(url.toString());
            jsonObject = new JSONObject(response);
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    public synchronized JSONObject getBlockHeader(String hash) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {
            StringBuilder url = new StringBuilder(_url);
            url.append("header/");
            url.append(hash);

            String response = WebUtil.getInstance(context).getURL(url.toString());
            jsonObject = new JSONObject(response);
            jsonObject = (JSONObject)jsonObject.getJSONArray("addresses").get(0);
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    public synchronized JSONObject getDynamicFees() {

        JSONObject jsonObject  = null;
        /*
        try {
            int sel = PrefsUtil.getInstance(context).getValue(PrefsUtil.FEE_PROVIDER_SEL, 0);
            if(sel == 1)    {

                int[] blocks = new int[] { 2, 6, 24 };

                List<SuggestedFee> suggestedFees = new ArrayList<SuggestedFee>();

                JSONRPC jsonrpc = new JSONRPC(TrustedNodeUtil.getInstance().getUser(), TrustedNodeUtil.getInstance().getPassword(), TrustedNodeUtil.getInstance().getNode(), TrustedNodeUtil.getInstance().getPort());

                for(int i = 0; i < blocks.length; i++)   {
                    JSONObject feeObj = jsonrpc.getFeeEstimate(blocks[i]);
                    if(feeObj != null && feeObj.has("result"))    {
                        double fee = feeObj.getDouble("result");

                        SuggestedFee suggestedFee = new SuggestedFee();
                        suggestedFee.setDefaultPerKB(BigInteger.valueOf((long)(fee * 1e8)));
                        suggestedFee.setStressed(false);
                        suggestedFee.setOK(true);
                        suggestedFees.add(suggestedFee);
                    }
                }

                if(suggestedFees.size() > 0)    {
                    FeeUtil.getInstance().setEstimatedFees(suggestedFees);
                }

            }
            else    {
                String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;
//            Log.i("APIFactory", "Dynamic fees:" + url.toString());
                String response = null;
                if(!AppUtil.getInstance(context).isOfflineMode())    {
                    response = WebUtil.getInstance(null).getURL(_url + "fees");
                }
                else    {
                    response = PayloadUtil.getInstance(context).deserializeFees().toString();
                }
//            Log.i("APIFactory", "Dynamic fees response:" + response);
                try {
                    jsonObject = new JSONObject(response);
                    parseDynamicFees_bitcoind(jsonObject);
                }
                catch(JSONException je) {
                    je.printStackTrace();
                    jsonObject = null;
                }
            }
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }
*/
        return jsonObject;
    }

    private synchronized boolean parseDynamicFees_bitcoind(JSONObject jsonObject) throws JSONException  {

        if(jsonObject != null)  {

            //
            // bitcoind
            //
            List<SuggestedFee> suggestedFees = new ArrayList<SuggestedFee>();

            if(jsonObject.has("2"))    {
                long fee = jsonObject.getInt("2");
                SuggestedFee suggestedFee = new SuggestedFee();
                suggestedFee.setDefaultPerKB(BigInteger.valueOf(fee * 1000L));
                suggestedFee.setStressed(false);
                suggestedFee.setOK(true);
                suggestedFees.add(suggestedFee);
            }

            if(jsonObject.has("6"))    {
                long fee = jsonObject.getInt("6");
                SuggestedFee suggestedFee = new SuggestedFee();
                suggestedFee.setDefaultPerKB(BigInteger.valueOf(fee * 1000L));
                suggestedFee.setStressed(false);
                suggestedFee.setOK(true);
                suggestedFees.add(suggestedFee);
            }

            if(jsonObject.has("24"))    {
                long fee = jsonObject.getInt("24");
                SuggestedFee suggestedFee = new SuggestedFee();
                suggestedFee.setDefaultPerKB(BigInteger.valueOf(fee * 1000L));
                suggestedFee.setStressed(false);
                suggestedFee.setOK(true);
                suggestedFees.add(suggestedFee);
            }

            if(suggestedFees.size() > 0)    {
                FeeUtil.getInstance().setEstimatedFees(suggestedFees);

//                Log.d("APIFactory", "high fee:" + FeeUtil.getInstance().getHighFee().getDefaultPerKB().toString());
//                Log.d("APIFactory", "suggested fee:" + FeeUtil.getInstance().getSuggestedFee().getDefaultPerKB().toString());
//                Log.d("APIFactory", "low fee:" + FeeUtil.getInstance().getLowFee().getDefaultPerKB().toString());
            }

            try {
                PayloadUtil.getInstance(context).serializeFees(jsonObject);
            }
            catch(IOException | DecryptionException e) {
                ;
            }

            return true;

        }

        return false;

    }

    public synchronized void validateAPIThread() {

        final Handler handler = new Handler();

        new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();

                /*if(!AppUtil.getInstance(context).isOfflineMode()) {

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
                }*/

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

        Log.i("APIFactory", "initWallet()");

        initWalletAmounts();

    }

    private synchronized void initWalletAmounts() {

        APIFactory.getInstance(context).reset();

        List<String> addressStrings = new ArrayList<String>();
        String[] s = null;

        try {
            /*
            if(PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUB44REG, false) == false)    {
                registerXPUB(HD_WalletFactory.getInstance(context).get().getAccount(0).xpubstr(), false);
            }
            */
            if(PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUB49REG, false) == false)    {
                registerXPUB(BIP49Util.getInstance(context).getWallet().getAccount(0).xpubstr(), 49);
            }
            if(PrefsUtil.getInstance(context).getValue(PrefsUtil.XPUB84REG, false) == false)    {
                registerXPUB(BIP84Util.getInstance(context).getWallet().getAccount(0).xpubstr(), 84);
            }

            xpub_txs.put(HD_WalletFactory.getInstance(context).get().getAccount(0).xpubstr(), new ArrayList<Tx>());

            addressStrings.addAll(Arrays.asList(BIP47Meta.getInstance().getIncomingAddresses(false)));
            for(String _s : Arrays.asList(BIP47Meta.getInstance().getIncomingLookAhead(context)))   {
                if(!addressStrings.contains(_s))    {
                    addressStrings.add(_s);
                }
            }
            for(String pcode : BIP47Meta.getInstance().getUnspentProviders())   {
                for(String addr : BIP47Meta.getInstance().getUnspentAddresses(context, pcode))   {
                    if(!addressStrings.contains(addr))    {
                        addressStrings.add(addr);
                    }
                }
            }
            if(addressStrings.size() > 0)    {
                s = addressStrings.toArray(new String[0]);
//                Log.i("APIFactory", addressStrings.toString());
                getUnspentOutputs_chainz_pubkeys(s);
            }

            Log.d("APIFactory", "addresses:" + addressStrings.toString());

            HD_Wallet hdw = HD_WalletFactory.getInstance(context).get();
            if(hdw != null && hdw.getXPUBs() != null)    {
                String[] all = null;
                if(s != null && s.length > 0)    {
                    all = new String[hdw.getXPUBs().length + 2/* + s.length*/];
                    all[0] = BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr();
                    all[1] = BIP84Util.getInstance(context).getWallet().getAccount(0).zpubstr();
                    System.arraycopy(hdw.getXPUBs(), 0, all, 2, hdw.getXPUBs().length);
                    //System.arraycopy(s, 0, all, hdw.getXPUBs().length + 2, s.length);
                }
                else    {
                    all = new String[hdw.getXPUBs().length + 2];
                    all[0] = BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr();
                    all[1] = BIP84Util.getInstance(context).getWallet().getAccount(0).zpubstr();
                    System.arraycopy(hdw.getXPUBs(), 0, all, 2, hdw.getXPUBs().length);
                }
                APIFactory.getInstance(context).getBIP47(addressStrings.toArray(new String[0]), true, null);
                APIFactory.getInstance(context).getXPUB(all, true);
                String[] xs = new String[4];
                xs[0] = HD_WalletFactory.getInstance(context).get().getAccount(0).xpubstr();
                xs[1] = HD_WalletFactory.getInstance(context).get().getAccount(1).xpubstr();
                xs[2] = BIP49Util.getInstance(context).getWallet().getAccount(0).ypubstr();
                xs[3] = BIP84Util.getInstance(context).getWallet().getAccount(0).zpubstr();
                getUnspentOutputs_chainz(xs);
                getDynamicFees();
            }

            //
            //
            //
            List<String> seenOutputs = new ArrayList<String>();
            List<UTXO> _utxos = getUtxos(false);
            for(UTXO _u : _utxos)   {
                for(MyTransactionOutPoint _o : _u.getOutpoints())   {
                    seenOutputs.add(_o.getTxHash().toString() + "-" + _o.getTxOutputN());
                }
            }

            for(String _s : BlockedUTXO.getInstance().getNotDustedUTXO())   {
//                Log.d("APIFactory", "not dusted:" + _s);
                if(!seenOutputs.contains(_s))    {
                    BlockedUTXO.getInstance().removeNotDusted(_s);
//                    Log.d("APIFactory", "not dusted removed:" + _s);
                }
            }
            for(String _s : BlockedUTXO.getInstance().getBlockedUTXO().keySet())   {
//                Log.d("APIFactory", "blocked:" + _s);
                if(!seenOutputs.contains(_s))    {
                    BlockedUTXO.getInstance().remove(_s);
//                    Log.d("APIFactory", "blocked removed:" + _s);
                }
            }

        }
        catch (IndexOutOfBoundsException ioobe) {
            ioobe.printStackTrace();
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    public synchronized int syncBIP47Incoming(String[] addresses) {

        HashMap<String, String> mapAddressToPublicKey = new HashMap<>(addresses.length * 2);
        JSONObject jsonObject = getBIP47(addresses, false, mapAddressToPublicKey);
        Log.d("APIFactory", "sync BIP47 incoming:" + jsonObject.toString());
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
                            String pubkey = mapAddressToPublicKey.get(addr);
                            pcode = BIP47Meta.getInstance().getPCode4Addr(pubkey);
                            idx = BIP47Meta.getInstance().getIdx4Addr(pubkey);
                        }

                        if(addrObj.has("final_balance"))  {
                            amount = addrObj.getLong("final_balance");
                            if(amount > 0L)    {
                                BIP47Meta.getInstance().addUnspent(pcode, idx);
                                Log.i("APIFactory", "BIP47 incoming amount:" + idx + ", " + addr + ", " + amount);
                            }
                            else    {
                                if(addrObj.has("address") || mapAddressToPublicKey.containsKey(addrObj.getString("address")))    {
                                    String pubkey = mapAddressToPublicKey.get(addrObj.getString("address")); //addrObj.getString("pubkey");
                                    if(pubkeys.containsKey(pubkey))    {
                                        int count = pubkeys.get(pubkey);
                                        count++;
                                        if(count == 3)    {
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
                        }
                        if(addrObj.has("n_tx"))  {
                            nbTx = addrObj.getInt("n_tx");
                            if(nbTx > 0)    {
                                Log.i("APIFactory", "sync receive idx:" + idx + ", " + addr);
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

    public synchronized int syncBIP47Outgoing(String[] addresses) {

        HashMap<String, String> mapAddressToPublicKey = new HashMap<>(addresses.length * 2);

        JSONObject jsonObject = getBIP47(addresses, false, mapAddressToPublicKey);
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
                    if(addrObj.has("address"))  {
                        addr = (String)addrObj.get("address");
                        pcode = BIP47Meta.getInstance().getPCode4Addr(addr);
                        idx = BIP47Meta.getInstance().getIdx4Addr(addr);

                        if(addrObj.has("n_tx"))  {
                            nbTx = addrObj.getInt("n_tx");
                            if(nbTx > 0)    {
                                int stored = BIP47Meta.getInstance().getOutgoingIdx(pcode);
                                if(idx >= stored)    {
//                                        Log.i("APIFactory", "sync send idx:" + idx + ", " + addr);
                                    BIP47Meta.getInstance().setOutgoingIdx(pcode, idx + 1);
                                }
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

    public long getXpubBalance()  {
        return xpub_balance - BlockedUTXO.getInstance().getTotalValueBlocked();
    }

    public void setXpubBalance(long value)  {
        xpub_balance = value;
    }

    public HashMap<String,Long> getXpubAmounts()  {
        return xpub_amounts;
    }

    public HashMap<String,List<Tx>> getXpubTxs()  {
        return xpub_txs;
    }

    public HashMap<String, String> getUnspentPaths() {
        return unspentPaths;
    }

    public HashMap<String, Integer> getUnspentAccounts() {
        return unspentAccounts;
    }

    public HashMap<String, Integer> getUnspentBIP49() {
        return unspentBIP49;
    }

    public HashMap<String, Integer> getUnspentBIP84() {
        return unspentBIP84;
    }

    public List<UTXO> getUtxos(boolean filter) {

        List<UTXO> unspents = new ArrayList<UTXO>();

        if(filter)    {
            for(String key : utxos.keySet())   {
                UTXO u = new UTXO();
                for(MyTransactionOutPoint out : utxos.get(key).getOutpoints())    {
                    if(!BlockedUTXO.getInstance().contains(out.getTxHash().toString(), out.getTxOutputN()))    {
                        u.getOutpoints().add(out);
                    }
                }
                if(u.getOutpoints().size() > 0)    {
                    unspents.add(u);
                }
            }
        }
        else    {
            unspents.addAll(utxos.values());
        }

        return unspents;
    }

    public void setUtxos(HashMap<String, UTXO> utxos) {
        APIFactory.utxos = utxos;
    }

    public synchronized List<Tx> getAllXpubTxs()  {

        List<Tx> ret = new ArrayList<Tx>();
        for(String key : xpub_txs.keySet())  {
            List<Tx> txs = xpub_txs.get(key);
            for(Tx tx : txs)   {
                ret.add(tx);
            }
        }
        for(String key : bip47_txs.keySet())  {
            List<Tx> txs = bip47_txs.get(key);
            for(Tx tx : txs)   {
                ret.add(tx);
            }
        }

        HashMap<String, ArrayList<Tx>> mapTx = new HashMap<>();

        for(Tx tx: ret) {
            if(mapTx.containsKey(tx.getHash())) {
                mapTx.get(tx.getHash()).add(tx);
            } else {
                ArrayList<Tx> list = new ArrayList<Tx>();
                list.add(tx);
                mapTx.put(tx.getHash(), list);

            }
        }

        ret.clear();

        for(Map.Entry<String, ArrayList<Tx>> entry : mapTx.entrySet()) {
            String hash = entry.getKey();
            ArrayList<Tx> txs = entry.getValue();
            if(txs.size() == 1)
                ret.add(txs.get(0));
            else {
                long confirmations = 0;
                long date = txs.get(0).getTS();
                long blockHeight = txs.get(0).getBlockHeight();
                String address = txs.get(0).getAddress();
                double amount = 0;
                String pcode = null;
                for(Tx tx : txs) {
                    amount += tx.getAmount();
                    confirmations = Math.max(confirmations, tx.getConfirmations());
                    if(tx.getPaymentCode() != null) {
                        pcode = tx.getPaymentCode();
                    }
                }
                Tx combinedTx = new Tx(hash, address, amount, date, confirmations, pcode);
                ret.add(combinedTx);
            }
        }

        Collections.sort(ret, new TxMostRecentDateComparator());

        return ret;
    }

    public synchronized UTXO getUnspentOutputsForSweep(String address) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        try {

            String response = null;

            if(!TorUtil.getInstance(context).statusFromBroadcast())    {
                StringBuilder args = new StringBuilder();
                args.append("&active=");
                args.append(address);
//                Log.d("APIFactory", args.toString());
                response = WebUtil.getInstance(context).getURL(_url + "unspent"+ args.toString());
//                Log.d("APIFactory", response);
            }
            else    {
                HashMap<String,String> args = new HashMap<String,String>();
                args.put("active", address);
//                Log.d("APIFactory", args.toString());
                response = WebUtil.getInstance(context).tor_getURL(_url + "unspent" + args.toString());
//                Log.d("APIFactory", response);
            }

            return parseUnspentOutputsForSweep(response, address);

        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private synchronized UTXO parseUnspentOutputsForSweep(String unspents, String addressDestination)   {

        UTXO utxo = null;

        if(unspents != null)    {

            try {
                JSONObject jsonObj = new JSONObject(unspents);

                if(jsonObj == null || !jsonObj.has("unspent_outputs"))    {
                    return null;
                }
                JSONArray utxoArray = jsonObj.getJSONArray("unspent_outputs");
                if(utxoArray == null || utxoArray.length() == 0) {
                    return null;
                }

//            Log.d("APIFactory", "unspents found:" + outputsRoot.size());

                for (int i = 0; i < utxoArray.length(); i++) {

                    JSONObject outDict = utxoArray.getJSONObject(i);

                    byte[] hashBytes = Hex.decode((String)outDict.get("tx_hash"));
                    Sha256Hash txHash = Sha256Hash.wrap(hashBytes);
                    int txOutputN = ((Number)outDict.get("tx_ouput_n")).intValue();
                    BigInteger value = //BigInteger.valueOf(((Number)outDict.get("value")).longValue());
                                        new BigInteger(outDict.get("value").toString(), 10);
                    String script = (String)outDict.get("script");
                    byte[] scriptBytes = Hex.decode(script);
                    int confirmations = ((Number)outDict.get("confirmations")).intValue();

                    try {
                        String address = null;

                        if(Bech32Util.getInstance().isBech32Script(script) || addressDestination.toLowerCase().startsWith("grs") || addressDestination.toLowerCase().startsWith("tgrs"))    {
                            if(Bech32Util.getInstance().isBech32Script(script))
                                address = Bech32Util.getInstance().getAddressFromScript(script);
                            else {
                                address = addressDestination;
                                Pair<Byte, byte[]> pair = Bech32Segwit.decode(SamouraiWallet.getInstance().isTestNet() ? "tgrs" : "grs", address.toLowerCase());
                                scriptBytes = Bech32Segwit.getScriptPubkey(pair.getLeft(), pair.getRight());
                            }
                            Log.d("address parsed:", address);
                        }
                        else if(Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), addressDestination).isP2SHAddress()) {
                            address = addressDestination;
                            scriptBytes = SegwitAddress.segWitOutputScript(address).getProgram();
                        }
                        else {
                            address = new Script(scriptBytes).getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                        }

                        // Construct the output
                        MyTransactionOutPoint outPoint = new MyTransactionOutPoint(txHash, txOutputN, value, scriptBytes, address);
                        outPoint.setConfirmations(confirmations);
                        if(utxo == null)    {
                            utxo = new UTXO();
                        }
                        utxo.getOutpoints().add(outPoint);

                    }
                    catch(Exception e) {
                        ;
                    }

                }

            }
            catch(JSONException je) {
                ;
            }

        }

        return utxo;

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

    public synchronized JSONObject getUnspentOutputs_chainz(String[] xpubs) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        JSONObject jsonObject  = null;

        try {

            String [] responses = new String[xpubs.length];

            for(int i = 0; i < xpubs.length; ++i) {

                if (AppUtil.getInstance(context).isOfflineMode()) {
                    responses[i] = PayloadUtil.getInstance(context).deserializeUTXO().toString();
                } else if (!TorUtil.getInstance(context).statusFromBroadcast()) {
                    StringBuilder args = new StringBuilder();
                    //ArrayList<String> unspentAddresses = getXPUB_unspent_addresses(xpubs);
                    args.append("xpub=" + xpubs[i]);
                    //args.append(StringUtils.join(unspentAddresses, URLEncoder.encode("|", "UTF-8")));
                    Log.d("APIFactory", "UTXO args:" + args.toString());
                    responses[i] = WebUtil.getInstance(context).getURL(_url + "unspent&" + args.toString());
                    Log.d("APIFactory", "UTXO:" + responses[i]);
                } else {
                    HashMap<String, String> args = new HashMap<String, String>();
                    ArrayList<String> unspentAddresses = getXPUB_unspent_addresses(xpubs);
                    args.put("active", StringUtils.join(unspentAddresses, "|"));
                    responses[i] = WebUtil.getInstance(context).tor_getURL(_url + "unspent" + args.toString());
                }
            }

            parseUnspentOutputs_chainz(xpubs, responses);

        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

    private synchronized boolean parseUnspentOutputs_chainz(String [] xpubs, String [] unspents) {
        boolean success = true;
        for(int i = 0; i < unspents.length; ++i) {
            success = parseUnspentOutputs_chainz(xpubs[i], unspents[i]);
        }
        return success;
    }

    private synchronized boolean parseUnspentOutputs_chainz(String xpub, String unspents)   {

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

                String last3address = null;

                for (int i = 0; i < utxoArray.length(); i++) {

                    JSONObject outDict = utxoArray.getJSONObject(i);

                    byte[] hashBytes = Hex.decode((String)outDict.get("tx_hash"));
                    Sha256Hash txHash = Sha256Hash.wrap(hashBytes);
                    int txOutputN = ((Number)outDict.get("tx_ouput_n")).intValue();
                    BigInteger value = new BigInteger(outDict.get("value").toString(), 10);
                    String script = (String)outDict.get("script");
                    byte[] scriptBytes = Hex.decode(script);
                    int confirmations = ((Number)outDict.get("confirmations")).intValue();

                    try {
                        String address = null;
                        if(outDict.has("addr")) {
                            try {
                                JSONObject addr = outDict.getJSONObject("addr");
                                if (addr.has("derived"))
                                    address = addr.getString("derived");
                            } catch (Exception e) {
                                //swallow, get address in the next section from the script
                            }
                        }

                        if(address == null) {
                            if(Bech32Util.getInstance().isBech32Script(script))    {
                                address = Bech32Util.getInstance().getAddressFromScript(script);
                            }
                            else    {
                                address = new Script(scriptBytes).getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
                                /*if(address.startsWith("3") || address.startsWith("2"))
                                    last3address = address;
                                else if(address.startsWith("F") || address.startsWith("m") || address.startsWith("n") && last3address != null)
                                {
                                    //determine if the hash160 of this address matches the previous 3 address.
                                    Address Faddress = Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address);
                                    byte [] hash160_F = Faddress.getHash160();
                                    Address last3addr = Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), last3address);
                                    byte [] hash160_3 = last3addr.getHash160();
                                    if(Arrays.equals(hash160_F, hash160_3)) {
                                        address = last3address;
                                        scriptBytes = SegwitAddress.segWitOutputScript(address).getProgram();
                                    }
                                }*/
                            }
                        }

                        if(outDict.has("xpub"))    {
                            JSONObject xpubObj = (JSONObject)outDict.get("xpub");
                            String path = (String)xpubObj.get("path");
                            String m = (String)xpubObj.get("m");
                            unspentPaths.put(address, path);
                            if(m.equals(BIP49Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                unspentBIP49.put(address, 0);   // assume account 0
                            }
                            else if(m.equals(BIP84Util.getInstance(context).getWallet().getAccount(0).xpubstr()))    {
                                unspentBIP84.put(address, 0);   // assume account 0
                            }
                            else    {
                                unspentAccounts.put(address, AddressFactory.getInstance(context).xpub2account().get(m));
                            }
                        }
                        else if(xpub.length() == 66)    {
                            int idx = BIP47Meta.getInstance().getIdx4AddrLookup().get(xpub);
                            BIP47Meta.getInstance().getIdx4AddrLookup().put(address, idx);
                            String pcode = BIP47Meta.getInstance().getPCode4AddrLookup().get(xpub);
                            BIP47Meta.getInstance().getPCode4AddrLookup().put(address, pcode);
                        }
                        else    {
                            unspentPaths.put(address, xpub_pathaddressmap.get(address));
                            try {
                                Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address);
                                unspentAccounts.put(address, 0);
                            } catch (AddressFormatException x) {
                                String _script1 = outDict.getString("script");
                                //Script _script = new Script(Utils.HEX.decode(outDict.getString("script")));
                                if(Bech32Util.getInstance().isBech32Script(_script1)) {
                                    unspentBIP84.put(address, 0);
                                } else {
                                    unspentBIP49.put(address, 0);
                                }
                            }
                        }

                        // Construct the output
                        MyTransactionOutPoint outPoint = new MyTransactionOutPoint(txHash, txOutputN, value, scriptBytes, address);
                        outPoint.setConfirmations(confirmations);

                        if(utxos.containsKey(script))    {
                            utxos.get(script).getOutpoints().add(outPoint);
                        }
                        else    {
                            UTXO utxo = new UTXO();
                            utxo.getOutpoints().add(outPoint);
                            utxos.put(script, utxo);
                        }

                        if(!BlockedUTXO.getInstance().contains(txHash.toString(), txOutputN))    {

                            if(Bech32Util.getInstance().isBech32Script(script))    {
                                UTXOFactory.getInstance().addP2WPKH(script, utxos.get(script));
                            }
                            else if(Address.fromBase58(SamouraiWallet.getInstance().getCurrentNetworkParams(), address).isP2SHAddress())    {
                                UTXOFactory.getInstance().addP2SH_P2WPKH(script, utxos.get(script));
                            }
                            else    {
                                UTXOFactory.getInstance().addP2PKH(script, utxos.get(script));
                            }

                        }

                    }
                    catch(Exception e) {
                        Log.i("APIFactory", "Exception: "+e.getMessage());
                    }

                }

                try {
                    PayloadUtil.getInstance(context).serializeUTXO(jsonObj);
                }
                catch(IOException | DecryptionException e) {
                    ;
                }

                return true;

            }
            catch(JSONException je) {
                ;
            }

        }

        return false;

    }

    private synchronized ArrayList<String> getXPUB_unspent_addresses(String[] xpubs) {

        String _url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        org.json.JSONObject jsonObject  = null;
        ArrayList<String> addresses = null;

        for(int i = 0; i < xpubs.length; i++)   {
            try {
                StringBuilder url = new StringBuilder(_url);
                String response;
                boolean extendedKey = xpubs[i].startsWith("xpub") || xpubs[i].startsWith("ypub") || xpubs[i].startsWith("zpub") ||
                        xpubs[i].startsWith("tpub") || xpubs[i].startsWith("upub") || xpubs[i].startsWith("vpub");
                if(extendedKey) {
                    url.append("xpub2&xpub=");
                    url.append(xpubs[i]);
                    Log.i("APIFactory", "XPUB:" + url.toString());
                    response = WebUtil.getInstance(null).getURL(url.toString());
                    Log.i("APIFactory", "XPUB response:" + response);
                }
                else {
                    url.append("unspent&active=");
                    url.append(xpubs[i]);
                    Log.i("APIFactory", "MultiAddr:" + url.toString());
                    response = WebUtil.getInstance(null).getURL(url.toString());
                    Log.i("APIFactory", "MultiAddr response:" + response);
                }
                try {
                    jsonObject = new org.json.JSONObject(response);

                    ArrayList<String> theseaddresses;
                    if(extendedKey)
                        theseaddresses = parseXPUB_unspent_addresses(jsonObject, xpubs[i]);
                    else theseaddresses = parseAddress_unspent_addresses(jsonObject, xpubs[i]);

                    if(theseaddresses != null) {
                        if(addresses == null )
                            addresses = new ArrayList<String>();
                        addresses.addAll(theseaddresses);
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
        }

        return addresses;
    }
    private synchronized ArrayList<String> parseAddress_unspent_addresses(org.json.JSONObject jsonObject, String address) throws JSONException {

        if(jsonObject == null)
            return null;
        if(jsonObject.has("unspent_outputs"))
        {
            ArrayList<String> addresses = new ArrayList<String>();
            org.json.JSONArray unspent_outputs = jsonObject.getJSONArray("unspent_outputs");
            if(unspent_outputs.length() > 0) {
                addresses.add(address);
                return addresses;
            }
        }
        return null;
    }
    private synchronized ArrayList<String> parseXPUB_unspent_addresses(org.json.JSONObject jsonObject, String xpub) throws JSONException  {


        if(jsonObject != null)  {
            ArrayList<String> addresses = new ArrayList<String>();
/*

            if(jsonObject.has("wallet"))  {
                JSONObject walletObj = (JSONObject)jsonObject.get("wallet");
                if(walletObj.has("final_balance"))  {
                    xpub_balance = walletObj.getLong("final_balance");
                }
            }
*/
            long latest_block = 0L;

            if(jsonObject.has("info"))  {
                org.json.JSONObject infoObj = (org.json.JSONObject)jsonObject.get("info");
                if(infoObj.has("latest_block"))  {
                    org.json.JSONObject blockObj = (org.json.JSONObject)infoObj.get("latest_block");
                    if(blockObj.has("height"))  {
                        latest_block = blockObj.getLong("height");
                    }
                }
            }

            if(jsonObject.has("addresses"))  {

                JSONArray addressesArray = (JSONArray)jsonObject.get("addresses");
                org.json.JSONObject addrObj = null;
                for(int i = 0; i < addressesArray.length(); i++)  {
                    addrObj = (org.json.JSONObject)addressesArray.get(i);
                    if(i == 1 && addrObj.has("n_tx") && addrObj.getInt("n_tx") > 0)  {
                    }
                    if(addrObj.has("final_balance") && addrObj.has("address"))  {
                    }
                }
            }

            if(jsonObject.has("txs"))  {

                JSONArray txArray = (JSONArray)jsonObject.get("txs");
                org.json.JSONObject txObj = null;
                for(int i = 0; i < txArray.length(); i++)  {

                    txObj = (org.json.JSONObject)txArray.get(i);
                    long height = 0L;
                    long amount = 0L;
                    long ts = 0L;
                    String hash = null;
                    String addr = null;
                    String _addr = null;
                    String path = null;
                    String input_xpub = null;
                    String output_xpub = null;
                    long move_amount = 0L;
                    long input_amount = 0L;
                    long output_amount = 0L;
                    long bip47_input_amount = 0L;
                    long xpub_input_amount = 0L;
                    long change_output_amount = 0L;
                    boolean hasBIP47Input = false;
                    boolean hasOnlyBIP47Input = true;
                    boolean hasChangeOutput = false;

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
                        org.json.JSONObject inputObj = null;
                        for(int j = 0; j < inputArray.length(); j++)  {
                            inputObj = (org.json.JSONObject)inputArray.get(j);
                            if(inputObj.has("prev_out"))  {
                                org.json.JSONObject prevOutObj = (org.json.JSONObject)inputObj.get("prev_out");
                                input_amount += prevOutObj.getLong("value");
                                if(prevOutObj.has("xpub"))  {
                                    org.json.JSONObject xpubObj = (org.json.JSONObject)prevOutObj.get("xpub");
                                    addr = (String)xpubObj.get("m");
                                    input_xpub = addr;
                                    xpub_input_amount -= prevOutObj.getLong("value");
                                    hasOnlyBIP47Input = false;
                                }
                                else if(prevOutObj.has("addr") && BIP47Meta.getInstance().getPCode4Addr(prevOutObj.getString("addr")) != null)  {
                                    hasBIP47Input = true;
                                    bip47_input_amount -= prevOutObj.getLong("value");
                                }
                                else if(prevOutObj.has("addr") && BIP47Meta.getInstance().getPCode4Addr(prevOutObj.getString("addr")) == null)  {
                                    hasOnlyBIP47Input = false;
                                }
                                else  {
                                    _addr = (String)prevOutObj.get("addr");
                                }
                            }
                        }
                    }

                    if(txObj.has("out"))  {
                        JSONArray outArray = (JSONArray)txObj.get("out");
                        org.json.JSONObject outObj = null;
                        for(int j = 0; j < outArray.length(); j++)  {
                            outObj = (org.json.JSONObject)outArray.get(j);
                            output_amount += outObj.getLong("value");
                            if(outObj.has("xpub"))  {
                                org.json.JSONObject xpubObj = (org.json.JSONObject)outObj.get("xpub");
                                //addr = (String)xpubObj.get("m");
                                addr = xpub;
                                change_output_amount += outObj.getLong("value");
                                path = xpubObj.getString("path");
                                if(outObj.has("spent"))  {
                                    if(outObj.getBoolean("spent") == false && outObj.has("addr"))  {
                                        if(!addresses.contains(outObj.getString("addr")))
                                            addresses.add(outObj.getString("addr"));
                                        //froms.put(outObj.getString("addr"), path);
                                    }
                                }
                                if(input_xpub != null && !input_xpub.equals(addr))    {
                                    output_xpub = addr;
                                    move_amount = outObj.getLong("value");
                                }
                            }
                            else  {
                                _addr = (String)outObj.get("addr");
                            }
                        }

                        if(hasOnlyBIP47Input && !hasChangeOutput)    {
                            amount = bip47_input_amount;
                        }
                        else if(hasBIP47Input)    {
                            amount = bip47_input_amount + xpub_input_amount + change_output_amount;
                        }
                        else    {
                            ;
                        }

                    }

                    if(addr != null)  {

                        //
                        // test for MOVE from Shuffling -> Samourai account
                        //


                    }
                }

            }
            return addresses;
        }
        return null;
    }

    private synchronized void parseBIP47(JSONObject jsonObject, String address) throws JSONException {

        if (jsonObject != null) {

            String account0_xpub = null;
            try {
                account0_xpub = HD_WalletFactory.getInstance(context).get().getAccount(0).xpubstr();
            } catch (IOException ioe) {
                ;
            } catch (MnemonicException.MnemonicLengthException mle) {
                ;
            }

            if (jsonObject.has("wallet")) {
                JSONObject walletObj = (JSONObject) jsonObject.get("wallet");
                if (walletObj.has("final_balance")) {
                    //    bip47_balance += walletObj.getLong("final_balance");
                }

            }

            long latest_block = 0L;
            long manual_amount = 0;

            if (jsonObject.has("info")) {
                JSONObject infoObj = (JSONObject) jsonObject.get("info");
                if (infoObj.has("latest_block")) {
                    JSONObject blockObj = (JSONObject) infoObj.get("latest_block");
                    if (blockObj.has("height")) {
                        latest_block = blockObj.getLong("height");
                    }
                }
            }

            ArrayList<String> addressesFound = new ArrayList<>();


            if (jsonObject.has("addresses")) {
                JSONArray addressArray = (JSONArray) jsonObject.get("addresses");
                JSONObject addrObj = null;
                for (int i = 0; i < addressArray.length(); i++) {
                    addrObj = (JSONObject) addressArray.get(i);
                    if(addrObj.has("address")) {
                        int count = addrObj.has("n_tx") ? addrObj.getInt("n_tx") : 1;
                        for(int j = 0; j < count; ++j) {
                            addressesFound.add(addrObj.getString("address"));
                        }
                    }
                    long amount = 0L;
                    String addr = null;
                    if (addrObj.has("address")) {
                        addr = (String) addrObj.get("address");
                    }
                    if (addrObj.has("final_balance")) {
                        amount = addrObj.getLong("final_balance");

                        String pcode = BIP47Meta.getInstance().getPCode4Addr(addr);
                        if(pcode != null) {
                            int idx = BIP47Meta.getInstance().getIdx4Addr(addr);
                            if (amount > 0L) {
                                BIP47Meta.getInstance().addUnspent(pcode, idx);
                            } else {
                                BIP47Meta.getInstance().removeUnspent(pcode, Integer.valueOf(idx));
                            }
                            //bip47_balance += amount;
                        }
                    }
                    if (addr != null) {
                        bip47_amounts.put(addr, amount);
                    }
                }
            }

            if (jsonObject.has("txs")) {

                JSONArray txArray = (JSONArray) jsonObject.get("txs");
                JSONObject txObj = null;
                for (int i = 0; i < txArray.length(); i++) {

                    txObj = (JSONObject) txArray.get(i);
                    long height = 0L;
                    long confirmations = -1;
                    long amount = 0L;
                    long ts = 0L;
                    String hash = null;
                    String addr = null;
                    boolean hasBIP47Input = false;
                    boolean hasBIP47Output = false;
                    boolean manual_ammount = false;

                    if (txObj.has("block_height")) {
                        height = txObj.getLong("block_height");
                    } else {
                        height = -1L;  // 0 confirmations
                        if (txObj.has("confirmations")) {
                            confirmations = txObj.getLong("confirmations");
                        }
                    }
                    if (txObj.has("hash")) {
                        hash = (String) txObj.get("hash");
                    }
                    if (txObj.has("change")) {
                        amount = txObj.getLong("change");
                        if (amount == 0)
                            manual_ammount = true;
                    } else manual_ammount = true;
                    if (txObj.has("time")) {
                        ts = txObj.getLong("time");
                    } else if (txObj.has("time_utc")) {
                        String _ts = txObj.getString("time_utc");
                        ts = new Date().getTime();
                        try {
                            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss'Z'");
                            dateFormat.setTimeZone(getTimeZone("GMT"));
                            Date parsedDate = dateFormat.parse(_ts);
                            ts = parsedDate.getTime() / 1000;
                        } catch (Exception e) {//this generic but you can control another types of exception
                            try {
                                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm'Z'");
                                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                                Date parsedDate = dateFormat.parse(_ts);
                                ts = parsedDate.getTime() / 1000;
                            } catch (Exception e1) {
                                ts = new Date().getTime() / 1000;
                            }
                            //look the origin of excption
                        }
                    }
                    if (amount < 0) {
                        if (BIP47Meta.getInstance().getPCode4Addr(address) != null) {
                            addr = address;
                            //amount -= prevOutObj.getLong("value");
                            hasBIP47Input = true;
                        }
                    } else {
                        if (BIP47Meta.getInstance().getPCode4Addr(address) != null) {
//                                Log.i("APIFactory", "found output:" + outObj.getString("addr"));
                            addr = address;
                            //amount += outObj.getLong("value");
                            hasBIP47Output = true;
                        }
                    }

                    if (txObj.has("inputs")) {
                        JSONArray inputArray = (JSONArray) txObj.get("inputs");
                        JSONObject inputObj = null;
                        for (int j = 0; j < inputArray.length(); j++) {
                            inputObj = (JSONObject) inputArray.get(j);
                            if (inputObj.has("prev_out")) {
                                JSONObject prevOutObj = (JSONObject) inputObj.get("prev_out");
                                if (prevOutObj.has("addr") && BIP47Meta.getInstance().getPCode4Addr(prevOutObj.getString("addr")) != null) {
//                                    Log.i("APIFactory", "found input:" + prevOutObj.getString("addr"));
                                    addr = prevOutObj.getString("addr");
                                    amount -= prevOutObj.getLong("value");
                                    hasBIP47Input = true;
                                }
                            }
                        }
                    }

                    if (txObj.has("out")) {
                        JSONArray outArray = (JSONArray) txObj.get("out");
                        JSONObject outObj = null;
                        for (int j = 0; j < outArray.length(); j++) {
                            outObj = (JSONObject) outArray.get(j);
                            if (outObj.has("xpub")) {
                                JSONObject xpubObj = (JSONObject) outObj.get("xpub");
                                addr = (String) xpubObj.get("m");
                                String path = (String) xpubObj.get("path");
                                String[] s = path.split("/");
                                if (s[1].equals("1") && hasBIP47Input) {
                                    amount += outObj.getLong("value");
                                }
                                //
                                // collect unspent outputs for each xpub
                                // store path info in order to generate private key later on
                                //
                                if (outObj.has("spent")) {
                                    if (outObj.getBoolean("spent") == false && outObj.has("addr")) {
                             /*           if(!haveUnspentOuts.containsKey(addr))  {
                                            List<String> addrs = new ArrayList<String>();
                                            haveUnspentOuts.put(addr, addrs);
                                        }
                                        String data = path + "," + (String)outObj.get("addr");
                                        if(!haveUnspentOuts.get(addr).contains(data))  {
                                            haveUnspentOuts.get(addr).add(data);
                                        }
                                    }*/
                                    }
                                } else if (outObj.has("addr") && BIP47Meta.getInstance().getPCode4Addr(outObj.getString("addr")) != null) {
//                                Log.i("APIFactory", "found output:" + outObj.getString("addr"));
                                    addr = outObj.getString("addr");
                                    amount += outObj.getLong("value");
                                    hasBIP47Output = true;
                                } else {
                                    ;
                                }
                            }
                        }
                    }
                    //for multiaddr
                    if(!txObj.has("out") && !txObj.has("inputs")) {
                        amount = txObj.getLong("change");
                        if(amount < 0)
                            hasBIP47Output = true;
                        else hasBIP47Input = true;

                        //address
                        addr = addressesFound.get(i);
                        manual_amount += amount;
                    }


                    if (addr != null) {

                        Log.i("APIFactory", "found BIP47 tx, value:" + amount + "," + addr);

                        xpub_balance += amount;

                        if ((hasBIP47Output || hasBIP47Input) /*&& !seenBIP47Tx.containsKey(hash)*/) {
                            Tx tx = new Tx(hash, addr, amount, ts, confirmations);
                            if (!bip47_txs.containsKey(account0_xpub)) {
                                bip47_txs.put(account0_xpub, new ArrayList<Tx>());
                            }
                            if (hasBIP47Input || hasBIP47Output && (BIP47Meta.getInstance().getPCode4Addr(addr) != null)) {
                                tx.setPaymentCode(BIP47Meta.getInstance().getPCode4Addr(addr));
                            }
                            bip47_txs.get(account0_xpub).add(tx);
                            //                           seenBIP47Tx.put(hash, "");
                        } else {
                            ;
                        }

                    }

                }

            }

        }
    }

    private synchronized JSONObject getBIP47(String[] addresses, boolean simple, HashMap<String, String> mapAddressToPubkey) {

        JSONObject jsonObject  = null;

        final int MAX_COUNT = 15;
        int numRequests = addresses.length / MAX_COUNT + 1;


        boolean regularAddress = false;
        if(addresses.length == 0)
            return jsonObject;
        if(addresses[0].startsWith("F") || addresses[0].startsWith("m") || addresses[0].startsWith("2") || addresses[0].startsWith("3") || addresses[0].startsWith("n")) {
            regularAddress = true;
            StringBuilder _addresses = new StringBuilder();
            for(String xpub : addresses) {
                _addresses.append(xpub).append("|");
            }
            addresses = new String[1];
            addresses[0] = _addresses.substring(0, _addresses.length()-1);
        } else if(addresses[0].length() == 66) {
            if(mapAddressToPubkey == null)
                mapAddressToPubkey = new HashMap<>(addresses.length * 2);
            StringBuilder _addresses =  new StringBuilder();
            String [] addressArray = new String [numRequests];
            for (String addr : addressArray) {
                addr = "";
            }
            regularAddress = false;
            int count = 0;
            for(String pubkey : addresses) {
                ECKey key = ECKey.fromPublicOnly(Utils.HEX.decode(pubkey));
                String p2pkh = key.toAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toBase58();
                SegwitAddress segwitAddress = new SegwitAddress(key, SamouraiWallet.getInstance().getCurrentNetworkParams());
                String bech32 = segwitAddress.getBech32AsString();

                mapAddressToPubkey.put(p2pkh, pubkey);
                mapAddressToPubkey.put(bech32, pubkey);
                _addresses.append(p2pkh).append("|").append(bech32).append("|");
                count++;
                addressArray[count / MAX_COUNT] += p2pkh + "|" + bech32 + "|";//_addresses.toString();
            }
            addresses = addressArray;
        }

        //StringBuilder args = new StringBuilder();
        //args.append("&active=");
        //args.append(addresses[0]);

        String url = SamouraiWallet.getInstance().isTestNet() ? WebUtil.SAMOURAI_API2_TESTNET : WebUtil.SAMOURAI_API2;

        try {
            for(String address: addresses) {
                Log.i("APIFactory", "BIP47 multiaddr:" + address);
                String response = WebUtil.getInstance(null).getURL(url + "multiaddr&active=" + address);
                Log.i("APIFactory", "BIP47 multiaddr:" + response);
                jsonObject = new JSONObject(response);
                parseBIP47(jsonObject, address);
            }
        }
        catch(Exception e) {
            jsonObject = null;
            e.printStackTrace();
        }

        return jsonObject;
    }

}
