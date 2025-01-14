package com.samourai.wallet.send.boost

import android.util.Log
import android.widget.Toast
import com.samourai.wallet.R
import com.samourai.wallet.SamouraiActivity
import com.samourai.wallet.SamouraiWallet
import com.samourai.wallet.api.APIFactory
import com.samourai.wallet.constants.SamouraiAccountIndex
import com.samourai.wallet.constants.WALLET_INDEX
import com.samourai.wallet.hd.HD_WalletFactory
import com.samourai.wallet.segwit.BIP49Util
import com.samourai.wallet.segwit.BIP84Util
import com.samourai.wallet.segwit.bech32.Bech32Util
import com.samourai.wallet.send.FeeUtil
import com.samourai.wallet.send.MyTransactionOutPoint
import com.samourai.wallet.send.PushTx
import com.samourai.wallet.send.SendFactory
import com.samourai.wallet.send.UTXO
import com.samourai.wallet.send.UTXO.UTXOComparator
import com.samourai.wallet.service.WebSocketService
import com.samourai.wallet.util.PrefsUtil
import com.samourai.wallet.util.func.AddressFactory
import com.samourai.wallet.util.func.FormatsUtil
import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.script.Script
import org.bouncycastle.util.encoders.Hex
import org.json.JSONException
import java.math.BigInteger
import java.util.Collections
import java.util.Vector

/**
 * samourai-wallet-android
 *
 */
class CPFPTask(private val activity: SamouraiActivity, private val hash: String) {

    class CPFPException(message: String) : Exception(message)

    private var addr: String = ""
    val outPoints: MutableList<MyTransactionOutPoint> = ArrayList()
    val receivers = HashMap<String, BigInteger>()
    val utxos = if (activity.account == SamouraiAccountIndex.POSTMIX)
        APIFactory.getInstance(activity).getUtxosPostMix(true) else
            APIFactory.getInstance(activity).getUtxos(true)


