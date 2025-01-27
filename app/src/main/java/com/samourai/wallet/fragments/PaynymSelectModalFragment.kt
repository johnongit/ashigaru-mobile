package com.samourai.wallet.fragments

import android.accounts.NetworkErrorException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.samourai.wallet.R
import com.samourai.wallet.bip47.BIP47Meta
import com.samourai.wallet.bip47.BIP47Util
import com.samourai.wallet.bip47.PaynymModel
import com.samourai.wallet.bip47.paynym.WebUtil
import com.samourai.wallet.payload.PayloadUtil
import com.samourai.wallet.paynym.PayNymHome
import com.samourai.wallet.paynym.api.PayNymApiService
import com.samourai.wallet.util.tech.fromJSON
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PaynymSelectModalFragment : BottomSheetDialogFragment() {

    private lateinit var dialogTitle: String
    var selectListener: Listener? = null
    private var job: Job? = null
    private var paymentCodes: ArrayList<PaynymModel> = arrayListOf()
    private var loadFromNetwork = false
    lateinit var recyclerView: RecyclerView
    lateinit var emptyview: LinearLayout
    lateinit var loadingView: LinearLayout
    lateinit var dialogTitleTxt: TextView
    lateinit var updatingProgressBar: LinearProgressIndicator

    private val disposables = CompositeDisposable()
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_paynymselectmodal_list_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recyclerView = view.findViewById(R.id.list)
        emptyview = view.findViewById(R.id.empty_paynym)
        loadingView = view.findViewById(R.id.paynym_loading)
        dialogTitleTxt = view.findViewById(R.id.dialogTitle)
        updatingProgressBar = view.findViewById(R.id.updatingProgressBar)
        dialogTitleTxt.text = dialogTitle
        if (!loadFromNetwork) {
            paymentCodes = ArrayList(BIP47Meta.getInstance().getSortedByLabels(false, true).map {
                PaynymModel(code = it, "", nymName = BIP47Meta.getInstance().getDisplayLabel(it))
            }.toMutableList())
            setAdapter()
            updateAsyncLastConnectedPaymentCodes()
        }
        if (loadFromNetwork) getFromNetwork()

    }

    private fun updateAsyncLastConnectedPaymentCodes() {

        updatingProgressBar.show()

        var disposable = Single.fromCallable {
            BIP47Util.getInstance(context).updateOutgoingStatusForNewPayNymConnections() > 0
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ updated: Boolean? ->

                    if (updated == true) {
                        paymentCodes = ArrayList(BIP47Meta.getInstance().getSortedByLabels(false, true).map {
                            PaynymModel(code = it, "", nymName = BIP47Meta.getInstance().getDisplayLabel(it))
                        }.toMutableList())
                        recyclerView.adapter = PaynymSelectModalAdapter()
                        if (paymentCodes.size > 0 && recyclerView.visibility != View.VISIBLE) {
                            recyclerView.visibility = View.VISIBLE
                            emptyview.visibility = View.GONE
                        }
                    }
                    updatingProgressBar.hide()
                }) { throwable: Throwable ->
                    Log.e(TAG, "issue when loading connected " + throwable.message, throwable)
                    updatingProgressBar.hide()
                }

        disposables.add(disposable);
    }

    override fun onDestroy() {
        disposables.dispose()
        super.onDestroy()
    }

    private fun setAdapter() {
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = PaynymSelectModalAdapter()
        if (paymentCodes.size == 0) {
            recyclerView.visibility = View.GONE
            emptyview.visibility = View.VISIBLE
            emptyview.findViewById<View>(R.id.paynym_add_btn).setOnClickListener { view1: View? ->
                startActivity(Intent(context, PayNymHome::class.java))
                dismiss()
            }
        }
    }

    private fun getFromNetwork() {

        fun getNymCache(): String? {
            return if (PayloadUtil.getInstance(requireActivity()).getPaynymResponseFile().exists()) {
                PayloadUtil.getInstance(requireActivity()).paynymResponseFile.readText();
            } else {
                null;
            }
        }

        loadingView.visibility = View.VISIBLE
        job = CoroutineScope(Dispatchers.Main).launch(Dispatchers.IO) {
            try {
                val strPaymentCode = BIP47Util.getInstance(requireContext().applicationContext).paymentCode.toString()
                val obj = JSONObject()
                obj.put("nym", strPaymentCode)
                val res = PayNymApiService.getInstance(strPaymentCode, activity?.applicationContext!!).getNymInfo()
                if (res.isSuccessful) {
                    parsePaynymResponse(res.body?.string()!!)
                } else {
                    throw NetworkErrorException("paynym.rs error");
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "Network error while loading from paynym.rs", Toast.LENGTH_LONG).show()
                }
                if(!getNymCache().isNullOrBlank()){
                    getNymCache()?.let { parsePaynymResponse(it) }
                }
                throw  CancellationException(e.message)
            }
        }
        job?.invokeOnCompletion {

        }
    }

    suspend fun parsePaynymResponse(res: String) {
        val json = JSONObject(res)
        val mutableCollection = mutableListOf<PaynymModel>()

        if (json.has("following")) {
            repeat(json.getJSONArray("following").length()) {
                val item = fromJSON<PaynymModel>(json.getJSONArray("following").getJSONObject(it).toString());
                item?.let { it1 -> mutableCollection.add(it1) }
            }
        }

        if (json.has("followers")) {
            repeat(json.getJSONArray("followers").length()) { position ->
                val item = fromJSON<PaynymModel>(json.getJSONArray("followers").getJSONObject(position).toString());
                mutableCollection.find { it.code == item?.code }.let {
                    if (it == null) {
                        item?.let { it1 -> mutableCollection.add(it1) }
                    }
                }
            }
        }
        paymentCodes = ArrayList(mutableCollection)
        if (!PayloadUtil.getInstance(requireActivity()).paynymResponseFile.exists()) {
            PayloadUtil.getInstance(requireActivity()).paynymResponseFile.createNewFile();
        }
        PayloadUtil.getInstance(requireActivity()).paynymResponseFile.writeText(res);
        withContext(Dispatchers.Main) {
            loadingView.visibility = View.GONE
            setAdapter()
        }
    }

    override fun onDestroyView() {
        job?.cancel("OnDestroy")
        super.onDestroyView()
    }

    override fun onDetach() {
        selectListener = null
        super.onDetach()
    }

    interface Listener {
        fun onPaynymSelectItemClicked(code: String?)
    }

    private inner class ViewHolder(inflater: LayoutInflater, parent: ViewGroup?) : RecyclerView.ViewHolder(inflater.inflate(R.layout.fragment_paynymselectmodal_list_item, parent, false)) {
        var avatar: ImageView = itemView.findViewById(R.id.img_paynym_avatar_select)
        var displayName: TextView = itemView.findViewById(R.id.paynym_display_name)
        var rootLayout: ConstraintLayout = itemView.findViewById(R.id.paynym_select_root)

        init {
            rootLayout.setOnClickListener {
                selectListener!!.onPaynymSelectItemClicked(paymentCodes[position].code)
                dismiss()
            }
        }
    }

    private inner class PaynymSelectModalAdapter : RecyclerView.Adapter<ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context), parent)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val code = paymentCodes[position].code
            var label = paymentCodes[position].nymName
            var metaLabel = BIP47Meta.getInstance().getLabel(paymentCodes[position].code)
            if (!metaLabel.isNullOrBlank()) {
                label = metaLabel
            }
            if (BIP47Meta.getInstance().getArchived(code)) {
                label += " (archived)"
            }
            if (BIP47Meta.getInstance().getOutgoingStatus(code) == BIP47Meta.STATUS_SENT_CFM) {
                label += " (connected)"
            }
            if (BIP47Meta.getInstance().getOutgoingStatus(code) == BIP47Meta.STATUS_NOT_SENT) {
                label += " (following)"
            }
            if (BIP47Meta.getInstance().getOutgoingStatus(code) == BIP47Meta.STATUS_SENT_NO_CFM) {
                label += " (not confirmed)"
            }
            holder.displayName.text = label
            if (code != null) {
                setPayNymLogos(code, holder.avatar)
            }
        }

        override fun getItemCount(): Int {
            return paymentCodes.size
        }
    }

    companion object {

        private val TAG = "PaynymSelectModalFrag"

        @JvmStatic
        fun newInstance(selectListener: Listener?,
                        dialogTitle: String = "PayNym",
                        loadFromNetwork: Boolean): PaynymSelectModalFragment {
            val fragment = PaynymSelectModalFragment()
            fragment.selectListener = selectListener
            fragment.loadFromNetwork = loadFromNetwork
            fragment.dialogTitle = dialogTitle
            return fragment
        }

        fun setPayNymLogos(strPaymentCode: String, avatar: ImageView) {
            try {
                Picasso.get().load(WebUtil.PAYNYM_API + strPaymentCode + "/avatar")
                    .into(avatar, createPicassoCallback(strPaymentCode, avatar))
            } catch (t: Throwable) {
                /**
                 * This catch block is useful if ever the onSuccess/onError callback system
                 * throws a runtime exception.
                 * It indicates a problem to be fixed, so we log in error.
                 * This has already been the case through the method LogUtil#error.
                 */
                Log.e(
                    TAG,
                    String.format(
                        "Throwable with Picasso on /avatar %s : %s",
                        strPaymentCode,
                        t.message
                    ),
                    t
                )
                avatar.setImageResource(R.drawable.paynym)
            }
        }

        private fun createPicassoCallback(
            strPaymentCode: String,
            avatar: ImageView
        ): Callback {
            return object : Callback {
                override fun onSuccess() {
                }

                override fun onError(e: java.lang.Exception) {
                    try {
                        Picasso.get().load(WebUtil.PAYNYM_API + "preview/" + strPaymentCode)
                            .into(avatar, object : Callback {
                                override fun onSuccess() {}

                                override fun onError(e: java.lang.Exception) {
                                    Log.e(
                                        TAG,
                                        "issue when loading avatar for $strPaymentCode", e
                                    )
                                    avatar.setImageResource(R.drawable.paynym)
                                }
                            })
                    } catch (t: Throwable) {
                        /**
                         * This catch block is useful if ever the onSuccess/onError callback system
                         * throws a runtime exception.
                         * It indicates a problem to be fixed, so we log in error.
                         * This has already been the case through the method LogUtil#error.
                         */
                        Log.e(
                            TAG,
                            String.format(
                                "Throwable with Picasso on /preview %s : %s",
                                strPaymentCode,
                                t.message
                            ),
                            t
                        )
                        avatar.setImageResource(R.drawable.paynym)
                    }
                }
            }
        }
    }
}