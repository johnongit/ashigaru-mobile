import SweepPrivateKey.Companion.MONTH_JANUARY
import SweepPrivateKey.Companion.indexToMonth
import SweepPrivateKey.Companion.monthToIndex
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.common.collect.Lists
import com.samourai.wallet.R
import com.samourai.wallet.constants.WALLET_INDEX
import com.samourai.wallet.fragments.CameraFragmentBottomSheet
import com.samourai.wallet.theme.SamouraiWalletTheme
import com.samourai.wallet.theme.samouraiAccent
import com.samourai.wallet.theme.samouraiBottomSheetBackground
import com.samourai.wallet.theme.samouraiError
import com.samourai.wallet.theme.samouraiSlateGreyAccent
import com.samourai.wallet.theme.samouraiSuccess
import com.samourai.wallet.theme.samouraiTextFieldBg
import com.samourai.wallet.theme.samouraiTextPrimary
import com.samourai.wallet.theme.samouraiTextSecondary
import com.samourai.wallet.theme.samouraiWarning
import com.samourai.wallet.theme.samouraiWindow
import com.samourai.wallet.tools.WrapToolsPageAnimation
import com.samourai.wallet.tools.viewmodels.SweepViewModel
import com.samourai.wallet.util.PrivKeyReader
import com.samourai.wallet.util.func.AddressFactory
import com.samourai.wallet.util.func.FormatsUtil
import com.samourai.wallet.util.tech.AppUtil
import org.bitcoinj.core.Coin
import java.util.Calendar
import java.util.GregorianCalendar


@Composable
fun SweepPrivateKeyView(
    supportFragmentManager: FragmentManager?,
    keyParameter: String = ""
) {
    val vm = viewModel<SweepViewModel>()
    val page by vm.getPageLive().observeAsState()
    val context = LocalContext.current
    LaunchedEffect(key1 = Unit) {
        vm.initWithContext(context, keyParameter)
    }
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
                    SweepFormSweepForm(supportFragmentManager)
                }
                WrapToolsPageAnimation(
                    visible = page == 1
                ) {
                    SweepTransactionPreview()
                }
                WrapToolsPageAnimation(
                    visible = page == 2
                ) {
                    SweepBroadcast()
                }
            }
        }
    }
}

