package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.state.CalculatorViewModel
import com.example.ui.theme.CalcPalette
import com.example.ui.theme.LocalCalcPalette
import com.example.ui.theme.NaturalCalcTheme
import com.example.ui.theme.PlusJakartaSansFamily

@Composable
fun CalculatorScreen(
    calcViewModel: CalculatorViewModel = viewModel(),
) {
    val state by calcViewModel.uiState.collectAsStateWithLifecycle()

    NaturalCalcTheme(preset = state.theme) {
        val palette = LocalCalcPalette.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.chassisTop, palette.chassisBottom)))
                .drawBehind {
                    // Authentic Casio fx-991ES Plus curved upper graphite faceplate over brushed metallic lower deck
                    val splitY = if (size.width >= size.height) size.height * 0.50f else size.height * 0.59f
                    val curveDepth = 18.dp.toPx()
                    val upperPath = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width, splitY - curveDepth)
                        quadraticTo(
                            size.width * 0.5f,
                            splitY + curveDepth,
                            0f,
                            splitY - curveDepth,
                        )
                        close()
                    }
                    drawPath(
                        path = upperPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(palette.faceplateTop, palette.faceplateBottom),
                            startY = 0f,
                            endY = splitY + curveDepth,
                        ),
                    )
                    // Subtle metallic chamfer seam separating dark faceplate and silver lower deck
                    val seamPath = Path().apply {
                        moveTo(0f, splitY - curveDepth)
                        quadraticTo(
                            size.width * 0.5f,
                            splitY + curveDepth,
                            size.width,
                            splitY - curveDepth,
                        )
                    }
                    drawPath(
                        path = seamPath,
                        color = palette.chassisBorder.copy(alpha = 0.55f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
                    )
                }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            val isWide = maxWidth >= 680.dp

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 980.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CasioHardwareHeader(palette)
                        Spacer(Modifier.height(5.dp))
                        CalcLcdViewport(state = state, palette = palette, viewModel = calcViewModel)
                    }
                    CalcKeypad(
                        state = state,
                        palette = palette,
                        viewModel = calcViewModel,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight(),
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CasioHardwareHeader(palette)
                    CalcLcdViewport(
                        state = state,
                        palette = palette,
                        viewModel = calcViewModel,
                    )
                    CalcKeypad(
                        state = state,
                        palette = palette,
                        viewModel = calcViewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }

            CalcOverlayHost(
                state = state,
                palette = palette,
                viewModel = calcViewModel,
            )
        }
    }
}

@Composable
private fun CasioHardwareHeader(palette: CalcPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(26.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "fx-991ES PLUS",
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    fontSize = 12.sp,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 0.6.sp,
                    color = palette.chassisHeaderText,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = "NATURAL-V.P.A.M.",
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    fontSize = 8.5.sp,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = palette.chassisSubHeaderText,
                    softWrap = false,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }

        // Authentic Casio 4-Cell Solar Panel + TWO WAY POWER label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "TWO WAY POWER",
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                fontSize = 6.5.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = palette.chassisHeaderText.copy(alpha = 0.72f),
                softWrap = false,
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.solarCellBg)
                    .border(1.dp, palette.bezelInner, RoundedCornerShape(3.dp))
                    .drawBehind {
                        // Draw 4 solar cell vertical busbars + subtle glossy sheen
                        val segW = size.width / 4f
                        for (i in 1..3) {
                            drawLine(
                                color = palette.solarCellLine,
                                start = Offset(segW * i, 1f),
                                end = Offset(segW * i, size.height - 1f),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                            ),
                        )
                    }
                    .testTag("solar_panel"),
            )
        }
    }
}
