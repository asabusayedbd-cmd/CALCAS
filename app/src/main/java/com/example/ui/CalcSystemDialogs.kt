package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.engine.AngleUnit
import com.example.engine.BigMath
import com.example.engine.NumberFormatMode
import com.example.engine.ScientificConstants
import com.example.state.ActiveOverlay
import com.example.state.CalcMode
import com.example.state.CalcThemePreset
import com.example.state.CalculatorUiState
import com.example.state.CalculatorViewModel
import com.example.ui.theme.CalcPalette
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily

@Composable
fun CalcOverlayHost(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    when (val overlay = state.overlay) {
        ActiveOverlay.None -> Unit
        ActiveOverlay.ModeMenu -> ModeMenuDialog(state.mode, palette, viewModel::selectMode, viewModel::dismissOverlay)
        ActiveOverlay.SetupMenu -> SetupMenuDialog(state, palette, viewModel)
        ActiveOverlay.ConstPicker -> ConstConvDialog(initialTab = 0, palette = palette, viewModel = viewModel)
        ActiveOverlay.ConvPicker -> ConstConvDialog(initialTab = 1, palette = palette, viewModel = viewModel)
        is ActiveOverlay.CalcPrompt -> CalcPromptDialog(overlay, palette, viewModel)
        is ActiveOverlay.SolvePrompt -> SolvePromptDialog(overlay, state.numberFormat, palette, viewModel)
        ActiveOverlay.StatSheet -> StatSheetDialog(state, palette, viewModel)
        ActiveOverlay.EqnSheet -> EqnSheetDialog(state.numberFormat, palette, viewModel)
        ActiveOverlay.MatrixSheet -> MatrixSheetDialog(state, palette, viewModel)
        ActiveOverlay.VectorSheet -> VectorSheetDialog(state, palette, viewModel)
        ActiveOverlay.TableSheet -> TableSheetDialog(state, palette, viewModel)
    }
}

@Composable
private fun ModeMenuDialog(
    currentMode: CalcMode,
    palette: CalcPalette,
    onSelect: (CalcMode) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "SELECT MODE",
                fontSize = 15.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                color = palette.sheetText,
            )
            CalcMode.entries.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { mode ->
                        val selected = mode == currentMode
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) palette.equalsKeyTop else palette.sheetSurface)
                                .border(1.dp, palette.sheetTextMuted.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                .clickable { onSelect(mode) }
                                .padding(10.dp)
                                .testTag("mode_item_${mode.id}"),
                        ) {
                            Text(
                                text = "${mode.id}: ${mode.label}",
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else palette.sheetText,
                            )
                            Text(
                                text = mode.subtitle,
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSansFamily,
                                color = if (selected) Color.White.copy(alpha = 0.85f) else palette.sheetTextMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun SetupMenuDialog(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    var fixDigits by remember {
        mutableIntStateOf((state.numberFormat as? NumberFormatMode.Fix)?.digits ?: 2)
    }
    var sciDigits by remember {
        mutableIntStateOf((state.numberFormat as? NumberFormatMode.Sci)?.digits ?: 3)
    }

    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "CALCULATOR SETUP",
                fontSize = 15.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                color = palette.sheetText,
            )

            // Angle Unit
            Text("Angle Unit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.sheetTextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                AngleUnit.entries.forEach { unit ->
                    ChoiceChip(
                        label = unit.name,
                        selected = state.angleUnit == unit,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                    ) { viewModel.setAngleUnit(unit) }
                }
            }

            // Number Format
            Text("Display Format", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.sheetTextMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip("Norm1", state.numberFormat == NumberFormatMode.Norm1, palette, Modifier.weight(1f)) {
                    viewModel.setNumberFormat(NumberFormatMode.Norm1)
                }
                ChoiceChip("Norm2", state.numberFormat == NumberFormatMode.Norm2, palette, Modifier.weight(1f)) {
                    viewModel.setNumberFormat(NumberFormatMode.Norm2)
                }
                ChoiceChip("Fix $fixDigits", state.numberFormat is NumberFormatMode.Fix, palette, Modifier.weight(1f)) {
                    fixDigits = (fixDigits + 1) % 10
                    viewModel.setNumberFormat(NumberFormatMode.Fix(fixDigits))
                }
                ChoiceChip("Sci $sciDigits", state.numberFormat is NumberFormatMode.Sci, palette, Modifier.weight(1f)) {
                    sciDigits = ((sciDigits) % 9) + 1
                    viewModel.setNumberFormat(NumberFormatMode.Sci(sciDigits))
                }
            }

            // Hardware Theme
            Text("Hardware Theme", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.sheetTextMuted)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CalcThemePreset.entries.forEach { preset ->
                    ChoiceChip(
                        label = preset.label,
                        selected = state.theme == preset,
                        palette = palette,
                        modifier = Modifier.fillMaxWidth(),
                    ) { viewModel.setTheme(preset) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Keypress Haptics", fontSize = 13.sp, color = palette.sheetText)
                Switch(checked = state.hapticsEnabled, onCheckedChange = viewModel::setHapticsEnabled)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { viewModel.clearAllMemories() }) {
                    Text("Clear Memory (A–F, M)", color = palette.actionKeyTop)
                }
                TextButton(onClick = viewModel::dismissOverlay) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
private fun ConstConvDialog(
    initialTab: Int,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    var query by remember { mutableStateOf("") }

    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip("40 CONST", tab == 0, palette, Modifier.weight(1f)) { tab = 0 }
                ChoiceChip("40 CONV", tab == 1, palette, Modifier.weight(1f)) { tab = 1 }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search code, symbol, or unit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
            ) {
                if (tab == 0) {
                    val filtered = ScientificConstants.constants.filter {
                        query.isBlank() ||
                            it.name.contains(query, ignoreCase = true) ||
                            it.symbol.contains(query, ignoreCase = true) ||
                            it.code.toString().padStart(2, '0').contains(query)
                    }
                    items(filtered, key = { it.code }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.insertScientificConstant(item) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${item.code.toString().padStart(2, '0')}: ${item.symbol} — ${item.name}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.sheetText,
                                )
                                Text(
                                    text = "${item.value.toEngineeringString()} ${item.unit}",
                                    fontSize = 10.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = palette.sheetTextMuted,
                                )
                            }
                        }
                        HorizontalDivider(color = palette.sheetTextMuted.copy(alpha = 0.15f))
                    }
                } else {
                    val filtered = ScientificConstants.conversions.filter {
                        query.isBlank() ||
                            it.label.contains(query, ignoreCase = true) ||
                            it.description.contains(query, ignoreCase = true) ||
                            it.code.toString().padStart(2, '0').contains(query)
                    }
                    items(filtered, key = { it.code }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.applyUnitConversion(item) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${item.code.toString().padStart(2, '0')}: ${item.label}",
                                fontSize = 13.sp,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = palette.sheetText,
                            )
                            Text(
                                text = item.description,
                                fontSize = 11.sp,
                                color = palette.sheetTextMuted,
                            )
                        }
                        HorizontalDivider(color = palette.sheetTextMuted.copy(alpha = 0.15f))
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::dismissOverlay) { Text("Close") }
            }
        }
    }
}

