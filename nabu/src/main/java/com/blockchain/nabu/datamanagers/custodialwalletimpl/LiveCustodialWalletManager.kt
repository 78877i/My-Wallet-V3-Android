package com.blockchain.nabu.datamanagers.custodialwalletimpl

import com.blockchain.featureflags.GatedFeature
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.nabu.Authenticator
import com.blockchain.nabu.datamanagers.ApprovalErrorStatus
import com.blockchain.nabu.datamanagers.Bank
import com.blockchain.nabu.datamanagers.BankAccount
import com.blockchain.nabu.datamanagers.BankState
import com.blockchain.nabu.datamanagers.BillingAddress
import com.blockchain.nabu.datamanagers.BuyOrderList
import com.blockchain.nabu.datamanagers.BuySellLimits
import com.blockchain.nabu.datamanagers.BuySellOrder
import com.blockchain.nabu.datamanagers.BuySellPair
import com.blockchain.nabu.datamanagers.BuySellPairs
import com.blockchain.nabu.datamanagers.CardToBeActivated
import com.blockchain.nabu.datamanagers.CryptoTransaction
import com.blockchain.nabu.datamanagers.CurrencyPair
import com.blockchain.nabu.datamanagers.CustodialOrder
import com.blockchain.nabu.datamanagers.CustodialOrderState
import com.blockchain.nabu.datamanagers.CustodialQuote
import com.blockchain.nabu.datamanagers.CustodialWalletManager
import com.blockchain.nabu.datamanagers.EligiblePaymentMethodType
import com.blockchain.nabu.datamanagers.EveryPayCredentials
import com.blockchain.nabu.datamanagers.FiatTransaction
import com.blockchain.nabu.datamanagers.InterestAccountDetails
import com.blockchain.nabu.datamanagers.InterestActivityItem
import com.blockchain.nabu.datamanagers.OrderState
import com.blockchain.nabu.datamanagers.Partner
import com.blockchain.nabu.datamanagers.PartnerCredentials
import com.blockchain.nabu.datamanagers.PaymentLimits
import com.blockchain.nabu.datamanagers.PaymentMethod
import com.blockchain.nabu.datamanagers.Product
import com.blockchain.nabu.datamanagers.RecurringBuyOrder
import com.blockchain.nabu.datamanagers.RecurringBuyTransaction
import com.blockchain.nabu.datamanagers.SimplifiedDueDiligenceUserState
import com.blockchain.nabu.datamanagers.TransactionErrorMapper
import com.blockchain.nabu.datamanagers.TransactionState
import com.blockchain.nabu.datamanagers.TransactionType
import com.blockchain.nabu.datamanagers.TransferDirection
import com.blockchain.nabu.datamanagers.TransferLimits
import com.blockchain.nabu.datamanagers.featureflags.BankLinkingEnabledProvider
import com.blockchain.nabu.datamanagers.featureflags.Feature
import com.blockchain.nabu.datamanagers.featureflags.FeatureEligibility
import com.blockchain.nabu.datamanagers.repositories.CustodialAssetWalletsBalancesRepository
import com.blockchain.nabu.datamanagers.repositories.RecurringBuyRepository
import com.blockchain.nabu.datamanagers.repositories.interest.Eligibility
import com.blockchain.nabu.datamanagers.repositories.interest.InterestLimits
import com.blockchain.nabu.datamanagers.repositories.interest.InterestRepository
import com.blockchain.nabu.datamanagers.repositories.swap.CustodialRepository
import com.blockchain.nabu.datamanagers.repositories.swap.TradeTransactionItem
import com.blockchain.nabu.models.data.BankPartner
import com.blockchain.nabu.models.data.BankTransferDetails
import com.blockchain.nabu.models.data.BankTransferStatus
import com.blockchain.nabu.models.data.CryptoWithdrawalFeeAndLimit
import com.blockchain.nabu.models.data.FiatWithdrawalFeeAndLimit
import com.blockchain.nabu.models.data.LinkBankTransfer
import com.blockchain.nabu.models.data.LinkedBank
import com.blockchain.nabu.models.data.LinkedBankErrorState
import com.blockchain.nabu.models.data.LinkedBankState
import com.blockchain.nabu.models.data.RecurringBuy
import com.blockchain.nabu.models.responses.banktransfer.BankInfoResponse
import com.blockchain.nabu.models.responses.banktransfer.BankMediaResponse.Companion.ICON
import com.blockchain.nabu.models.responses.banktransfer.BankTransferChargeAttributes
import com.blockchain.nabu.models.responses.banktransfer.BankTransferChargeResponse
import com.blockchain.nabu.models.responses.banktransfer.BankTransferPaymentAttributes
import com.blockchain.nabu.models.responses.banktransfer.BankTransferPaymentBody
import com.blockchain.nabu.models.responses.banktransfer.CreateLinkBankResponse
import com.blockchain.nabu.models.responses.banktransfer.LinkedBankTransferResponse
import com.blockchain.nabu.models.responses.banktransfer.OpenBankingTokenBody
import com.blockchain.nabu.models.responses.banktransfer.ProviderAccountAttrs
import com.blockchain.nabu.models.responses.banktransfer.UpdateProviderAccountBody
import com.blockchain.nabu.models.responses.banktransfer.WithdrawFeeRequest
import com.blockchain.nabu.models.responses.cards.CardResponse
import com.blockchain.nabu.models.responses.cards.PaymentMethodResponse
import com.blockchain.nabu.models.responses.interest.InterestAccountDetailsResponse
import com.blockchain.nabu.models.responses.interest.InterestActivityItemResponse
import com.blockchain.nabu.models.responses.interest.InterestRateResponse
import com.blockchain.nabu.models.responses.interest.InterestWithdrawalBody
import com.blockchain.nabu.models.responses.nabu.AddAddressRequest
import com.blockchain.nabu.models.responses.nabu.State
import com.blockchain.nabu.models.responses.simplebuy.AddNewCardBodyRequest
import com.blockchain.nabu.models.responses.simplebuy.BankAccountResponse
import com.blockchain.nabu.models.responses.simplebuy.BuyOrderListResponse
import com.blockchain.nabu.models.responses.simplebuy.BuySellOrderResponse
import com.blockchain.nabu.models.responses.simplebuy.ConfirmOrderRequestBody
import com.blockchain.nabu.models.responses.simplebuy.CustodialWalletOrder
import com.blockchain.nabu.models.responses.simplebuy.ProductTransferRequestBody
import com.blockchain.nabu.models.responses.simplebuy.RecurringBuyRequestBody
import com.blockchain.nabu.models.responses.simplebuy.SimpleBuyConfirmationAttributes
import com.blockchain.nabu.models.responses.simplebuy.TransactionResponse
import com.blockchain.nabu.models.responses.simplebuy.TransferRequest
import com.blockchain.nabu.models.responses.simplebuy.toRecurringBuy
import com.blockchain.nabu.models.responses.simplebuy.toRecurringBuyOrder
import com.blockchain.nabu.models.responses.simplebuy.toRecurringBuyTransaction
import com.blockchain.nabu.models.responses.swap.CreateOrderRequest
import com.blockchain.nabu.models.responses.swap.CustodialOrderResponse
import com.blockchain.nabu.models.responses.tokenresponse.NabuSessionTokenResponse
import com.blockchain.nabu.service.NabuService
import com.blockchain.preferences.CurrencyPrefs
import com.blockchain.preferences.SimpleBuyPrefs
import com.blockchain.remoteconfig.FeatureFlag
import com.blockchain.utils.fromIso8601ToUtc
import com.blockchain.utils.toLocalTime
import com.braintreepayments.cardform.utils.CardType
import info.blockchain.balance.CryptoCurrency
import info.blockchain.balance.CryptoValue
import info.blockchain.balance.FiatValue
import info.blockchain.balance.Money
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Single
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.flatMapIterable
import io.reactivex.rxkotlin.zipWith
import okhttp3.internal.toLongOrDefault
import java.math.BigInteger
import java.util.Calendar
import java.util.Date
import java.util.UnknownFormatConversionException

