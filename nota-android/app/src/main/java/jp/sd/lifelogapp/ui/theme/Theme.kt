package jp.sd.lifelogapp.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val RoundedShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun LifelogAppTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeFor(ThemeState.current)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = RoundedShapes,
        content = content
    )
}
