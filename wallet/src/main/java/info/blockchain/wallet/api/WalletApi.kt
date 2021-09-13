package info.blockchain.wallet.api

import com.blockchain.api.ApiException
import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import info.blockchain.wallet.ApiCode
import info.blockchain.wallet.api.data.Settings
import info.blockchain.wallet.api.data.Status
import info.blockchain.wallet.api.data.WalletOptions
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import okhttp3.ResponseBody
import org.spongycastle.util.encoders.Hex
import retrofit2.Call
import retrofit2.Response
import java.net.URLEncoder

class WalletApi(
    private val explorerInstance: WalletExplorerEndpoints,
    private val apiCode: ApiCode,
    private val captchaSiteKey: String
) {
    fun updateFirebaseNotificationToken(
        token: String,
        guid: String?,
        sharedKey: String?
    ): Observable<ResponseBody> {
        return explorerInstance.postToWallet(
            "update-firebase",
            guid,
            sharedKey,
            token,
            token.length,
            getApiCode()
        )
    }

    fun sendSecureChannel(
        message: String
    ): Observable<ResponseBody> {
        return explorerInstance.postSecureChannel(
            "send-secure-channel",
            message,
            message.length,
            getApiCode()
        )
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        setterVisibility = JsonAutoDetect.Visibility.NONE,
        creatorVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE
    )
    class IPResponse {
        @JsonProperty("ip")
        var ip: String = ""
    }

    fun getExternalIP(): Single<String> {
        return explorerInstance.externalIP.map { it.ip }
    }

    fun setAccess(key: String?, value: String, pin: String?): Observable<Response<Status>> {
        val hex = Hex.toHexString(value.toByteArray())
        return explorerInstance.pinStore(key, pin, hex, "put", getApiCode())
    }

    fun validateAccess(key: String?, pin: String?): Observable<Response<Status>> {
        return explorerInstance.pinStore(key, pin, null, "get", getApiCode())
    }

    fun insertWallet(
        guid: String?,
        sharedKey: String?,
        activeAddressList: List<String>?,
        encryptedPayload: String,
        newChecksum: String?,
        email: String?,
        device: String?
    ): Call<ResponseBody> {
        val pipedAddresses = activeAddressList?.joinToString("|")

        return explorerInstance.syncWalletCall(
            "insert",
            guid,
            sharedKey,
            encryptedPayload,
            encryptedPayload.length,
            URLEncoder.encode(newChecksum, "utf-8"),
            pipedAddresses,
            email,
            device,
            null,
            getApiCode()
        )
    }

    fun submitCoinReceiveAddresses(guid: String, sharedKey: String, coinAddresses: String): Observable<ResponseBody> =
        explorerInstance.submitCoinReceiveAddresses(
            "subscribe-coin-addresses",
            sharedKey,
            guid,
            coinAddresses
        )

    fun updateWallet(
        guid: String?,
        sharedKey: String?,
        activeAddressList: List<String>?,
        encryptedPayload: String,
        newChecksum: String?,
        oldChecksum: String?,
        device: String?
    ): Call<ResponseBody> {

        val pipedAddresses = activeAddressList?.joinToString("|") ?: ""

        return explorerInstance.syncWalletCall(
            "update",
            guid,
            sharedKey,
            encryptedPayload,
            encryptedPayload.length,
            URLEncoder.encode(newChecksum, "utf-8"),
            pipedAddresses,
            null,
            device,
            oldChecksum,
            getApiCode()
        )
    }

    fun fetchWalletData(guid: String?, sharedKey: String?): Call<ResponseBody> {
        return explorerInstance.fetchWalletData(
            "wallet.aes.json",
            guid,
            sharedKey,
            "json",
            getApiCode()
        )
    }

    fun submitTwoFactorCode(sessionId: String, guid: String?, twoFactorCode: String): Observable<ResponseBody> {
        val headerMap: MutableMap<String, String> =
            HashMap()
        headerMap["Authorization"] = sessionId.withBearerPrefix()
        return explorerInstance.submitTwoFactorCode(
            headerMap,
            "get-wallet",
            guid,
            twoFactorCode,
            twoFactorCode.length,
            "plain",
            getApiCode()
        )
    }

    fun getSessionId(guid: String?): Observable<Response<ResponseBody>> {
        return explorerInstance.getSessionId(guid)
    }

    fun fetchEncryptedPayload(
        guid: String?,
        sessionId: String,
        resend2FASms: Boolean
    ): Observable<Response<ResponseBody>> =
        explorerInstance.fetchEncryptedPayload(
            guid,
            "SID=$sessionId",
            "json",
            resend2FASms,
            getApiCode()
        )

    fun fetchPairingEncryptionPasswordCall(guid: String?): Call<ResponseBody> {
        return explorerInstance.fetchPairingEncryptionPasswordCall(
            "pairing-encryption-password",
            guid,
            getApiCode()
        )
    }

    fun fetchPairingEncryptionPassword(guid: String?): Observable<ResponseBody> {
        return explorerInstance.fetchPairingEncryptionPassword(
            "pairing-encryption-password",
            guid,
            getApiCode()
        )
    }

    fun fetchSettings(method: String?, guid: String?, sharedKey: String?): Observable<Settings> {
        return explorerInstance.fetchSettings(
            method,
            guid,
            sharedKey,
            "plain",
            getApiCode()
        )
    }

    fun updateSettings(
        method: String?,
        guid: String?,
        sharedKey: String?,
        payload: String,
        context: String?
    ): Observable<ResponseBody> {
        return explorerInstance.updateSettings(
            method,
            guid,
            sharedKey,
            payload,
            payload.length,
            "plain",
            context,
            getApiCode()
        )
    }

    val walletOptions: Observable<WalletOptions>
        get() = explorerInstance.getWalletOptions(getApiCode())

    fun getSignedJsonToken(guid: String?, sharedKey: String?, partner: String?): Single<String> {
        return explorerInstance.getSignedJsonToken(
            guid,
            sharedKey,
            "email%7Cwallet_age",
            partner,
            getApiCode()
        )
            .map { signedToken ->
                if (!signedToken.isSuccessful) {
                    throw ApiException(signedToken.error)
                } else {
                    signedToken.token
                }
            }
    }

    fun createSessionId(email: String): Single<ResponseBody> =
        explorerInstance.createSessionId(email, getApiCode())

    fun authorizeSession(authToken: String, sessionId: String): Single<Response<ResponseBody>> =
        explorerInstance.authorizeSession(
            sessionId.withBearerPrefix(),
            authToken,
            getApiCode(),
            "authorize-approve",
            true
        )

    fun sendEmailForVerification(sessionId: String, email: String, captcha: String): Single<ResponseBody> {
        return explorerInstance.sendEmailForVerification(
            sessionId.withBearerPrefix(),
            "send-guid-reminder",
            getApiCode(),
            email,
            captcha,
            captchaSiteKey
        )
    }

    fun updateMobileSetup(
        guid: String,
        sharedKey: String,
        isMobileSetup: Boolean,
        deviceType: Int
    ): Single<ResponseBody> {
        return explorerInstance.updateMobileSetup(
            "update-mobile-setup",
            guid,
            sharedKey,
            isMobileSetup,
            deviceType
        )
    }

    fun updateMnemonicBackup(guid: String, sharedKey: String): Single<ResponseBody> {
        return explorerInstance.updateMnemonicBackup(
            "update-mnemonic-backup",
            guid,
            sharedKey
        )
    }

    fun verifyCloudBackup(
        guid: String,
        sharedKey: String,
        hasCloudBackup: Boolean,
        deviceType: Int
    ): Single<ResponseBody> {
        return explorerInstance.verifyCloudBackup(
            "verify-cloud-backup",
            guid,
            sharedKey,
            hasCloudBackup,
            deviceType
        )
    }

    private fun getApiCode(): String {
        return apiCode.apiCode
    }

    private fun String.withBearerPrefix() =
        "Bearer $this"
}