class LiveCustodialWalletManager(
    private val nabuService: NabuService,
    private val authenticator: Authenticator,
    private val simpleBuyPrefs: SimpleBuyPrefs,
    private val paymentAccountMapperMappers: Map<String, PaymentAccountMapper>,
    private val kycFeatureEligibility: FeatureEligibility,
    private val custodialAssetWalletsBalancesRepository: CustodialAssetWalletsBalancesRepository,
    private val interestRepository: InterestRepository,
    private val currencyPrefs: CurrencyPrefs,
    private val custodialRepository: CustodialRepository,
    private val achDepositWithdrawFeatureFlag: FeatureFlag,
    private val sddFeatureFlag: FeatureFlag,
    private val bankLinkingEnabledProvider: BankLinkingEnabledProvider,
    private val transactionErrorMapper: TransactionErrorMapper,
    private val recurringBuysRepository: RecurringBuyRepository,
    private val features: InternalFeatureFlagApi
) : CustodialWalletManager {

    override val defaultFiatCurrency: String
        get() = currencyPrefs.defaultFiatCurrency

    override fun getQuote(
        cryptoCurrency: CryptoCurrency,
        fiatCurrency: String,
        action: String,
        currency: String,
        amount: String
    ): Single<CustodialQuote> =
        authenticator.authenticate {
            nabuService.getSimpleBuyQuote(
                sessionToken = it,
                action = action,
                currencyPair = "${cryptoCurrency.networkTicker}-$fiatCurrency",
                currency = currency,
                amount = amount
            )
        }.map { quoteResponse ->
            val amountCrypto = CryptoValue.fromMajor(
                cryptoCurrency,
                (amount.toBigInteger().toFloat().div(quoteResponse.rate)).toBigDecimal()
            )
            CustodialQuote(
                date = quoteResponse.time.toLocalTime(),
                fee = FiatValue.fromMinor(
                    fiatCurrency,
                    quoteResponse.fee.times(amountCrypto.toBigInteger().toLong())
                ),
                estimatedAmount = amountCrypto,
                rate = FiatValue.fromMinor(fiatCurrency, quoteResponse.rate)
            )
        }

    override fun createOrder(
        custodialWalletOrder: CustodialWalletOrder,
        stateAction: String?
    ): Single<BuySellOrder> =
        authenticator.authenticate {
            nabuService.createOrder(
                it,
                custodialWalletOrder,
                stateAction
            )
        }.map { response -> response.toBuySellOrder() }

    override fun createRecurringBuyOrder(
        recurringBuyRequestBody: RecurringBuyRequestBody
    ): Single<RecurringBuyOrder> =
        authenticator.authenticate {
            nabuService.createRecurringBuyOrder(
                it,
                recurringBuyRequestBody
            )
        }.map { response -> response.toRecurringBuyOrder() }

    override fun createWithdrawOrder(amount: Money, bankId: String): Completable =
        authenticator.authenticateCompletable {
            nabuService.createWithdrawOrder(
                sessionToken = it,
                amount = amount.toBigInteger().toString(),
                currency = amount.currencyCode,
                beneficiaryId = bankId
            )
        }

    override fun fetchFiatWithdrawFeeAndMinLimit(
        currency: String,
        product: Product,
        paymentMethodType: PaymentMethodType
    ): Single<FiatWithdrawalFeeAndLimit> =
        authenticator.authenticate {
            nabuService.fetchWithdrawFeesAndLimits(it, product.toRequestString(), paymentMethodType.mapToRequest())
        }.map { response ->
            val fee = response.fees.firstOrNull { it.symbol == currency }?.let {
                FiatValue.fromMinor(it.symbol, it.minorValue.toLong())
            } ?: FiatValue.zero(currency)

            val minLimit = response.minAmounts.firstOrNull { it.symbol == currency }?.let {
                FiatValue.fromMinor(it.symbol, it.minorValue.toLong())
            } ?: FiatValue.zero(currency)

            FiatWithdrawalFeeAndLimit(minLimit, fee)
        }

    private fun PaymentMethodType.mapToRequest(): String =
        when (this) {
            PaymentMethodType.BANK_TRANSFER -> WithdrawFeeRequest.BANK_TRANSFER
            PaymentMethodType.BANK_ACCOUNT -> WithdrawFeeRequest.BANK_ACCOUNT
            else -> throw IllegalStateException("map not specified for $this")
        }

    override fun fetchCryptoWithdrawFeeAndMinLimit(
        currency: CryptoCurrency,
        product: Product
    ): Single<CryptoWithdrawalFeeAndLimit> =
        authenticator.authenticate {
            nabuService.fetchWithdrawFeesAndLimits(it, product.toRequestString(), WithdrawFeeRequest.DEFAULT)
        }.map { response ->
            val fee = response.fees.firstOrNull {
                it.symbol == currency.networkTicker
            }?.minorValue?.toBigInteger() ?: BigInteger.ZERO

            val minLimit = response.minAmounts.firstOrNull {
                it.symbol == currency.networkTicker
            }?.minorValue?.toBigInteger() ?: BigInteger.ZERO

            CryptoWithdrawalFeeAndLimit(minLimit, fee)
        }

    override fun fetchWithdrawLocksTime(
        paymentMethodType: PaymentMethodType,
        fiatCurrency: String,
        productType: String
    ): Single<BigInteger> =
        authenticator.authenticate {
            nabuService.fetchWithdrawLocksRules(it, paymentMethodType, fiatCurrency, productType)
        }.flatMap { response ->
            response.rule?.let {
                Single.just(it.lockTime.toBigInteger())
            } ?: Single.just(BigInteger.ZERO)
        }

    override fun getSupportedBuySellCryptoCurrencies(
        fiatCurrency: String?
    ): Single<BuySellPairs> =
        nabuService.getSupportedCurrencies(fiatCurrency)
            .map {
                val supportedPairs = it.pairs.filter { pair ->
                    pair.isCryptoCurrencySupported()
                }
                BuySellPairs(supportedPairs.map { pair ->
                    BuySellPair(
                        pair = pair.pair,
                        buyLimits = BuySellLimits(
                            pair.buyMin,
                            pair.buyMax
                        ),
                        sellLimits = BuySellLimits(
                            pair.sellMin,
                            pair.sellMax
                        )
                    )
                })
            }

    override fun getSupportedFiatCurrencies(): Single<List<String>> =
        authenticator.authenticate {
            nabuService.getSupportedCurrencies()
        }.map {
            it.pairs.map { pair ->
                pair.pair.split("-")[1]
            }.distinct()
        }

    override fun getCustodialFiatTransactions(
        currency: String,
        product: Product,
        type: String?
    ): Single<List<FiatTransaction>> =
        authenticator.authenticate { token ->
            nabuService.getTransactions(token, currency, product.toRequestString(), type).map { response ->
                response.items.mapNotNull {
                    val state = it.state.toTransactionState() ?: return@mapNotNull null
                    val txType = it.type.toTransactionType() ?: return@mapNotNull null

                    FiatTransaction(
                        id = it.id,
                        amount = FiatValue.fromMinor(it.amount.symbol, it.amountMinor.toLong()),
                        date = it.insertedAt.fromIso8601ToUtc()?.toLocalTime() ?: Date(),
                        state = state,
                        type = txType
                    )
                }
            }
        }

    override fun getCustodialCryptoTransactions(
        currency: String,
        product: Product,
        type: String?
    ): Single<List<CryptoTransaction>> =
        authenticator.authenticate { token ->
            nabuService.getTransactions(token, currency, product.toRequestString(), type).map { response ->
                response.items.mapNotNull {
                    val crypto = CryptoCurrency.fromNetworkTicker(it.amount.symbol) ?: return@mapNotNull null
                    val state = it.state.toTransactionState() ?: return@mapNotNull null
                    val txType = it.type.toTransactionType() ?: return@mapNotNull null

                    CryptoTransaction(
                        id = it.id,
                        amount = CryptoValue.fromMinor(crypto, it.amountMinor.toBigInteger()),
                        date = it.insertedAt.fromIso8601ToUtc()?.toLocalTime() ?: Date(),
                        state = state,
                        type = txType,
                        fee = it.feeMinor?.let { fee ->
                            CryptoValue.fromMinor(crypto, fee.toBigInteger())
                        } ?: CryptoValue.zero(crypto),
                        receivingAddress = it.extraAttributes.beneficiary?.accountRef.orEmpty(),
                        txHash = it.txHash.orEmpty(),
                        currency = currencyPrefs.selectedFiatCurrency
                    )
                }
            }
        }

    override fun getPredefinedAmounts(currency: String): Single<List<FiatValue>> =
        authenticator.authenticate {
            nabuService.getPredefinedAmounts(it, currency)
        }.map { response ->
            val currencyAmounts = response.firstOrNull { it[currency] != null } ?: emptyMap()
            currencyAmounts[currency]?.map { value ->
                FiatValue.fromMinor(currency, value)
            } ?: emptyList()
        }

    override fun getBankAccountDetails(currency: String): Single<BankAccount> =
        authenticator.authenticate {
            nabuService.getSimpleBuyBankAccountDetails(it, currency)
        }.map { response ->
            paymentAccountMapperMappers[currency]?.map(response)
                ?: throw IllegalStateException("Not valid Account returned")
        }

    override fun getCustodialAccountAddress(cryptoCurrency: CryptoCurrency): Single<String> =
        authenticator.authenticate {
            nabuService.getSimpleBuyBankAccountDetails(it, cryptoCurrency.networkTicker)
        }.map { response ->
            response.address
        }

    override fun isCurrencySupportedForSimpleBuy(fiatCurrency: String): Single<Boolean> =
        nabuService.getSupportedCurrencies(fiatCurrency).map {
            it.pairs.firstOrNull { it.pair.split("-")[1] == fiatCurrency } != null
        }.onErrorReturn { false }

    override fun getOutstandingBuyOrders(crypto: CryptoCurrency): Single<BuyOrderList> =
        authenticator.authenticate {
            nabuService.getOutstandingOrders(
                sessionToken = it,
                pendingOnly = true
            )
        }.map {
            it.filterAndMapToOrder(crypto)
        }

    override fun getAllOutstandingBuyOrders(): Single<BuyOrderList> =
        authenticator.authenticate {
            nabuService.getOutstandingOrders(
                sessionToken = it,
                pendingOnly = true
            )
        }.map {
            it.filter { order -> order.type() == OrderType.BUY }.map { order -> order.toBuySellOrder() }
                .filter { order -> order.state != OrderState.UNKNOWN }
        }

    override fun getAllOutstandingOrders(): Single<BuyOrderList> =
        authenticator.authenticate {
            nabuService.getOutstandingOrders(
                sessionToken = it,
                pendingOnly = true
            )
        }.map {
            it.map { order -> order.toBuySellOrder() }
                .filter { order -> order.state != OrderState.UNKNOWN }
        }

    override fun getAllOrdersFor(crypto: CryptoCurrency): Single<BuyOrderList> =
        authenticator.authenticate {
            nabuService.getOutstandingOrders(
                sessionToken = it,
                pendingOnly = false
            )
        }.map {
            it.filterAndMapToOrder(crypto)
        }

    private fun BuyOrderListResponse.filterAndMapToOrder(crypto: CryptoCurrency): List<BuySellOrder> =
        this.filter { order ->
            order.outputCurrency == crypto.networkTicker ||
                order.inputCurrency == crypto.networkTicker
        }
            .map { order -> order.toBuySellOrder() }

    override fun getBuyOrder(orderId: String): Single<BuySellOrder> =
        authenticator.authenticate {
            nabuService.getBuyOrder(it, orderId)
        }.map { it.toBuySellOrder() }

    override fun deleteBuyOrder(orderId: String): Completable =
        authenticator.authenticateCompletable {
            nabuService.deleteBuyOrder(it, orderId)
        }

    override fun deleteCard(cardId: String): Completable =
        authenticator.authenticateCompletable {
            nabuService.deleteCard(it, cardId)
        }

    override fun removeBank(bank: Bank): Completable =
        authenticator.authenticateCompletable {
            bank.remove(it)
        }

    override fun getTotalBalanceForAsset(crypto: CryptoCurrency): Maybe<CryptoValue> =
        custodialAssetWalletsBalancesRepository.getCustodialTotalBalanceForAsset(crypto)

    override fun getActionableBalanceForAsset(crypto: CryptoCurrency): Maybe<CryptoValue> =
        custodialAssetWalletsBalancesRepository.getCustodialActionableBalanceForAsset(crypto)

    override fun getPendingBalanceForAsset(crypto: CryptoCurrency): Maybe<CryptoValue> =
        custodialAssetWalletsBalancesRepository.getCustodialPendingBalanceForAsset(crypto)

    override fun transferFundsToWallet(amount: CryptoValue, walletAddress: String): Single<String> =
        authenticator.authenticate {
            nabuService.transferFunds(
                it,
                TransferRequest(
                    address = walletAddress,
                    currency = amount.currency.networkTicker,
                    amount = amount.toBigInteger().toString()
                )
            )
        }

    override fun cancelAllPendingOrders(): Completable {
        return getAllOutstandingOrders().toObservable()
            .flatMapIterable()
            .flatMapCompletable { deleteBuyOrder(it.id) }
    }

    override fun updateSupportedCardTypes(
        fiatCurrency: String
    ): Completable =
        authenticator.authenticate {
            paymentMethods(it, fiatCurrency, true).doOnSuccess { paymentMethods ->
                updateSupportedCards(paymentMethods)
            }
        }.ignoreElement()

    override fun linkToABank(fiatCurrency: String): Single<LinkBankTransfer> {
        return authenticator.authenticate {
            nabuService.linkToABank(it, fiatCurrency)
                .zipWith(bankLinkingEnabledProvider.supportedBankPartners())
        }.flatMap { (response, supportedPartners) ->
            response.partner.toLinkingBankPartner(supportedPartners)?.let {
                val attributes =
                    response.attributes
                        ?: return@flatMap Single.error<LinkBankTransfer>(
                            IllegalStateException("Missing attributes")
                        )
                Single.just(
                    LinkBankTransfer(
                        response.id,
                        it,
                        it.attributes(attributes)
                    )
                )
            }
        }
    }

    override fun updateSelectedBankAccount(
        linkingId: String,
        providerAccountId: String,
        accountId: String,
        partner: BankPartner
    ): Completable = authenticator.authenticateCompletable {
        nabuService.updateAccountProviderId(
            it, linkingId, UpdateProviderAccountBody(
                when (partner) {
                    BankPartner.YODLEE ->
                        ProviderAccountAttrs(
                            providerAccountId = providerAccountId,
                            accountId = accountId
                        )
                    BankPartner.YAPILY ->
                        ProviderAccountAttrs(
                            institutionId = accountId,
                            callback = BankPartner.YAPILY_DEEPLINK_BANK_LINK_URL
                        )
                }
            )
        )
    }

    override fun fetchSuggestedPaymentMethod(
        fiatCurrency: String,
        fetchSddLimits: Boolean,
        onlyEligible: Boolean
    ): Single<List<PaymentMethod>> =
        paymentMethods(
            fiatCurrency = fiatCurrency, onlyEligible = onlyEligible, fetchSdddLimits = fetchSddLimits
        )

    private val updateSupportedCards: (List<PaymentMethodResponse>) -> Unit = { paymentMethods ->
        val cardTypes =
            paymentMethods
                .filter { it.eligible && it.type.toPaymentMethodType() == PaymentMethodType.PAYMENT_CARD }
                .filter { it.subTypes.isNullOrEmpty().not() }
                .mapNotNull { it.subTypes }
                .flatten().distinct()
        simpleBuyPrefs.updateSupportedCards(cardTypes.joinToString())
    }

    private fun getSupportedPaymentMethods(
        sessionTokenResponse: NabuSessionTokenResponse,
        fiatCurrency: String,
        shouldFetchSddLimits: Boolean,
        onlyEligible: Boolean
    ) = paymentMethods(
        sessionToken = sessionTokenResponse,
        currency = fiatCurrency,
        shouldFetchSddLimits = shouldFetchSddLimits,
        eligibleOnly = onlyEligible
    ).map { methods ->
        methods.filter { method -> method.eligible || !onlyEligible }
    }.doOnSuccess {
        updateSupportedCards(it)
    }.zipWith(bankLinkingEnabledProvider.bankLinkingEnabled(fiatCurrency)).map { (methods, enabled) ->
        methods.filter {
            it.type != PaymentMethodResponse.BANK_TRANSFER || enabled
        }
    }

    override fun getBankTransferLimits(fiatCurrency: String, onlyEligible: Boolean) =
        authenticator.authenticate {
            nabuService.paymentMethods(it, fiatCurrency, onlyEligible, null).map { methods ->
                methods.filter { method -> method.eligible || !onlyEligible }
            }
        }.map {
            it.filter { response ->
                response.type == PaymentMethodResponse.BANK_TRANSFER && response.currency == fiatCurrency
            }.map { paymentMethod ->
                PaymentLimits(
                    min = paymentMethod.limits.min,
                    max = paymentMethod.limits.daily?.available ?: paymentMethod.limits.max,
                    currency = fiatCurrency
                )
            }.first()
        }

    private fun paymentMethods(fiatCurrency: String, onlyEligible: Boolean, fetchSdddLimits: Boolean = false) =
        authenticator.authenticate {
            Singles.zip(
                custodialAssetWalletsBalancesRepository.getFiatTotalBalanceForAsset(fiatCurrency)
                    .map { balance -> CustodialFiatBalance(fiatCurrency, true, balance) }
                    .toSingle(CustodialFiatBalance(fiatCurrency, false, null)),
                nabuService.getCards(it).onErrorReturn { emptyList() },
                getBanks().map { banks -> banks.filter { it.paymentMethodType == PaymentMethodType.BANK_TRANSFER } }
                    .onErrorReturn { emptyList() },
                getSupportedPaymentMethods(
                    sessionTokenResponse = it,
                    fiatCurrency = fiatCurrency,
                    onlyEligible = onlyEligible,
                    shouldFetchSddLimits = fetchSdddLimits
                )
            ) { custodialFiatBalance, cardsResponse, linkedBanks, paymentMethods ->
                val availablePaymentMethods = mutableListOf<PaymentMethod>()

                paymentMethods.forEach { paymentMethod ->
                    if (paymentMethod.type == PaymentMethodResponse.PAYMENT_CARD) {
                        val cardLimits =
                            PaymentLimits(paymentMethod.limits.min, paymentMethod.limits.max, fiatCurrency)
                        cardsResponse.takeIf { cards -> cards.isNotEmpty() }?.filter { it.state.isActive() }
                            ?.forEach { cardResponse: CardResponse ->
                                availablePaymentMethods.add(
                                    cardResponse.toCardPaymentMethod(cardLimits)
                                )
                            }
                    } else if (
                        paymentMethod.type == PaymentMethodResponse.FUNDS &&
                        paymentMethod.currency == fiatCurrency &&
                        SUPPORTED_FUNDS_CURRENCIES.contains(paymentMethod.currency)
                    ) {
                        custodialFiatBalance.balance?.takeIf { balance ->
                            balance > FiatValue.fromMinor(
                                paymentMethod.currency,
                                paymentMethod.limits.min
                            )
                        }?.let { balance ->
                            val fundsLimits =
                                PaymentLimits(
                                    paymentMethod.limits.min,
                                    paymentMethod.limits.max.coerceAtMost(balance.toBigInteger().toLong()),
                                    paymentMethod.currency
                                )
                            availablePaymentMethods.add(
                                PaymentMethod.Funds(
                                    balance,
                                    paymentMethod.currency,
                                    fundsLimits,
                                    true
                                )
                            )
                        }
                    } else if (
                        paymentMethod.type == PaymentMethodResponse.BANK_TRANSFER &&
                        linkedBanks.isNotEmpty()
                    ) {
                        val bankLimits =
                            PaymentLimits(paymentMethod.limits.min, paymentMethod.limits.max, fiatCurrency)
                        linkedBanks.filter { linkedBank ->
                            linkedBank.state == BankState.ACTIVE
                        }.forEach { linkedBank: Bank ->
                            availablePaymentMethods.add(
                                linkedBank.toBankPaymentMethod(bankLimits)
                            )
                        }
                    } else if (
                        paymentMethod.type == PaymentMethodResponse.BANK_ACCOUNT &&
                        paymentMethod.eligible &&
                        paymentMethod.currency?.isSupportedCurrency() == true &&
                        paymentMethod.currency == fiatCurrency
                    ) {
                        availablePaymentMethods.add(
                            PaymentMethod.UndefinedFunds(
                                paymentMethod.currency,
                                PaymentLimits(
                                    paymentMethod.limits.min, paymentMethod.limits.max, paymentMethod.currency
                                ),
                                paymentMethod.eligible
                            )
                        )
                    }
                }

                paymentMethods.firstOrNull { paymentMethod ->
                    paymentMethod.type == PaymentMethodResponse.PAYMENT_CARD
                }?.let { paymentMethod ->
                    availablePaymentMethods.add(
                        PaymentMethod.UndefinedCard(
                            PaymentLimits(
                                paymentMethod.limits.min,
                                paymentMethod.limits.max,
                                fiatCurrency
                            ),
                            paymentMethod.eligible
                        )
                    )
                }

                paymentMethods.firstOrNull { paymentMethod ->
                    paymentMethod.type == PaymentMethodResponse.BANK_TRANSFER
                }?.let { bankTransfer ->
                    availablePaymentMethods.add(
                        PaymentMethod.UndefinedBankTransfer(
                            PaymentLimits(
                                bankTransfer.limits.min,
                                bankTransfer.limits.max,
                                fiatCurrency
                            ),
                            bankTransfer.eligible
                        )
                    )
                }
                availablePaymentMethods.sortedBy { paymentMethod -> paymentMethod.order }.toList()
            }
        }

    override fun getRecurringBuyEligibility() = recurringBuysRepository.getRecurringBuyEligibleMethods()

    override fun getRecurringBuysForAsset(assetTicker: String): Single<List<RecurringBuy>> =
        if (features.isFeatureEnabled(GatedFeature.RECURRING_BUYS)) {
            authenticator.authenticate { sessionToken ->
                nabuService.getRecurringBuysForAsset(sessionToken, assetTicker).map { list ->
                    list.mapNotNull {
                        it.toRecurringBuy()
                    }
                }
            }
        } else {
            Single.just(emptyList())
        }

    override fun getRecurringBuyOrdersFor(crypto: CryptoCurrency): Single<List<RecurringBuyTransaction>> =
        if (features.isFeatureEnabled(GatedFeature.RECURRING_BUYS)) {
            authenticator.authenticate { sessionToken ->
                nabuService.getRecurringBuysTransactions(
                    sessionToken, crypto.networkTicker
                ).map { list ->
                    list.map {
                        it.toRecurringBuyTransaction()
                    }
                }
            }
        } else {
            Single.just(emptyList())
        }

    override fun cancelRecurringBuy(id: String): Completable =
        authenticator.authenticateCompletable { sessionToken ->
            nabuService.cancelRecurringBuy(sessionToken, id)
        }

    override fun addNewCard(
        fiatCurrency: String,
        billingAddress: BillingAddress
    ): Single<CardToBeActivated> =
        authenticator.authenticate {
            nabuService.addNewCard(
                sessionToken = it,
                addNewCardBodyRequest = AddNewCardBodyRequest(
                    fiatCurrency,
                    AddAddressRequest.fromBillingAddress(billingAddress)
                )
            )
        }.map {
            CardToBeActivated(cardId = it.id, partner = it.partner)
        }

    override fun getEligiblePaymentMethodTypes(fiatCurrency: String): Single<List<EligiblePaymentMethodType>> =
        authenticator.authenticate {
            paymentMethods(
                sessionToken = it,
                currency = fiatCurrency,
                eligibleOnly = true
            ).zipWith(bankLinkingEnabledProvider.bankLinkingEnabled(fiatCurrency))
                .map { (methodsResponse, bankLinkingEnabled) ->
                    methodsResponse.mapNotNull { method ->
                        when (method.type) {
                            PaymentMethodResponse.PAYMENT_CARD -> EligiblePaymentMethodType(
                                PaymentMethodType.PAYMENT_CARD,
                                method.currency ?: return@mapNotNull null
                            )
                            PaymentMethodResponse.BANK_TRANSFER -> {
                                if (bankLinkingEnabled) {
                                    EligiblePaymentMethodType(
                                        PaymentMethodType.BANK_TRANSFER,
                                        method.currency ?: return@mapNotNull null
                                    )
                                } else {
                                    return@mapNotNull null
                                }
                            }
                            PaymentMethodResponse.BANK_ACCOUNT
                            -> EligiblePaymentMethodType(
                                PaymentMethodType.BANK_ACCOUNT,
                                method.currency ?: return@mapNotNull null
                            )
                            else -> null
                        }
                    }
                }
        }

    override fun activateCard(
        cardId: String,
        attributes: SimpleBuyConfirmationAttributes
    ): Single<PartnerCredentials> =
        authenticator.authenticate {
            nabuService.activateCard(it, cardId, attributes)
        }.map {
            PartnerCredentials(it.everypay?.let { response ->
                EveryPayCredentials(
                    response.apiUsername,
                    response.mobileToken,
                    response.paymentLink
                )
            })
        }

    override fun getCardDetails(cardId: String): Single<PaymentMethod.Card> =
        authenticator.authenticate {
            nabuService.getCardDetails(it, cardId)
        }.map { cardsResponse ->
            cardsResponse.toCardPaymentMethod(
                PaymentLimits(FiatValue.zero(cardsResponse.currency), FiatValue.zero(cardsResponse.currency))
            )
        }

    override fun fetchUnawareLimitsCards(
        states: List<CardStatus>
    ): Single<List<PaymentMethod.Card>> =
        authenticator.authenticate {
            nabuService.getCards(it)
        }.map { cardsResponse ->
            cardsResponse.filter { states.contains(it.state.toCardStatus()) || states.isEmpty() }.map {
                it.toCardPaymentMethod(
                    PaymentLimits(FiatValue.zero(it.currency), FiatValue.zero(it.currency))
                )
            }
        }

    override fun confirmOrder(
        orderId: String,
        attributes: SimpleBuyConfirmationAttributes?,
        paymentMethodId: String?,
        isBankPartner: Boolean?
    ): Single<BuySellOrder> =
        authenticator.authenticate {
            nabuService.confirmOrder(
                it, orderId,
                ConfirmOrderRequestBody(
                    paymentMethodId = paymentMethodId,
                    attributes = attributes,
                    paymentType = if (isBankPartner == true) {
                        PaymentMethodResponse.BANK_TRANSFER
                    } else null
                )
            )
        }.map {
            it.toBuySellOrder()
        }

    override fun getInterestAccountRates(crypto: CryptoCurrency): Single<Double> =
        authenticator.authenticate { sessionToken ->
            nabuService.getInterestRates(sessionToken, crypto.networkTicker).toSingle(InterestRateResponse(0.0))
                .flatMap {
                    Single.just(it.rate)
                }
        }

    override fun getInterestAccountBalance(
        crypto: CryptoCurrency
    ): Single<CryptoValue> = interestRepository.getInterestAccountBalance(crypto)

    override fun getPendingInterestAccountBalance(
        crypto: CryptoCurrency
    ): Single<CryptoValue> = interestRepository.getInterestPendingBalance(crypto)

    override fun getActionableInterestAccountBalance(
        crypto: CryptoCurrency
    ): Single<CryptoValue> = interestRepository.getInterestActionableBalance(crypto)

    override fun getInterestAccountDetails(
        crypto: CryptoCurrency
    ): Single<InterestAccountDetails> = interestRepository.getInterestAccountDetails(crypto)

    override fun getInterestAccountAddress(crypto: CryptoCurrency): Single<String> =
        authenticator.authenticate { sessionToken ->
            nabuService.getInterestAddress(sessionToken, crypto.networkTicker).map {
                it.accountRef
            }
        }

    override fun getInterestActivity(crypto: CryptoCurrency): Single<List<InterestActivityItem>> =
        kycFeatureEligibility.isEligibleFor(Feature.INTEREST_RATES)
            .onErrorReturnItem(false)
            .flatMap { eligible ->
                if (eligible) {
                    authenticator.authenticate { sessionToken ->
                        nabuService.getInterestActivity(sessionToken, crypto.networkTicker)
                            .map { interestActivityResponse ->
                                interestActivityResponse.items.map {
                                    val cryptoCurrency =
                                        CryptoCurrency.fromNetworkTicker(it.amount.symbol)!!

                                    it.toInterestActivityItem(cryptoCurrency)
                                }
                            }
                    }
                } else {
                    Single.just(emptyList())
                }
            }

    override fun getInterestLimits(crypto: CryptoCurrency): Maybe<InterestLimits> =
        interestRepository.getLimitForAsset(crypto)

    override fun getInterestAvailabilityForAsset(crypto: CryptoCurrency): Single<Boolean> =
        interestRepository.getAvailabilityForAsset(crypto)

    override fun getInterestEnabledAssets(): Single<List<CryptoCurrency>> =
        interestRepository.getAvailableAssets()

    override fun getInterestEligibilityForAsset(crypto: CryptoCurrency): Single<Eligibility> =
        interestRepository.getEligibilityForAsset(crypto)

    override fun startInterestWithdrawal(cryptoCurrency: CryptoCurrency, amount: Money, address: String) =
        authenticator.authenticateCompletable {
            nabuService.createInterestWithdrawal(
                it,
                InterestWithdrawalBody(
                    withdrawalAddress = address,
                    amount = amount.toBigInteger().toString(),
                    currency = cryptoCurrency.networkTicker
                )
            ).doOnComplete {
                interestRepository.clearBalanceForAsset(cryptoCurrency)
            }
        }

    override fun invalidateInterestBalanceForAsset(crypto: CryptoCurrency) {
        interestRepository.clearBalanceForAsset(crypto)
    }

    override fun getSupportedFundsFiats(
        fiatCurrency: String
    ): Single<List<String>> {

        return authenticator.authenticate {
            paymentMethods(it, fiatCurrency, true)
        }.map { methods ->
            methods.filter {
                it.type.toPaymentMethodType() == PaymentMethodType.FUNDS &&
                    SUPPORTED_FUNDS_CURRENCIES.contains(it.currency) && it.eligible
            }.mapNotNull {
                it.currency
            }
        }
    }

    private fun getSupportedCurrenciesForBankTransactions(fiatCurrency: String): Single<List<String>> {
        return authenticator.authenticate {
            paymentMethods(it, fiatCurrency, true)
        }.map { methods ->
            methods.filter {
                (it.type == PaymentMethodResponse.BANK_ACCOUNT || it.type == PaymentMethodResponse.BANK_TRANSFER) &&
                    it.currency == fiatCurrency
            }.mapNotNull {
                it.currency
            }
        }
    }

    /**
     * Returns a list of the available payment methods. [shouldFetchSddLimits] if true, then the responded
     * payment methods will contain the limits for SDD user. We use this argument only if we want to get back
     * these limits. To achieve back-words compatibility with the other platforms we had to use
     * a flag called visible (instead of not returning the corresponding payment methods at all.
     * Any payment method with the flag visible=false should be discarded.
     */
    private fun paymentMethods(
        sessionToken: NabuSessionTokenResponse,
        currency: String,
        eligibleOnly: Boolean,
        shouldFetchSddLimits: Boolean = false
    ) = nabuService.paymentMethods(
        sessionToken = sessionToken,
        currency = currency,
        eligibleOnly = eligibleOnly,
        tier = if (shouldFetchSddLimits) SDD_ELIGIBLE_TIER else null
    ).map {
        it.filter { paymentMethod -> paymentMethod.visible }
    }

    override fun canTransactWithBankMethods(fiatCurrency: String): Single<Boolean> {
        if (!fiatCurrency.isSupportedCurrency())
            return Single.just(false)
        return getSupportedCurrenciesForBankTransactions(fiatCurrency).zipWith(
            achDepositWithdrawFeatureFlag.enabled
        )
            .map { (supportedCurrencies, achEnabled) ->
                val currencies = supportedCurrencies.filter { achEnabled || it != ACH_CURRENCY }
                currencies.contains(fiatCurrency)
            }
    }

    override fun getExchangeSendAddressFor(crypto: CryptoCurrency): Maybe<String> =
        authenticator.authenticateMaybe { sessionToken ->
            nabuService.fetchPitSendToAddressForCrypto(sessionToken, crypto.networkTicker)
                .flatMapMaybe { response ->
                    if (response.state == State.ACTIVE) {
                        Maybe.just(response.address)
                    } else {
                        Maybe.empty()
                    }
                }
                .onErrorComplete()
        }

    override fun isSimplifiedDueDiligenceEligible(): Single<Boolean> =
        nabuService.isSDDEligible().zipWith(sddFeatureFlag.enabled).map { (response, featureEnabled) ->
            featureEnabled && response.eligible && response.tier == SDD_ELIGIBLE_TIER
        }.onErrorReturn { false }

    override fun fetchSimplifiedDueDiligenceUserState(): Single<SimplifiedDueDiligenceUserState> =
        authenticator.authenticate { sessionToken ->
            nabuService.isSDDVerified(sessionToken)
        }.map {
            SimplifiedDueDiligenceUserState(
                isVerified = it.verified,
                stateFinalised = it.taskComplete
            )
        }

    override fun createCustodialOrder(
        direction: TransferDirection,
        quoteId: String,
        volume: Money,
        destinationAddress: String?,
        refundAddress: String?
    ): Single<CustodialOrder> =
        authenticator.authenticate { sessionToken ->
            nabuService.createCustodialOrder(
                sessionToken,
                CreateOrderRequest(
                    direction = direction.toString(),
                    quoteId = quoteId,
                    volume = volume.toBigInteger().toString(),
                    destinationAddress = destinationAddress,
                    refundAddress = refundAddress
                )
            ).onErrorResumeNext {
                Single.error(transactionErrorMapper.mapToTransactionError(it))
            }.map {
                it.toCustodialOrder() ?: throw IllegalStateException("Invalid order created")
            }
        }

    override fun getProductTransferLimits(
        currency: String,
        product: Product,
        orderDirection: TransferDirection?
    ): Single<TransferLimits> =
        authenticator.authenticate {
            val side = when (product) {
                Product.BUY,
                Product.SELL -> product.name
                else -> null
            }

            val direction = if (product == Product.TRADE && orderDirection != null) {
                orderDirection.name
            } else null

            nabuService.fetchProductLimits(
                it,
                currency,
                product.toRequestString(),
                side,
                direction
            ).map { response ->
                if (response.maxOrder == null && response.minOrder == null && response.maxPossibleOrder == null) {
                    TransferLimits(currency)
                } else {
                    TransferLimits(
                        minLimit = FiatValue.fromMinor(currency, response.minOrder?.toLong() ?: 0L),
                        maxOrder = FiatValue.fromMinor(currency, response.maxOrder?.toLong() ?: 0L),
                        maxLimit = FiatValue.fromMinor(
                            currency,
                            response.maxPossibleOrder?.toLong() ?: 0L
                        )
                    )
                }
            }
        }

    override fun getCustodialActivityForAsset(
        cryptoCurrency: CryptoCurrency,
        directions: Set<TransferDirection>
    ): Single<List<TradeTransactionItem>> =
        custodialRepository.getCustodialActivityForAsset(cryptoCurrency, directions)

    override fun updateOrder(id: String, success: Boolean): Completable =
        authenticator.authenticateCompletable { sessionToken ->
            nabuService.updateOrder(
                sessionToken = sessionToken,
                id = id,
                success = success
            )
        }

    override fun getLinkedBank(id: String): Single<LinkedBank> =
        authenticator.authenticate { sessionToken ->
            nabuService.getLinkedBank(
                sessionToken = sessionToken,
                id = id
            )
        }.map {
            it.toLinkedBank()
        }

    override fun getBanks(): Single<List<Bank>> {
        return authenticator.authenticate { sessionToken ->
            nabuService.getBanks(
                sessionToken = sessionToken
            )
        }.map { banksResponse ->
            banksResponse.map { it.toBank() }
        }
    }

    private fun BankInfoResponse.toBank(): Bank =
        Bank(
            id = id,
            name = name.takeIf { it?.isNotEmpty() == true } ?: accountName.orEmpty(),
            state = state.toBankState(),
            currency = currency,
            account = accountNumber ?: "****",
            accountType = bankAccountType.orEmpty(),
            paymentMethodType = if (this.isBankTransferAccount)
                PaymentMethodType.BANK_TRANSFER else PaymentMethodType.BANK_ACCOUNT,
            iconUrl = attributes?.media?.find { it.type == ICON }?.source.orEmpty()
        )

    private fun LinkedBankTransferResponse.toLinkedBank(): LinkedBank? {
        return LinkedBank(
            id = id,
            currency = currency,
            partner = partner.toLinkingBankPartner(BankPartner.values().toList()) ?: return null,
            state = state.toLinkedBankState(),
            bankName = details?.bankName.orEmpty(),
            accountName = details?.accountName.orEmpty(),
            accountNumber = details?.accountNumber?.replace("x", "").orEmpty(),
            errorStatus = error?.toLinkedBankErrorState() ?: LinkedBankErrorState.NONE,
            accountType = details?.bankAccountType.orEmpty(),
            authorisationUrl = attributes?.authorisationUrl.orEmpty(),
            sortCode = details?.sortCode.orEmpty(),
            accountIban = details?.iban.orEmpty(),
            bic = details?.bic.orEmpty(),
            entity = attributes?.entity.orEmpty(),
            iconUrl = attributes?.media?.find { it.source == ICON }?.source.orEmpty(),
            callbackPath = attributes?.callbackPath.orEmpty()
        )
    }

    override fun isFiatCurrencySupported(destination: String): Boolean =
        SUPPORTED_FUNDS_CURRENCIES.contains(destination)

    override fun createPendingDeposit(
        crypto: CryptoCurrency,
        address: String,
        hash: String,
        amount: Money,
        product: Product
    ): Completable =
        authenticator.authenticateCompletable { sessionToken ->
            nabuService.createDepositTransaction(
                sessionToken = sessionToken,
                currency = crypto.networkTicker,
                address = address,
                hash = hash,
                amount = amount.toBigInteger().toString(),
                product = product.toString()

            )
        }

    override fun startBankTransfer(
        id: String,
        amount: Money,
        currency: String,
        callback: String?
    ): Single<String> =
        authenticator.authenticate { sessionToken ->
            nabuService.startBankTransferPayment(
                sessionToken = sessionToken,
                id = id,
                body = BankTransferPaymentBody(
                    amountMinor = amount.toBigInteger().toString(),
                    currency = currency,
                    attributes = if (callback != null) {
                        BankTransferPaymentAttributes(callback)
                    } else null
                )
            ).map {
                it.paymentId
            }
        }

    override fun updateOpenBankingConsent(
        url: String,
        token: String
    ): Completable =
        authenticator.authenticateCompletable { sessionToken ->
            nabuService.updateOpenBankingToken(
                url,
                sessionToken,
                OpenBankingTokenBody(
                    oneTimeToken = token
                )
            )
        }

    override fun getBankTransferCharge(paymentId: String): Single<BankTransferDetails> =
        authenticator.authenticate { sessionToken ->
            nabuService.getBankTransferCharge(
                sessionToken = sessionToken,
                paymentId = paymentId
            ).map {
                it.toBankTransferDetails()
            }
        }

    override fun executeCustodialTransfer(amount: Money, origin: Product, destination: Product): Completable =
        authenticator.authenticateCompletable { sessionToken ->
            nabuService.executeTransfer(
                sessionToken = sessionToken,
                body = ProductTransferRequestBody(
                    amount = amount.toBigInteger().toString(),
                    currency = amount.currencyCode,
                    origin = origin.toRequestString(),
                    destination = destination.name
                )
            ).doOnComplete {
                interestRepository.clearBalanceForAsset(amount.currencyCode)
            }
        }

    private fun CardResponse.toCardPaymentMethod(cardLimits: PaymentLimits) =
        PaymentMethod.Card(
            cardId = id,
            limits = cardLimits,
            label = card?.label.orEmpty(),
            endDigits = card?.number.orEmpty(),
            partner = partner.toSupportedPartner(),
            expireDate = card?.let {
                Calendar.getInstance().apply {
                    set(
                        it.expireYear ?: this.get(Calendar.YEAR),
                        it.expireMonth ?: this.get(Calendar.MONTH),
                        0
                    )
                }.time
            } ?: Date(),
            cardType = card?.type ?: CardType.UNKNOWN,
            status = state.toCardStatus(),
            isEligible = true
        )

    private fun Bank.toBankPaymentMethod(bankLimits: PaymentLimits) =
        PaymentMethod.Bank(
            bankId = this.id,
            limits = bankLimits,
            bankName = this.name,
            accountEnding = this.account,
            accountType = this.accountType,
            isEligible = true,
            iconUrl = this.iconUrl
        )

    private fun String.isActive(): Boolean =
        toCardStatus() == CardStatus.ACTIVE

    private fun String.isActiveOrExpired(): Boolean =
        isActive() || toCardStatus() == CardStatus.EXPIRED

    private fun String.toCardStatus(): CardStatus =
        when (this) {
            CardResponse.ACTIVE -> CardStatus.ACTIVE
            CardResponse.BLOCKED -> CardStatus.BLOCKED
            CardResponse.PENDING -> CardStatus.PENDING
            CardResponse.CREATED -> CardStatus.CREATED
            CardResponse.EXPIRED -> CardStatus.EXPIRED
            else -> CardStatus.UNKNOWN
        }

    private fun String.toLinkedBankState(): LinkedBankState =
        when (this) {
            LinkedBankTransferResponse.CREATED -> LinkedBankState.CREATED
            LinkedBankTransferResponse.ACTIVE -> LinkedBankState.ACTIVE
            LinkedBankTransferResponse.PENDING,
            LinkedBankTransferResponse.FRAUD_REVIEW,
            LinkedBankTransferResponse.MANUAL_REVIEW -> LinkedBankState.PENDING
            LinkedBankTransferResponse.BLOCKED -> LinkedBankState.BLOCKED
            else -> LinkedBankState.UNKNOWN
        }

    private fun BankTransferChargeResponse.toBankTransferDetails() =
        BankTransferDetails(
            id = this.beneficiaryId,
            amount = FiatValue.fromMinor(this.amount.symbol, this.amountMinor.toLong()),
            authorisationUrl = this.extraAttributes.authorisationUrl,
            status = this.state?.toBankTransferStatus() ?: this.extraAttributes.status?.toBankTransferStatus()
            ?: BankTransferStatus.UNKNOWN
        )

    private fun String.toBankTransferStatus() =
        when (this) {
            BankTransferChargeAttributes.CREATED,
            BankTransferChargeAttributes.PRE_CHARGE_REVIEW,
            BankTransferChargeAttributes.PRE_CHARGE_APPROVED,
            BankTransferChargeAttributes.AWAITING_AUTHORIZATION,
            BankTransferChargeAttributes.PENDING,
            BankTransferChargeAttributes.AUTHORIZED,
            BankTransferChargeAttributes.CREDITED -> BankTransferStatus.PENDING
            BankTransferChargeAttributes.FAILED,
            BankTransferChargeAttributes.FRAUD_REVIEW,
            BankTransferChargeAttributes.MANUAL_REVIEW,
            BankTransferChargeAttributes.REJECTED -> BankTransferStatus.ERROR
            BankTransferChargeAttributes.CLEARED,
            BankTransferChargeAttributes.COMPLETE -> BankTransferStatus.COMPLETE
            else -> BankTransferStatus.UNKNOWN
        }

    override fun getSwapTrades(): Single<List<CustodialOrder>> =
        authenticator.authenticate { sessionToken ->
            nabuService.getSwapTrades(sessionToken)
        }.map { response ->
            response.mapNotNull { orderResp ->
                orderResp.toSwapOrder()
            }
        }

    private fun CustodialOrderResponse.toSwapOrder(): CustodialOrder? {
        return CustodialOrder(
            id = this.id,
            state = this.state.toCustodialOrderState(),
            depositAddress = this.kind.depositAddress,
            createdAt = this.createdAt.fromIso8601ToUtc()?.toLocalTime() ?: Date(),
            inputMoney = CryptoValue.fromMinor(
                CryptoCurrency.fromNetworkTicker(
                    this.pair.toCryptoCurrencyPair()?.source?.networkTicker.toString()
                ) ?: return null, this.priceFunnel.inputMoney.toBigInteger()
            ),
            outputMoney = CryptoValue.fromMinor(
                CryptoCurrency.fromNetworkTicker(
                    this.pair.toCryptoCurrencyPair()?.destination?.networkTicker.toString()
                ) ?: return null, this.priceFunnel.outputMoney.toBigInteger()
            )
        )
    }

    private fun String.toBankState(): BankState =
        when (this) {
            BankInfoResponse.ACTIVE -> BankState.ACTIVE
            BankInfoResponse.PENDING -> BankState.PENDING
            BankInfoResponse.BLOCKED -> BankState.BLOCKED
            else -> BankState.UNKNOWN
        }

    private fun CustodialOrderResponse.toCustodialOrder(): CustodialOrder? {
        return CustodialOrder(
            id = this.id,
            state = this.state.toCustodialOrderState(),
            depositAddress = this.kind.depositAddress,
            createdAt = this.createdAt.fromIso8601ToUtc() ?: Date(),
            inputMoney = CurrencyPair.fromRawPair(pair, SUPPORTED_FUNDS_CURRENCIES)?.let {
                it.toSourceMoney(priceFunnel.inputMoney.toBigInteger())
            } ?: return null,
            outputMoney = CurrencyPair.fromRawPair(pair, SUPPORTED_FUNDS_CURRENCIES)?.let {
                it.toDestinationMoney(priceFunnel.outputMoney.toBigInteger())
            } ?: return null
        )
    }

    private fun Bank.remove(sessionToken: NabuSessionTokenResponse): Completable =
        when (this.paymentMethodType) {
            PaymentMethodType.BANK_ACCOUNT -> nabuService.removeBeneficiary(sessionToken, id)
            PaymentMethodType.BANK_TRANSFER -> nabuService.removeLinkedBank(sessionToken, id)
            else -> Completable.error(java.lang.IllegalStateException("Unknown Bank type"))
        }

    companion object {
        internal val SUPPORTED_FUNDS_CURRENCIES = listOf(
            "GBP", "EUR", "USD"
        )
        private val SUPPORTED_FUNDS_FOR_WIRE_TRANSFER = listOf(
            "GBP", "EUR", "USD"
        )

        private const val ACH_CURRENCY = "USD"

        private const val SDD_ELIGIBLE_TIER = 3
    }

    private fun String.isSupportedCurrency(): Boolean =
        SUPPORTED_FUNDS_FOR_WIRE_TRANSFER.contains(this)
}

