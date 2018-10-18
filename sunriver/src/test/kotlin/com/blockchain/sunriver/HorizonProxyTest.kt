package com.blockchain.sunriver

import com.blockchain.koin.sunriverModule
import com.blockchain.network.initRule
import com.blockchain.testutils.getStringFromResource
import com.blockchain.testutils.lumens
import io.fabric8.mockwebserver.DefaultMockServer
import org.amshove.kluent.`should be instance of`
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should equal`
import org.amshove.kluent.`should not equal`
import org.amshove.kluent.`should throw`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.standalone.StandAloneContext
import org.koin.standalone.get
import org.koin.test.AutoCloseKoinTest
import org.stellar.sdk.Network
import org.stellar.sdk.requests.ErrorResponse
import org.stellar.sdk.responses.operations.CreateAccountOperationResponse
import org.stellar.sdk.responses.operations.PaymentOperationResponse

class HorizonProxyTest : AutoCloseKoinTest() {

    private val server = DefaultMockServer()

    @get:Rule
    private val initMockServer = server.initRule()

    @Before
    fun startKoin() {
        StandAloneContext.startKoin(
            listOf(
                sunriverModule
            ),
            extraProperties = mapOf("HorizonURL" to server.url(""))
        )
    }

    @Test
    fun `get xlm balance`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
            .andReturn(
                200,
                getStringFromResource("accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4.json")
            )
            .once()

        val proxy = get<HorizonProxy>()

        val balance =
            proxy.getBalance("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")

        balance `should equal` 109969.99997.lumens()
    }

    @Test
    fun `get balance if account does not exist`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
            .andReturn(
                404,
                getStringFromResource("accounts/not_found.json")
            )
            .once()

        val proxy = get<HorizonProxy>()

        val balance =
            proxy.getBalance("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")

        balance `should equal` 0.lumens()
    }

    @Test
    fun `on any other kind of server error, bubble up exception`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
            .andReturn(
                301,
                getStringFromResource("accounts/not_found.json")
            )
            .once()

        val proxy = get<HorizonProxy>();

        {
            proxy.getBalance("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
        } `should throw` ErrorResponse::class
    }

    @Test
    fun `get xlm transaction history`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4/operations")
            .andReturn(
                200,
                getStringFromResource("transactions/transaction_list.json")
            )
            .once()

        val proxy = get<HorizonProxy>()

        val transactions =
            proxy.getTransactionList("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")

        transactions.size `should equal` 3
        transactions[0] `should be instance of` CreateAccountOperationResponse::class.java
        transactions[1] `should be instance of` PaymentOperationResponse::class.java
        transactions[2] `should be instance of` PaymentOperationResponse::class.java
    }

    @Test
    fun `get xlm transaction history if not found`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4/operations")
            .andReturn(
                404,
                getStringFromResource("accounts/not_found.json")
            )
            .once()

        val proxy = get<HorizonProxy>()

        val transactions =
            proxy.getTransactionList("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")

        transactions.size `should equal` 0
    }

    @Test
    fun `get xlm transaction history, on any other kind of server error, bubble up exception`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4/operations")
            .andReturn(
                301,
                getStringFromResource("accounts/not_found.json")
            )
            .once()

        val proxy = get<HorizonProxy>();

        {
            proxy.getTransactionList("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
        } `should throw` ErrorResponse::class
    }

    @Test
    fun `accountExists - get account existence`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
            .andReturn(
                200,
                getStringFromResource("accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4.json")
            )
            .once()

        val proxy = get<HorizonProxy>()

        proxy.accountExists("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4") `should be` true
    }

    @Test
    fun `accountExists - get account non-existence`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
            .andReturn(
                404,
                getStringFromResource("accounts/not_found.json")
            )
            .once()

        val proxy = get<HorizonProxy>()

        proxy.accountExists("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4") `should be` false
    }

    @Test
    fun `accountExists - on any other kind of server error, bubble up exception`() {
        server.expect().get().withPath("/accounts/GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
            .andReturn(
                301,
                getStringFromResource("accounts/not_found.json")
            )
            .once()

        val proxy = get<HorizonProxy>();

        {
            proxy.accountExists("GC7GSOOQCBBWNUOB6DIWNVM7537UKQ353H6LCU3DB54NUTVFR2T6OHF4")
        } `should throw` ErrorResponse::class
    }

    @Test
    fun `Uses test net if url contains the word test`() {
        HorizonProxy("test_net")

        Network.current().networkPassphrase `should equal` "Test SDF Network ; September 2015"
    }

    @Test
    fun `Uses the public network if url does not contains the word test`() {
        HorizonProxy("te_st_net")

        Network.current().networkPassphrase `should equal` "Public Global Stellar Network ; September 2015"
    }
}
