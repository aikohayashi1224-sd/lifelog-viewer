package jp.sd.lifelogapp.ui.theme

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// 色は、日本の伝統色（nipponcolors.com）から選び、
// 大きな面は淡く、文字とボタンは濃く（読みやすさを優先）している。
enum class ThemeOption(val label: String) {
    MIZU("水色"),
    CREAM("クリーム"),
    PINK("ピンク"),
    GRAY("ライトグレー")
}

fun colorSchemeFor(option: ThemeOption): ColorScheme = when (option) {
    // 甕覗(A5DEE4)・勿忘草(7DB9DE) を土台に、ボタンは藍に寄せた濃い青
    ThemeOption.MIZU -> lightColorScheme(
        primary = Color(0xFF1E6F8C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFCFEBF1),
        onPrimaryContainer = Color(0xFF102B36),
        secondary = Color(0xFF7DB9DE),
        onSecondary = Color(0xFF0F2B38),
        secondaryContainer = Color(0xFFDDF0F6),
        onSecondaryContainer = Color(0xFF102B36),
        tertiary = Color(0xFF566C73),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFEEF8FB),
        onBackground = Color(0xFF14303B),
        surface = Color(0xFFF8FCFD),
        onSurface = Color(0xFF14303B),
        surfaceVariant = Color(0xFFDFF0F5),
        onSurfaceVariant = Color(0xFF3E5A66),
        outline = Color(0xFF6F98A8),
        outlineVariant = Color(0xFFBFDCE5),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF4FAFC),
        surfaceContainer = Color(0xFFEAF4F8),
        surfaceContainerHigh = Color(0xFFE0EEF4),
        surfaceContainerHighest = Color(0xFFD5E8EF),
        surfaceTint = Color(0x00000000)
    )
    // 白練(FCFAF2)・鳥の子(DAC9A6) を土台に、ボタンは茶に寄せた濃い色
    ThemeOption.CREAM -> lightColorScheme(
        primary = Color(0xFF86672E),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEEE2C0),
        onPrimaryContainer = Color(0xFF392C12),
        secondary = Color(0xFFDAC9A6),
        onSecondary = Color(0xFF392C12),
        secondaryContainer = Color(0xFFF3EACF),
        onSecondaryContainer = Color(0xFF392C12),
        tertiary = Color(0xFF6B5A3A),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFBF8EC),
        onBackground = Color(0xFF3A3123),
        surface = Color(0xFFFFFEF8),
        onSurface = Color(0xFF3A3123),
        surfaceVariant = Color(0xFFF2EBD6),
        onSurfaceVariant = Color(0xFF5E513B),
        outline = Color(0xFFA2916D),
        outlineVariant = Color(0xFFE0D5B8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFCF9EF),
        surfaceContainer = Color(0xFFF6F1E0),
        surfaceContainerHigh = Color(0xFFF0E9D2),
        surfaceContainerHighest = Color(0xFFE8E0C6),
        surfaceTint = Color(0x00000000)
    )
    // 桜(FEDFE1)・撫子(DC9FB4) を土台に、ボタンは紅梅に寄せた濃い色
    ThemeOption.PINK -> lightColorScheme(
        primary = Color(0xFFB0405C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFBDDE2),
        onPrimaryContainer = Color(0xFF45202B),
        secondary = Color(0xFFF1B5C4),
        onSecondary = Color(0xFF45202B),
        secondaryContainer = Color(0xFFFCE7EA),
        onSecondaryContainer = Color(0xFF45202B),
        tertiary = Color(0xFF8C5063),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFFFF4F6),
        onBackground = Color(0xFF3F2229),
        surface = Color(0xFFFFFAFB),
        onSurface = Color(0xFF3F2229),
        surfaceVariant = Color(0xFFFCE6E9),
        onSurfaceVariant = Color(0xFF6E4852),
        outline = Color(0xFFB9818F),
        outlineVariant = Color(0xFFEFCBD3),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFF7F8),
        surfaceContainer = Color(0xFFFDEFF1),
        surfaceContainerHigh = Color(0xFFFAE6E9),
        surfaceContainerHighest = Color(0xFFF5DCE0),
        surfaceTint = Color(0x00000000)
    )
    // 白鼠(BDC0BA)・銀鼠(91989F)・藍鼠(566C73) を使った、静かな灰色
    ThemeOption.GRAY -> lightColorScheme(
        primary = Color(0xFF4D6169),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFDADDD9),
        onPrimaryContainer = Color(0xFF1F272A),
        secondary = Color(0xFFBDC0BA),
        onSecondary = Color(0xFF1F272A),
        secondaryContainer = Color(0xFFE6E8E5),
        onSecondaryContainer = Color(0xFF1F272A),
        tertiary = Color(0xFF566C73),
        onTertiary = Color(0xFFFFFFFF),
        background = Color(0xFFF3F4F2),
        onBackground = Color(0xFF232B2E),
        surface = Color(0xFFFAFAF9),
        onSurface = Color(0xFF232B2E),
        surfaceVariant = Color(0xFFE5E7E4),
        onSurfaceVariant = Color(0xFF4A5559),
        outline = Color(0xFF8A9296),
        outlineVariant = Color(0xFFD3D6D2),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF7F8F6),
        surfaceContainer = Color(0xFFEFF0EE),
        surfaceContainerHigh = Color(0xFFE7E9E6),
        surfaceContainerHighest = Color(0xFFDEE1DD),
        surfaceTint = Color(0x00000000)
    )
}

object ThemeState {
    var current by mutableStateOf(ThemeOption.MIZU)
}

object ThemePrefs {
    private const val PREFS_NAME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"

    fun load(context: Context): ThemeOption {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString(KEY_THEME, ThemeOption.MIZU.name)
        return try {
            ThemeOption.valueOf(name ?: ThemeOption.MIZU.name)
        } catch (e: Exception) {
            ThemeOption.MIZU
        }
    }

    fun save(context: Context, option: ThemeOption) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, option.name)
            .apply()
    }
}
