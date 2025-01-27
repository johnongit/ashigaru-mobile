package com.samourai.wallet.onboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.transition.Fade
import androidx.transition.TransitionManager
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.samourai.wallet.CreateWalletActivity
import com.samourai.wallet.R
import com.samourai.wallet.SamouraiWallet
import com.samourai.wallet.databinding.ActivitySetUpWalletBinding
import com.samourai.wallet.fragments.CameraFragmentBottomSheet
import com.samourai.wallet.network.dojo.DojoUtil
import com.samourai.wallet.payload.ExternalBackupManager
import com.samourai.wallet.tor.EnumTorState
import com.samourai.wallet.tor.SamouraiTorManager
import com.samourai.wallet.tor.TorState
import com.samourai.wallet.util.*
import com.samourai.wallet.util.tech.AppUtil
import com.samourai.wallet.util.tech.askNotificationPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class SetUpWalletActivity : AppCompatActivity() {

    private var activeColor = 0
    private var disabledColor: Int = 0
    private var waiting: Int = 0
    private var storagePermGranted = false
    private lateinit var binding: ActivitySetUpWalletBinding

    private val setUpWalletViewModel: SetUpWalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetUpWalletBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.statusBarColor = ContextCompat.getColor(this, R.color.window)

        activeColor = ContextCompat.getColor(this, R.color.green_ui_2)
        disabledColor = ContextCompat.getColor(this, R.color.disabledRed)
        waiting = ContextCompat.getColor(this, R.color.warning_yellow)


        if (PrefsUtil.getInstance(applicationContext).setValue(PrefsUtil.AUTO_BACKUP, true) &&
            !ExternalBackupManager.hasPermissions()) {

            ExternalBackupManager.askPermission(this);
        }

        SamouraiTorManager.getTorStateLiveData().observe(this) {
            setTorState(it)
        }

        setTorState(SamouraiTorManager.getTorState())
        val setUpWalletTorSwitch = binding.setUpWalletTorSwitch
        val setUpWalletContainer = binding.setUpWalletContainer
        val setUpWalletAddressInput = binding.setUpWalletAddressInput
        val setUpWalletDojoProgress = binding.setUpWalletDojoProgress
        val onBoardingDojoStatus = binding.onBoardingDojoStatus
        val setUpWalletDojoSwitch = binding.setUpWalletDojoSwitch
        val setUpWalletApiKeyInput = binding.setUpWalletApiKeyInput
        val setUpWalletCreateNewWallet = binding.setUpWalletCreateNewWallet
        val setUpWalletConnectDojo = binding.setUpWalletConnectDojo
        val setUpWalletRestoreButton = binding.setUpWalletRestoreButton

        setUpWalletTorSwitch.setOnClickListener {
            if (setUpWalletTorSwitch.isChecked) {
                SamouraiTorManager.start()
            } else {
                if (DojoUtil.getInstance(applicationContext).dojoParams != null) {
                    setUpWalletTorSwitch.isChecked = true
                    MaterialAlertDialogBuilder(this)
                        .setMessage(R.string.cannot_disable_tor_dojo)
                        .setPositiveButton(R.string.ok) { dialog,
                                                          _ ->
                            dialog.dismiss()
                        }
                        .show()
                    return@setOnClickListener
                }
                SamouraiTorManager.stop()
            }
        }
        setUpWalletViewModel.errorsLiveData.observe(this) {
            it?.let {
                Snackbar.make(setUpWalletContainer, "Error: $it", Snackbar.LENGTH_LONG)
                    .show()
            }
        }
        setUpWalletViewModel.apiEndpoint.observe(this) {
            if (setUpWalletAddressInput.text.toString() != it)
                setUpWalletAddressInput.setText(it)
        }
        setUpWalletViewModel.apiKey.observe(this) {
            if (setUpWalletApiKeyInput.text.toString() != it)
                setUpWalletApiKeyInput.setText(it)
        }
        setUpWalletViewModel.dojoStatus.observe(this) {
            val defaultColor = MaterialColors.getColor(applicationContext, R.attr.colorPrimary, ContextCompat.getColor(this, R.color.primary_light))
            onBoardingDojoStatus.setTextColor(defaultColor)
            when (it) {
                SetUpWalletViewModel.DojoStatus.CONNECTED -> {
                    disableInputs(false)
                    onBoardingDojoStatus.text = getString(R.string.connected)
                    onBoardingDojoStatus.setTextColor(activeColor)
                    setUpWalletDojoSwitch.visibility = View.VISIBLE
                    setUpWalletDojoProgress.visibility = View.GONE
                }
                SetUpWalletViewModel.DojoStatus.NOT_CONFIGURED -> {
                    disableInputs(false)
                    onBoardingDojoStatus.text = getString(R.string.not_configured)
                }

                SetUpWalletViewModel.DojoStatus.CONNECTING -> {
                    disableInputs(true)
                    onBoardingDojoStatus.text = getString(R.string.connecting)
                    onBoardingDojoStatus.setTextColor(waiting)
                    setUpWalletDojoProgress.visibility = View.VISIBLE
                    setUpWalletDojoSwitch.visibility = View.INVISIBLE
                }
                SetUpWalletViewModel.DojoStatus.ERROR -> {
                    disableInputs(false)
                    onBoardingDojoStatus.text = "Error"
                    setUpWalletDojoProgress.visibility = View.GONE
                    setUpWalletDojoSwitch.visibility = View.VISIBLE
                }
                else -> {

                }
            }

        }
        setUpDojoLayout()
        setUpWalletConnectDojo.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val string = if (clipboard.hasPrimaryClip()) {
                val item = clipboard.primaryClip?.getItemAt(0)
                item?.text.toString()
            } else {
                ""
            }
            if (DojoUtil.getInstance(applicationContext).isValidPairingPayload(string)) {
                binding.jsonDojoTextField.text = string
                try {
                    setUpWalletViewModel.viewModelScope.launch(Dispatchers.Default) {
                        withContext(Dispatchers.Main) {
                            setUpWalletViewModel.setDojoParams(string, applicationContext)
                        }
                        delay(600)
                        withContext(Dispatchers.Main) {
                            connectDojo()
                        }
                    }
                } catch (e: Exception) {
                }
            }
            else
                Toast.makeText(applicationContext, "Clipboard doesn't contain a valid Dojo pairing payload", Toast.LENGTH_LONG).show()

        }
        setUpWalletCreateNewWallet.setOnClickListener {
            if (DojoUtil.getInstance(this@SetUpWalletActivity).dojoParams != null || AppUtil.getInstance(this@SetUpWalletActivity).isOfflineMode) {
                val intent = Intent(this, CreateWalletActivity::class.java)
                startActivity(intent)
            }
            else {
                Toast.makeText(
                    this@SetUpWalletActivity,
                    "No Dojo credentials. Enter these to create a wallet. Or go offline.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        setUpWalletRestoreButton.setOnClickListener {
            if (DojoUtil.getInstance(this@SetUpWalletActivity).dojoParams != null || AppUtil.getInstance(this@SetUpWalletActivity).isOfflineMode) {
                val intent = Intent(this, RestoreOptionActivity::class.java)
                startActivity(intent)
            }
            else {
                Toast.makeText(
                    this@SetUpWalletActivity,
                    "No Dojo credentials. Enter these to restore a wallet. Or go offline.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        setUpWalletViewModel.viewModelScope.launch {
            withContext(Dispatchers.Main) {
                askNotificationPermission(this@SetUpWalletActivity)
            }
        }
    }

    private fun connectDojo() {
        val urlPattern =
            Pattern.compile("^(https?|http)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]")
        var apiUrl = binding.setUpWalletAddressInput.text.toString()
        if (!apiUrl.startsWith("http")) {
            apiUrl = "http://$apiUrl"
        }
        if (apiUrl.endsWith(".onion") || apiUrl.endsWith(".onion/")) {
            apiUrl = if (!SamouraiWallet.getInstance().isTestNet) {
                if (apiUrl.last() == '/') "${apiUrl}v2" else "$apiUrl/v2/"
            } else {
                if (apiUrl.last() == '/') "${apiUrl}test/v2" else "$apiUrl/test/v2"
            }
        }
        if (!urlPattern.matcher(apiUrl).matches()) {
            binding.setUpWalletAddressInput.error = getString(R.string.invalid_api_endpoint)
            binding.setUpWalletAddressInput.requestFocus()
            return
        }
        if (binding.setUpWalletApiKeyInput.text.toString().isEmpty()) {
            binding.setUpWalletApiKeyInput.error = getString(R.string.api_key_blank_error)
            binding.setUpWalletApiKeyInput.requestFocus()
            return
        }

        setUpWalletViewModel.setApiUrl(apiUrl)
        setUpWalletViewModel.setApiKey(binding.setUpWalletApiKeyInput.text.toString())

        if (SamouraiTorManager.isConnected()) {
            setUpWalletViewModel.connectToDojo(applicationContext)
        } else {
            SamouraiTorManager.start()
            SamouraiTorManager.getTorStateLiveData().observe(this, Observer {
                if (it.state == EnumTorState.ON) {
                    setUpWalletViewModel.viewModelScope.launch(Dispatchers.Default) {
                        delay(600)
                        withContext(Dispatchers.Main) {
                            setUpWalletViewModel.connectToDojo(applicationContext)
                        }
                    }
                }
            })
        }
    }

    private fun setUpDojoLayout() {

        binding.setUpWalletScanDojo.setOnClickListener {
            val cameraFragmentBottomSheet = CameraFragmentBottomSheet()
            cameraFragmentBottomSheet.show(supportFragmentManager, cameraFragmentBottomSheet.tag)
            cameraFragmentBottomSheet.setQrCodeScanListener { code: String ->
                cameraFragmentBottomSheet.dismissAllowingStateLoss()

                try {
                    setUpWalletViewModel.viewModelScope.launch(Dispatchers.Default) {
                        withContext(Dispatchers.Main) {
                            setUpWalletViewModel.setDojoParams(code, applicationContext)
                        }
                        delay(600)
                        withContext(Dispatchers.Main) {
                            connectDojo()
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }

        binding.setUpWalletDojoSwitch.setOnCheckedChangeListener { buttonView,
                                                                   isChecked ->
            run {
                if (!isChecked && DojoUtil.getInstance(applicationContext).dojoParams != null) {
                    binding.setUpWalletDojoSwitch.isChecked = true
                    MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.confirm)
                        .setMessage(getString(R.string.do_you_want_to_unpair))
                        .setPositiveButton(R.string.ok) { _,
                                                          _ ->
                            run {
                                setUpWalletViewModel.unPairDojo(applicationContext)
                                binding.setUpWalletDojoSwitch.isChecked = false
                            }
                        }.setNegativeButton(R.string.cancel) { dialog,
                                                               _ ->
                            dialog.dismiss()
                        }
                        .show()
                } else {
                    showDojoInputLayout(isChecked)
                }
            }
        }

        binding.setUpWalletDojoMessage.setOnClickListener {
            binding.setUpWalletDojoSwitch.isChecked = !binding.setUpWalletDojoSwitch.isChecked
        }

        slideDown(binding.setUpWalletDojoInputGroup)
    }

    private fun showDojoInputLayout(checked: Boolean) {
        if (checked) {
            slideUp(binding.setUpWalletDojoInputGroup)
        } else {
            slideDown(binding.setUpWalletDojoInputGroup)
        }
    }

    private fun setTorState(torState: TorState) {
        val onBoardingTorStatus = binding.onBoardingTorStatus
        val setUpWalletTorProgress = binding.setUpWalletTorProgress
        val setUpWalletTorSwitch = binding.setUpWalletTorSwitch
        when (torState.state) {
            EnumTorState.STARTING -> {
                onBoardingTorStatus.text = getString(R.string.tor_initializing)
                onBoardingTorStatus.setTextColor(waiting)
                makeViewTransition(setUpWalletTorProgress, setUpWalletTorSwitch)
                setUpWalletTorSwitch.isChecked = true
            }
            EnumTorState.ON -> {
                disableInputs(false)
                onBoardingTorStatus.text = getString(R.string.active)
                onBoardingTorStatus.setTextColor(activeColor)
                setUpWalletTorSwitch.isChecked = true
                makeViewTransition(setUpWalletTorSwitch, setUpWalletTorProgress)
            }
            EnumTorState.OFF -> {
                setUpWalletTorSwitch.isChecked = false
                onBoardingTorStatus.text = getString(R.string.off)
                onBoardingTorStatus.setTextColor(disabledColor)
                makeViewTransition(setUpWalletTorSwitch, setUpWalletTorProgress)
            }
            EnumTorState.STOPPING -> {
                setUpWalletTorSwitch.isChecked = false
                onBoardingTorStatus.text = getString(R.string.off)
                onBoardingTorStatus.setTextColor(waiting)
                makeViewTransition(setUpWalletTorSwitch, setUpWalletTorProgress)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.size > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            storagePermGranted = true;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun makeViewTransition(entering: View?, leaving: View?) {
        TransitionManager.beginDelayedTransition(binding.setUpWalletContainer, Fade())
        leaving?.let {
            it.visibility = View.INVISIBLE
        }
        entering?.let {
            it.visibility = View.VISIBLE
        }
    }

    private fun disableInputs(enable: Boolean) {

        binding.setUpWalletRestoreButton.isEnabled = !enable
        binding.setUpWalletAddressInput.isEnabled = !enable
        binding.setUpWalletScanDojo.isEnabled = !enable
        binding.setUpWalletTorSwitch.isEnabled = !enable
        binding.setUpWalletApiKeyInput.isEnabled = !enable
        binding.setUpWalletCreateNewWallet.isEnabled = !enable
        binding.setUpWalletConnectDojo.isEnabled = !enable
    }

    private fun slideDown(view: View): ViewPropertyAnimator? {
        return view.animate()
            .translationY(100F)
            .alpha(0f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(200)
            .withEndAction {
                view.visibility = View.GONE
            }.apply {
                this.start()
            }

    }

    private fun slideUp(view: View): ViewPropertyAnimator? {
        view.visibility = View.VISIBLE
        view.alpha = 0f
        return view.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .apply {
                this.start()
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        ExternalBackupManager.onActivityResult(requestCode, resultCode, data, application)
        super.onActivityResult(requestCode, resultCode, data)
    }

}