@Composable
fun SweepBroadcast() {
    val vm = viewModel<SweepViewModel>()
    val broadcastError by vm.getBroadcastErrorStateLive().observeAsState()
    val broadCastLoading by vm.getBroadcastStateLive().observeAsState(false)

    val message = if (broadcastError != null) {
        broadcastError
    } else {
        if (broadCastLoading) {
            stringResource(id = R.string.tx_broadcast_ok)
        } else {
            "Sweep transaction success"
        }
    }

    val painter = if (broadcastError != null)
        painterResource(id = R.drawable.ic_baseline_error_outline_24)
    else
        painterResource(id = R.drawable.ic_broom)

    Scaffold(
        backgroundColor = samouraiBottomSheetBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.action_sweep), fontSize = 13.sp, color = samouraiAccent)
                },
                backgroundColor = samouraiBottomSheetBackground,
                elevation = 0.dp,
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .align(CenterHorizontally)
            ) {
                if (broadCastLoading) CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 1.dp,
                    modifier = Modifier.size(160.dp),
                )
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .align(Center)
                        .clip(RoundedCornerShape(150.dp))
                        .background(if (broadcastError != null) samouraiError else samouraiSuccess)
                ) {
                    Icon(
                        painter = painter,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Center),
                        tint = Color.White,
                        contentDescription = ""
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = "$message", fontWeight = FontWeight.SemiBold)
        }
    }

}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SweepFormSweepForm(supportFragmentManager: FragmentManager?) {
    val vm = viewModel<SweepViewModel>()
    val address by vm.getAddressLive().observeAsState()
    val keyFormat by vm.getPrivateKeyFormatLive().observeAsState()
    val loading by vm.getLoadingLive().observeAsState(false)
    val addressEdit = remember { mutableStateOf(address ?: "") }
    val addressValidationError by vm.getAddressValidationLive().observeAsState()
    val context = LocalContext.current
    val isOffline by AppUtil.getInstance(context).offlineStateLive().observeAsState(false)
    val keyboardController = LocalSoftwareKeyboardController.current

    val bip38Passphrase by vm.getBIP38PassphraseLive().observeAsState("")
    var passphraseEntry by remember { mutableStateOf(bip38Passphrase) }

    var switchFBState = remember { mutableStateOf(false)}

    val today: Calendar = GregorianCalendar()
    val currentMonth = indexToMonth.get(today[Calendar.MONTH]) ?: MONTH_JANUARY
    val currentYear = today[Calendar.YEAR]
    var selectedMonth = remember { mutableStateOf(currentMonth) }
    var selectedYear = remember { mutableStateOf(currentYear.toString()) }


    val months = Lists.newArrayList(monthToIndex.keys)
    val years: MutableList<String> = mutableListOf()
    for(n in 2020..currentYear){
        years.add("$n")
    }


    LaunchedEffect(key1 = address, block = {
        if (addressEdit.value.isEmpty()) {
            addressEdit.value = address ?: "";
        }
        AppUtil.getInstance(context).checkOfflineState()
    })

    LaunchedEffect(selectedMonth.value) {
        val candidateYears = loadCandidateYears(selectedMonth.value, today)
        if (! candidateYears.contains(selectedYear.value)) {
            selectedYear.value = candidateYears.get(candidateYears.size-1)
        }
    }

    LaunchedEffect(selectedYear.value) {
        val candidateMonths = loadCandidateMonths(selectedYear.value.toInt(), today)
        if (! candidateMonths.contains(selectedMonth.value)) {
            selectedMonth.value = candidateMonths.get(candidateMonths.size-1)
        }
    }

    Scaffold(
        backgroundColor = samouraiBottomSheetBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(id = R.string.action_sweep), fontSize = 13.sp, color = samouraiAccent)
                },
                backgroundColor = samouraiBottomSheetBackground,
                elevation = 0.dp,
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(top = 24.dp)
                .padding(horizontal = 24.dp),
            Arrangement.Center
        ) {
            AnimatedVisibility(visible = isOffline) {
                Text(
                    text = stringResource(id = R.string.in_offline_mode),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(
                            vertical = 12.dp,
                            horizontal = 2.dp
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(samouraiWarning)
                        .padding(
                            12.dp
                        )
                        .fillMaxWidth(),
                    color = Color.White
                )
            }
            TextField(value = addressEdit.value,
                modifier = Modifier
                    .fillMaxWidth(),
                onValueChange = {
                    addressEdit.value = it
                }, colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = samouraiTextFieldBg,
                    cursorColor = samouraiAccent
                ), label = {
                    Text(stringResource(id = R.string.enter_privkey), color = Color.White)
                }, textStyle = TextStyle(fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(onDone = {
                        keyboardController?.hide()
                }),
                isError = addressValidationError != null,
                trailingIcon = {
                    Icon(
                        painter = if (addressEdit.value.isNullOrEmpty()) painterResource(id = R.drawable.qrcode_scan)
                        else painterResource(id = R.drawable.ic_close_white_24dp),
                        contentDescription = "Clear/Scan",
                        modifier = Modifier.clickable {
                            if (addressEdit.value.isEmpty()) {
                                if (supportFragmentManager != null) {
                                    val cameraFragmentBottomSheet = CameraFragmentBottomSheet()
                                    cameraFragmentBottomSheet.show(supportFragmentManager, cameraFragmentBottomSheet.tag)
                                    cameraFragmentBottomSheet.setQrCodeScanListener {
                                        cameraFragmentBottomSheet.dismiss()
                                        addressEdit.value = it
                                    }
                                }
                            } else {
                                addressEdit.value = "";
                                vm.clear()
                            }
                        }
                    )
                })
            AnimatedVisibility(visible = addressValidationError != null) {
                Text(
                    text = addressValidationError ?: "",
                    maxLines = 2,
                    modifier = Modifier.padding(vertical = 8.dp),
                    overflow = TextOverflow.Ellipsis,
                    color = samouraiError, fontSize = 13.sp
                )
            }
            if (keyFormat == PrivKeyReader.BIP38) TextField(
                value = passphraseEntry ?: "",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                onValueChange = {
                    passphraseEntry = it
                },
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = samouraiTextFieldBg,
                    cursorColor = samouraiAccent
                ),
                label = {
                    Text(stringResource(id = R.string.bip38_pw), color = Color.White)
                },
                textStyle = TextStyle(fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    keyboardController?.hide()
                }),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column {
                Row (
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { switchFBState.value = !switchFBState.value }
                ) {
                    Switch(
                        checked = switchFBState.value,
                        onCheckedChange = {switchFBState.value = it},
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Timelocked fidelity bond",
                    )
                }
                if (true) {
                    Spacer(modifier = Modifier.width(16.dp))
                    Row {
                        DropDownTextField(
                            modifier = Modifier
                                .alpha(if (switchFBState.value) 1f else 0f)
                                .fillMaxWidth()
                                .weight(1.6f),
                            label = "Month",
                            value = selectedMonth,
                            onOptionSelected = {
                                selectedMonth.value = it
                            },
                            options = months,
                            enable = switchFBState,
                        )

                        Spacer(modifier = Modifier.width(30.dp))

                        DropDownTextField(
                            modifier = Modifier
                                .alpha(if (switchFBState.value) 1f else 0f)
                                .fillMaxWidth()
                                .weight(1.6f),
                            label = "Year",
                            value = selectedYear,
                            onOptionSelected = {
                                selectedYear.value = it
                            },
                            options = years,
                            enable = switchFBState,
                        )
                    }
                }
            }

            Button(
                onClick = {
                    keyboardController?.hide()

                    val timelockDerivationIndex = computeTimelockDerivationIndex(
                        selectedYear.value,
                        selectedMonth.value,
                        switchFBState.value)

                    vm.setAddress(
                        addressEdit.value,
                        context,
                        passphraseEntry,
                        timelockDerivationIndex)
                },
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.textButtonColors(
                    backgroundColor = samouraiAccent,
                    contentColor = Color.White
                ),
            ) {
                AnimatedVisibility(visible = !loading) {
                    Text(
                        stringResource(R.string.preview_sweep),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                AnimatedVisibility(visible = loading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

fun loadCandidateMonths(
    selectedYear: Int,
    asofDate: Calendar
):List<String> {

    val candidateMonths = Lists.newArrayList<String>()
    Lists.newArrayList(indexToMonth.keys)
    val calendar = Calendar.getInstance()

    for (indexToMonthEntry in indexToMonth.entries) {
        calendar[selectedYear, indexToMonthEntry.key, 1, 0] = 0
        if (asofDate.after(calendar)) {
            candidateMonths.add(indexToMonthEntry.value)
        } else {
            break
        }
    }
    return candidateMonths
}

private fun loadCandidateYears(
    selectedMonth: String?,
    asofDate: Calendar
):List<String> {

    val candidateYears = Lists.newArrayList<String>()
    val currentYear = asofDate[Calendar.YEAR]
    val calendar = Calendar.getInstance()

    for (candidateYear in 2020..currentYear) {
        val monthIndex = monthToIndex.get(selectedMonth) ?: 0
        calendar[candidateYear, monthIndex, 1, 0] = 0
        if (asofDate.after(calendar)) {
            candidateYears.add("$candidateYear")
        } else {
            break
        }
    }
    return candidateYears
}

fun computeTimelockDerivationIndex(
    selectedYear: String,
    selectedMonth: String?,
    fidelityBondsMode: Boolean
): Int {

    if (fidelityBondsMode) {
        val monthIndex = monthToIndex.get(selectedMonth) ?: 0
        val yearIndex = selectedYear.toInt() - 2020
        return yearIndex * 12 + monthIndex
    }
    return -1
}


@Composable
fun SweepTransactionPreview() {
    val vm = viewModel<SweepViewModel>()
    val context = LocalContext.current
    val sweepAddress by vm.getSweepAddressLive().observeAsState()
    val sweepAmount by vm.getAmountLive().observeAsState()
    val validFees by vm.getValidFees().observeAsState(true)
    val dustOutput by vm.getDustStatus().observeAsState(true)
    val sweepFees by vm.getSweepFees().observeAsState()
    val receiveAddressType by vm.getReceiveAddressType().observeAsState()
    var showAdvanceOption by rememberSaveable { mutableStateOf(false) }
    var size by rememberSaveable { mutableStateOf(490) }
    var receiveAddress by rememberSaveable { mutableStateOf("") }
    val density = LocalDensity.current
    var confirmDialog by remember { mutableStateOf(false) }
    var satsFormat by remember { mutableStateOf(true) }

    LaunchedEffect(receiveAddressType) {
        receiveAddress = AddressFactory.getInstance().getAddress(receiveAddressType).right
    }

    Box(modifier = Modifier
        .onGloballyPositioned {
            size = it.size.height
        }) {
        Scaffold(
            backgroundColor = samouraiBottomSheetBackground,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(id = R.string.action_sweep), fontSize = 13.sp, color = samouraiAccent)
                    },
                    backgroundColor = samouraiBottomSheetBackground,
                    elevation = 0.dp,
                    actions = {
                        IconButton(onClick = {
                            showAdvanceOption = true
                        }) {
                            Icon(painter = painterResource(id = R.drawable.ic_cogs), contentDescription = "", tint = Color.White)
                        }
                    }
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(Modifier.weight(.8f)) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {

                        item {
                            ListItem(
                                title = stringResource(id = R.string.address),
                                value = "$sweepAddress"
                            )
                        }

                        item {
                            ListItem(
                                modifier = Modifier.clickable {
                                    satsFormat = !satsFormat
                                },
                                title = stringResource(R.string.balance_of_unspent),
                                value = "${if (satsFormat) FormatsUtil.formatSats(sweepAmount) else FormatsUtil.formatBTC(sweepAmount)} "
                            )
                        }
                        item {
                            ListItem(
                                modifier = Modifier.clickable {
                                    satsFormat = !satsFormat
                                },
                                title = stringResource(id = R.string.cost_of_sweep),
                                value = "${if (satsFormat) FormatsUtil.formatSats(sweepFees) else FormatsUtil.formatBTC(sweepFees)} "
                            )
                        }
                        item {
                            ListItem(
                                title = stringResource(id = R.string.receive_address),
                                value = receiveAddress
                            )
                        }
                        item {
                            SliderSegment()
                        }
                        item {
                            SweepEstimatedBlockConfirm()
                        }
                    }
                }
                Box(modifier = Modifier.padding(vertical = 12.dp)) {
                    val enable = validFees && !dustOutput;
                    Button(
                        enabled = enable,
                        onClick = {
                            if (enable)
                                confirmDialog = true
                        },
                        contentPadding = PaddingValues(
                            vertical = 12.dp
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            backgroundColor = if (enable) samouraiAccent else samouraiSlateGreyAccent.copy(alpha = 0.6f),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(text = stringResource(id = R.string.sweep))
                    }
                }

            }


        }
        AnimatedVisibility(
            enter = slideInVertically {
                with(density) { -size.dp.roundToPx() }
            },
            exit = slideOutVertically(
                targetOffsetY = { -it },
            ),
            modifier = Modifier
                .fillMaxSize(),
            visible = showAdvanceOption
        ) {
            SweepAdvanceOption(
                onClose = {
                    showAdvanceOption = false
                }
            )
        }
    }

    if (confirmDialog) {
        AlertDialog(
            onDismissRequest = {
                confirmDialog = false
            },
            text = {
                Text("${stringResource(id = R.string.sweep)} ${Coin.valueOf(sweepAmount ?: 0L).toPlainString()} from $sweepAddress  (fee: ${Coin.valueOf(sweepFees ?: 0L).toPlainString()})?")
            },
            modifier = Modifier.shadow(24.dp),
            shape = RoundedCornerShape(16.dp),
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.initiateSweep(context)
                    }) {
                    Text(stringResource(id = R.string.confirm), color = samouraiTextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmDialog = false
                    }) {
                    Text(stringResource(id = R.string.cancel), color = samouraiTextPrimary)
                }
            }
        )
    }

}

