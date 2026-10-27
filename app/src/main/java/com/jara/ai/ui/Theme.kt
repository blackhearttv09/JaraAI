package com.jara.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF070B1F); val Card = Color(0xFF0E1736); val Line = Color(0xFF26337A)
val Blue = Color(0xFF2F6BFF); val Cyan = Color(0xFF3FA9FF); val Purple = Color(0xFF9B4DFF)
val Muted = Color(0xFF8F9BC7); val Good = Color(0xFF2ECC71); val Bad = Color(0xFFFF5252)
val Grad = Brush.linearGradient(listOf(Purple, Blue, Cyan))

@Composable
fun JaraTheme(content: @Composable () -> Unit) = MaterialTheme(
    colorScheme = darkColorScheme(primary = Blue, secondary = Purple, background = Navy, surface = Card,
        onBackground = Color.White, onSurface = Color.White, onPrimary = Color.White),
    content = { Surface(color = Navy, modifier = Modifier.fillMaxSize(), content = content) }
)

@Composable
fun JCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.clip16().background(Card).border(1.dp, Line, RoundedCornerShape(16.dp))
    Column(if (onClick != null) m.clickable(onClick = onClick).padding(14.dp) else m.padding(14.dp), content = content)
}
private fun Modifier.clip16() = this.then(Modifier.clipRound())
private fun Modifier.clipRound() = androidx.compose.ui.draw.clip(RoundedCornerShape(16.dp))

@Composable
fun PrimaryBtn(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clip12().background(if (enabled) Brush.horizontalGradient(listOf(Blue, Cyan)) else Brush.horizontalGradient(listOf(Line, Line)))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
private fun Modifier.clip12() = androidx.compose.ui.draw.clip(RoundedCornerShape(12.dp))

@Composable
fun OutlineBtn(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).clip12().border(BorderStroke(1.dp, Blue), RoundedCornerShape(12.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = Cyan, fontSize = 15.sp)
    }
}

@Composable
fun Screen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Navy).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
