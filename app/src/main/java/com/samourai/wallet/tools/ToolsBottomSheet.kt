package com.samourai.wallet.tools

import AddressCalculator
import SweepPrivateKeyView
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.samourai.wallet.R
import com.samourai.wallet.theme.SamouraiWalletTheme
import com.samourai.wallet.theme.samouraiBottomSheetBackground
import kotlinx.coroutines.launch


class ToolsBottomSheet : BottomSheetDialogFragment() {

    enum class ToolType {
        ADDRESS_CALC,
        SIGN,
        SWEEP
    }

    var behavior: BottomSheetBehavior<*>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val parent = view.parent as View
        val params = parent.layoutParams as CoordinatorLayout.LayoutParams
        behavior = params.behavior as BottomSheetBehavior<*>?
        if (behavior != null) {
            behavior?.state = BottomSheetBehavior.STATE_EXPANDED
            behavior?.skipCollapsed = true
        }
        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        super.onViewCreated(view, savedInstanceState)
    }

    //Disables tools bottom sheet dragging
    //this will prevent accidental closing of tools dialog
    fun disableDragging(disable: Boolean = true) {
        behavior?.isDraggable = !disable
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val compose = ComposeView(requireContext())
        compose.setContent {
            SamouraiWalletTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    ToolsMainView(this, parentFragmentManager)
                }
            }
        }
        compose.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        container?.addView(compose)
        return compose
    }

    companion object {
        fun showTools(fragment: FragmentManager) {
            ToolsBottomSheet()
                .apply {
                    show(fragment, this.tag)
                }
        }

        fun showTools(fragment: FragmentManager, type: ToolType, bundle: Bundle? = null) {
            SingleToolBottomSheet(type)
                .apply {
                    arguments = bundle
                    show(fragment, this.tag)
                }
        }
    }

     class SingleToolBottomSheet(private val toolType: ToolType) : BottomSheetDialogFragment() {

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val compose = ComposeView(requireContext())
            val model: AddressCalculatorViewModel by viewModels()
            if (toolType != ToolType.SWEEP) {
                val types = requireContext().resources.getStringArray(R.array.account_types)
                model.calculateAddress(types.first(), true, index = 0, context = requireContext())
            }
            val key = arguments?.getString("KEY", "") ?: ""
            compose.setContent {
                SamouraiWalletTheme {
                    Surface(color = MaterialTheme.colors.background) {
                        when (toolType) {
                            ToolType.SWEEP -> SweepPrivateKeyView(parentFragmentManager, onCloseClick = {
                                this.dismiss()
                            }, keyParameter = key)
                            ToolType.SIGN -> SignMessage()
                            ToolType.ADDRESS_CALC -> AddressCalculator()
                        }
                    }
                }
            }
            compose.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            container?.addView(compose)
            return compose
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ToolsMainView(toolsBottomSheet: ToolsBottomSheet?, parentFragmentManager: FragmentManager?) {
    val vm = viewModel<AddressCalculatorViewModel>()
    val sweepViewModel = viewModel<SweepViewModel>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current;
    val addressCalcBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val signMessageBottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
    val sweepPrivateKeyBottomSheet = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)

    BackHandler(addressCalcBottomSheetState.isVisible) {
        scope.launch {
            addressCalcBottomSheetState.hide()
        }
    }
    BackHandler(signMessageBottomSheetState.isVisible) {
        scope.launch {
            sweepViewModel.clear()
            addressCalcBottomSheetState.hide()
        }
    }
    BackHandler(signMessageBottomSheetState.isVisible) {
        scope.launch {
            sweepPrivateKeyBottomSheet.hide()
        }
    }

    Scaffold {
        Column {
            TopAppBar(backgroundColor = MaterialTheme.colors.primary, title = { Text(text = "Tools ", color = Color.White) }, navigationIcon = {
                IconButton(onClick = {
                    toolsBottomSheet?.dismiss()
                }) {
                    Icon(painter = painterResource(id = R.drawable.ic_close_white_24dp), contentDescription = "", tint = Color.White)
                }
            }
            )
            ToolsItem(
                title = "Sweep Private Key",
                subTitle = "Enter a private key and sweep any funds to\n" +
                        "your next bitcoin address",
                icon = R.drawable.ic_broom,
                onClick = {
                    scope.launch {
                        toolsBottomSheet?.disableDragging()
                        sweepViewModel.clear()
                        sweepPrivateKeyBottomSheet.animateTo(ModalBottomSheetValue.Expanded)
                    }
                }
            )
            ToolsItem(
                title = "Sign message",
                subTitle = "Sign messages using your wallet private key",
                icon = R.drawable.ic_signature,
                onClick = {
                    scope.launch {
                        val types = context.resources.getStringArray(R.array.account_types)
                        vm.calculateAddress(types.first(), true, index = 0, context = context)
                        vm.clearMessage()
                        toolsBottomSheet?.disableDragging()
                        signMessageBottomSheetState.animateTo(ModalBottomSheetValue.Expanded)
                    }
                }
            )
            ToolsItem(
                title = "Wallet address calculator",
                subTitle = "Calculate any address derived from your " +
                        "wallet seed. Sign messages using your private" +
                        "key",
                icon = R.drawable.ic_calculator,
                onClick = {
                    scope.launch {
                        toolsBottomSheet?.disableDragging()
                        //Load first account type to viewmodel
                        val types = context.resources.getStringArray(R.array.account_types)
                        vm.calculateAddress(types.first(), true, index = 0, context = context)
                        vm.clearMessage()
                        addressCalcBottomSheetState.show()
                    }
                }
            )
        }

        //Clear previous address calc state
        if (addressCalcBottomSheetState.currentValue != ModalBottomSheetValue.Hidden) {
            DisposableEffect(Unit) {
                onDispose {
                    toolsBottomSheet?.disableDragging(disable = false)
                    val types = context.resources.getStringArray(R.array.account_types)
                    vm.calculateAddress(types.first(), true, index = 0, context = context)
                    vm.setPage(0)
                }
            }
        }
        if (signMessageBottomSheetState.currentValue != ModalBottomSheetValue.Hidden) {
            DisposableEffect(Unit) {
                onDispose {
                    val types = context.resources.getStringArray(R.array.account_types)
                    vm.calculateAddress(types.first(), true, index = 0, context = context)
                    toolsBottomSheet?.disableDragging(disable = false)
                }
            }
        }

        if (sweepPrivateKeyBottomSheet.currentValue != ModalBottomSheetValue.Hidden) {
            DisposableEffect(Unit) {
                onDispose {
                    sweepViewModel.clear()
                    toolsBottomSheet?.disableDragging(disable = false)
                }
            }
        }

        ModalBottomSheetLayout(
            sheetState = addressCalcBottomSheetState,
            scrimColor = Color.Black.copy(alpha = 0.7f),
            sheetBackgroundColor = samouraiBottomSheetBackground,
            sheetContent = {
                AddressCalculator()
            },
            sheetShape = MaterialTheme.shapes.small.copy(topEnd = CornerSize(12.dp), topStart = CornerSize(12.dp))
        ) {}

        ModalBottomSheetLayout(
            sheetState = sweepPrivateKeyBottomSheet,
            scrimColor = Color.Black.copy(alpha = 0.7f),
            sheetBackgroundColor = samouraiBottomSheetBackground,
            sheetContent = {
                SweepPrivateKeyView(parentFragmentManager, onCloseClick = {
                    scope.launch {
                        sweepPrivateKeyBottomSheet.hide()
                    }
                })
            },
            sheetShape = MaterialTheme.shapes.small.copy(topEnd = CornerSize(12.dp), topStart = CornerSize(12.dp))
        ) {}

        ModalBottomSheetLayout(
            sheetState = signMessageBottomSheetState,
            scrimColor = Color.Black.copy(alpha = 0.7f),
            sheetBackgroundColor = samouraiBottomSheetBackground,
            sheetContent = {
                SignMessage()
            },
            sheetShape = MaterialTheme.shapes.small.copy(topEnd = CornerSize(12.dp), topStart = CornerSize(12.dp))
        ) {
        }
    }
}

@Composable
fun ToolsItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    icon: Int,
    title: String,
    subTitle: String,
) {
    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onClick()
                }
                .border(0.dp, Color.Transparent) // outer border
                .padding(12.dp)
        ) {
            Box(modifier = Modifier.size(54.dp)) {
                Icon(
                    painter = painterResource(id = icon),
                    tint = MaterialTheme.colors.onSecondary,
                    modifier = Modifier
                        .size(34.dp)
                        .padding(end = 12.dp)
                        .align(Center)
                        .apply { },
                    contentDescription = null
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.size(5.dp))
                Text(
                    text = subTitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSecondary
                )
            }
        }
    }

}

@Preview(showBackground = true, heightDp = 80, widthDp = 410)
@Composable
fun DefaultToolsItemPreview() {
    SamouraiWalletTheme {
        Surface {
            ToolsItem(
                title = "Sweep Private Key",
                subTitle = "Enter a private key and sweep any funds to" +
                        "your next bitcoin address",
                icon = R.drawable.ic_broom
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    SamouraiWalletTheme {
        // A surface container using the 'background' color from the theme
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colors.background) {
            ToolsMainView(null, null)
        }
    }
}