private fun Product.toRequestString(): String =
    when (this) {
        Product.TRADE -> "SWAP"
        Product.BUY,
        Product.SELL -> "SIMPLEBUY"
        else -> this.toString()
    }

private fun String.toLinkedBankState(): LinkedBankState =
    when (this) {
        LinkedBankTransferResponse.ACTIVE -> LinkedBankState.ACTIVE
        LinkedBankTransferResponse.PENDING -> LinkedBankState.PENDING
        LinkedBankTransferResponse.BLOCKED -> LinkedBankState.BLOCKED
        else -> LinkedBankState.UNKNOWN
    }

private fun String.toLinkingBankPartner(supportedBankPartners: List<BankPartner>): BankPartner? {
    val partner = when (this) {
        CreateLinkBankResponse.YODLEE_PARTNER -> BankPartner.YODLEE
        CreateLinkBankResponse.YAPILY_PARTNER -> BankPartner.YAPILY
        else -> null
    }

    return if (supportedBankPartners.contains(partner)) {
        partner
    } else null
}

private fun String.toLinkedBankErrorState(): LinkedBankErrorState =
    when (this) {
        LinkedBankTransferResponse.ERROR_ALREADY_LINKED -> LinkedBankErrorState.ACCOUNT_ALREADY_LINKED
        LinkedBankTransferResponse.ERROR_UNSUPPORTED_ACCOUNT -> LinkedBankErrorState.ACCOUNT_TYPE_UNSUPPORTED
        LinkedBankTransferResponse.ERROR_NAMES_MISMATCHED -> LinkedBankErrorState.NAMES_MISMATCHED
        LinkedBankTransferResponse.ERROR_ACCOUNT_EXPIRED -> LinkedBankErrorState.EXPIRED
        LinkedBankTransferResponse.ERROR_ACCOUNT_REJECTED -> LinkedBankErrorState.REJECTED
        LinkedBankTransferResponse.ERROR_ACCOUNT_FAILURE -> LinkedBankErrorState.FAILURE
        LinkedBankTransferResponse.BANK_TRANSFER_ACCOUNT_INVALID -> LinkedBankErrorState.INVALID
        else -> LinkedBankErrorState.UNKNOWN
    }

