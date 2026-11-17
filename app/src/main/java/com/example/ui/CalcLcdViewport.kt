package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ast.MathFunction
import com.example.ast.MathOperator
import com.example.ast.MathVariable
import com.example.ast.SlotRole
import com.example.data.CalcHistoryEntity
import com.example.engine.AngleUnit
import com.example.engine.CalcMatrix
import com.example.engine.NumberBase
import com.example.state.ActiveOverlay
import com.example.state.CalcMode
import com.example.state.CalculatorUiState
import com.example.state.CalculatorViewModel
import com.example.state.ModifierKey
import com.example.ui.theme.CalcPalette
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily

@Composable
fun CalcLcdViewport(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.bezelOuter, RoundedCornerShape(16.dp))
            .border(1.5.dp, palette.bezelInner, RoundedCornerShape(16.dp))
            .padding(8.dp),
    ) {
        // Collapsible History Tape above LCD
        AnimatedVisibility(
            visible = state.historyExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            HistoryTapePanel(
                history = state.history,
                palette = palette,
                onRestoreExpr = viewModel::restoreFromHistory,
                onInsertResult = viewModel::insertResultFromHistory,
                onClearHistory = viewModel::clearHistory,
            )
        }

        // Recessed Dot-Matrix LCD Screen
        val lcdColor = if (state.error != null) palette.lcdErrorBg else palette.lcdBg
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(lcdColor)
                .border(1.dp, palette.lcdInk.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
            // 1. Hardware Status Bar
            LcdStatusBar(
                state = state,
                palette = palette,
                onToggleAngle = {
                    val next = when (state.angleUnit) {
                        AngleUnit.DEG -> AngleUnit.RAD
                        AngleUnit.RAD -> AngleUnit.GRAD
                        AngleUnit.GRAD -> AngleUnit.DEG
                    }
                    viewModel.setAngleUnit(next)
                },
                onOpenMode = { viewModel.openOverlay(ActiveOverlay.ModeMenu) },
                onToggleHistory = viewModel::toggleHistoryExpanded,
                onOpenSetup = { viewModel.openOverlay(ActiveOverlay.SetupMenu) },
            )

            Spacer(Modifier.height(4.dp))

            // 2. Expression or Error Display Area
            if (state.error != null) {
                ErrorScreenView(
                    error = state.error,
                    palette = palette,
                    onGotoError = viewModel::onMoveLeft,
                )
            } else {
                val hScroll = rememberScrollState()
                LaunchedEffect(state.document) {
                    hScroll.animateScrollTo(hScroll.maxValue)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 54.dp, max = 94.dp)
                        .horizontalScroll(hScroll),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    NaturalExpressionView(
                        document = state.document,
                        palette = palette,
                        fontSize = 20.sp,
                        onTapSlot = viewModel::onTapSlot,
                    )
                }
            }

            // 3. Secondary / Mode Live Preview Line (Pol/Rec, CMPLX alternate, SOLVE L-R, BASE-N bases)
            val secondaryText = buildSecondaryLine(state)
            if (secondaryText != null) {
                Text(
                    text = secondaryText,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFamily,
                    color = palette.lcdInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                )
            }

            // 4. Lower-Right Result Viewport
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 34.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val res = state.result
                if (res != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(palette.badgeBg)
                            .clickable { viewModel.onSToDKey() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("result_format_badge"),
                    ) {
                        Text(
                            text = res.formatBadge,
                            fontSize = 9.sp,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = palette.badgeText,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    val resultScroll = rememberScrollState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(resultScroll, reverseScrolling = true),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        when {
                            res.matrixResult != null -> CompactMatrixResult(res.matrixResult, palette)
                            res.exactSymbolic != null -> ExactSymbolicResultView(
                                exact = res.exactSymbolic,
                                palette = palette,
                                fontSize = 23.sp,
                                modifier = Modifier.testTag("lcd_result_text"),
                            )
                            else -> Text(
                                text = res.primaryText,
                                fontSize = 24.sp,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Medium,
                                color = palette.lcdInk,
                                maxLines = 1,
                                modifier = Modifier.testTag("lcd_result_text"),
                            )
                        }
                    }
                } else {
                    Text(
                        text = if (state.mode == CalcMode.COMP) "Math" else state.mode.label,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFamily,
                        color = palette.lcdInkMuted.copy(alpha = 0.55f),
                    )
                }
            }
        }

        // Contextual Quick Action Strip for Specialized Modes (BASE-N, CMPLX, MATRIX, VECTOR, STAT, EQN, TABLE)
        ModeContextStrip(state = state, palette = palette, viewModel = viewModel)
    }
}

