package com.samourai.wallet.paynym.paynymDetails

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.samourai.http.client.AndroidHttpClient
import com.samourai.wallet.R
import com.samourai.wallet.SamouraiActivity
import com.samourai.wallet.SamouraiWallet
import com.samourai.wallet.SamouraiWalletConst
import com.samourai.wallet.access.AccessFactory
import com.samourai.wallet.api.APIFactory
import com.samourai.wallet.api.Tx
import com.samourai.wallet.api.fee.EnumFeeRepresentation
import com.samourai.wallet.bip47.BIP47Meta
import com.samourai.wallet.bip47.BIP47Util
import com.samourai.wallet.bip47.SendNotifTxFactory
import com.samourai.wallet.bip47.paynym.WebUtil
import com.samourai.wallet.bip47.rpc.PaymentCode
import com.samourai.wallet.bip47.rpc.SecretPoint
import com.samourai.wallet.crypto.DecryptionException
import com.samourai.wallet.databinding.ActivityPaynymDetailsBinding
import com.samourai.wallet.hd.HD_WalletFactory
import com.samourai.wallet.httpClient.IHttpClient
import com.samourai.wallet.payload.PayloadUtil
import com.samourai.wallet.paynym.PayNymViewModel
import com.samourai.wallet.paynym.fragments.EditPaynymBottomSheet
import com.samourai.wallet.paynym.fragments.ShowPayNymQRBottomSheet
import com.samourai.wallet.segwit.BIP49Util
import com.samourai.wallet.segwit.BIP84Util
import com.samourai.wallet.segwit.bech32.Bech32Util
import com.samourai.wallet.send.FeeUtil
import com.samourai.wallet.send.MyTransactionInput
import com.samourai.wallet.send.MyTransactionOutPoint
import com.samourai.wallet.send.PushTx
import com.samourai.wallet.send.SendActivity
import com.samourai.wallet.send.SendFactory
import com.samourai.wallet.send.SuggestedFee
import com.samourai.wallet.send.UTXO
import com.samourai.wallet.send.UTXO.UTXOComparator
import com.samourai.wallet.send.UTXOFactory
import com.samourai.wallet.util.CharSequenceX
import com.samourai.wallet.util.PrefsUtil
import com.samourai.wallet.util.func.AddressFactory
import com.samourai.wallet.util.func.FormatsUtil
import com.samourai.wallet.util.func.MonetaryUtil
import com.samourai.wallet.util.func.RBFFactory.createRBFSpendFromTx
import com.samourai.wallet.util.func.SentToFromBIP47Util
import com.samourai.wallet.util.func.synPayNym
import com.samourai.wallet.utxos.UTXOUtil
import com.samourai.wallet.widgets.ItemDividerDecorator
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils.isNotBlank
import org.bitcoinj.core.AddressFormatException
import org.bitcoinj.crypto.MnemonicException.MnemonicLengthException
import org.bitcoinj.script.Script
import org.bouncycastle.util.encoders.DecoderException
import org.bouncycastle.util.encoders.Hex
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.spec.InvalidKeySpecException
import java.util.Collections
import java.util.Objects.nonNull

class PayNymDetailsActivity : SamouraiActivity() {