private fun String.toCryptoCurrencyPair(): CurrencyPair.CryptoCurrencyPair? {
    val parts = split("-")
    if (parts.size != 2) return null
    val source = CryptoCurrency.fromNetworkTicker(parts[0]) ?: return null
    val destination = CryptoCurrency.fromNetworkTicker(parts[1]) ?: return null
    return CurrencyPair.CryptoCurrencyPair(source, destination)
}

fun String.toTransactionState(): TransactionState? =
    when (this) {
        TransactionResponse.COMPLETE -> TransactionState.COMPLETED
        TransactionResponse.FAILED -> TransactionState.FAILED
        TransactionResponse.PENDING,
        TransactionResponse.CLEARED,
        TransactionResponse.FRAUD_REVIEW,
        TransactionResponse.MANUAL_REVIEW -> TransactionState.PENDING
        else -> null
    }

fun String.toCustodialOrderState(): CustodialOrderState =
    when (this) {
        CustodialOrderResponse.CREATED -> CustodialOrderState.CREATED
        CustodialOrderResponse.PENDING_CONFIRMATION -> CustodialOrderState.PENDING_CONFIRMATION
        CustodialOrderResponse.PENDING_EXECUTION -> CustodialOrderState.PENDING_EXECUTION
        CustodialOrderResponse.PENDING_DEPOSIT -> CustodialOrderState.PENDING_DEPOSIT
        CustodialOrderResponse.PENDING_LEDGER -> CustodialOrderState.PENDING_LEDGER
        CustodialOrderResponse.FINISH_DEPOSIT -> CustodialOrderState.FINISH_DEPOSIT
        CustodialOrderResponse.PENDING_WITHDRAWAL -> CustodialOrderState.PENDING_WITHDRAWAL
        CustodialOrderResponse.EXPIRED -> CustodialOrderState.EXPIRED
        CustodialOrderResponse.FINISHED -> CustodialOrderState.FINISHED
        CustodialOrderResponse.CANCELED -> CustodialOrderState.CANCELED
        CustodialOrderResponse.FAILED -> CustodialOrderState.FAILED
        else -> CustodialOrderState.UNKNOWN
    }

