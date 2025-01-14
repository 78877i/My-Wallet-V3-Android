package piuk.blockchain.android.ui.dashboard.coinview

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.core.content.ContextCompat
import com.blockchain.coincore.AssetAction
import com.blockchain.coincore.AssetFilter
import com.blockchain.coincore.BlockchainAccount
import com.blockchain.coincore.CryptoAccount
import com.blockchain.commonarch.presentation.mvi.MviActivity
import com.blockchain.componentlib.basic.ComposeColors
import com.blockchain.componentlib.basic.ComposeTypographies
import com.blockchain.componentlib.basic.ImageResource
import com.blockchain.componentlib.button.ButtonState
import com.blockchain.componentlib.charts.PercentageChangeData
import com.blockchain.componentlib.databinding.ToolbarGeneralBinding
import com.blockchain.core.price.HistoricalRateList
import com.blockchain.core.price.HistoricalTimeSpan
import com.blockchain.core.price.Prices24HrWithDelta
import com.blockchain.koin.scopedInject
import com.blockchain.nabu.models.data.RecurringBuy
import com.blockchain.notifications.analytics.LaunchOrigin
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.AssetInfo
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatCurrency
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ActivityCoinviewBinding
import piuk.blockchain.android.simplebuy.CustodialBalanceClicked
import piuk.blockchain.android.simplebuy.SimpleBuyActivity
import piuk.blockchain.android.ui.customviews.BlockchainListDividerDecor
import piuk.blockchain.android.ui.customviews.BlockchainSnackbar
import piuk.blockchain.android.ui.customviews.account.PendingBalanceAccountDecorator
import piuk.blockchain.android.ui.dashboard.assetdetails.AssetDetailsItemNew
import piuk.blockchain.android.ui.dashboard.coinview.accounts.AccountsAdapterDelegate
import piuk.blockchain.android.ui.dashboard.coinview.recurringbuy.RecurringBuyDetailsSheet
import piuk.blockchain.android.ui.recurringbuy.RecurringBuyAnalytics
import piuk.blockchain.android.ui.recurringbuy.onboarding.RecurringBuyOnboardingActivity
import piuk.blockchain.android.ui.resources.AssetResources
import piuk.blockchain.android.ui.transactionflow.flow.TransactionFlowActivity
import piuk.blockchain.android.ui.transfer.receive.detail.ReceiveDetailSheet
import timber.log.Timber

