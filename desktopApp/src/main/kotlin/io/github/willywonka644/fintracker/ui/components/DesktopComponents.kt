package io.github.willywonka644.fintracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.ui.theme.FinTheme
import io.github.willywonka644.fintracker.util.MoneyFormat

@Composable
fun DesktopFinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val color = MaterialTheme.colorScheme.surface
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
fun DesktopMoneyText(
    amount: Double,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    emphasize: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    val color = when {
        !emphasize -> MaterialTheme.colorScheme.onSurface
        amount >= 0 -> finColors.income
        else -> finColors.expense
    }
    Text(
        text = MoneyFormat.currency(amount),
        style = style.copy(color = color, fontFeatureSettings = "tnum"),
        modifier = modifier,
    )
}

@Composable
fun DesktopHeroCard(
    title: String,
    amount: Double,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val finColors = FinTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(finColors.heroGradient)
            .drawBehind {
                drawCircle(Color.White.copy(alpha = 0.09f), radius = size.width * 0.55f,
                    center = Offset(size.width * 1.05f, -size.height * 0.25f))
                drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.38f,
                    center = Offset(-size.width * 0.08f, size.height * 1.1f))
            }
            .padding(20.dp),
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(4.dp))
            Text(
                MoneyFormat.currency(amount),
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                color = Color.White,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
fun DesktopKpiCard(
    title: String,
    amount: Double,
    isIncome: Boolean,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val color = if (isIncome) FinTheme.colors.income else FinTheme.colors.expense
    DesktopFinCard(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = FinTheme.colors.textSub)
        Spacer(Modifier.height(8.dp))
        Text(
            MoneyFormat.currency(amount),
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            color = color,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.textFaint)
        }
    }
}