private fun String.toTransactionType(): TransactionType? =
    when (this) {
        TransactionResponse.DEPOSIT,
        TransactionResponse.CHARGE -> TransactionType.DEPOSIT
        TransactionResponse.WITHDRAWAL -> TransactionType.WITHDRAWAL
        else -> null
    }

private fun String.toSupportedPartner(): Partner =
    when (this) {
        "EVERYPAY" -> Partner.EVERYPAY
        else -> Partner.UNKNOWN
    }

enum class PaymentMethodType {
    BANK_TRANSFER,
    BANK_ACCOUNT,
    PAYMENT_CARD,
    FUNDS,
    UNKNOWN
}

private fun String.toLocalState(): OrderState =
    when (this) {
        BuySellOrderResponse.PENDING_DEPOSIT -> OrderState.AWAITING_FUNDS
        BuySellOrderResponse.FINISHED -> OrderState.FINISHED
        BuySellOrderResponse.PENDING_CONFIRMATION -> OrderState.PENDING_CONFIRMATION
        BuySellOrderResponse.PENDING_EXECUTION,
        BuySellOrderResponse.DEPOSIT_MATCHED -> OrderState.PENDING_EXECUTION
        BuySellOrderResponse.FAILED,
        BuySellOrderResponse.EXPIRED -> OrderState.FAILED
        BuySellOrderResponse.CANCELED -> OrderState.CANCELED
        else -> OrderState.UNKNOWN
    }