@Composable
private fun CalcPromptDialog(
    prompt: ActiveOverlay.CalcPrompt,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    val activeVar = prompt.variables[prompt.currentIndex]
    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "CALC — Variable Input (${prompt.currentIndex + 1}/${prompt.variables.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = palette.sheetText,
            )
            OutlinedTextField(
                value = prompt.draft,
                onValueChange = viewModel::updateCalcDraft,
                label = { Text("${activeVar.symbol} ?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::dismissOverlay) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = viewModel::confirmCalcStep) {
                    Text(if (prompt.currentIndex + 1 < prompt.variables.size) "Next" else "Evaluate")
                }
            }
        }
    }
}

@Composable
private fun SolvePromptDialog(
    prompt: ActiveOverlay.SolvePrompt,
    format: NumberFormatMode,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "SOLVE for X  (f(X) = 0)",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = palette.sheetText,
            )
            OutlinedTextField(
                value = prompt.initialGuessDraft,
                onValueChange = viewModel::updateSolveGuess,
                label = { Text("Initial guess X?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (prompt.result != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.lcdBg)
                        .padding(10.dp),
                ) {
                    Text(
                        text = "X = ${BigMath.formatDisplay(prompt.result.root, format)}",
                        fontSize = 17.sp,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        color = palette.lcdInk,
                    )
                    Text(
                        text = "L - R = ${BigMath.formatDisplay(prompt.result.residualLR, format)}",
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMonoFamily,
                        color = palette.lcdInkMuted,
                    )
                }
            }
            if (prompt.errorMessage != null) {
                Text(prompt.errorMessage, color = palette.actionKeyTop, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::dismissOverlay) { Text("Close") }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = viewModel::runSolve,
                    colors = ButtonDefaults.buttonColors(containerColor = palette.equalsKeyTop),
                ) {
                    Text("Solve")
                }
            }
        }
    }
}

@Composable
internal fun ChoiceChip(
    label: String,
    selected: Boolean,
    palette: CalcPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) palette.equalsKeyTop else palette.sheetSurface)
            .border(1.dp, palette.sheetTextMuted.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else palette.sheetText,
            maxLines = 1,
        )
    }
}
