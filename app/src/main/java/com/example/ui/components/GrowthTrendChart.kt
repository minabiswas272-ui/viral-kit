package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ViralGrowthKit
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun GrowthTrendChart(
    kits: List<ViralGrowthKit>,
    onSelectKit: ((ViralGrowthKit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val chronologicalKits = remember(kits) {
        kits.sortedBy { (it.createdAt ?: it.updatedAt ?: Timestamp(0, 0)).toDate().time }
    }

    var selectedIndex by remember(chronologicalKits.size) {
        mutableIntStateOf((chronologicalKits.lastIndex).coerceAtLeast(0))
    }

    val drawProgress = remember { Animatable(0f) }
    LaunchedEffect(chronologicalKits.size) {
        drawProgress.snapTo(0f)
        drawProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val mintColor = MaterialTheme.colorScheme.secondary
    val goldColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer

    val latestScore = chronologicalKits.lastOrNull()?.viralScore?.toInt() ?: 0
    val firstScore = chronologicalKits.firstOrNull()?.viralScore?.toInt() ?: 0
    val delta = if (chronologicalKits.size >= 2) latestScore - firstScore else 0

    val trendIcon = when {
        delta > 0 -> Icons.AutoMirrored.Rounded.TrendingUp
        delta < 0 -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> Icons.AutoMirrored.Rounded.TrendingFlat
    }
    val deltaColor = when {
        delta > 0 -> mintColor
        delta < 0 -> primaryColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(22.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("growth_trend_chart_card")
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Delta Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.ShowChart,
                    contentDescription = null,
                    tint = mintColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Viral Growth Trend",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (chronologicalKits.size >= 2) {
                            "Retention score trajectory across ${chronologicalKits.size} saved kits"
                        } else {
                            "Save 2+ kits to track score momentum over time"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (chronologicalKits.size >= 2) {
                    Surface(
                        color = deltaColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                trendIcon,
                                contentDescription = null,
                                tint = deltaColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (delta >= 0) "+$delta pts" else "$delta pts",
                                style = MaterialTheme.typography.labelSmall,
                                color = deltaColor
                            )
                        }
                    }
                }
            }

            if (chronologicalKits.isEmpty()) {
                Text(
                    text = "No growth data yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Interactive Area + Line Chart
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Y-Axis Labels
                    Column(
                        modifier = Modifier
                            .padding(end = 8.dp, top = 6.dp, bottom = 10.dp)
                            .height(160.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("80", style = MaterialTheme.typography.labelSmall, color = mintColor)
                        Text("50", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Box(modifier = Modifier.weight(1f).height(175.dp)) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(chronologicalKits) {
                                    detectTapGestures { tapOffset ->
                                        if (chronologicalKits.isEmpty()) return@detectTapGestures
                                        val padH = 16.dp.toPx()
                                        val chartW = (size.width - padH * 2f).coerceAtLeast(1f)
                                        val count = chronologicalKits.size
                                        val tappedIdx = if (count == 1) {
                                            0
                                        } else {
                                            chronologicalKits.indices.minByOrNull { idx ->
                                                val x = padH + (idx.toFloat() / (count - 1)) * chartW
                                                abs(tapOffset.x - x)
                                            } ?: 0
                                        }
                                        selectedIndex = tappedIdx
                                    }
                                }
                        ) {
                            val padH = 16.dp.toPx()
                            val padTop = 12.dp.toPx()
                            val padBottom = 16.dp.toPx()
                            val chartW = (size.width - padH * 2f).coerceAtLeast(1f)
                            val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)

                            // Reference dashed gridlines (100, 80, 50, 0)
                            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            listOf(100f to gridColor, 80f to mintColor.copy(alpha = 0.45f), 50f to gridColor, 0f to gridColor)
                                .forEach { (scoreVal, lineCol) ->
                                    val y = padTop + (1f - scoreVal / 100f) * chartH
                                    drawLine(
                                        color = lineCol,
                                        start = Offset(padH, y),
                                        end = Offset(size.width - padH, y),
                                        strokeWidth = 1.dp.toPx(),
                                        pathEffect = if (scoreVal == 80f || scoreVal == 50f) dashEffect else null
                                    )
                                }

                            val points = chronologicalKits.mapIndexed { idx, kit ->
                                val x = if (chronologicalKits.size == 1) {
                                    padH + chartW / 2f
                                } else {
                                    padH + (idx.toFloat() / (chronologicalKits.size - 1)) * chartW
                                }
                                val clamped = kit.viralScore.coerceIn(0L, 100L).toFloat()
                                val y = padTop + (1f - clamped / 100f) * chartH
                                Offset(x, y)
                            }

                            if (points.size >= 2) {
                                val linePath = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    for (i in 0 until points.lastIndex) {
                                        val p0 = points[i]
                                        val p1 = points[i + 1]
                                        val midX = (p0.x + p1.x) / 2f
                                        cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                                    }
                                }

                                val areaPath = Path().apply {
                                    addPath(linePath)
                                    lineTo(points.last().x, padTop + chartH)
                                    lineTo(points.first().x, padTop + chartH)
                                    close()
                                }

                                clipRect(right = size.width * drawProgress.value) {
                                    drawPath(
                                        path = areaPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                mintColor.copy(alpha = 0.34f),
                                                mintColor.copy(alpha = 0.02f)
                                            ),
                                            startY = padTop,
                                            endY = padTop + chartH
                                        )
                                    )
                                    drawPath(
                                        path = linePath,
                                        color = mintColor,
                                        style = Stroke(
                                            width = 3.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            // Selected vertical guide + data markers
                            val safeSelected = selectedIndex.coerceIn(0, points.lastIndex)
                            val selPt = points[safeSelected]
                            drawLine(
                                color = goldColor.copy(alpha = 0.55f),
                                start = Offset(selPt.x, padTop),
                                end = Offset(selPt.x, padTop + chartH),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = dashEffect
                            )

                            points.forEachIndexed { idx, pt ->
                                val isSel = idx == safeSelected
                                val score = chronologicalKits[idx].viralScore
                                val nodeColor = when {
                                    score >= 80 -> mintColor
                                    score >= 60 -> goldColor
                                    else -> primaryColor
                                }
                                if (isSel) {
                                    drawCircle(
                                        color = nodeColor.copy(alpha = 0.28f),
                                        radius = 11.dp.toPx(),
                                        center = pt
                                    )
                                }
                                drawCircle(
                                    color = surfaceColor,
                                    radius = if (isSel) 6.dp.toPx() else 4.5.dp.toPx(),
                                    center = pt
                                )
                                drawCircle(
                                    color = nodeColor,
                                    radius = if (isSel) 4.5.dp.toPx() else 3.dp.toPx(),
                                    center = pt
                                )
                            }
                        }
                    }
                }

                // X-Axis Range Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 34.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatShortDate(chronologicalKits.first()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (chronologicalKits.size >= 3) {
                        Text(
                            text = formatShortDate(chronologicalKits[chronologicalKits.size / 2]),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (chronologicalKits.size >= 2) {
                        Text(
                            text = formatShortDate(chronologicalKits.last()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Interactive Selected Point Tooltip Card
                val inspectedKit = chronologicalKits[selectedIndex.coerceIn(0, chronologicalKits.lastIndex)]
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "${inspectedKit.title} · ${inspectedKit.viralScore}/100",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${inspectedKit.platform} · ${inspectedKit.language} · ${formatShortDate(inspectedKit)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (onSelectKit != null) {
                            TextButton(onClick = { onSelectKit(inspectedKit) }) {
                                Text("Inspect")
                            }
                        }
                    }
                }

                // Platform Performance Breakdown
                val platformAverages = remember(chronologicalKits) {
                    chronologicalKits.groupBy { it.platform }
                        .mapValues { (_, list) -> list.map { it.viralScore }.average().toInt() }
                        .entries
                        .sortedByDescending { it.value }
                }
                if (platformAverages.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "PLATFORM AVERAGE SCORE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        platformAverages.forEach { (platform, avgScore) ->
                            val barColor = when {
                                avgScore >= 80 -> mintColor
                                avgScore >= 60 -> goldColor
                                else -> primaryColor
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = platform,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$avgScore / 100",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (avgScore.coerceIn(0, 100) / 100f) },
                                    color = barColor,
                                    trackColor = barColor.copy(alpha = 0.16f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatShortDate(kit: ViralGrowthKit): String {
    val ts = kit.createdAt ?: kit.updatedAt ?: return "Just now"
    return SimpleDateFormat("MMM d", Locale.getDefault()).format(ts.toDate())
}