enum class CardStatus {
    PENDING,
    ACTIVE,
    BLOCKED,
    CREATED,
    UNKNOWN,
    EXPIRED
}

private fun BuySellOrderResponse.type() =
    when (side) {
        "BUY" -> OrderType.BUY
        "SELL" -> OrderType.SELL
        else -> throw IllegalStateException("Unsupported order type")
    }

enum class OrderType {
    BUY,
    SELL
}

private fun BuySellOrderResponse.toBuySellOrder(): BuySellOrder {
    val fiatCurrency = if (type() == OrderType.BUY) inputCurrency else outputCurrency
    val cryptoCurrency =
        CryptoCurrency.fromNetworkTicker(if (type() == OrderType.BUY) outputCurrency else inputCurrency)
            ?: throw UnknownFormatConversionException("Unknown Crypto currency: $inputCurrency")
    val fiatAmount =
        if (type() == OrderType.BUY) inputQuantity.toLongOrDefault(0) else outputQuantity.toLongOrDefault(0)

    val cryptoAmount =
        (if (type() == OrderType.BUY) outputQuantity.toBigInteger() else inputQuantity.toBigInteger())

    return BuySellOrder(
        id = id,
        pair = pair,
        fiat = FiatValue.fromMinor(fiatCurrency, fiatAmount),
        crypto = CryptoValue.fromMinor(cryptoCurrency, cryptoAmount),
        state = state.toLocalState(),
        expires = expiresAt.fromIso8601ToUtc()?.toLocalTime() ?: Date(),
        updated = updatedAt.fromIso8601ToUtc()?.toLocalTime() ?: Date(),
        created = insertedAt.fromIso8601ToUtc()?.toLocalTime() ?: Date(),
        fee = fee?.let {
            FiatValue.fromMinor(fiatCurrency, it.toLongOrDefault(0))
        },
        paymentMethodId = paymentMethodId ?: (
            when (paymentType.toPaymentMethodType()) {
                PaymentMethodType.FUNDS -> PaymentMethod.FUNDS_PAYMENT_ID
                PaymentMethodType.BANK_TRANSFER -> PaymentMethod.UNDEFINED_BANK_TRANSFER_PAYMENT_ID
                else -> PaymentMethod.UNDEFINED_CARD_PAYMENT_ID
            }),
        paymentMethodType = paymentType.toPaymentMethodType(),
        price = price?.let {
            FiatValue.fromMinor(fiatCurrency, it.toLong())
        },
        orderValue = if (type() == OrderType.BUY)
            CryptoValue.fromMinor(cryptoCurrency, cryptoAmount)
        else
            FiatValue.fromMinor(outputCurrency, outputQuantity.toLongOrDefault(0)),
        attributes = attributes,
        type = type(),
        depositPaymentId = depositPaymentId.orEmpty(),
        approvalErrorStatus = attributes?.status?.toApprovalError() ?: ApprovalErrorStatus.NONE
    )
}

