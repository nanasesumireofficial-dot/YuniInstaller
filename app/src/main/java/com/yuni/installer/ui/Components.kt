package com.yuni.installer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuni.installer.Op
import com.yuni.installer.R

@Composable
fun GlowBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Yuni.Bg)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        listOf(Yuni.Red.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.9f, -100f),
                        radius = size.width * 1.2f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(Yuni.Red.copy(alpha = 0.15f), Color.Transparent),
                        center = Offset(-100f, size.height),
                        radius = size.width * 1.1f,
                    ),
                )
            },
        content = content,
    )
}

@Composable
fun YuniCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1419), Color(0xFF120E11))))
            .border(1.dp, if (glow) Yuni.Red.copy(alpha = 0.55f) else Yuni.Border, shape),
        content = content,
    )
}

@Composable
fun IconTile(size: Dp = 46.dp, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Yuni.Red.copy(alpha = 0.32f), Yuni.Red.copy(alpha = 0.12f))))
            .border(1.dp, Yuni.Red.copy(alpha = 0.40f), shape),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

enum class ChipTone { Good, Warn, Neutral }

@Composable
fun StatusChip(text: String, tone: ChipTone) {
    val c = when (tone) {
        ChipTone.Good -> Yuni.Green
        ChipTone.Warn -> Yuni.Red
        ChipTone.Neutral -> Yuni.Muted
    }
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = c,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .background(c.copy(alpha = 0.12f))
            .border(1.dp, c.copy(alpha = 0.4f), shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val bg = if (primary) {
        Brush.verticalGradient(listOf(Yuni.RedBright, Yuni.RedDeep))
    } else {
        SolidColor(Color.White.copy(alpha = 0.04f))
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(bg)
            .then(if (primary) Modifier else Modifier.border(1.dp, Yuni.Border, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) Color.White else Yuni.Text,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Yuni.Muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
    )
}

@Composable
fun StatPill(label: String, value: String, highlight: Boolean = false) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (highlight) Yuni.Red.copy(alpha = 0.55f) else Yuni.Border, shape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), color = Yuni.Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(value, color = Yuni.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Wordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painterResource(R.drawable.ic_yuni_logo), contentDescription = "Yuni", modifier = Modifier.size(38.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Yuni.Text)) { append("Yuni") }
                withStyle(SpanStyle(color = Yuni.Red)) { append("Installer") }
            },
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
fun YuniField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Yuni.Text, fontSize = 14.sp),
        cursorBrush = SolidColor(Yuni.Red),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Yuni.Border, shape)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = Yuni.Muted, fontSize = 14.sp)
                inner()
            }
        },
    )
}

@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        label,
        color = if (selected) Color.White else Yuni.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Yuni.Red.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.03f))
            .border(1.dp, if (selected) Yuni.Red else Yuni.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
fun AnimatedProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "DownloadProgressAnimation",
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50)),
        color = Yuni.RedBright,
        trackColor = Yuni.Border,
    )
}

@Composable
fun OpStatus(op: Op) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (op) {
                is Op.Downloading -> {
                    val percent = (op.progress * 100).toInt()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Downloading...", color = Yuni.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("$percent%", color = Yuni.RedBright, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    AnimatedProgressBar(progress = op.progress)
                }
                Op.Installing -> {
                    Text("Installing Package...", color = Yuni.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                        color = Yuni.RedBright,
                        trackColor = Yuni.Border,
                    )
                }
                is Op.BatchStep -> {
                    val percent = (op.progress * 100).toInt()
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Step ${op.step}/${op.total}: ${op.stepName}",
                            color = Yuni.Text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("$percent%", color = Yuni.RedBright, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    AnimatedProgressBar(progress = op.progress)
                }
                is Op.Patching -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("LSPatch: ${op.stepName}", color = Yuni.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${(op.progress * 100).toInt()}%", color = Yuni.RedBright, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    AnimatedProgressBar(progress = op.progress)
                }
            }
        }
    }
}
