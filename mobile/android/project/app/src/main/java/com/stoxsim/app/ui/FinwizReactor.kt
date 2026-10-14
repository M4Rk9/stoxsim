package com.stoxsim.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.stoxsim.app.R
import com.stoxsim.app.data.LearningModule
import com.stoxsim.app.data.learningModules
import kotlin.math.cos
import kotlin.math.sin

/** Real Compose buttons supply touch targets and accessibility; the Canvas only draws connections. */
@Composable internal fun FinwizReactor(selectedId: String, read: Set<String>, select: (String) -> Unit, enabled: Boolean) {
    val ink = MaterialTheme.colorScheme.onSurface
    val guide = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 240.dp || LocalDensity.current.fontScale > 1.3f
        if (compact) Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ReactorCore(Modifier.size(100.dp))
            learningModules.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { module -> ModuleNode(module, module.id == selectedId, module.id in read, enabled, Modifier.weight(1f)) { select(module.id) } }
            } }
        } else Box(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Canvas(Modifier.matchParentSize()) {
                val centre = Offset(size.width / 2, size.height / 2)
                val halfCore = 40.dp.toPx()
                repeat(4) { index ->
                    val y = (index + 0.5f) * size.height / 4
                    val left = learningModules[index].id == selectedId
                    val right = learningModules[index + 4].id == selectedId
                    drawLine(if (left) ink.copy(alpha = 0.65f) else guide, Offset(centre.x - halfCore, y), centre, if (left) 1.5.dp.toPx() else 1.dp.toPx())
                    drawLine(if (right) ink.copy(alpha = 0.65f) else guide, centre, Offset(centre.x + halfCore, y), if (right) 1.5.dp.toPx() else 1.dp.toPx())
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    learningModules.take(4).forEach { module -> ModuleNode(module, module.id == selectedId, module.id in read, enabled) { select(module.id) } }
                }
                ReactorCore(Modifier.width(80.dp).height(100.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    learningModules.drop(4).forEach { module -> ModuleNode(module, module.id == selectedId, module.id in read, enabled) { select(module.id) } }
                }
            }
        }
    }
}

@Composable internal fun ReactorCore(modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val guide = MaterialTheme.colorScheme.outlineVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = minOf(size.width, size.height) * 0.44f
            drawCircle(surface, radius)
            drawCircle(guide, radius, style = Stroke(1.dp.toPx()))
            drawCircle(ink.copy(alpha = 0.65f), radius * 0.78f, style = Stroke(1.4.dp.toPx()))
            repeat(8) { index ->
                val angle = index * Math.PI / 4
                val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
                drawLine(ink.copy(alpha = 0.5f), center + direction * radius * 0.83f, center + direction * radius * 0.96f, 2.dp.toPx())
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_nav_finwiz), null, Modifier.size(22.dp))
            Text("FINWIZ", style = MaterialTheme.typography.labelSmall)
            Text("AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun ModuleNode(module: LearningModule, active: Boolean, read: Boolean, enabled: Boolean,
    modifier: Modifier = Modifier, click: () -> Unit) {
    Surface(onClick = click, enabled = enabled, modifier = modifier.fillMaxWidth().heightIn(min = 52.dp)
        .semantics { contentDescription = "Select module ${module.title}"; selected = active },
        shape = MaterialTheme.shapes.small, color = if (active) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (active) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(when (module.id) {
                "STOCK_FUNDAMENTALS" -> "Fundamentals"
                "TECHNICAL_ANALYSIS" -> "Technicals"
                "FUNDAMENTAL_ANALYSIS" -> "Statements"
                "MARKET_EVALUATION" -> "Market view"
                else -> module.title
            }, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            if (read) Text("✓ Read", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
