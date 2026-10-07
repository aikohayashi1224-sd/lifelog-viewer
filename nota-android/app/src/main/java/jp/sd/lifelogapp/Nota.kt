package jp.sd.lifelogapp

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

// アプリの案内役「のたろん」。表情は5種類。
enum class NotaKind(val res: Int) {
    SMILE(R.drawable.nota_smile),   // 笑顔（あいさつ）
    JOY(R.drawable.nota_joy),       // うれしい
    BOW(R.drawable.nota_bow),       // おじぎ（お願い・ひとこと）
    THINK(R.drawable.nota_think),   // 考え中・ご注意
    SLEEP(R.drawable.nota_sleep)    // おやすみ（何もないとき）
}

// 「のたろんを、お休みさせる」設定。お休み中は、ログイン画面以外には、出ない。
object NotaState {
    private const val PREFS = "nota_prefs"
    private const val KEY = "visible"
    var visible by mutableStateOf(true)

    fun load(context: Context) {
        visible = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
    }

    fun save(context: Context, value: Boolean) {
        visible = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
    }
}

@Composable
fun Nota(kind: NotaKind, width: Dp, modifier: Modifier = Modifier, always: Boolean = false) {
    if (!NotaState.visible && !always) return
    Image(
        painter = painterResource(kind.res),
        contentDescription = "のたろん",
        modifier = modifier.width(width).aspectRatio(560f / 476f)
    )
}
