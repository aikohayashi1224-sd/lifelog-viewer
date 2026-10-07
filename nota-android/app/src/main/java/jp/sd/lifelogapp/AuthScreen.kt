package jp.sd.lifelogapp

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

private fun authErrorMessage(e: Exception, action: String): String {
    val raw = e.message ?: ""
    return when {
        raw.contains("weak_password", ignoreCase = true) ->
            "パスワードは6文字以上で入力してください。"
        raw.contains("user_already_exists", ignoreCase = true) || raw.contains("already registered", ignoreCase = true) ->
            "このメールアドレスは既に登録されています。ログインをお試しください。"
        raw.contains("invalid_credentials", ignoreCase = true) || raw.contains("Invalid login credentials", ignoreCase = true) ->
            "メールアドレスまたはパスワードが正しくありません。"
        raw.contains("invalid_email", ignoreCase = true) || raw.contains("email_address_invalid", ignoreCase = true) ->
            "メールアドレスの形式が正しくありません。"
        raw.contains("over_request_rate_limit", ignoreCase = true) ->
            "試行回数が多すぎます。しばらく時間をおいてから、もう一度お試しください。"
        raw.isBlank() ->
            "${action}できませんでした。時間をおいて再度お試しください。"
        else ->
            "${action}できませんでした: ${raw.substringBefore(":").trim()}"
    }
}

@Composable
fun AuthScreen(modifier: Modifier = Modifier) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
    FloatingBubbles(modifier = Modifier.fillMaxSize())
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        var started by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { started = true }
        val pop by animateFloatAsState(
            targetValue = if (started) 1f else 0f,
            animationSpec = tween(durationMillis = 1100),
            label = "notaPop"
        )
        val bobTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "notaBob")
        val bob by bobTransition.animateFloat(
            initialValue = -5f, targetValue = 5f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                tween(2200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "bob"
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Nota(
                NotaKind.SMILE, 200.dp, always = true,
                modifier = Modifier.graphicsLayer {
                    val sc = 0.6f + 0.4f * pop
                    scaleX = sc; scaleY = sc; alpha = pop; translationY = bob * density
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "のたろん", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "ひとりじゃない、を、ゆるやかに。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it.filter { c -> c.code in 33..126 } },
            label = { Text("メールアドレス") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("パスワード") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                TextButton(onClick = { passwordVisible = !passwordVisible }) {
                    Text(if (passwordVisible) "隠す" else "表示")
                }
            },
            supportingText = { Text("6文字以上で入力してください") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Row {
            Button(
                enabled = !isLoading,
                onClick = {
                    isLoading = true
                    statusMessage = null
                    scope.launch {
                        try {
                            supabase.auth.signUpWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            statusMessage = "登録しました"
                        } catch (e: Exception) {
                            Log.e("AuthScreen", "signUp failed", e)
                            statusMessage = authErrorMessage(e, "登録")
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("新規登録")
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                enabled = !isLoading,
                onClick = {
                    isLoading = true
                    statusMessage = null
                    scope.launch {
                        try {
                            supabase.auth.signInWith(Email) {
                                this.email = email
                                this.password = password
                            }
                            statusMessage = "ログインしました: ${supabase.auth.currentUserOrNull()?.email}"
                        } catch (e: Exception) {
                            Log.e("AuthScreen", "signIn failed", e)
                            statusMessage = authErrorMessage(e, "ログイン")
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("ログイン")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
        }
        statusMessage?.let {
            Text(text = it)
        }
    }
    }
}
