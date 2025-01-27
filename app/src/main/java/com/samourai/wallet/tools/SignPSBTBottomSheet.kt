package com.samourai.wallet.tools

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.invertedx.hummingbird.URQRView
import com.samourai.wallet.R
import com.samourai.wallet.fragments.ScanFragment
import com.samourai.wallet.theme.SamouraiWalletTheme
import com.samourai.wallet.theme.samouraiAccent
import com.samourai.wallet.theme.samouraiBottomSheetBackground
import com.samourai.wallet.theme.samouraiSuccess
import com.samourai.wallet.theme.samouraiTextFieldBg
import com.samourai.wallet.tools.viewmodels.SignPSBTViewModel
import com.samourai.wallet.util.tech.AppUtil
import com.sparrowwallet.hummingbird.UR
import com.sparrowwallet.hummingbird.registry.RegistryType
import org.bouncycastle.util.encoders.Hex
import java.util.Objects.nonNull


@Composable
fun SignPSBTTool(
    keyParameter: String = ""
) {
    val vm = viewModel<SignPSBTViewModel>()
    val page by vm.page.observeAsState(0)

    SamouraiWalletTheme {
        Scaffold(
            modifier = Modifier.requiredHeight(530.dp),
            backgroundColor = samouraiBottomSheetBackground,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
            ) {
                WrapToolsPageAnimation(
                    visible = page == 0
                ) {
                    InputFormPSBT(keyParameter = keyParameter)
                }
                WrapToolsPageAnimation(
                    visible = page == 2
                ) {
                    SignSuccess()
                }
            }
        }
    }
}


@Composable
fun SignSuccess() {
    val context = LocalContext.current
    val vm = viewModel<SignPSBTViewModel>()
    val transaction by vm.signedTx.observeAsState(null)
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val loading by vm.loading.observeAsState(false)
    val showCheck by vm.showCheck.observeAsState(false)
    val animatedIconDrawable = AnimatedVectorDrawableCompat.create(
        LocalContext.current,
        R.drawable.animated_check_vd
    )
    val instructionText = buildAnnotatedString {
        withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
            append("Scan the animated QR code with")
        }
        append(" ")
        withStyle(style = SpanStyle(color = samouraiAccent)) {
            append("Samourai Sentinel")
        }
        withStyle(style = SpanStyle(color = Color.White.copy(alpha = 0.6f))) {
            append(" or other compatible software to broadcast this transaction.")
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                title = {
                    Text(text = "Sign transaction", color = samouraiAccent)
                },
                actions = {
                    IconButton(
                        modifier = Modifier
                            .alpha(if (loading) 0f else 1f),
                        onClick = {
                            val data = transaction?.bitcoinSerialize()
                            if (!loading && nonNull(data)) {
                                clipboardManager.setText(AnnotatedString(String(Hex.encode(data))))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                    }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_content_copy_24),
                            contentDescription = "Copy"
                        )
                    }
                },

                )
        },
        backgroundColor = samouraiBottomSheetBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(top = 10.dp)
                .padding(horizontal = 10.dp),
            Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val painter = painterResource(id = R.drawable.ic_sign_check)
            if (loading && !showCheck) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 1.dp,
                    modifier = Modifier.size(160.dp),
                )
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(150.dp))
                        .background(samouraiSuccess)
                ) {
                    Icon(
                        painter = painter,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.Center),
                        tint = Color.White,
                        contentDescription = ""
                    )
                }
                    }
            }
            else if (loading && showCheck) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                ) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(150.dp))
                            .background(samouraiSuccess)
                    ) {
                        AndroidView(
                            modifier = Modifier
                                .size(100.dp)
                                .align(Alignment.Center),
                            factory = { context ->
                                androidx.appcompat.widget.AppCompatImageView(context).apply {
                                    setImageDrawable(animatedIconDrawable)
                                    animatedIconDrawable?.start()
                                }
                            }
                        )
                    }
                }
            }
            else {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White),
                        ) {
                            AndroidView(
                                modifier = Modifier
                                    .requiredSize(280.dp)
                                    .alpha(1f),
                                factory = { context ->
                                    URQRView(context)
                                }) { view ->
                                val data = transaction?.bitcoinSerialize()
                                if (nonNull(data)) {
                                    view.setContent(
                                        UR.fromBytes(
                                            RegistryType.BYTES.type,
                                            Hex.decode(String(Hex.encode(transaction?.bitcoinSerialize())))
                                        )
                                    )
                                }
                            }
                        }
                        Text(
                            text = instructionText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 67.dp, vertical = 20.dp),
                            fontFamily = FontFamily(Font(R.font.roboto_regular))
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InputFormPSBT(keyParameter: String = "") {
    val context = LocalContext.current
    val isOffline by AppUtil.getInstance(context).offlineStateLive().observeAsState(false)
    var psbtEdit by remember { mutableStateOf(keyParameter) }
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val vm = viewModel<SignPSBTViewModel>()
    val validPSBT by vm.validPSBT.observeAsState(null)
    val supportFragmentManager = getSupportFragmentManger()
    val keyboardController = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = Color.Transparent,
                elevation = 0.dp,
                title = {
                    Text(text = "Sign transaction", color = samouraiAccent)
                }
            )
        },
        backgroundColor = samouraiBottomSheetBackground,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 4.dp)
                .fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.padding(12.dp))
                TextField(value = psbtEdit,
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                        .onFocusChanged {
                            vm.setPSBT(psbtEdit)
                        },
                    singleLine = false,
                    maxLines = 50,
                    onValueChange = {
                        psbtEdit = it
                        vm.setPSBT(it)
                    }, colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = samouraiTextFieldBg,
                        cursorColor = samouraiAccent
                    ), label = {
                        Text("Enter transaction to be signed", color = Color.White)
                    }, textStyle = TextStyle(fontSize = 13.sp),
                    keyboardOptions = KeyboardOptions(
                        autoCorrect = false,
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Text,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        vm.setPSBT(psbtEdit)
                        keyboardController?.hide()
                    }),
                    trailingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.qrcode_scan),
                            contentDescription = "Scan PSBT",
                            modifier = Modifier.clickable {
                                if (supportFragmentManager != null) {
                                    val cameraFragmentBottomSheet = ScanFragment()
                                    cameraFragmentBottomSheet.show(supportFragmentManager, cameraFragmentBottomSheet.tag)
                                    cameraFragmentBottomSheet .setOnScanListener {
                                        cameraFragmentBottomSheet.dismissAllowingStateLoss()
                                        psbtEdit = it
                                        vm.setPSBT(psbtEdit)
                                    }
                                }
                            }
                        )
                    }
                )
                Button(
                    onClick = {
                        vm.signPSBT(context, psbtEdit)
                    },
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (validPSBT != null) 1f else 0.5f)
                        .padding(top = 3.dp),
                    enabled = validPSBT != null,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    colors = ButtonDefaults.textButtonColors(
                        backgroundColor = samouraiAccent,
                        contentColor = Color.White
                    )
                ) {
                    Text(stringResource(R.string.sign_psbt), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}