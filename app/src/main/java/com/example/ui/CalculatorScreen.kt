package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.state.CalculatorViewModel
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                        BrandHeader(palette)
                        Spacer(Modifier.height(6.dp))
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BrandHeader(palette)
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
private fun BrandHeader(palette: com.example.ui.theme.CalcPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "NATURAL-V.P.A.M.",
            fontSize = 10.sp,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = palette.sheetTextMuted,
        )
        Text(
            text = "fx-991ES PRECISION",
            fontSize = 10.sp,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            color = palette.sheetTextMuted,
        )
    }
}
