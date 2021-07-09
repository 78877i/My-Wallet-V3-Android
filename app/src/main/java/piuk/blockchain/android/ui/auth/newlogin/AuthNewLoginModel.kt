package piuk.blockchain.android.ui.auth.newlogin

import com.blockchain.logging.CrashLogger
import com.blockchain.preferences.Authorization
import com.blockchain.preferences.BrowserIdentity
import com.blockchain.preferences.SecureChannelPrefs
import info.blockchain.wallet.api.WalletApi
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.serialization.decodeFromString
import piuk.blockchain.android.ui.base.mvi.MviModel
import piuk.blockchain.androidcore.data.api.EnvironmentConfig
import piuk.blockchain.androidcore.utils.pubKeyHash
import timber.log.Timber

class AuthNewLoginModel(
    initialState: AuthNewLoginState,
    mainScheduler: Scheduler,
    environmentConfig: EnvironmentConfig,
    crashLogger: CrashLogger,
    private val secureChannelManager: SecureChannelManager,
    private val secureChannelPrefs: SecureChannelPrefs,
    private val walletApi: WalletApi
) : MviModel<AuthNewLoginState, AuthNewLoginIntents>(initialState, mainScheduler, environmentConfig, crashLogger) {

    override fun performAction(previousState: AuthNewLoginState, intent: AuthNewLoginIntents): Disposable? {
        return when (intent) {
            is AuthNewLoginIntents.InitAuthInfo ->
                parseMessage(
                    pubKeyHash = intent.pubKeyHash,
                    messageInJson = intent.messageInJson,
                    originIp = intent.originIp
                )
            is AuthNewLoginIntents.ProcessBrowserMessage ->
                processIp(
                    browserIdentity = intent.browserIdentity,
                    message = intent.message,
                    originIp = intent.originIp
                )
            is AuthNewLoginIntents.LoginDenied -> processLoginDenied(previousState)
            is AuthNewLoginIntents.LoginApproved -> processLoginApproved(previousState)
            is AuthNewLoginIntents.EnableApproval -> null
        }
    }

    private fun processLoginApproved(previousState: AuthNewLoginState): Nothing? {
        secureChannelManager.sendLoginMessage(
            channelId = previousState.message.channelId,
            pubKeyHash = previousState.browserIdentity.pubKeyHash()
        )
        secureChannelPrefs.addBrowserIdentityAuthorization(
            pubkeyHash = previousState.browserIdentity.pubKeyHash(),
            authorization = getRequestedAuthorization(previousState.message)!!
        )
        return null
    }

    private fun processLoginDenied(previousState: AuthNewLoginState): Nothing? {
        secureChannelManager.sendErrorMessage(
            channelId = previousState.message.channelId,
            pubKeyHash = previousState.browserIdentity.pubKeyHash()
        )
        return null
    }

    private fun parseMessage(pubKeyHash: String, messageInJson: String, originIp: String): Disposable? {

        process(
            AuthNewLoginIntents.ProcessBrowserMessage(
                originIp = originIp,
                browserIdentity = secureChannelPrefs.getBrowserIdentity(pubKeyHash)!!,
                message = SecureChannelManager.jsonBuilder.decodeFromString(messageInJson)
            )
        )
        return null
    }

    private fun processIp(browserIdentity: BrowserIdentity, message: SecureChannelBrowserMessage, originIp: String) =
        walletApi.getExternalIP().subscribeBy(
            onSuccess = { deviceIp ->
                process(
                    AuthNewLoginIntents.EnableApproval(
                        enableApproval = isAuthorized(browserIdentity, message) ||
                            originIp == deviceIp
                    )
                )
            },
            onError = {
                Timber.e(it)
                process(
                    AuthNewLoginIntents.EnableApproval(
                        enableApproval = false
                    )
                )
            }
        )

    private fun getRequestedAuthorization(message: SecureChannelBrowserMessage): Authorization? {
        return try {
            Authorization.valueOf(message.type.toUpperCase())
        } catch (e: Exception) {
            null
        }
    }

    private fun isAuthorized(browserIdentity: BrowserIdentity, message: SecureChannelBrowserMessage): Boolean =
        browserIdentity.authorized.contains(getRequestedAuthorization(message))
}