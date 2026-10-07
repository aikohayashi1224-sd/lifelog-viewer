package jp.sd.lifelogapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import jp.sd.lifelogapp.ui.theme.LifelogAppTheme
import jp.sd.lifelogapp.ui.theme.ThemePrefs
import jp.sd.lifelogapp.ui.theme.ThemeState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ThemeState.current = ThemePrefs.load(this)
        NotaState.load(this)
        setContent {
            LifelogAppTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val sessionStatus by supabase.auth.sessionStatus.collectAsState()
                    when (sessionStatus) {
                        is SessionStatus.Authenticated -> HomeScreen(modifier = Modifier.padding(innerPadding))
                        else -> AuthScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }
}