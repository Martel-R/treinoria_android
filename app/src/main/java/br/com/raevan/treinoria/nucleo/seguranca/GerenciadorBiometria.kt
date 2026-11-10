package br.com.raevan.treinoria.nucleo.seguranca

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object GerenciadorBiometria {

    private fun obterAutenticadoresPermitidos(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun podeAutenticarComBiometria(context: Context): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val status = biometricManager.canAuthenticate(obterAutenticadoresPermitidos())
            status == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Throwable) {
            false
        }
    }

    fun solicitarAutenticacao(
        activity: FragmentActivity,
        titulo: String = "Desbloquear TreinorIA",
        subtitulo: String = "Confirme sua digital ou reconhecimento facial para acessar seus treinos",
        aoSucesso: () -> Unit,
        aoErro: (String) -> Unit = {}
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    aoSucesso()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    aoErro(errString.toString())
                }
            }

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(titulo)
                .setSubtitle(subtitulo)
                .setAllowedAuthenticators(obterAutenticadoresPermitidos())
                .build()

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            aoErro(e.localizedMessage ?: "Não foi possível abrir o leitor biométrico.")
        }
    }
}
