package br.com.raevan.treinoria.apresentacao.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.raevan.treinoria.dados.rede.ClienteSupabase
import br.com.raevan.treinoria.dados.repositorios.RepositorioTreinoria
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LoginViewModel : ViewModel() {

    private val _carregando = MutableStateFlow(false)
    val carregando: StateFlow<Boolean> = _carregando.asStateFlow()

    private val _mensagemErro = MutableStateFlow<String?>(null)
    val mensagemErro: StateFlow<String?> = _mensagemErro.asStateFlow()

    private val _usuarioAutenticado = MutableStateFlow(false)
    val usuarioAutenticado: StateFlow<Boolean> = _usuarioAutenticado.asStateFlow()

    init {
        verificarSessao()
    }

    fun verificarSessao() {
        val sessao = try { ClienteSupabase.client.auth.currentSessionOrNull() } catch (_: Exception) { null }
        val temSessaoSalva = try { GerenciadorSessao.obterInstancia().temSessaoSalva() } catch (_: Exception) { false }
        _usuarioAutenticado.value = (sessao != null || temSessaoSalva)
    }

    fun fazerLogin(emailDigitado: String, senhaDigitada: String) {
        if (emailDigitado.isBlank() || senhaDigitada.isBlank()) {
            _mensagemErro.value = "Informe seu e-mail e sua senha."
            return
        }

        viewModelScope.launch {
            _carregando.value = true
            _mensagemErro.value = null
            try {
                ClienteSupabase.client.auth.signInWith(Email) {
                    email = emailDigitado.trim()
                    password = senhaDigitada
                }
                try {
                    GerenciadorSessao.obterInstancia().salvarUltimoEmail(emailDigitado.trim())
                    RepositorioTreinoria.carregarPerfil()
                } catch (_: Exception) {}
                _usuarioAutenticado.value = true
            } catch (e: Exception) {
                _mensagemErro.value = "E-mail ou senha incorretos. Verifique seus dados."
            } finally {
                _carregando.value = false
            }
        }
    }

    fun cadastrarConta(
        nome: String,
        cpf: String,
        whatsapp: String,
        cidade: String,
        uf: String,
        emailDigitado: String,
        senhaDigitada: String,
        confirmarSenhaDigitada: String,
        perfilConta: String, // "athlete" ou "trainer"
        cref: String
    ) {
        val cpfDigitos = cpf.replace(Regex("\\D"), "")
        if (nome.isBlank() || emailDigitado.isBlank() || senhaDigitada.isBlank()) {
            _mensagemErro.value = "Preencha nome completo, e-mail e senha."
            return
        }
        if (cpfDigitos.length != 11) {
            _mensagemErro.value = "Informe um CPF válido com 11 dígitos."
            return
        }
        if (senhaDigitada.length < 6) {
            _mensagemErro.value = "A senha deve ter pelo menos 6 caracteres."
            return
        }
        if (senhaDigitada != confirmarSenhaDigitada) {
            _mensagemErro.value = "As senhas digitadas não conferem."
            return
        }

        viewModelScope.launch {
            _carregando.value = true
            _mensagemErro.value = null
            try {
                ClienteSupabase.client.auth.signUpWith(Email) {
                    email = emailDigitado.trim()
                    password = senhaDigitada
                    data = buildJsonObject {
                        put("name", nome.trim())
                        put("cpf", cpfDigitos)
                        put("whatsapp_phone", whatsapp.replace(Regex("\\D"), ""))
                        put("city", cidade.trim().ifBlank { "Macapá" })
                        put("state", uf.trim().ifBlank { "AP" }.uppercase())
                        put("account_role", perfilConta)
                        if (perfilConta == "trainer" && cref.isNotBlank()) {
                            put("cref_register", cref.trim())
                        }
                    }
                }
                try {
                    ClienteSupabase.client.auth.signInWith(Email) {
                        email = emailDigitado.trim()
                        password = senhaDigitada
                    }
                } catch (_: Exception) {}

                try {
                    GerenciadorSessao.obterInstancia().salvarUltimoEmail(emailDigitado.trim())
                    RepositorioTreinoria.garantirDadosIniciaisUsuario()
                    RepositorioTreinoria.carregarPerfil()
                } catch (_: Exception) {}

                _usuarioAutenticado.value = true
            } catch (e: Exception) {
                _mensagemErro.value = e.message ?: "Não foi possível criar sua conta. Verifique se o e-mail ou CPF já possuem cadastro."
            } finally {
                _carregando.value = false
            }
        }
    }

    fun fazerLogout() {
        _usuarioAutenticado.value = false
        _mensagemErro.value = null
        try {
            kotlinx.coroutines.runBlocking {
                GerenciadorSessao.obterInstancia().deleteSession()
            }
        } catch (_: Exception) {}
        viewModelScope.launch {
            try {
                ClienteSupabase.client.auth.signOut()
            } catch (_: Exception) {}
        }
    }

    fun limparErro() {
        _mensagemErro.value = null
    }
}
