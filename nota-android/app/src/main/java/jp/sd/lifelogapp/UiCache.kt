package jp.sd.lifelogapp

import android.content.Context

// 画面を開いた瞬間に前回の内容を出すための、端末内のキャッシュ（サーバーの応答をそのまま保存）。
// ユーザーIDごとに分けて保存し、ログアウト時に全部消す。最新の内容は、あとから取りに行って上書きする。
object UiCache {
    private const val PREFS = "ui_cache"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(context: Context, userId: String, key: String): String? =
        prefs(context).getString("$userId:$key", null)

    fun put(context: Context, userId: String, key: String, value: String) {
        prefs(context).edit().putString("$userId:$key", value).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