    /**
     * Validates and prepares CPFP tx
     */
    fun checkCPFP(): String {
        val txObj = APIFactory.getInstance(activity).getTxInfo(hash)
        if (txObj.has("inputs") && txObj.has("outputs")) {
            val keepCurrentSuggestedFee = FeeUtil.getInstance().suggestedFee
            try {
                val inputs = txObj.getJSONArray("inputs")
                val outputs = txObj.getJSONArray("outputs")
                var p2pkh = 0
                var p2sh_p2wpkh = 0
                var p2wpkh = 0
                for (i in 0 until inputs.length()) {
                    if (inputs.getJSONObject(i).has("outpoint") && inputs.getJSONObject(i).getJSONObject("outpoint").has("scriptpubkey")) {
                        val scriptpubkey = inputs.getJSONObject(i).getJSONObject("outpoint").getString("scriptpubkey")
                        val script = Script(Hex.decode(scriptpubkey))
                        var address: String? = null
                        if (Bech32Util.getInstance().isBech32Script(scriptpubkey)) {
                            try {
                                address = Bech32Util.getInstance().getAddressFromScript(scriptpubkey)
                            } catch (e: Exception) {
                            }
                        } else {
                            address = script.getToAddress(SamouraiWallet.getInstance().currentNetworkParams).toString()
                        }
                        when {
                            FormatsUtil.getInstance().isValidBech32(address) -> {
                                p2wpkh++
                            }
                            Address.fromBase58(SamouraiWallet.getInstance().currentNetworkParams, address).isP2SHAddress -> {
                                p2sh_p2wpkh++
                            }
                            else -> {
                                p2pkh++
                            }
                        }
                    }
                }
                FeeUtil.getInstance().suggestedFee = FeeUtil.getInstance().highFee
                val estimatedFee = FeeUtil.getInstance().estimatedFeeSegwit(p2pkh, p2sh_p2wpkh, p2wpkh, outputs.length())
                var total_inputs = 0L
                var total_outputs = 0L
                var fee = 0L
                var utxo: UTXO? = null
                for (i in 0 until inputs.length()) {
                    val obj = inputs.getJSONObject(i)
                    if (obj.has("outpoint")) {
                        val objPrev = obj.getJSONObject("outpoint")
                        if (objPrev.has("value")) {
                            total_inputs += objPrev.getLong("value")
                        }
                    }
                }
                for (i in 0 until outputs.length()) {
                    val obj = outputs.getJSONObject(i)
                    if (obj.has("value") && obj.has("address")) {
                        total_outputs += obj.getLong("value")
                        val addr = obj.getString("address")
                        Log.d("CPFPTask", "checking address:$addr")
                        utxo = if (utxo == null) {
                            getUTXO(addr)
                        } else {
                            break
                        }
                    }
                }
                var feeWarning = false
                fee = total_inputs - total_outputs
                if (fee > estimatedFee.toLong()) {
                    feeWarning = true
                }
                Log.d("CPFPTask", "total inputs:$total_inputs")
                Log.d("CPFPTask", "total outputs:$total_outputs")
                Log.d("CPFPTask", "fee:$fee")
                Log.d("CPFPTask", "estimated fee:" + estimatedFee.toLong())
                Log.d("CPFPTask", "fee warning:$feeWarning")
                if (utxo != null) {
                    Log.d("CPFPTask", "utxo found")
                    val selectedUTXO: MutableList<UTXO> = ArrayList()
                    selectedUTXO.add(utxo)
                    var selected = utxo.outpoints.size
                    val remainingFee = if (estimatedFee.toLong() > fee) estimatedFee.toLong() - fee else 0L
                    Log.d("CPFPTask", "remaining fee:$remainingFee")
                    addr  = if (PrefsUtil.getInstance(activity).getValue(PrefsUtil.USE_LIKE_TYPED_CHANGE, true)) {
                        utxo.outpoints[0].address
                    } else {
                        outputs.getJSONObject(0).getString("address")
                    }

                    val walletIndex: WALLET_INDEX;
                    if (FormatsUtil.getInstance().isValidBech32(addr)) {
                        walletIndex = WALLET_INDEX.BIP84_RECEIVE;
                    } else if (Address.fromBase58(SamouraiWallet.getInstance().currentNetworkParams, addr).isP2SHAddress) {
                        walletIndex = WALLET_INDEX.BIP49_RECEIVE;
                    } else {
                        walletIndex = WALLET_INDEX.BIP44_RECEIVE;
                    }
                    val ownReceiveAddr = AddressFactory.getInstance(activity).getAddressAndIncrement(walletIndex).right

                    Log.d("CPFPTask", "receive address:$ownReceiveAddr")
                    var totalAmount = utxo.value
                    Log.d("CPFPTask", "amount before fee:$totalAmount")
                    var outpointTypes = FeeUtil.getInstance().getOutpointCount(Vector(utxo.outpoints))
                    var cpfpFee = FeeUtil.getInstance().estimatedFeeSegwit(outpointTypes.left, outpointTypes.middle, outpointTypes.right, 1)
                    Log.d("CPFPTask", "cpfp fee:" + cpfpFee.toLong())
                    p2pkh = outpointTypes.left
                    p2sh_p2wpkh = outpointTypes.middle
                    p2wpkh = outpointTypes.right
                    if (totalAmount < cpfpFee.toLong() + remainingFee) {
                        Log.d("CPFPTask", "selecting additional utxo")
                        Collections.sort(utxos, UTXOComparator())
                        for (_utxo in utxos) {
                            totalAmount += _utxo.value
                            selectedUTXO.add(_utxo)
                            selected += _utxo.outpoints.size
                            outpointTypes = FeeUtil.getInstance().getOutpointCount(Vector(utxo.outpoints))
                            p2pkh += outpointTypes.left
                            p2sh_p2wpkh += outpointTypes.middle
                            p2wpkh += outpointTypes.right
                            cpfpFee = FeeUtil.getInstance().estimatedFeeSegwit(p2pkh, p2sh_p2wpkh, p2wpkh, 1)
                            if (totalAmount > cpfpFee.toLong() + remainingFee + SamouraiWallet.bDust.toLong()) {
                                break
                            }
                        }
                        if (totalAmount < cpfpFee.toLong() + remainingFee + SamouraiWallet.bDust.toLong()) {
                            throw CPFPException(activity.getString(R.string.insufficient_funds))
                        }
                    }
                    cpfpFee = cpfpFee.add(BigInteger.valueOf(remainingFee))
                    Log.d("CPFPTask", "cpfp fee:" + cpfpFee.toLong())
                    for (u in selectedUTXO) {
                        outPoints.addAll(u.outpoints)
                    }
                    var _totalAmount = 0L
                    for (outpoint in outPoints) {
                        _totalAmount += outpoint.value.longValue()
                    }
                    Log.d("CPFPTask", "checked total amount:$_totalAmount")
                    assert(_totalAmount == totalAmount)
                    val amount = totalAmount - cpfpFee.toLong()
                    Log.d("CPFPTask", "amount after fee:$amount")
                    if (amount < SamouraiWallet.bDust.toLong()) {
                        Log.d("CPFPTask", "dust output")
                        Toast.makeText(activity, R.string.cannot_output_dust, Toast.LENGTH_SHORT).show()
                    }
                    receivers[ownReceiveAddr] = BigInteger.valueOf(amount)
                    var message = ""
                    if (feeWarning) {
                        message += activity.getString(R.string.fee_bump_not_necessary)
                        message += "\n\n"
                    }
                    Log.i("remainingFee", remainingFee.toString())
                    message += activity.getString(R.string.bump_fee).toString() + " " + Coin.valueOf(cpfpFee.toLong()).toPlainString() + " BTC"
                    return message;
                } else {
                    throw CPFPException(activity.getString(R.string.cannot_create_cpfp))
                }
            } catch (je: JSONException) {
                throw CPFPException("cpfp:" + je.message)
            } finally {
                FeeUtil.getInstance().suggestedFee = keepCurrentSuggestedFee
            }
        } else {
            throw CPFPException(activity.getString(R.string.cpfp_cannot_retrieve_tx))
        }
    }