@Composable
fun SweepEstimatedBlockConfirm() {
    val vm = viewModel<SweepViewModel>()
    val nbBlocks by vm.getBlockWaitTime().observeAsState()
    ListItem(
        title = stringResource(id = R.string.estimated_confirmation_time),
        value = nbBlocks.toString()
    )
}

@Composable
fun SliderSegment() {
    val vm = viewModel<SweepViewModel>()
    var sliderPosition by rememberSaveable { mutableStateOf(0.5f) }
    val feeRange by vm.getFeeRangeLive().observeAsState(0.5f)
    val satsPerByte by vm.getFeeSatsValueLive().observeAsState()
    val validFees by vm.getValidFees().observeAsState(true)
    val dustOutput by vm.getDustStatus().observeAsState(false)
    LaunchedEffect(true) {
        sliderPosition = feeRange
    }
    val context = LocalContext.current
    Column(modifier = Modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Slider(value = sliderPosition,
                modifier = Modifier
                    .weight(.7f)
                    .padding(horizontal = 2.dp),
                colors = SliderDefaults.colors(
                    thumbColor = if (validFees) samouraiAccent else samouraiError,
                    activeTickColor = if (validFees) samouraiAccent else samouraiError,
                    activeTrackColor = if (validFees) samouraiAccent else samouraiError,
                    inactiveTickColor = Color.Gray,
                    inactiveTrackColor = samouraiWindow
                ),
                onValueChange = {
                    sliderPosition = it;
                    vm.setFeeRange(it, context = context)
                })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(.3f)
            ) {
                Text(
                    text = if (validFees) "$satsPerByte sats/vB" else "_.__",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                )
            }
        }
        AnimatedVisibility(visible = !validFees) {
            Text(
                text = stringResource(R.string.sweep_invalid_fee_warning),
                fontSize = 10.sp,
                color = samouraiError,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        AnimatedVisibility(visible = dustOutput) {
            Text(
                text = stringResource(R.string.sweep_dust_warning),
                fontSize = 10.sp,
                color = samouraiError,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
fun SweepAdvanceOption(onClose: () -> Unit) {
    val list = stringArrayResource(id = R.array.address_types)
    val vm = viewModel<SweepViewModel>()
    val item by vm.getReceiveAddressType().observeAsState()
    val selectedItem = when (item) {
        WALLET_INDEX.BIP84_RECEIVE -> list[0]
        WALLET_INDEX.BIP49_RECEIVE -> list[1]
        WALLET_INDEX.BIP44_RECEIVE -> list[2]
        else -> list[0]
    }
    var selectedItemState = remember { mutableStateOf(selectedItem) }

    Scaffold(
        backgroundColor = samouraiBottomSheetBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "Advance options", fontSize = 13.sp, color = samouraiAccent)
                },
                backgroundColor = samouraiBottomSheetBackground,
                elevation = 0.dp,
                navigationIcon = {
                    IconButton(onClick = {
                        onClose()
                    }) {
                        Icon(
                            painter =
                            painterResource(id = R.drawable.ic_close_white_24dp),
                            contentDescription = "", tint = samouraiAccent
                        )
                    }
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(samouraiBottomSheetBackground)
        ) {
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = CenterHorizontally
            ) {
                DropDownTextField(
                    modifier = Modifier
                        .fillMaxWidth(),
                    value = selectedItemState,
                    label = stringResource(id = R.string.address_type),
                    onOptionSelected = {
                        val index = when (it) {
                            list[0] -> WALLET_INDEX.BIP84_RECEIVE
                            list[1] -> WALLET_INDEX.BIP49_RECEIVE
                            list[2] -> WALLET_INDEX.BIP44_RECEIVE
                            else -> WALLET_INDEX.BIP84_RECEIVE
                        }
                        vm.setAddressType(index)
                    },
                    options = list.toList()
                )
                Column(
                    Modifier.padding(vertical = 24.dp)
                ) {
                    Text(
                        "Sweep to address",
                        fontSize = 16.sp, color = samouraiTextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.padding(top = 4.dp))
                    Text(
                        AddressFactory.getInstance().getAddress(item).right, fontSize = 13.sp, color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ListItem(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(
            vertical = 1.dp,
        )
    ) {
        Text(
            text = title,
            fontSize = 13.sp,
            color = samouraiTextSecondary,
            style = MaterialTheme.typography.subtitle2
        )
        Text(
            text = value,
            style = MaterialTheme.typography.subtitle2,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
@Preview(widthDp = 320, heightDp = 480)
fun SweepPreviewCompose() {
    SweepTransactionPreview()
}

@Composable
@Preview(widthDp = 320, heightDp = 480)
fun SweepFormPreview() {
    SweepFormSweepForm(null)
}


@Composable
@Preview(widthDp = 320, heightDp = 480)
fun SweepBroadcastPreview() {
    SweepBroadcast()
}


class SweepPrivateKey  {
    companion object {

        val MONTH_JANUARY = "January"

        var monthToIndex = mapOf(
            MONTH_JANUARY to 0,
            "February" to 1,
            "March" to 2,
            "April" to 3,
            "May" to 4,
            "June" to 5,
            "July" to 6,
            "August" to 7,
            "September" to 8,
            "October" to 9,
            "November" to 10,
            "December" to 11
        )

        var indexToMonth = mapOf(
            0 to MONTH_JANUARY,
            1 to "February",
            2 to "March",
            3 to "April",
            4 to "May",
            5 to "June",
            6 to "July",
            7 to "August",
            8 to "September",
            9 to "October",
            10 to "November",
            11 to "December"
        )
    }
}