    private var pcode: String? = null
    private var unregistered: Boolean = false
    private var label: String? = null
    private val txesList: MutableList<Tx> = ArrayList()
    private lateinit var paynymTxListAdapter: PaynymTxListAdapter
    private val disposables = CompositeDisposable()
    private var menu: Menu? = null
    private val payNymViewModel: PayNymViewModel by viewModels()
    private var following = false
    private val job = Job()
    private lateinit var binding: ActivityPaynymDetailsBinding
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var isPaynymRegistered = true
    private var claimed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPaynymDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarPaynym)
        window.statusBarColor = resources.getColor(R.color.grey_accent)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        binding.historyRecyclerView.isNestedScrollingEnabled = true
        if (intent.hasExtra("pcode")) {
            pcode = intent.getStringExtra("pcode")
        } else {
            finish()
        }
        unregistered = intent.getBooleanExtra("unregistered", false)
        if (intent.hasExtra("label")) {
            label = intent.getStringExtra("label")
        }
        claimed = PrefsUtil.getInstance(applicationContext).getValue(PrefsUtil.PAYNYM_CLAIMED, false)
        paynymTxListAdapter = PaynymTxListAdapter(txesList, this)
        binding.historyRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.historyRecyclerView.adapter = paynymTxListAdapter
        val drawable = ContextCompat.getDrawable(this, R.drawable.divider_grey)
        binding.historyRecyclerView.addItemDecoration(ItemDividerDecorator(drawable))
        setPayNym()
        loadTxes()
        payNymViewModel.followers.observe(this) {
            setPayNym()
        }
        payNymViewModel.errorsLiveData.observe(this) {
            Snackbar.make( binding.paynymCode, "$it", Snackbar.LENGTH_LONG).show()
        }
        payNymViewModel.loaderLiveData.observe(this) {
            binding.progressBar.visibility = if (it) View.VISIBLE else View.INVISIBLE
        }
        binding.followBtn.setOnClickListener { followPaynym() }

    }

    private fun setUnrigesteredPcode() {
        binding.followBtn.text = getString(R.string.connect)
        binding.feeMessage.text = getString(R.string.connect_paynym_fee)
        binding.followMessage.text = "${getString(R.string.blockchain_connect_with)} ${getLabel()} ${resources.getText(R.string.paynym_connect_message)}"
        following = true
        binding.paynymChipLayout.removeView(binding.paynymChipLayout.getChildAt(1))
    }

    private fun setPayNym() {
        binding.followMessage.text = resources.getString(R.string.follow) + " " + getLabel() + " " + resources.getText(R.string.paynym_follow_message_2).toString()
        if (BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_NOT_SENT) {
            showFollow()
        } else {
            if (isNotBlank(pcode) && !BIP47Meta.getInstance().isFollowing(pcode)) {
                val disposable = Observable.fromCallable<Boolean> {

                    runBlocking {
                        withContext(Dispatchers.IO) {
                            async {
                                payNymViewModel.doFollow(pcode!!)
                            }.await()
                            async {
                                payNymViewModel.getPayNymData()
                            }.await()
                        }
                    }
                    true
                }.subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { status: Boolean ->
                        Log.i(TAG, "auto follow an already connected PayNym")
                    }
                disposables.add(disposable)
                return
            }
            showHistory()
        }
        if (BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_SENT_NO_CFM) {
            showWaitingForConfirm()
        }
        if (BIP47Meta.getInstance().getIncomingIdx(pcode) >= 0) {
            binding.historyLayout!!.visibility = View.VISIBLE
        }
        if (BIP47Meta.getInstance().isFollowing(pcode) || !isPaynymRegistered || !claimed) {
            binding.followBtn.text = getString(R.string.connect)
            binding.feeMessage.text = getString(R.string.connect_paynym_fee)
            binding.followMessage.text = "${getString(R.string.blockchain_connect_with)} ${getLabel()} ${resources.getText(R.string.paynym_connect_message)}"
            if (!following)
                addChip(getString(R.string.following))
            following = true
        } else {
            if (!BIP47Meta.getInstance().exists(pcode, true)) {
                binding.feeMessage.text = getString(R.string.follow_paynym_fee_free)
            }
        }
        binding.paynymCode.text = BIP47Meta.getInstance().getAbbreviatedPcode(pcode)
        binding.txtviewPaynym.text = getName()
        binding.paynymAvatarProgress.visibility = View.VISIBLE

        if (!unregistered) {
            Picasso.get()
                .load(WebUtil.PAYNYM_API + pcode + "/avatar")
                .into( binding.userAvatar, object : Callback {
                    override fun onSuccess() {
                        binding.paynymAvatarProgress.visibility = View.GONE
                    }

                    override fun onError(e: Exception) {
                        binding.paynymAvatarProgress.visibility = View.GONE
                        Picasso.get()
                            .load(WebUtil.PAYNYM_API + "/preview/" + pcode)
                            .into( binding.userAvatar, object : Callback {
                                override fun onSuccess() {
                                    binding.paynymAvatarProgress.visibility = View.GONE
                                    isPaynymRegistered = false
                                    setUnrigesteredPcode()
                                }

                                override fun onError(e: Exception) {
                                    binding.paynymAvatarProgress.visibility = View.GONE
                                    Toast.makeText(this@PayNymDetailsActivity, "Unable to load avatar", Toast.LENGTH_SHORT).show()
                                }
                            })
                    }
                })
        } else {
            Picasso.get()
                .load(WebUtil.PAYNYM_API + "/preview/" + pcode)
                .into( binding.userAvatar, object : Callback {
                    override fun onSuccess() {
                        binding.paynymAvatarProgress.visibility = View.GONE
                        isPaynymRegistered = false
                        setUnrigesteredPcode()
                    }

                    override fun onError(e: Exception) {
                        binding.paynymAvatarProgress.visibility = View.GONE
                        Toast.makeText(this@PayNymDetailsActivity, "Unable to load avatar", Toast.LENGTH_SHORT).show()
                    }
                })
        }

        if (menu != null) {
            menu!!.findItem(R.id.retry_notiftx).isVisible = BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_SENT_NO_CFM
            menu!!.findItem(R.id.action_unfollow).isVisible = BIP47Meta.getInstance().exists(pcode, true)
        }
    }

    private fun addChip(chipText: String) {
        val scale = resources.displayMetrics.density

        if ( binding.paynymChipLayout.childCount == 2) {
            return
        }
        binding.paynymChipLayout.addView(Chip(this).apply {
            text = chipText
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply {
                        marginStart = (12 * scale + 0.5f).toInt()
                    }
            setChipBackgroundColorResource(R.color.white)
            setTextColor(ContextCompat.getColor(this@PayNymDetailsActivity, R.color.darkgrey))
        })
    }

    private fun showWaitingForConfirm() {
        binding.historyLayout.visibility = View.VISIBLE
        binding.feeMessage.visibility = View.GONE
        binding.followLayout.visibility = View.VISIBLE
        binding.confirmMessage.visibility = View.VISIBLE
        binding.followBtn.visibility = View.GONE
        binding.followMessage.visibility = View.GONE
    }

    private fun showHistory() {
        addChip("Connected")
        binding.historyLayout.visibility = View.VISIBLE
        binding.followLayout.visibility = View.GONE
        binding.confirmMessage.visibility = View.GONE
    }

    private fun showFollow() {
        binding.historyLayout.visibility = View.GONE
        binding.followBtn.visibility = View.VISIBLE
        binding.followLayout.visibility = View.VISIBLE
        binding.confirmMessage.visibility = View.GONE
        binding.followMessage.visibility = View.VISIBLE
    }

    private fun showFollowAlert(strAmount: String, onClickListener: View.OnClickListener?) {
        try {
            val dialog = Dialog(this, android.R.style.Theme_Dialog)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.paynym_follow_dialog)
            dialog.setCanceledOnTouchOutside(true)
            if (dialog.window != null) dialog.window!!.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val title = dialog.findViewById<TextView>(R.id.follow_title_paynym_dialog)
            val oneTimeFeeMessage = dialog.findViewById<TextView>(R.id.one_time_fee_message)
            title.text = ("Connect " + BIP47Meta.getInstance().getLabel(pcode))
            val followBtn = dialog.findViewById<Button>(R.id.follow_paynym_btn)
            val message = resources.getText(R.string.paynym_follow_fee_message).toString()
            val part1 = message.substring(0, 28)
            val part2 = message.substring(29)
            oneTimeFeeMessage.text = "$part1 $strAmount $part2"
            followBtn.setOnClickListener { view: View? ->
                dialog.dismiss()
                onClickListener?.onClick(view)
            }
            dialog.findViewById<View>(R.id.cancel_btn).setOnClickListener { view: View? -> dialog.dismiss() }
            dialog.setCanceledOnTouchOutside(false)
            dialog.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            dialog.show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun followPaynym() {
        val bip47 = BIP47Util.getInstance(this);
        if(bip47.paymentCode.toString() == pcode || bip47.featurePaymentCode.toString() == pcode){
            MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.app_name)
                    .setMessage(getString(R.string.you_cannot_follow_your_own_paynym))
                    .setPositiveButton(R.string.ok) { dialogInterface, _ -> dialogInterface.dismiss() }
                    .show()
            return
        }
        if (BIP47Meta.getInstance().isFollowing(pcode) || !isPaynymRegistered || !claimed) {
                doNotifTx()
        } else {
            MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.confirm)
                    .setMessage("Are you sure want to follow ${getLabel()} ?")
                    .setPositiveButton("Yes") { _, _ ->
                        pcode?.let {
                            pcode?.let {

                                val disposable = Observable.fromCallable<Boolean> {

                                    runBlocking {
                                        withContext(Dispatchers.IO) {
                                            async {
                                                payNymViewModel.doFollow(it)
                                            }.await()
                                        }
                                    }
                                    true
                                }.subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe { status: Boolean ->
                                        Log.i(TAG, "follow a PayNym")
                                    }
                                disposables.add(disposable)

                            }
                        }
                    }
                    .setNegativeButton("No") { _, _ -> }
                    .show()
        }

    }

    private fun getLabel(): String {
        return if (label == null) BIP47Meta.getInstance().getDisplayLabel(pcode) else label!!
    }

    private fun getName(): String {
        if (pcode == BIP47Meta.strSamouraiDonationPCode && SamouraiWallet.getInstance().isTestNet) return "+ashigarutest"
        if (pcode == BIP47Meta.strSamouraiDonationPCode && !SamouraiWallet.getInstance().isTestNet) return "+ashigaru"
        return BIP47Meta.getInstance().getName(pcode)
    }

    private fun loadTxes() {
        val disposable = Observable.fromCallable<List<Tx>> {
            val txesListSelected: MutableList<Tx> = ArrayList()
            val txs = APIFactory.getInstance(this).allXpubTxs
            APIFactory.getInstance(applicationContext).xpubAmounts[HD_WalletFactory.getInstance(applicationContext).get().getAccount(0).xpubstr()]
            if (txs != null) for (tx: Tx in txs) {
                if (tx.paymentCode != null) {
                    if ((tx.paymentCode == pcode)) {
                        txesListSelected.add(tx)
                    }
                }
                val hashes = SentToFromBIP47Util.getInstance()[pcode]
                if (hashes != null) for (hash: String in hashes) {
                    if ((hash == tx.hash)) {
                        if (!txesListSelected.contains(tx)) {
                            txesListSelected.add(tx)
                        }
                    }
                }
            }
            txesListSelected
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { txes: List<Tx>? ->
                    txesList.clear()
                    txesList.addAll((txes)!!)
                    paynymTxListAdapter.notifyDataSetChanged()
                }
        disposables.add(disposable)
    }

    override fun onDestroy() {
        disposables.dispose()
        try {
            PayloadUtil.getInstance(applicationContext).saveWalletToJSON(CharSequenceX(AccessFactory.getInstance(applicationContext).guid + AccessFactory.getInstance(applicationContext).pin))
        } catch (e: MnemonicLengthException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: JSONException) {
            e.printStackTrace()
        } catch (e: DecryptionException) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                supportFinishAfterTransition()
            }
            R.id.send_menu_paynym_details -> {
                if (BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_SENT_CFM) {
                    val intent = Intent(this, SendActivity::class.java)
                    intent.putExtra("pcode", pcode)
                    startActivity(intent)
                } else {
                    if (BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_NOT_SENT) {
                        followPaynym()
                    } else {
                        Snackbar.make(findViewById(android.R.id.content), "Follow transaction is still pending", Snackbar.LENGTH_SHORT).show()
                    }
                }
            }
            R.id.edit_menu_paynym_details -> {
                val bundle = Bundle()
                bundle.putString("label", getLabel())
                bundle.putString("nymName", getName())
                bundle.putString("pcode", pcode)
                bundle.putString("buttonText", "Save")
                val editPaynymBottomSheet = EditPaynymBottomSheet()
                editPaynymBottomSheet.arguments = bundle
                editPaynymBottomSheet.show(supportFragmentManager, editPaynymBottomSheet.tag)
                editPaynymBottomSheet.setSaveButtonListener { view: View? ->
                    updatePaynym(editPaynymBottomSheet.label, editPaynymBottomSheet.pcode)
                    setPayNym()
                }
            }
            R.id.archive_paynym -> {
                BIP47Meta.getInstance().setArchived(pcode, true)
                finish()
            }
            R.id.resync_menu_paynym_details -> {
                doSync()
            }
            R.id.view_code_paynym_details -> {
                val bundle = Bundle()
                bundle.putString("pcode", pcode)
                val showPayNymQRBottomSheet = ShowPayNymQRBottomSheet()
                showPayNymQRBottomSheet.arguments = bundle
                showPayNymQRBottomSheet.show(supportFragmentManager, showPayNymQRBottomSheet.tag)
            }
            R.id.retry_notiftx -> {
                doNotifTx()
            }
            R.id.action_unfollow -> {
                MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.confirm)
                        .setMessage("Are you sure want to unfollow ${getLabel()} ?")
                        .setPositiveButton("Yes") { _, _ ->
                            pcode?.let {
                                payNymViewModel.doUnFollow(it).invokeOnCompletion {
                                    finish()
                                }
                            }
                        }
                        .setNegativeButton("No", { _, _ -> })
                        .show()
            }
            R.id.paynym_indexes -> {
                PayloadUtil.getInstance(this@PayNymDetailsActivity).getPaynymsFromBackupFile();
                val outgoing = BIP47Meta.getInstance().getOutgoingIdx(pcode)
                val incoming = BIP47Meta.getInstance().getIncomingIdx(pcode)
                Toast.makeText(this@PayNymDetailsActivity, "Incoming index:$incoming, Outgoing index:$outgoing", Toast.LENGTH_SHORT).show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updatePaynym(label: String?, pcode: String?) {
        if (pcode == null || pcode.isEmpty() || !FormatsUtil.getInstance().isValidPaymentCode(pcode)) {
            Snackbar.make(findViewById(android.R.id.content), R.string.invalid_payment_code, Snackbar.LENGTH_SHORT).show()
        } else if (label == null || label.isEmpty()) {
            Snackbar.make(findViewById(android.R.id.content), R.string.bip47_no_label_error, Snackbar.LENGTH_SHORT).show()
        } else {
            BIP47Meta.getInstance().setLabel(pcode, label)
            scope.launch(Dispatchers.IO) {
                try {
                    PayloadUtil.getInstance(this@PayNymDetailsActivity).saveWalletToJSON(CharSequenceX(AccessFactory.getInstance(this@PayNymDetailsActivity).guid + AccessFactory.getInstance().pin))
                } catch (mle: MnemonicLengthException) {
                    mle.printStackTrace()
                    Toast.makeText(this@PayNymDetailsActivity, R.string.decryption_error, Toast.LENGTH_SHORT).show()
                } catch (de: DecoderException) {
                    de.printStackTrace()
                    Toast.makeText(this@PayNymDetailsActivity, R.string.decryption_error, Toast.LENGTH_SHORT).show()
                } catch (je: JSONException) {
                    je.printStackTrace()
                    Toast.makeText(this@PayNymDetailsActivity, R.string.decryption_error, Toast.LENGTH_SHORT).show()
                } catch (ioe: IOException) {
                    ioe.printStackTrace()
                    Toast.makeText(this@PayNymDetailsActivity, R.string.decryption_error, Toast.LENGTH_SHORT).show()
                } catch (npe: NullPointerException) {
                    npe.printStackTrace()
                    Toast.makeText(this@PayNymDetailsActivity, R.string.decryption_error, Toast.LENGTH_SHORT).show()
                } catch (de: DecryptionException) {
                    de.printStackTrace()
                    Toast.makeText(this@PayNymDetailsActivity, R.string.decryption_error, Toast.LENGTH_SHORT).show()
                }
                scope.launch(Dispatchers.Main) {
                    doUpdatePayNymInfo(pcode)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.paynym_details_menu, menu)
        if (pcode != null) {
            menu.findItem(R.id.retry_notiftx).isVisible = BIP47Meta.getInstance().getOutgoingStatus(pcode) == BIP47Meta.STATUS_SENT_NO_CFM
            menu.findItem(R.id.retry_notiftx).isVisible = BIP47Meta.getInstance().exists(pcode, true)
        }
        this.menu = menu
        return super.onCreateOptionsMenu(menu)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_PCODE) {
            setPayNym()
        }
    }

    private fun doNotifTx() {
        binding.progressBar.visibility = View.VISIBLE
        scope.launch(Dispatchers.IO) {
            try {
                doNotifyTxAsync()
            } catch (t : Throwable) {
                manageException(t)
            }
        }
        return
    }

    private fun doNotifyTxAsync() {

        val selectedUTXO: MutableList<UTXO?> = ArrayList()
        var totalValueSelected = 0L
        val fee: BigInteger?

        // spend dust threshold amount to notification address
        val amount = SendNotifTxFactory._bNotifTxValue.toLong()

        // add Ashigaru fee to total amount
        //amount += SendNotifTxFactory._bSWFee.toLong()

        // get unspents
        var utxos: MutableList<UTXO?>?
        if (UTXOFactory.getInstance().totalP2SH_P2WPKH > amount + FeeUtil.getInstance()
                .estimatedFeeSegwit(0, 1, 0, 4).toLong()
        ) {
            utxos = ArrayList()
            utxos.addAll(
                APIFactory.getInstance(this@PayNymDetailsActivity).getUtxosP2SH_P2WPKH(true)
            )
        } else {
            utxos = APIFactory.getInstance(this@PayNymDetailsActivity).getUtxos(true)
        }

        // sort in ascending order by value
        val _utxos: List<UTXO?>? = utxos
        Collections.sort(_utxos, UTXOComparator())
        Collections.reverse(_utxos)

        // get smallest 1 UTXO > than spend + fee + sw fee + dust
        for (u in _utxos!!) {
            if (u!!.value >= amount + SamouraiWallet.bDust.toLong() + FeeUtil.getInstance()
                    .estimatedFee(1, 4).toLong()
            ) {

                selectedUTXO.add(u)
                totalValueSelected += u.value
                Log.d("PayNymDetailsActivity", "value selected:" + u.value)
                Log.d("PayNymDetailsActivity", "total value selected:$totalValueSelected")
                Log.d("PayNymDetailsActivity", "nb inputs:" + u.outpoints.size)
                break
            }
        }

        val outputCountForFeeEstimation =
            if (FeeUtil.getInstance().feeRepresentation === EnumFeeRepresentation.BLOCK_COUNT) 6
            else 3

        val keepCurrentSuggestedFee = FeeUtil.getInstance().suggestedFee
        try {
            if (FeeUtil.getInstance().feeRepresentation === EnumFeeRepresentation.NEXT_BLOCK_RATE) {
                FeeUtil.getInstance().suggestedFee = FeeUtil.getInstance().highFee
            } else {

                val lo = FeeUtil.getInstance().lowFee.defaultPerKB.toLong() / 1000L
                val mi = FeeUtil.getInstance().normalFee.defaultPerKB.toLong() / 1000L
                val hi = FeeUtil.getInstance().highFee.defaultPerKB.toLong() / 1000L
                if (lo == mi && mi == hi) {
                    val hi_sf = SuggestedFee()
                    hi_sf.defaultPerKB = BigInteger.valueOf((hi * 1000.0 * 1.15).toLong())
                    FeeUtil.getInstance().suggestedFee = hi_sf
                } else if (lo == mi) {
                    FeeUtil.getInstance().suggestedFee = FeeUtil.getInstance().highFee
                } else {
                    FeeUtil.getInstance().suggestedFee = FeeUtil.getInstance().normalFee
                }
            }
            if (selectedUTXO.size == 0) {
                // sort in descending order by value
                Collections.sort(_utxos, UTXOComparator())
                var selected = 0

                // get largest UTXOs > than spend + fee + dust
                for (u in _utxos) {
                    selectedUTXO.add(u)
                    totalValueSelected += u!!.value
                    selected += u.outpoints.size
                    if (totalValueSelected >= amount + SamouraiWallet.bDust.toLong() + FeeUtil.getInstance()
                            .estimatedFee(selected, 4).toLong()
                    ) {
                        Log.d("PayNymDetailsActivity", "multiple outputs")
                        Log.d("PayNymDetailsActivity", "total value selected:$totalValueSelected")
                        Log.d("PayNymDetailsActivity", "nb inputs:" + u.outpoints.size)
                        break
                    }
                }

                fee = FeeUtil.getInstance().estimatedFee(selected, outputCountForFeeEstimation)
            } else {
                fee = FeeUtil.getInstance().estimatedFee(1, outputCountForFeeEstimation)
            }
        } catch (e: Exception) {
            return
        } finally {
            FeeUtil.getInstance().suggestedFee = keepCurrentSuggestedFee
        }

        //
        // total amount to spend including fee
        //
        val toSpent = amount + fee!!.toLong()
        val balance = APIFactory.getInstance(this@PayNymDetailsActivity).xpubBalance
        if (toSpent >= balance || toSpent >= totalValueSelected) {
            scope.launch(Dispatchers.Main) {
                binding.progressBar.visibility = View.INVISIBLE
                var message: String? =
                    getText(R.string.bip47_notif_tx_insufficient_funds_1).toString() + " "
                val biAmount = BigInteger.valueOf(toSpent)
                val strAmount = FormatsUtil.formatBTC(biAmount.toLong());
                message += strAmount
                message += " " + getText(R.string.bip47_notif_tx_insufficient_funds_2)
                val dlg = MaterialAlertDialogBuilder(this@PayNymDetailsActivity)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton(R.string.close) { _, _ -> }
                if (!isFinishing) {
                    dlg.show()
                }
            }
            return
        }

        //
        // payment code to be notified
        //
        val payment_code: PaymentCode?
        payment_code = try {
            PaymentCode(pcode)
        } catch (afe: AddressFormatException) {
            null
        }
        if (payment_code == null) {
            return
        }

        //
        // create outpoints for spend later
        //
        val outpoints: MutableList<MyTransactionOutPoint> = ArrayList()
        for (u in selectedUTXO) {
            outpoints.addAll(u!!.outpoints)
        }
        //
        // create inputs from outpoints
        //
        val inputs: MutableList<MyTransactionInput> = ArrayList()
        val currentNetworkParams = SamouraiWallet.getInstance().currentNetworkParams
        for (o in outpoints) {
            val script = Script(o.scriptBytes)
            if (script.scriptType == Script.ScriptType.NO_TYPE) {
                continue
            }
            val input = MyTransactionInput(
                currentNetworkParams,
                null,
                ByteArray(0),
                o,
                o.txHash.toString(),
                o.txOutputN
            )
            inputs.add(input)
        }
        //
        // sort inputs
        //
        Collections.sort(inputs, SendFactory.BIP69InputComparator())
        //
        // find outpoint that corresponds to 0th input
        //
        var outPoint: MyTransactionOutPoint? = null
        for (o in outpoints) {
            if (o.txHash.toString() == inputs[0].getTxHash() && o.txOutputN == inputs[0].getTxPos()) {
                outPoint = o
                break
            }
        }
        if (outPoint == null) {
            throw Exception(getString(R.string.bip47_cannot_identify_outpoint))
        }
        var op_return: ByteArray? = null
        //
        // get private key corresponding to outpoint
        //
        try {
    //            Script inputScript = new Script(outPoint.getConnectedPubKeyScript());
            val scriptBytes = outPoint?.connectedPubKeyScript
            var address: String?
            address = if (Bech32Util.getInstance().isBech32Script(Hex.toHexString(scriptBytes))) {
                Bech32Util.getInstance().getAddressFromScript(Hex.toHexString(scriptBytes))
            } else {
                Script(scriptBytes).getToAddress(currentNetworkParams).toString()
            }
            //            String address = inputScript.getToAddress(SamouraiWallet.getInstance().getCurrentNetworkParams()).toString();
            val ecKey = SendFactory.getPrivKey(address, 0)
            if (ecKey == null || !ecKey.hasPrivKey()) {
                throw Exception(getString(R.string.bip47_cannot_compose_notif_tx))
            }

            //
            // use outpoint for payload masking
            //
            val privkey = ecKey.privKeyBytes
            val pubkey = payment_code.notificationAddress(currentNetworkParams).pubKey
            val outpoint = outPoint?.bitcoinSerialize()
            //                Log.i("PayNymDetailsActivity", "outpoint:" + Hex.toHexString(outpoint));
    //                Log.i("PayNymDetailsActivity", "payer shared secret:" + Hex.toHexString(new SecretPoint(privkey, pubkey).ECDHSecretAsBytes()));
            val mask =
                PaymentCode.getMask(SecretPoint(privkey, pubkey).ECDHSecretAsBytes(), outpoint)
            //                Log.i("PayNymDetailsActivity", "mask:" + Hex.toHexString(mask));
    //                Log.i("PayNymDetailsActivity", "mask length:" + mask.length);
    //                Log.i("PayNymDetailsActivity", "payload0:" + Hex.toHexString(BIP47Util.getInstance(context).getPaymentCode().getPayload()));
            op_return = PaymentCode.blind(
                BIP47Util.getInstance(this@PayNymDetailsActivity).paymentCode.payload,
                mask
            )
            //                Log.i("PayNymDetailsActivity", "payload1:" + Hex.toHexString(op_return));
        } catch (ike: InvalidKeyException) {
            throw ike
        } catch (ikse: InvalidKeySpecException) {
            throw ikse
        } catch (nsae: NoSuchAlgorithmException) {
            throw nsae
        } catch (nspe: NoSuchProviderException) {
            throw nspe
        } catch (e: Exception) {
            throw e
        }
        val receivers = HashMap<String, BigInteger>()
        receivers[Hex.toHexString(op_return)] = BigInteger.ZERO

        val notificationAddr = payment_code.notificationAddress(currentNetworkParams).addressString
        /*
            val samFeeAddress = if (SamouraiWallet.getInstance().isTestNet)
                SendNotifTxFactory.getInstance().TESTNET_SAMOURAI_NOTIF_TX_FEE_ADDRESS
            else SendNotifTxFactory.getInstance().SAMOURAI_NOTIF_TX_FEE_ADDRESS;
             */

        receivers[notificationAddr] = SendNotifTxFactory._bNotifTxValue
        //receivers[samFeeAddress] = SendNotifTxFactory._bSWFee

        val change = totalValueSelected - (amount + fee.toLong())
        if (change > 0L) {
            val change_address = BIP84Util.getInstance(this@PayNymDetailsActivity).getAddressAt(
                AddressFactory.CHANGE_CHAIN,
                BIP84Util.getInstance(this@PayNymDetailsActivity).wallet.getAccount(0).change.addrIdx
            ).bech32AsString
            receivers[change_address] = BigInteger.valueOf(change)
        }
        Log.d("PayNymDetailsActivity", "outpoints:" + outpoints.size)
        Log.d(
            "PayNymDetailsActivity",
            "totalValueSelected:" + BigInteger.valueOf(totalValueSelected).toString()
        )
        Log.d("PayNymDetailsActivity", "amount:" + BigInteger.valueOf(amount).toString())
        Log.d("PayNymDetailsActivity", "change:" + BigInteger.valueOf(change).toString())
        Log.d("PayNymDetailsActivity", "fee:$fee")
        if (change < 0L) {
            throw Exception(getString(R.string.bip47_cannot_compose_notif_tx))
        }
        val _outPoint: MyTransactionOutPoint = outPoint!!
        var strNotifTxMsg = getText(R.string.bip47_setup4_text1).toString() + " "
        val notifAmount = amount
        val strAmount =
            MonetaryUtil.getInstance().btcFormat.format((notifAmount.toDouble() + fee.toLong()) / 1e8) + " BTC "
        strNotifTxMsg += strAmount + getText(R.string.bip47_setup4_text2)

        scope.launch(Dispatchers.Main) {
            try {
                binding.progressBar.visibility = View.INVISIBLE

                showFollowAlert(strAmount) { view: View? ->
                    binding.progressBar.visibility = View.VISIBLE

                    scope.launch(Dispatchers.IO) {
                        try {
                            var tx = SendFactory.getInstance(this@PayNymDetailsActivity)
                                .makeTransaction(outpoints, receivers)
                            if (tx != null) {
                                val input0hash = tx.getInput(0L).outpoint.hash.toString()
                                val input0index = tx.getInput(0L).outpoint.index.toInt()
                                if (input0hash != _outPoint.txHash.toString() || input0index != _outPoint.txOutputN) {
                                    throw Exception(getString(R.string.bip47_cannot_compose_notif_tx))
                                }
                                tx = SendFactory.getInstance(this@PayNymDetailsActivity)
                                    .signTransaction(tx, 0)
                                val hexTx = String(Hex.encode(tx.bitcoinSerialize()))

                                var hashTx = tx.hashAsString
                                var changeIdx = 0
                                for (i in 0 until tx.outputs.size) {
                                    if (tx.getOutput(i.toLong()).value.value == change) {
                                        changeIdx = i
                                        break
                                    }
                                }

                                var isOK = false
                                var response: String?
                                try {
                                    val rbf = createRBFSpendFromTx(tx, this@PayNymDetailsActivity)
                                    response =
                                        PushTx.getInstance(this@PayNymDetailsActivity).samourai(hexTx, null)
                                    Log.d("SendActivity", "pushTx:$response")
                                    if (response != null) {
                                        val jsonObject = JSONObject(response)
                                        if (jsonObject.has("status")) {
                                            if ((jsonObject.getString("status") == "ok")) {
                                                isOK = true
                                                APIFactory.getInstance(this@PayNymDetailsActivity)
                                                    .initWallet()
                                            }
                                        }
                                    } else {
                                        throw Exception(getString(R.string.pushtx_returns_null))
                                    }
                                    scope.launch(Dispatchers.Main) {
                                        binding.progressBar.visibility = View.INVISIBLE

                                        if (isOK) {

                                            UTXOUtil.getInstance()
                                                .add(hashTx, changeIdx, "\u2623 notif tx change\u2623")

                                            Toast.makeText(
                                                this@PayNymDetailsActivity,
                                                R.string.payment_channel_init,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            //
                                            // set outgoing index for payment code to 0
                                            //
                                            BIP47Meta.getInstance().setOutgoingIdx(pcode, 0)
                                            //                        Log.i("SendNotifTxFactory", "tx hash:" + tx.getHashAsString());
                                            //
                                            // status to NO_CFM
                                            //
                                            BIP47Meta.getInstance().setOutgoingStatus(
                                                pcode,
                                                tx.hashAsString,
                                                BIP47Meta.STATUS_SENT_NO_CFM
                                            )

                                            //updateRBFSpendForBroadcastTxAndRegister(rbf,  tx, samFeeAddress, 84, this@PayNymDetailsActivity)

                                            //
                                            // increment change index
                                            //
                                            if (change > 0L) {
                                                BIP49Util.getInstance(this@PayNymDetailsActivity)
                                                    .wallet.getAccount(0).change.incAddrIdx()
                                            }
                                            if (!BIP47Meta.getInstance().exists(pcode, false)) {
                                                BIP47Meta.getInstance().setLabel(
                                                    pcode,
                                                    BIP47Meta.getInstance().getAbbreviatedPcode(pcode)
                                                );
                                                BIP47Meta.getInstance().setFollowing(pcode, true);
                                            }
                                            savePayLoad()
                                        } else {
                                            Toast.makeText(
                                                this@PayNymDetailsActivity,
                                                R.string.tx_failed,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        scope.launch(Dispatchers.Main) {
                                            setPayNym()
                                        }
                                    }
                                } catch (e: Exception) {
                                    manageException(e)
                                }
                            }
                        } catch (t: Throwable) {
                            manageException(t)
                        }
                    }
                }
            } catch (t: Throwable) {
                manageException(t)
            }
        }
    }

    private fun manageException(it: Throwable) {
        scope.launch(Dispatchers.Main) {
            Log.i(TAG, it.message, it)
            binding.progressBar.visibility = View.INVISIBLE
            Toast.makeText(this@PayNymDetailsActivity, it.message, Toast.LENGTH_SHORT).show()
        }
    }

    @Throws(MnemonicLengthException::class, DecryptionException::class, JSONException::class, IOException::class)
    private fun savePayLoad() {
        PayloadUtil.getInstance(this@PayNymDetailsActivity).serializePayNyms(BIP47Meta.getInstance().toJSON())
        PayloadUtil.getInstance(this@PayNymDetailsActivity).saveWalletToJSON(CharSequenceX(AccessFactory.getInstance(this@PayNymDetailsActivity).guid + AccessFactory.getInstance(this@PayNymDetailsActivity).pin))
    }

    private fun doUpdatePayNymInfo(pcode: String?) {
        val disposable = Observable.fromCallable {
            val obj = JSONObject()
            obj.put("nym", pcode)
            val res = WebUtil.getInstance(this).postURL("application/json", null, WebUtil.PAYNYM_API + "api/v1/nym", obj.toString())
            //                    Log.d("PayNymDetailsActivity", res);
            val responseObj = JSONObject(res)
            if (responseObj.has("nymName")) {
                val strNymName = responseObj.getString("nymName")
                BIP47Meta.getInstance().setName(pcode, strNymName)
                if (FormatsUtil.getInstance().isValidPaymentCode(BIP47Meta.getInstance().getLabel(pcode))) {
                    BIP47Meta.getInstance().setLabel(pcode, strNymName)
                }
            }
            if (responseObj.has("segwit") && responseObj.getBoolean("segwit")) {
                BIP47Meta.getInstance().setSegwit(pcode, true)
            }
            true
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ success: Boolean ->
                    setPayNym()
                    if (success) {
                        savePayLoad()
                    }
                }) { errror: Throwable ->
                    Toast.makeText(this, "Unable to update paynym", Toast.LENGTH_SHORT).show()
                }
        disposables.add(disposable)
    }

    private fun doSync() {
        binding.progressBar.visibility = View.VISIBLE
        val disposable = Observable.fromCallable {
            synPayNym(pcode, this)
            true
        }.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ _: Boolean? ->
                    binding.progressBar.visibility = View.INVISIBLE
                    setPayNym()
                }) { error: Throwable ->
                    error.printStackTrace()
                    binding.progressBar.visibility = View.INVISIBLE
                }
        disposables.add(disposable)
    }



    companion object {
        private const val EDIT_PCODE = 2000
        private const val RECOMMENDED_PCODE = 2001
        private const val SCAN_PCODE = 2077
        private const val TAG = "PayNymDetailsActivity"
    }
}
