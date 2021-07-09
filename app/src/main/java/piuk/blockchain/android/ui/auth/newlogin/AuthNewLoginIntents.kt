package piuk.blockchain.android.ui.auth.newlogin

import com.blockchain.preferences.BrowserIdentity
import piuk.blockchain.android.ui.base.mvi.MviIntent

sealed class AuthNewLoginIntents : MviIntent<AuthNewLoginState> {
    data class InitAuthInfo(
        val pubKeyHash: String,
        val messageInJson: String,
        val originIp: String,
        private val items: List<AuthNewLoginDetailsType>,
        private val forcePin: Boolean
    ) : AuthNewLoginIntents() {
        override fun reduce(oldState: AuthNewLoginState): AuthNewLoginState {
            return oldState.copy(
                ip = originIp,
                items = items,
                forcePin = forcePin
            )
        }
    }

    data class ProcessBrowserMessage(
        val originIp: String,
        val browserIdentity: BrowserIdentity,
        val message: SecureChannelBrowserMessage
    ) : AuthNewLoginIntents() {
        override fun reduce(oldState: AuthNewLoginState): AuthNewLoginState =
            oldState.copy(
                items = oldState.items + AuthNewLoginLastLogin(message.timestamp),
                browserIdentity = browserIdentity,
                message = message,
                ip = originIp
            )
    }

    data class EnableApproval(
        private val enableApproval: Boolean
    ) : AuthNewLoginIntents() {
        override fun reduce(oldState: AuthNewLoginState): AuthNewLoginState =
            oldState.copy(
                enableApproval = enableApproval
            )
    }

    object LoginApproved : AuthNewLoginIntents() {
        override fun reduce(oldState: AuthNewLoginState): AuthNewLoginState = oldState.copy()
    }

    object LoginDenied : AuthNewLoginIntents() {
        override fun reduce(oldState: AuthNewLoginState): AuthNewLoginState = oldState.copy()
    }
}