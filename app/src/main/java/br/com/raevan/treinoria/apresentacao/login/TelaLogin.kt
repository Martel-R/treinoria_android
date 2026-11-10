package br.com.raevan.treinoria.apresentacao.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.raevan.treinoria.dados.sessao.GerenciadorSessao
import br.com.raevan.treinoria.nucleo.tema.CianoNeon
import br.com.raevan.treinoria.nucleo.tema.VerdeEsmeralda
import br.com.raevan.treinoria.nucleo.tema.VermelhoAlerta

@Composable
fun TelaLogin(
    viewModel: LoginViewModel,
    aoLoginSucesso: () -> Unit
) {
    var email by remember {
        val salvo = try { GerenciadorSessao.obterInstancia().obterUltimoEmail() } catch (_: Exception) { "" }
        mutableStateOf(salvo)
    }
    var senha by remember { mutableStateOf("") }
    var confirmarSenha by remember { mutableStateOf("") }
    var senhaVisivel by remember { mutableStateOf(false) }
    var confirmarSenhaVisivel by remember { mutableStateOf(false) }

    var modoCadastro by remember { mutableStateOf(false) }
    var perfilSelecionado by remember { mutableStateOf("athlete") } // "athlete" ou "trainer"
    var nome by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var whatsapp by remember { mutableStateOf("") }
    var cidade by remember { mutableStateOf("Macapá") }
    var uf by remember { mutableStateOf("AP") }
    var cref by remember { mutableStateOf("") }

    val carregando by viewModel.carregando.collectAsStateWithLifecycle()
    val mensagemErro by viewModel.mensagemErro.collectAsStateWithLifecycle()
    val usuarioAutenticado by viewModel.usuarioAutenticado.collectAsStateWithLifecycle()

    LaunchedEffect(usuarioAutenticado) {
        if (usuarioAutenticado) {
            aoLoginSucesso()
        }
    }

    val senhasConferem = senha.length >= 6 && senha == confirmarSenha

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(VerdeEsmeralda.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = "TreinorIA",
                    tint = VerdeEsmeralda,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "TREINORIA PRO",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = VerdeEsmeralda
            )
            Text(
                text = "Aluno & Personal Trainer • Raevan Tech Group",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            if (mensagemErro != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VermelhoAlerta.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VermelhoAlerta)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mensagemErro!!, color = VermelhoAlerta, fontSize = 12.sp)
                    }
                }
            }

            if (modoCadastro) {
                // Seletor Aluno vs Personal Trainer
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = perfilSelecionado == "athlete",
                        onClick = { perfilSelecionado = "athlete" },
                        label = { Text("🏋️ Sou Aluno / Atleta", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = perfilSelecionado == "trainer",
                        onClick = { perfilSelecionado = "trainer" },
                        label = { Text("📋 Sou Personal Trainer", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome Completo *") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cpf,
                        onValueChange = { cpf = it.take(14) },
                        label = { Text("CPF (11 dígitos) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = whatsapp,
                        onValueChange = { whatsapp = it.take(15) },
                        label = { Text("WhatsApp c/ DDD *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = cidade,
                        onValueChange = { cidade = it },
                        label = { Text("Cidade *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = uf,
                        onValueChange = { uf = it.take(2).uppercase() },
                        label = { Text("UF *") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (perfilSelecionado == "trainer") {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = cref,
                        onValueChange = { cref = it },
                        label = { Text("Registro CREF (Opcional)") },
                        leadingIcon = { Icon(Icons.Default.Verified, contentDescription = null, tint = CianoNeon) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Seu E-mail *") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Campo 1 de Senha com Olhinho
            OutlinedTextField(
                value = senha,
                onValueChange = { senha = it },
                label = { Text(if (modoCadastro) "Crie sua Senha (mín. 6 caracteres) *" else "Sua Senha *") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { senhaVisivel = !senhaVisivel }) {
                        Icon(
                            imageVector = if (senhaVisivel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (senhaVisivel) "Ocultar senha" else "Mostrar senha"
                        )
                    }
                },
                visualTransformation = if (senhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Campo 2 de Confirmação de Senha com Olhinho e Validação em Tempo Real
            if (modoCadastro) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirmarSenha,
                    onValueChange = { confirmarSenha = it },
                    label = { Text("Confirme sua Senha *") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { confirmarSenhaVisivel = !confirmarSenhaVisivel }) {
                            Icon(
                                imageVector = if (confirmarSenhaVisivel) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (confirmarSenhaVisivel) "Ocultar senha" else "Mostrar senha"
                            )
                        }
                    },
                    visualTransformation = if (confirmarSenhaVisivel) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    isError = confirmarSenha.isNotEmpty() && !senhasConferem,
                    supportingText = {
                        if (confirmarSenha.isNotEmpty()) {
                            Text(
                                text = if (senhasConferem) "✓ Senhas conferem!" else "As senhas não conferem (mín. 6 caracteres)",
                                color = if (senhasConferem) VerdeEsmeralda else VermelhoAlerta,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    if (modoCadastro) {
                        viewModel.cadastrarConta(
                            nome = nome,
                            cpf = cpf,
                            whatsapp = whatsapp,
                            cidade = cidade,
                            uf = uf,
                            emailDigitado = email,
                            senhaDigitada = senha,
                            confirmarSenhaDigitada = confirmarSenha,
                            perfilConta = perfilSelecionado,
                            cref = cref
                        )
                    } else {
                        viewModel.fazerLogin(email, senha)
                    }
                },
                enabled = !carregando && (!modoCadastro || senhasConferem),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = VerdeEsmeralda),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (carregando) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = if (modoCadastro) "Liberar 7 Dias Grátis" else "Entrar no TreinorIA",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = {
                    modoCadastro = !modoCadastro
                    viewModel.limparErro()
                }
            ) {
                Text(
                    text = if (modoCadastro) "Já possui conta? Toque aqui para entrar" else "Novo por aqui? Criar conta grátis (Aluno ou Personal)",
                    color = VerdeEsmeralda,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
