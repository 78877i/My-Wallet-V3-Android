package piuk.blockchain.android.repositories

import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.TransactionType
import com.blockchain.nabu.datamanagers.repositories.ExpiringRepository
import info.blockchain.balance.CryptoCurrency
import io.reactivex.Maybe
import io.reactivex.Observable
import piuk.blockchain.android.coincore.AccountGroup
import piuk.blockchain.android.coincore.ActivitySummaryItem
import piuk.blockchain.android.coincore.ActivitySummaryList
import piuk.blockchain.android.coincore.BlockchainAccount
import piuk.blockchain.android.coincore.Coincore
import piuk.blockchain.android.coincore.CryptoActivitySummaryItem
import piuk.blockchain.android.coincore.CustodialInterestActivitySummaryItem
import piuk.blockchain.android.coincore.CustodialTradingActivitySummaryItem
import piuk.blockchain.android.coincore.CustodialTransferActivitySummaryItem
import piuk.blockchain.android.coincore.FiatActivitySummaryItem
import piuk.blockchain.android.coincore.InterestAccount
import piuk.blockchain.android.coincore.TradeActivitySummaryItem
import piuk.blockchain.android.coincore.TradingAccount
import piuk.blockchain.android.coincore.impl.AllWalletsAccount
import piuk.blockchain.android.coincore.impl.CryptoInterestAccount
import timber.log.Timber

class AssetActivityRepository(
    private val coincore: Coincore
) : ExpiringRepository<ActivitySummaryList>() {

    private val transactionCache = mutableListOf<ActivitySummaryItem>()

    fun fetch(
        account: BlockchainAccount,
        isRefreshRequested: Boolean
    ): Observable<ActivitySummaryList> {
        val cacheMaybe = if (isRefreshRequested || isCacheExpired()) Maybe.empty() else getFromCache()
        return Maybe.concat(
            cacheMaybe,
            requestNetwork(isRefreshRequested)
        )
            .toObservable()
            .map { list ->
                list.filter { item ->
                    when (account) {
                        is AccountGroup -> {
                            account.includes(item.account)
                        }
                        is CryptoInterestAccount -> {
                            account.asset == (item as? CustodialInterestActivitySummaryItem)?.cryptoCurrency
                        }
                        else -> {
                            account == item.account
                        }
                    }
                }
            }.map { filteredList ->
                if (account is AllWalletsAccount) {
                    reconcileTransfersAndBuys(filteredList)
                } else {
                    filteredList
                }.sorted()
            }.map { filteredList ->
                if (account is AllWalletsAccount) {
                    reconcileCustodialAndInterestTxs(filteredList)
                } else {
                    filteredList
                }.sorted()
            }.map { list ->
                Timber.d("Activity list size: ${list.size}")
                val pruned = list.distinct()
                Timber.d("Activity list pruned size: ${pruned.size}")
                pruned
            }
    }

    private fun reconcileTransfersAndBuys(list: ActivitySummaryList): List<ActivitySummaryItem> {
        val custodialWalletActivity = list.filter {
            it.account is TradingAccount && it is CustodialTradingActivitySummaryItem
        }
        val activityList = list.toMutableList()

        custodialWalletActivity.forEach { custodialItem ->
            val item = custodialItem as CustodialTradingActivitySummaryItem
            val matchingItem = activityList.find { a ->
                a.txId.contains(item.depositPaymentId)
            } as? FiatActivitySummaryItem

            if (matchingItem?.type == TransactionType.DEPOSIT) {
                activityList.remove(matchingItem)
                transactionCache.remove(matchingItem)
            }
        }

        return activityList.toList().sorted()
    }

    private fun reconcileCustodialAndInterestTxs(list: ActivitySummaryList): List<ActivitySummaryItem> {
        val interestWalletActivity = list.filter {
            it.account is InterestAccount && it is CustodialInterestActivitySummaryItem
        }
        val activityList = list.toMutableList()

        interestWalletActivity.forEach { interestItem ->
            val matchingItem = activityList.find { a ->
                a.txId.contains(interestItem.txId) && a is CustodialTransferActivitySummaryItem
            } as? CustodialTransferActivitySummaryItem

            if (matchingItem?.type == TransactionType.DEPOSIT || matchingItem?.type == TransactionType.WITHDRAWAL) {
                activityList.remove(matchingItem)
                transactionCache.remove(matchingItem)
            }
        }

        return activityList.toList().sorted()
    }

    fun findCachedItem(cryptoCurrency: CryptoCurrency, txHash: String): ActivitySummaryItem? =
        transactionCache.filterIsInstance<CryptoActivitySummaryItem>().find {
            it.cryptoCurrency == cryptoCurrency && it.txId == txHash
        }

    fun findCachedTradeItem(cryptoCurrency: CryptoCurrency, txHash: String): TradeActivitySummaryItem? =
        transactionCache.filterIsInstance<TradeActivitySummaryItem>().find {
            when (it.currencyPair) {
                is CurrencyPair.CryptoCurrencyPair -> it.currencyPair.source == cryptoCurrency && it.txId == txHash
                is CurrencyPair.CryptoToFiatCurrencyPair ->
                    it.currencyPair.source == cryptoCurrency && it.txId == txHash
            }
        }

    fun findCachedItem(currency: String, txHash: String): FiatActivitySummaryItem? =
        transactionCache.filterIsInstance<FiatActivitySummaryItem>().find {
            it.currency == currency && it.txId == txHash
        }

    fun findCachedItemById(txHash: String): ActivitySummaryItem? =
        transactionCache.find {
            it.txId == txHash
        }

    private fun requestNetwork(refreshRequested: Boolean): Maybe<ActivitySummaryList> {
        return if (refreshRequested || isCacheExpired()) {
            getFromNetwork()
        } else {
            Maybe.empty()
        }
    }

    override fun getFromNetwork(): Maybe<ActivitySummaryList> =
        coincore.allWallets()
            .flatMap { it.activity }
            .doOnSuccess { activityList ->
                // on error of activity returns onSuccess with empty list
                if (activityList.isNotEmpty()) {
                    transactionCache.clear()
                    transactionCache.addAll(activityList)
                }
                lastUpdatedTimestamp = System.currentTimeMillis()
            }.map { list ->
                // if network comes empty, but we have cache, return cache instead
                if (list.isEmpty() && transactionCache.isNotEmpty()) {
                    transactionCache
                } else {
                    list
                }
            }.toMaybe()

    override fun getFromCache(): Maybe<ActivitySummaryList> {
        return Maybe.just(transactionCache)
    }

    fun clear() {
        transactionCache.clear()
    }
}