@Composable
private fun LcdStatusBar(
    state: CalculatorUiState,
    palette: CalcPalette,
    onToggleAngle: () -> Unit,
    onOpenMode: () -> Unit,
    onToggleHistory: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            StatusPill("S", active = state.modifier == ModifierKey.SHIFT, activeBg = palette.shiftAccent, activeFg = Color.Black, palette = palette)
            StatusPill("A", active = state.modifier == ModifierKey.ALPHA, activeBg = palette.alphaAccent, activeFg = Color.White, palette = palette)
            if (state.modifier == ModifierKey.STO) {
                StatusPill("STO", active = true, activeBg = palette.badgeBg, activeFg = palette.badgeText, palette = palette)
            }
            if (state.modifier == ModifierKey.RCL) {
                StatusPill("RCL", active = true, activeBg = palette.badgeBg, activeFg = palette.badgeText, palette = palette)
            }
            if (state.hypActive) {
                StatusPill("HYP", active = true, activeBg = palette.badgeBg, activeFg = palette.badgeText, palette = palette)
            }
            if (state.hasMemory) {
                StatusPill("M", active = true, activeBg = palette.badgeBg, activeFg = palette.badgeText, palette = palette)
            }
            StatusPill(
                label = state.angleUnit.shortLabel,
                active = true,
                activeBg = palette.badgeBg,
                activeFg = palette.badgeText,
                palette = palette,
                modifier = Modifier.clickable(onClick = onToggleAngle),
            )
            StatusPill(
                label = state.numberFormat.label,
                active = false,
                activeBg = palette.badgeBg,
                activeFg = palette.badgeText,
                palette = palette,
                modifier = Modifier.clickable(onClick = onOpenSetup),
            )
            StatusPill(
                label = state.mode.label,
                active = true,
                activeBg = palette.lcdInk.copy(alpha = 0.14f),
                activeFg = palette.lcdInk,
                palette = palette,
                modifier = Modifier.clickable(onClick = onOpenMode),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = "History Tape",
                tint = palette.lcdInk,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onToggleHistory)
                    .testTag("history_toggle_button"),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = palette.lcdInk,
                modifier = Modifier
                    .size(19.dp)
                    .clickable(onClick = onOpenSetup)
                    .testTag("setup_button"),
            )
        }
    }
}