    fun doCPFP(): Boolean {
        WebSocketService.restartService(activity)
        var tx = SendFactory.getInstance(activity).makeTransaction(outPoints, receivers)
        if (tx != null) {
            tx = SendFactory.getInstance(activity).signTransaction(tx, activity.account)
            val hexTx = String(Hex.encode(tx.bitcoinSerialize()))
            Log.d("CPFPTask", hexTx)
            val strTxHash = tx.hashAsString
            Log.d("CPFPTask", strTxHash)
            try {
                PushTx.getInstance(activity).pushTx(hexTx)
                return true
            } catch (e: Exception) {
                // reset receive index upon tx fail
                this.reset()
                return false
            }
        } else {
            throw  Exception("Unable to compose tx")
        }
    }


    fun reset() {
        try {
            when {
                Bech32Util.getInstance().isBech32Script(addr) -> {
                    val prevIdx = BIP84Util.getInstance(activity).wallet.getAccount(activity.account).receive.addrIdx - 1
                    BIP84Util.getInstance(activity).wallet.getAccount(activity.account).receive.addrIdx = prevIdx
                }
                Address.fromBase58(SamouraiWallet.getInstance().currentNetworkParams, addr).isP2SHAddress -> {
                    val prevIdx = BIP49Util.getInstance(activity).wallet.getAccount(activity.account).receive.addrIdx - 1
                    BIP49Util.getInstance(activity).wallet.getAccount(activity.account).receive.addrIdx = prevIdx
                }
                else -> {
                    val prevIdx = HD_WalletFactory.getInstance(activity).get().getAccount(activity.account).receive.addrIdx - 1
                    HD_WalletFactory.getInstance(activity).get().getAccount(activity.account).receive.addrIdx = prevIdx
                }
            }
        } catch (ex:Exception) {

        }
    }

    private fun getUTXO(address: String): UTXO? {
        var ret: UTXO? = null
        var idx = -1
        for (i in utxos.indices) {
            val utxo = utxos[i]
            Log.d("CPFPTask", "utxo address:" + utxo.outpoints[0].address)
            if (utxo.outpoints[0].address == address) {
                ret = utxo
                idx = i
                break
            }
        }
        if (ret != null) {
            utxos.removeAt(idx)
            return ret
        }
        return null
    }
}