package piuk.blockchain.android.ui.login.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.DigitsKeyListener
import android.text.method.LinkMovementMethod
import android.util.Base64
import androidx.annotation.StringRes
import com.blockchain.extensions.exhaustive
import com.blockchain.featureflags.GatedFeature
import com.blockchain.featureflags.InternalFeatureFlagApi
import com.blockchain.koin.scopedInject
import com.blockchain.koin.ssoAccountRecoveryFeatureFlag
import com.blockchain.logging.CrashLogger
import com.blockchain.remoteconfig.FeatureFlag
import com.google.android.material.textfield.TextInputLayout
import io.reactivex.rxkotlin.subscribeBy
import org.json.JSONObject
import org.koin.android.ext.android.inject
import piuk.blockchain.android.R
import piuk.blockchain.android.databinding.ActivityLoginAuthBinding
import piuk.blockchain.android.ui.auth.PinEntryActivity
import piuk.blockchain.android.ui.base.mvi.MviActivity
import piuk.blockchain.android.ui.customviews.ToastCustom
import piuk.blockchain.android.ui.customviews.toast
import piuk.blockchain.android.ui.recover.AccountRecoveryActivity
import piuk.blockchain.android.ui.recover.RecoverFundsActivity
import piuk.blockchain.android.ui.start.ManualPairingActivity
import piuk.blockchain.android.urllinks.RESET_2FA
import piuk.blockchain.android.urllinks.SECOND_PASSWORD_EXPLANATION
import piuk.blockchain.android.util.StringUtils
import piuk.blockchain.android.util.gone
import piuk.blockchain.android.util.visible
import timber.log.Timber
import java.lang.Exception