@Composable
private fun StatusPill(
    label: String,
    active: Boolean,
    activeBg: Color,
    activeFg: Color,
    palette: CalcPalette,
    modifier: Modifier = Modifier,
) {
    val bg = if (active) activeBg else palette.lcdInk.copy(alpha = 0.08f)
    val fg = if (active) activeFg else palette.lcdInkMuted.copy(alpha = 0.5f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

@Composable
private fun ErrorScreenView(
    error: com.example.state.ErrorDisplay,
    palette: CalcPalette,
    onGotoError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onGotoError),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = error.kind.title,
            fontSize = 18.sp,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Bold,
            color = palette.lcdErrorInk,
            modifier = Modifier.testTag("lcd_error_title"),
        )
        Text(
            text = "${error.message}  •  [◄/►] Goto  [AC] Clear",
            fontSize = 11.sp,
            fontFamily = JetBrainsMonoFamily,
            color = palette.lcdErrorInk.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun CompactMatrixResult(matrix: CalcMatrix, palette: CalcPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0 until matrix.rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (c in 0 until matrix.cols) {
                    Text(
                        text = com.example.engine.BigMath.formatDisplay(matrix[r, c]),
                        fontSize = 12.sp,
                        fontFamily = JetBrainsMonoFamily,
                        color = palette.lcdInk,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryTapePanel(
    history: List<CalcHistoryEntity>,
    palette: CalcPalette,
    onRestoreExpr: (CalcHistoryEntity) -> Unit,
    onInsertResult: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.lcdBgSecondary)
            .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "HISTORY TAPE (Tap expr or result)",
                fontSize = 10.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                color = palette.lcdInkMuted,
            )
            if (history.isNotEmpty()) {
                IconButton(onClick = onClearHistory, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Clear History",
                        tint = palette.lcdInkMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        if (history.isEmpty()) {
            Text(
                text = "No calculations yet",
                fontSize = 12.sp,
                fontFamily = PlusJakartaSansFamily,
                color = palette.lcdInkMuted,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp),
            ) {
                items(history, key = { it.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.expressionPlain,
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMonoFamily,
                            color = palette.lcdInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onRestoreExpr(item) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "= ${item.resultExact ?: item.resultDecimal}",
                            fontSize = 12.sp,
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            color = palette.lcdInk,
                            modifier = Modifier.clickable { onInsertResult(item.resultDecimal) },
                        )
                    }
                    HorizontalDivider(color = palette.lcdInk.copy(alpha = 0.1f))
                }
            }
        }
    }
}

@Composable
private fun ModeContextStrip(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    when (state.mode) {
        CalcMode.BASE_N -> {
            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    NumberBase.entries.forEach { base ->
                        StripChip(
                            label = base.label,
                            selected = state.baseNBase == base,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                        ) { viewModel.setBaseNBase(base) }
                    }
                    StripChip(
                        label = state.baseNWidth.label,
                        selected = false,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                    ) { viewModel.toggleBaseNWidth() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.baseNBase == NumberBase.HEX) {
                        listOf('A', 'B', 'C', 'D', 'E', 'F').forEach { ch ->
                            StripChip(ch.toString(), false, palette, Modifier.weight(1f)) {
                                viewModel.onHexDigit(ch)
                            }
                        }
                    } else {
                        StripChip("AND", false, palette, Modifier.weight(1f)) { viewModel.insertBaseNOp(MathOperator.LOGIC_AND) }
                        StripChip("OR", false, palette, Modifier.weight(1f)) { viewModel.insertBaseNOp(MathOperator.LOGIC_OR) }
                        StripChip("XOR", false, palette, Modifier.weight(1f)) { viewModel.insertBaseNOp(MathOperator.LOGIC_XOR) }
                        StripChip("XNOR", false, palette, Modifier.weight(1f)) { viewModel.insertBaseNOp(MathOperator.LOGIC_XNOR) }
                        StripChip("NOT", false, palette, Modifier.weight(1f)) { viewModel.insertBaseNFunc(MathFunction.BIT_NOT) }
                    }
                }
            }
        }

        CalcMode.CMPLX -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StripChip("i", false, palette, Modifier.weight(1f)) { viewModel.onEngKey() }
                StripChip("∠", false, palette, Modifier.weight(1f)) { viewModel.onOperator(MathOperator.ANGLE) }
                StripChip("Conjg", false, palette, Modifier.weight(1f)) { viewModel.insertFuncAndCloseOverlay(MathFunction.CONJG) }
                StripChip("arg", false, palette, Modifier.weight(1f)) { viewModel.insertFuncAndCloseOverlay(MathFunction.ARG) }
                StripChip("r∠θ⇔a+bi", false, palette, Modifier.weight(1.3f)) { viewModel.onSToDKey() }
            }
        }

        CalcMode.MATRIX -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StripChip("Edit Mat", false, palette, Modifier.weight(1.2f)) { viewModel.openOverlay(ActiveOverlay.MatrixSheet) }
                StripChip("MatA", false, palette, Modifier.weight(1f)) { viewModel.insertMatrixOrVectorVar(MathVariable.MAT_A) }
                StripChip("MatB", false, palette, Modifier.weight(1f)) { viewModel.insertMatrixOrVectorVar(MathVariable.MAT_B) }
                StripChip("Det", false, palette, Modifier.weight(1f)) { viewModel.insertFuncAndCloseOverlay(MathFunction.DET) }
                StripChip("Trn", false, palette, Modifier.weight(1f)) { viewModel.insertFuncAndCloseOverlay(MathFunction.TRN) }
            }
        }

        CalcMode.STAT -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StripChip("Edit STAT Table & Regressions", true, palette, Modifier.weight(1f)) {
                    viewModel.openOverlay(ActiveOverlay.StatSheet)
                }
            }
        }

        CalcMode.EQN -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StripChip("Open Equation Solver (Linear / Quad / Cubic)", true, palette, Modifier.weight(1f)) {
                    viewModel.openOverlay(ActiveOverlay.EqnSheet)
                }
            }
        }

        CalcMode.VECTOR -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StripChip("Open Vector Studio (Dot / Cross / Angle)", true, palette, Modifier.weight(1f)) {
                    viewModel.openOverlay(ActiveOverlay.VectorSheet)
                }
            }
        }

        CalcMode.TABLE -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StripChip("Generate f(x) / g(x) Table", true, palette, Modifier.weight(1f)) {
                    viewModel.openOverlay(ActiveOverlay.TableSheet)
                }
            }
        }

        CalcMode.COMP -> Unit
    }
}

@Composable
private fun StripChip(
    label: String,
    selected: Boolean,
    palette: CalcPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) palette.equalsKeyTop else palette.sciKeyTop)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) palette.equalsKeyText else palette.sciKeyText,
            maxLines = 1,
        )
    }
}

private fun buildSecondaryLine(state: CalculatorUiState): String? {
    if (state.mode == CalcMode.BASE_N && state.baseNResult != null) {
        val r = state.baseNResult
        return "DEC:${r.format(NumberBase.DEC)}  HEX:${r.format(NumberBase.HEX)}  OCT:${r.format(NumberBase.OCT)}"
    }
    return state.result?.secondaryLine
}
