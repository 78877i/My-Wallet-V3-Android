package piuk.blockchain.android.coincore.impl

import androidx.annotation.VisibleForTesting
import com.blockchain.core.price.ExchangeRate
import com.blockchain.core.price.ExchangeRatesDataManager
import com.blockchain.core.price.HistoricalRate
import com.blockchain.core.price.HistoricalRateList
import com.blockchain.core.price.HistoricalTimeSpan
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.logging.CrashLogger
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.wallet.DefaultLabels
import info.blockchain.balance.AssetInfo
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.Singles
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.AssetAction
import piuk.blockchain.android.coincore.AssetFilter
import piuk.blockchain.android.coincore.CryptoAccount
import piuk.blockchain.android.coincore.CryptoAsset
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.coincore.NonCustodialAccount
import piuk.blockchain.android.coincore.SingleAccount
import piuk.blockchain.android.coincore.SingleAccountList
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.android.identity.UserIdentity
import piuk.blockchain.android.thepit.PitLinking
import piuk.blockchain.androidcore.data.payload.PayloadDataManager
import piuk.blockchain.androidcore.utils.helperfunctions.unsafeLazy
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

interface AccountRefreshTrigger {
    fun forceAccountsRefresh()
}

internal abstract class CryptoAssetBase(
    protected val payloadManager: PayloadDataManager,
    protected val exchangeRates: ExchangeRatesDataManager,
    protected val currencyPrefs: CurrencyPrefs,
    protected val labels: DefaultLabels,
    protected val custodialManager: CustodialWalletManager,
    private val pitLinking: PitLinking,
    protected val crashLogger: CrashLogger,
    protected val identity: UserIdentity,
    protected val features: InternalFeatureFlagApi
) : CryptoAsset, AccountRefreshTrigger {

    private val activeAccounts: ActiveAccountList by unsafeLazy {
        ActiveAccountList(asset, custodialManager)
    }

    protected val accounts: Single<SingleAccountList>
        get() = activeAccounts.fetchAccountList(::loadAccounts).flatMap {
            updateLabelsIfNeeded(it).toSingle { it }
        }

    private fun updateLabelsIfNeeded(list: SingleAccountList): Completable =
        Completable.concat(
            list.map {
                val cryptoNonCustodialAccount = it as? CryptoNonCustodialAccount
                if (cryptoNonCustodialAccount?.labelNeedsUpdate() == true) {
                    cryptoNonCustodialAccount.updateLabel(
                        cryptoNonCustodialAccount.label.replace(
                            labels.getOldDefaultNonCustodialWalletLabel(asset),
                            labels.getDefaultNonCustodialWalletLabel()
                        )
                    ).doOnError { error ->
                        crashLogger.logException(error)
                    }.onErrorComplete()
                } else {
                    Completable.complete()
                }
            }
        )

    override val isEnabled: Boolean
        get() = true

    // Init token, set up accounts and fetch a few activities
    override fun init(): Completable =
        initToken()
            .doOnError { throwable ->
                crashLogger.logException(throwable, "Coincore: Failed to load $asset wallet")
            }
            .doOnComplete { Timber.d("Coincore: Init $asset Complete") }
            .doOnError { Timber.d("Coincore: Init $asset Failed") }

    private fun loadAccounts(): Single<SingleAccountList> =
        Single.zip(
            loadNonCustodialAccounts(labels),
            loadCustodialAccounts(),
            loadInterestAccounts()
        ) { nc, c, i ->
            nc + c + i
        }.doOnError {
            val errorMsg = "Error loading accounts for ${asset.ticker}"
            Timber.e("$errorMsg: $it")
            crashLogger.logException(it, errorMsg)
        }

    private fun CryptoNonCustodialAccount.labelNeedsUpdate(): Boolean {
        val regex = """${labels.getOldDefaultNonCustodialWalletLabel(asset)}(\s?)([\d]*)""".toRegex()
        return label.matches(regex)
    }

    final override fun forceAccountsRefresh() {
        activeAccounts.setForceRefresh()
    }

    abstract fun initToken(): Completable

    abstract fun loadCustodialAccounts(): Single<SingleAccountList>
    abstract fun loadNonCustodialAccounts(labels: DefaultLabels): Single<SingleAccountList>

    private fun loadInterestAccounts(): Single<SingleAccountList> =
        custodialManager.getInterestAvailabilityForAsset(asset)
            .map {
                if (it) {
                    listOf(
                        CryptoInterestAccount(
                            asset,
                            labels.getDefaultInterestWalletLabel(),
                            custodialManager,
                            exchangeRates,
                            features
                        )
                    )
                } else {
                    emptyList()
                }
            }

    override fun interestRate(): Single<Double> = custodialManager.getInterestAccountRates(asset)

    final override fun accountGroup(filter: AssetFilter): Maybe<AccountGroup> =
        accounts.flatMapMaybe {
            Maybe.fromCallable {
                it.makeAccountGroup(asset, labels, filter)
            }
        }

    final override fun defaultAccount(): Single<SingleAccount> =
        accounts.map { it.first { a -> a.isDefault } }

    private fun getNonCustodialAccountList(): Single<SingleAccountList> =
        accountGroup(filter = AssetFilter.NonCustodial)
            .map { group -> group.accounts }
            .defaultIfEmpty(emptyList())

    final override fun exchangeRate(): Single<ExchangeRate> =
        Single.fromCallable { exchangeRates.getLastCryptoToUserFiatRate(asset) }

    final override fun exchangeRateYesterday(): Single<ExchangeRate> {
        // TODO: Make this a distinct call on ExchangeRates, because we're going to want to cache it
        val yesterday = (System.currentTimeMillis() / 1000) - SECONDS_PER_DAY
        return exchangeRates.getHistoricRate(asset, yesterday)
    }

    final override fun historicRate(epochWhen: Long): Single<ExchangeRate> =
        exchangeRates.getHistoricRate(asset, epochWhen)

    override fun historicRateSeries(period: HistoricalTimeSpan): Single<HistoricalRateList> =
        asset.startDate?.let {
            exchangeRates.getHistoricPriceSeries(asset, period)
        } ?: Single.just(emptyList())

    override fun lastDayTrend(): Single<List<HistoricalRate>> =
        asset.startDate?.let {
            exchangeRates.get24hPriceSeries(asset)
        } ?: Single.just(emptyList())

    private fun getPitLinkingTargets(): Maybe<SingleAccountList> =
        pitLinking.isPitLinked().filter { it }
            .flatMap { custodialManager.getExchangeSendAddressFor(asset) }
            .map { address ->
                listOf(
                    CryptoExchangeAccount(
                        asset = asset,
                        label = labels.getDefaultExchangeWalletLabel(),
                        address = address,
                        exchangeRates = exchangeRates
                    )
                )
            }

    private fun getInterestTargets(): Maybe<SingleAccountList> =
        custodialManager.getInterestEligibilityForAsset(asset).flatMapMaybe { eligibility ->
            if (eligibility.eligible) {
                accounts.flatMapMaybe {
                    Maybe.just(it.filterIsInstance<CryptoInterestAccount>())
                }
            } else {
                Maybe.empty()
            }
        }

    private fun getCustodialTargets(): Maybe<SingleAccountList> =
        accountGroup(AssetFilter.Custodial)
            .map { it.accounts }
            .onErrorComplete()

    private fun getNonCustodialTargets(exclude: SingleAccount? = null): Maybe<SingleAccountList> =
        getNonCustodialAccountList()
            .map { ll ->
                ll.filter { a -> a !== exclude }
            }.flattenAsObservable {
                it
            }.flatMapMaybe { account ->
                account.actions.flatMapMaybe {
                    if (it.contains(AssetAction.Receive)) {
                        Maybe.just(account)
                    } else Maybe.empty()
                }
            }.toList().toMaybe()

    final override fun transactionTargets(account: SingleAccount): Single<SingleAccountList> {
        require(account is CryptoAccount)
        require(account.asset == asset)

        return when (account) {
            is TradingAccount -> Maybe.concat(
                listOf(
                    getNonCustodialTargets(),
                    getInterestTargets()
                )
            ).toList()
                .map { ll -> ll.flatten() }
                .onErrorReturnItem(emptyList())
            is NonCustodialAccount ->
                Maybe.concat(
                    listOf(
                        getPitLinkingTargets(),
                        getInterestTargets(),
                        getCustodialTargets(),
                        getNonCustodialTargets(exclude = account)
                    )
                ).toList()
                    .map { ll -> ll.flatten() }
                    .onErrorReturnItem(emptyList())
            is InterestAccount -> {
                Maybe.concat(
                    getCustodialTargets(),
                    getNonCustodialTargets()
                ).toList()
                    .map { ll -> ll.flatten() }
                    .onErrorReturnItem(emptyList())
            }
            else -> Single.just(emptyList())
        }
    }

    companion object {
        private const val SECONDS_PER_DAY = 24 * 60 * 60
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
internal class ActiveAccountList(
    private val asset: AssetInfo,
    private val custodialManager: CustodialWalletManager
) {
    private val activeList = mutableSetOf<CryptoAccount>()

    private var interestEnabled = false
    private val forceRefreshOnNext = AtomicBoolean(true)

    fun setForceRefresh() {
        forceRefreshOnNext.set(true)
    }

    fun fetchAccountList(
        loader: () -> Single<SingleAccountList>
    ): Single<SingleAccountList> =
        shouldRefresh().flatMap { refresh ->
            if (refresh) {
                loader().map { updateWith(it) }
            } else {
                Single.just(activeList.toList())
            }
        }

    private fun shouldRefresh() =
        Singles.zip(
            Single.just(interestEnabled),
            custodialManager.getInterestAvailabilityForAsset(asset),
            Single.just(forceRefreshOnNext.getAndSet(false))
        ) { wasEnabled, isEnabled, force ->
            interestEnabled = isEnabled
            wasEnabled != isEnabled || force
        }.onErrorReturn { false }

    @Synchronized
    private fun updateWith(
        accounts: List<SingleAccount>
    ): List<CryptoAccount> {
        val newActives = mutableSetOf<CryptoAccount>()
        accounts.filterIsInstance<CryptoAccount>()
            .forEach { a ->
                val existing = activeList.find { it.matches(a) }
                if (existing != null) {
                    newActives.add(existing)
                } else {
                    newActives.add(a)
                }
            }
        activeList.clear()
        activeList.addAll(newActives)

        return activeList.toList()
    }
}