private fun String.toApprovalError(): ApprovalErrorStatus =
    when (this) {
        BuySellOrderResponse.APPROVAL_ERROR_FAILED -> ApprovalErrorStatus.FAILED
        BuySellOrderResponse.APPROVAL_ERROR_DECLINED -> ApprovalErrorStatus.DECLINED
        BuySellOrderResponse.APPROVAL_ERROR_REJECTED -> ApprovalErrorStatus.REJECTED
        BuySellOrderResponse.APPROVAL_ERROR_EXPIRED -> ApprovalErrorStatus.EXPIRED
        else -> ApprovalErrorStatus.UNKNOWN
    }

fun String.toPaymentMethodType(): PaymentMethodType =
    when (this) {
        PaymentMethodResponse.PAYMENT_CARD -> PaymentMethodType.PAYMENT_CARD
        PaymentMethodResponse.BANK_TRANSFER -> PaymentMethodType.BANK_TRANSFER
        PaymentMethodResponse.FUNDS -> PaymentMethodType.FUNDS
        else -> PaymentMethodType.UNKNOWN
    }

private fun InterestActivityItemResponse.toInterestActivityItem(cryptoCurrency: CryptoCurrency) =
    InterestActivityItem(
        value = CryptoValue.fromMinor(cryptoCurrency, amountMinor.toBigInteger()),
        cryptoCurrency = cryptoCurrency,
        id = id,
        insertedAt = insertedAt.fromIso8601ToUtc()?.toLocalTime() ?: Date(),
        state = InterestActivityItem.toInterestState(state),
        type = InterestActivityItem.toTransactionType(type),
        extraAttributes = extraAttributes
    )

private fun InterestAccountDetailsResponse.toInterestAccountDetails(cryptoCurrency: CryptoCurrency) =
    InterestAccountDetails(
        balance = CryptoValue.fromMinor(cryptoCurrency, balance.toBigInteger()),
        pendingInterest = CryptoValue.fromMinor(cryptoCurrency, pendingInterest.toBigInteger()),
        pendingDeposit = CryptoValue.fromMinor(cryptoCurrency, pendingDeposit.toBigInteger()),
        totalInterest = CryptoValue.fromMinor(cryptoCurrency, totalInterest.toBigInteger()),
        lockedBalance = CryptoValue.fromMinor(cryptoCurrency, locked.toBigInteger())
    )

interface PaymentAccountMapper {
    fun map(bankAccountResponse: BankAccountResponse): BankAccount?
}

private data class CustodialFiatBalance(
    val currency: String,
    val available: Boolean,
    val balance: FiatValue?
)