class CoinViewActivity :
    MviActivity<CoinViewModel, CoinViewIntent, CoinViewState, ActivityCoinviewBinding>(),
    RecurringBuyDetailsSheet.Host {

    override val alwaysDisableScreenshots: Boolean
        get() = false

    override val toolbarBinding: ToolbarGeneralBinding
        get() = binding.toolbar

    override val model: CoinViewModel by scopedInject()

    private val assetTicker: String by lazy {
        intent.getStringExtra(ASSET_TICKER) ?: ""
    }

    private val assetName: String by lazy {
        intent.getStringExtra(ASSET_NAME) ?: ""
    }

    private val labels: DefaultLabels by inject()
    private val assetResources: AssetResources by inject()
    private val listItems = mutableListOf<AssetDetailsItemNew>()

    override fun initBinding(): ActivityCoinviewBinding = ActivityCoinviewBinding.inflate(layoutInflater)

    private val adapterDelegate by lazy {
        AccountsAdapterDelegate(
            onAccountSelected = ::onAccountSelected,
            labels = labels,
            onCardClicked = ::openOnboardingForRecurringBuy,
            onRecurringBuyClicked = ::onRecurringBuyClicked,
            assetDetailsDecorator = {
                PendingBalanceAccountDecorator(it.account)
            },
            assetResources = assetResources
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        updateToolbar(toolbarTitle = assetName, backAction = { onBackPressed() })
        with(binding) {
            assetList.apply {
                adapter = adapterDelegate
                addItemDecoration(BlockchainListDividerDecor(this@CoinViewActivity))
            }

            // TODO update these in relevant story - placeholder texts
            assetAboutTitle.apply {
                text = getString(R.string.coinview_about_asset, assetName)
                textColor = ComposeColors.Title
                style = ComposeTypographies.Body2
            }

            assetAboutBlurb.apply {
                text =
                    "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut" +
                    " labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco " +
                    "laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in" +
                    " voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat " +
                    "cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
                textColor = ComposeColors.Title
                style = ComposeTypographies.Paragraph1
            }

            assetChartViewSwitcher.displayedChild = CHART_LOADING
            assetAccountsViewSwitcher.displayedChild = ACCOUNTS_LOADING
            assetPricesSwitcher.displayedChild = PRICES_LOADING
            assetBalancesSwitcher.displayedChild = BALANCES_LOADING

            assetChart.isChartLive = false

            chartControls.apply {
                items = listOf(
                    getString(R.string.coinview_chart_tab_day),
                    getString(R.string.coinview_chart_tab_week),
                    getString(R.string.coinview_chart_tab_month),
                    getString(R.string.coinview_chart_tab_year),
                    getString(R.string.coinview_chart_tab_all)
                )
                onItemSelected = {
                    model.process(CoinViewIntent.LoadNewChartPeriod(HistoricalTimeSpan.fromInt(it)))
                }
                selectedItemIndex = 0
                showLiveIndicator = false
            }

            assetBalance.apply {
                onIconClick = {
                    // model.process(ToggleWatchlist)
                }
            }

            showLoadingCtas()

            // TODO in upcoming stories
            assetWebsite.apply {
                text = "Visit Website ->"
                textColor = ComposeColors.Primary
                style = ComposeTypographies.Paragraph2
                onClick = {
                    BlockchainSnackbar.make(binding.root, "Website link clicked").show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.process(CoinViewIntent.LoadAsset(assetTicker))
    }

    override fun render(newState: CoinViewState) {
        newState.asset?.let {
            with(binding) {
                assetAboutTitle.text = getString(R.string.coinview_about_asset, it.assetInfo.name)
                assetPrice.endIcon = ImageResource.Remote(it.assetInfo.logo)
            }
        }

        if (newState.viewState != CoinViewViewState.None) {
            renderUiState(newState)
        }

        if (newState.error != CoinViewError.None) {
            Timber.e("---- error in coinview ${newState.error.name}")

            model.process(CoinViewIntent.ResetErrorState)
        }
    }

    private fun renderUiState(newState: CoinViewState) {
        when (val state = newState.viewState) {
            CoinViewViewState.LoadingWallets,
            CoinViewViewState.LoadingRecurringBuys -> {
                binding.assetAccountsViewSwitcher.displayedChild = ACCOUNTS_LOADING
            }
            is CoinViewViewState.ShowAccountInfo -> {
                renderAccountsDetails(state.assetInfo.accountsList)
                renderBalanceInformation(state.assetInfo.totalCryptoBalance, state.assetInfo.totalFiatBalance)

                binding.assetAccountsViewSwitcher.displayedChild = ACCOUNTS_LIST
            }
            CoinViewViewState.LoadingChart -> {
                binding.assetChartViewSwitcher.displayedChild = CHART_LOADING
            }
            is CoinViewViewState.ShowAssetInfo -> {
                with(binding) {
                    assetChartViewSwitcher.displayedChild = CHART_VIEW
                    assetChart.setData(state.entries)
                }
                renderPriceInformation(state.prices, state.historicalRateList, state.selectedFiat)
            }
            is CoinViewViewState.ShowRecurringBuys -> renderRecurringBuys(state.recurringBuys)
            CoinViewViewState.LoadingQuickActions -> showLoadingCtas()
            is CoinViewViewState.QuickActionsLoaded -> {
                newState.asset?.let { asset ->
                    renderQuickActions(asset.assetInfo, state.actionableAccount, state.startAction, state.endAction)
                }
            }
            CoinViewViewState.None -> {
                // do nothing
            }
        }

        model.process(CoinViewIntent.ResetViewState)
    }

    private fun showLoadingCtas() {
        with(binding) {
            primaryCta.buttonState = ButtonState.Loading
            secondaryCta.buttonState = ButtonState.Loading
        }
    }

    private fun renderQuickActions(
        asset: AssetInfo,
        highestBalanceWallet: BlockchainAccount,
        startAction: QuickActionCta,
        endAction: QuickActionCta
    ) {
        with(binding) {
            val startButtonResources = getQuickActionUi(asset, highestBalanceWallet, startAction)
            val endButtonResources = getQuickActionUi(asset, highestBalanceWallet, endAction)
            primaryCta.apply {
                buttonState = ButtonState.Enabled
                text = endButtonResources.name
                icon = endButtonResources.icon
                onClick = {
                    endButtonResources.onClick()
                }
            }

            secondaryCta.apply {
                buttonState = ButtonState.Enabled
                text = startButtonResources.name
                icon = startButtonResources.icon
                onClick = {
                    startButtonResources.onClick()
                }
            }
        }
    }

    private fun getQuickActionUi(
        asset: AssetInfo,
        highestBalanceWallet: BlockchainAccount,
        action: QuickActionCta
    ): QuickAction =
        when (action) {
            QuickActionCta.Buy -> QuickAction(
                getString(R.string.common_buy),
                ImageResource.Local(
                    R.drawable.ic_bottom_nav_buy,
                    colorFilter = ColorFilter.tint(
                        Color(ContextCompat.getColor(this@CoinViewActivity, R.color.white))
                    )
                )
            ) {
                startActivity(
                    SimpleBuyActivity.newIntent(
                        context = this,
                        asset = asset
                    )
                )
            }
            QuickActionCta.Sell -> QuickAction(
                getString(R.string.common_sell),
                ImageResource.Local(
                    R.drawable.ic_fiat_notes,
                    colorFilter = ColorFilter.tint(
                        Color(ContextCompat.getColor(this@CoinViewActivity, R.color.white))
                    )
                )
            ) {
                startActivity(
                    TransactionFlowActivity.newIntent(
                        context = this,
                        action = AssetAction.Sell,
                        sourceAccount = highestBalanceWallet
                    )
                )
            }
            QuickActionCta.Send -> QuickAction(
                getString(R.string.common_send),
                ImageResource.Local(
                    R.drawable.ic_icon_send,
                    colorFilter = ColorFilter.tint(
                        Color(ContextCompat.getColor(this@CoinViewActivity, R.color.white))
                    )
                )
            ) {
                startActivity(
                    TransactionFlowActivity.newIntent(
                        context = this,
                        action = AssetAction.Send,
                        sourceAccount = highestBalanceWallet
                    )
                )
            }
            QuickActionCta.Receive -> QuickAction(
                getString(R.string.common_receive),
                ImageResource.Local(
                    R.drawable.ic_qr_scan,
                    colorFilter = ColorFilter.tint(
                        Color(ContextCompat.getColor(this@CoinViewActivity, R.color.white))
                    )
                )
            ) {
                showBottomSheet(ReceiveDetailSheet.newInstance(highestBalanceWallet as CryptoAccount))
            }
        }

    private fun renderRecurringBuys(recurringBuys: List<RecurringBuy>) {
        if (recurringBuys.isNotEmpty()) {
            val recurringBuysItems = recurringBuys.map {
                AssetDetailsItemNew.RecurringBuyInfo(it)
            }
            listItems.addAll(recurringBuysItems)
        } else {
            listItems.add(AssetDetailsItemNew.RecurringBuyBanner)
        }
        updateList()
    }

    private fun renderBalanceInformation(totalCryptoBalance: CryptoValue, totalFiatBalance: FiatValue) {
        with(binding) {
            assetBalance.apply {
                labelText = getString(R.string.coinview_balance_label, assetTicker)
                primaryText = totalFiatBalance.toStringWithSymbol()
                secondaryText = totalCryptoBalance.toStringWithSymbol()
            }
            assetBalancesSwitcher.displayedChild = BALANCES_VIEW
        }
    }

    private fun renderPriceInformation(
        prices: Prices24HrWithDelta,
        historicalRateList: HistoricalRateList,
        selectedFiat: FiatCurrency
    ) {
        val currentPrice = prices.currentRate.price.toStringWithSymbol()
        // We have filtered out nulls by here, so we can 'safely' default to zeros for the price
        val firstPrice: Double = historicalRateList.firstOrNull()?.rate ?: 0.0
        val lastPrice: Double = historicalRateList.lastOrNull()?.rate ?: 0.0
        val difference = lastPrice - firstPrice

        with(binding) {
            val percentChange =
                if (chartControls.selectedItemIndex == HistoricalTimeSpan.DAY.ordinal) {
                    prices.delta24h
                } else {
                    (difference / firstPrice) * 100
                }

            val changeDifference = Money.fromMajor(selectedFiat, difference.toBigDecimal()).toStringWithSymbol()

            assetPrice.apply {
                price = currentPrice
                percentageChangeData = PercentageChangeData(
                    changeDifference, percentChange / 100,
                    when (chartControls.selectedItemIndex) {
                        HistoricalTimeSpan.DAY.ordinal -> getString(R.string.coinview_price_day)
                        HistoricalTimeSpan.WEEK.ordinal -> getString(R.string.coinview_price_week)
                        HistoricalTimeSpan.MONTH.ordinal -> getString(R.string.coinview_price_month)
                        HistoricalTimeSpan.YEAR.ordinal -> getString(R.string.coinview_price_year)
                        HistoricalTimeSpan.ALL_TIME.ordinal -> getString(R.string.coinview_price_all)
                        else -> getString(R.string.empty)
                    }
                )
                title = getString(R.string.coinview_price_label, assetName)
            }

            assetPricesSwitcher.displayedChild = PRICES_VIEW
        }
    }

    private fun renderAccountsDetails(assetDetails: List<AssetDisplayInfo>) {
        val itemList = mutableListOf<AssetDetailsItemNew>()

        assetDetails.map {
            itemList.add(
                AssetDetailsItemNew.CryptoDetailsInfo(
                    assetFilter = it.filter,
                    account = it.account,
                    balance = it.amount,
                    fiatBalance = it.fiatValue,
                    actions = it.actions,
                    interestRate = it.interestRate
                )
            )
        }

        listItems.addAll(0, itemList)
        updateList()
    }

    private fun onAccountSelected(account: BlockchainAccount, assetFilter: AssetFilter) {
        // clearList()

        if (account is CryptoAccount && assetFilter == AssetFilter.Custodial) {
            analytics.logEvent(CustodialBalanceClicked(account.currency))
        }

        //        model.process(
        //            ShowAssetActionsIntent(account)
        //        )
    }

    private fun openOnboardingForRecurringBuy() {
        analytics.logEvent(RecurringBuyAnalytics.RecurringBuyLearnMoreClicked(LaunchOrigin.CURRENCY_PAGE))
        startActivity(
            RecurringBuyOnboardingActivity.newIntent(
                context = this,
                fromCoinView = true,
                assetTicker = assetTicker
            )
        )
    }

    private fun onRecurringBuyClicked(recurringBuy: RecurringBuy) {
        recurringBuy.asset.let {
            analytics.logEvent(
                RecurringBuyAnalytics.RecurringBuyDetailsClicked(
                    LaunchOrigin.CURRENCY_PAGE,
                    it.networkTicker
                )
            )
        }

        showBottomSheet(RecurringBuyDetailsSheet.newInstance(recurringBuy))
    }

    override fun onRecurringBuyDeleted(asset: AssetInfo) {
        model.process(CoinViewIntent.LoadRecurringBuys(asset))
    }

    private fun clearList() {
        listItems.clear()
        updateList()
    }

    private fun updateList() {
        adapterDelegate.items = listItems
        adapterDelegate.notifyDataSetChanged()
    }

    override fun onSheetClosed() {
        // do nothing
    }

    companion object {
        private const val CHART_LOADING = 0
        private const val CHART_VIEW = 1
        private const val ACCOUNTS_LOADING = 0
        private const val ACCOUNTS_LIST = 1
        private const val PRICES_LOADING = 0
        private const val PRICES_VIEW = 1
        private const val BALANCES_LOADING = 0
        private const val BALANCES_VIEW = 1
        private const val ASSET_TICKER = "ASSET_TICKER"
        private const val ASSET_NAME = "ASSET_NAME"

        fun newIntent(context: Context, asset: AssetInfo): Intent =
            Intent(context, CoinViewActivity::class.java).apply {
                putExtra(ASSET_TICKER, asset.networkTicker)
                putExtra(ASSET_NAME, asset.name)
            }
    }
}

private data class QuickAction(
    val name: String,
    val icon: ImageResource,
    val onClick: () -> Unit
)