class LoginAuthActivity :
    MviActivity<LoginAuthModel, LoginAuthIntents, LoginAuthState, ActivityLoginAuthBinding>() {

    override val alwaysDisableScreenshots: Boolean
        get() = true

    override val model: LoginAuthModel by scopedInject()

    private val crashLogger: CrashLogger by inject()

    private lateinit var currentState: LoginAuthState

    private val internalFlags: InternalFeatureFlagApi by inject()

    private val ssoARFF: FeatureFlag by inject(ssoAccountRecoveryFeatureFlag)

    private var isAccountRecoveryEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        subscription = ssoARFF.enabled.subscribeBy(
            onSuccess = { enabled ->
                isAccountRecoveryEnabled = enabled
            },
            onError = { isAccountRecoveryEnabled = false }
        )
        initControls()
    }

    override fun onStart() {
        super.onStart()
        processIntentData()
    }

    private fun processIntentData() {
        intent.data?.let { uri ->
            uri.fragment?.let { fragment ->
                try {
                    val json = decodeJson(fragment)
                    val guid = json.getString(GUID)

                    binding.loginEmailText.setText(json.getString(EMAIL))
                    binding.loginWalletLabel.text = getString(R.string.login_wallet_id_text, guid)
                    if (json.has(EMAIL_CODE)) {
                        val authToken = json.getString(EMAIL_CODE)
                        model.process(LoginAuthIntents.GetSessionId(guid, authToken))
                    } else {
                        model.process(LoginAuthIntents.GetSessionId(guid, ""))
                    }
                } catch (ex: Exception) {
                    Timber.e(ex)
                    crashLogger.logException(ex)
                    // Fall back to legacy manual pairing
                    model.process(LoginAuthIntents.ShowManualPairing)
                }
            } ?: kotlin.run {
                Timber.v("The URI fragment from the Intent is empty!")
                model.process(LoginAuthIntents.ShowAuthRequired)
            }
        } ?: kotlin.run {
            Timber.v("The Intent data is empty!")
            model.process(LoginAuthIntents.ShowAuthRequired)
        }
    }

    private fun initControls() {
        with(binding) {
            backButton.setOnClickListener { finish() }
            passwordText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    passwordTextLayout.clearErrorState()
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            codeText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    codeTextLayout.clearErrorState()
                }

                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            forgotPasswordButton.setOnClickListener { launchPasswordRecoveryFlow() }
            continueButton.setOnClickListener {
                if (currentState.authMethod != TwoFAMethod.OFF) {
                    model.process(
                        LoginAuthIntents.SubmitTwoFactorCode(
                            password = passwordText.text.toString(),
                            code = codeText.text.toString()
                        )
                    )
                } else {
                    model.process(LoginAuthIntents.VerifyPassword(passwordText.text.toString()))
                }
            }
            manualPairingButton.setOnClickListener { start<ManualPairingActivity>(this@LoginAuthActivity) }
        }
    }

    override fun initBinding(): ActivityLoginAuthBinding = ActivityLoginAuthBinding.inflate(layoutInflater)

    override fun render(newState: LoginAuthState) {
        currentState = newState
        when (newState.authStatus) {
            AuthStatus.None -> binding.progressBar.visible()
            AuthStatus.GetSessionId,
            AuthStatus.AuthorizeApproval,
            AuthStatus.GetPayload -> binding.progressBar.gone()
            AuthStatus.Submit2FA,
            AuthStatus.VerifyPassword -> binding.progressBar.visible()
            AuthStatus.Complete -> startActivity(Intent(this, PinEntryActivity::class.java))
            AuthStatus.PairingFailed -> showErrorToast(R.string.pairing_failed)
            AuthStatus.InvalidPassword -> {
                binding.progressBar.gone()
                binding.passwordTextLayout.setErrorState(getString(R.string.invalid_password))
            }
            AuthStatus.AuthFailed -> showErrorToast(R.string.auth_failed)
            AuthStatus.InitialError -> showErrorToast(R.string.common_error)
            AuthStatus.AuthRequired -> showToast(getString(R.string.auth_required))
            AuthStatus.Invalid2FACode -> {
                binding.progressBar.gone()
                binding.codeTextLayout.setErrorState(getString(R.string.invalid_two_fa_code))
            }
            AuthStatus.ShowManualPairing -> {
                binding.authScrollView.gone()
                binding.progressBar.gone()
                binding.continueButton.gone()
                binding.manualPairingButton.isEnabled = true
                binding.manualPairingButton.visible()
            }
        }.exhaustive
        update2FALayout(newState.authMethod)
    }

    private fun update2FALayout(authMethod: TwoFAMethod) {
        with(binding) {
            when (authMethod) {
                TwoFAMethod.OFF -> codeTextLayout.gone()
                TwoFAMethod.YUBI_KEY -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.hardware_key_hint)
                    codeLabel.visible()
                    codeLabel.text = getString(R.string.tap_hardware_key_label)
                }
                TwoFAMethod.SMS -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.two_factor_code_hint)
                    setup2FANotice(
                        textId = R.string.lost_2fa_notice,
                        annotationForLink = RESET_2FA_LINK_ANNOTATION,
                        url = RESET_2FA
                    )
                }
                TwoFAMethod.GOOGLE_AUTHENTICATOR -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.two_factor_code_hint)
                    codeText.inputType = InputType.TYPE_NUMBER_VARIATION_NORMAL
                    codeText.keyListener = DigitsKeyListener.getInstance(DIGITS)
                    setup2FANotice(
                        textId = R.string.lost_2fa_notice,
                        annotationForLink = RESET_2FA_LINK_ANNOTATION,
                        url = RESET_2FA
                    )
                }
                TwoFAMethod.SECOND_PASSWORD -> {
                    codeTextLayout.visible()
                    codeTextLayout.hint = getString(R.string.second_password_hint)
                    forgotSecondPasswordButton.visible()
                    forgotSecondPasswordButton.setOnClickListener { launchPasswordRecoveryFlow() }
                    setup2FANotice(
                        textId = R.string.second_password_notice,
                        annotationForLink = SECOND_PASSWORD_LINK_ANNOTATION,
                        url = SECOND_PASSWORD_EXPLANATION
                    )
                }
            }.exhaustive
        }
    }

    private fun setup2FANotice(@StringRes textId: Int, annotationForLink: String, url: String) {
        binding.twoFaNotice.apply {
            visible()
            val links = mapOf(annotationForLink to Uri.parse(url))
            text = StringUtils.getStringWithMappedAnnotations(context, textId, links)
            movementMethod = LinkMovementMethod.getInstance()
        }
    }

    private fun showErrorToast(@StringRes message: Int) {
        binding.progressBar.gone()
        binding.passwordText.setText("")
        toast(message, ToastCustom.TYPE_ERROR)
    }

    private fun showToast(message: String) {
        binding.progressBar.gone()
        binding.passwordText.setText("")
        toast(message, ToastCustom.TYPE_GENERAL)
    }

    private fun launchPasswordRecoveryFlow() {
        if (internalFlags.isFeatureEnabled(GatedFeature.ACCOUNT_RECOVERY) && isAccountRecoveryEnabled) {
            start<AccountRecoveryActivity>(this)
        } else {
            RecoverFundsActivity.start(this)
        }
    }

    private fun decodeJson(fragment: String): JSONObject {
        val encodedData = fragment.substringAfterLast(LINK_DELIMITER)
        logInvalidCharacters(encodedData)
        val urlSafeEncodedData = encodedData.apply {
            unEscapedCharactersMap.map { entry ->
                replace(entry.key, entry.value)
            }
        }
        val data = Base64.decode(
            urlSafeEncodedData.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return JSONObject(String(data))
    }

    private fun logInvalidCharacters(encodedData: String) {
        encodedData.mapIndexed { index, chr ->
            if (!alphaNumericRegex.matches(chr.toString())) {
                crashLogger.logState(
                    INVALID_PAYLOAD_CHARACTER, "" +
                        "$chr at index $index part ${
                            encodedData.safeSubstring(
                                startIndex = index, endIndex = index + 3
                            )
                        }"
                )
            }
        }
    }

    companion object {
        private const val LINK_DELIMITER = "/login/"
        private const val GUID = "guid"
        private const val EMAIL = "email"
        private const val EMAIL_CODE = "email_code"
        private const val DIGITS = "1234567890"
        private const val SECOND_PASSWORD_LINK_ANNOTATION = "learn_more"
        private const val RESET_2FA_LINK_ANNOTATION = "reset_2fa"
        private const val INVALID_PAYLOAD_CHARACTER = "invalid_payload_character"
        private val alphaNumericRegex = Regex("[a-zA-Z0-9]")
        private val unEscapedCharactersMap = mapOf(
            "%2B" to "+",
            "%2F" to "/",
            "%2b" to "+",
            "%2f" to "/"
        )
    }

    private fun TextInputLayout.setErrorState(errorMessage: String) {
        isErrorEnabled = true
        error = errorMessage
    }

    private fun TextInputLayout.clearErrorState() {
        error = ""
        isErrorEnabled = false
    }
}

private fun String.safeSubstring(startIndex: Int, endIndex: Int): String {
    return try {
        substring(startIndex = startIndex, endIndex = endIndex)
    } catch (e: StringIndexOutOfBoundsException) {
        ""
    }
}
