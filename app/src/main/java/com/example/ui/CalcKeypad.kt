package com.example.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ast.MathFunction
import com.example.ast.MathOperator
import com.example.state.ActiveOverlay
import com.example.state.CalculatorUiState
import com.example.state.CalculatorViewModel
import com.example.state.ModifierKey
import com.example.ui.theme.CalcPalette
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlusJakartaSansFamily

/**
 * Responsive, ultra-low-latency 3-tier scientific calculator keypad inspired by the Casio fx-991ES Plus:
 * 1. Hardware Control Bar (`SHIFT`, `ALPHA`, 4-way `REPLAY` D-Pad, `MODE`, `AC/ON`)
 * 2. Upper Scientific Matrix (6 columns × 4 rows with SHIFT/ALPHA sublabels)
 * 3. Lower Tactile Numpad (5 columns × 4 rows)
 */
@Composable
fun CalcKeypad(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        // Tier 1: Control Cluster (SHIFT, ALPHA, REPLAY D-Pad, MODE, SETUP)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Column(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    PillControlKey(
                        label = "SHIFT",
                        textColor = palette.shiftAccent,
                        active = state.modifier == ModifierKey.SHIFT,
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_shift",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = viewModel::onShiftPressed,
                    )
                    PillControlKey(
                        label = "ALPHA",
                        textColor = palette.alphaAccent,
                        active = state.modifier == ModifierKey.ALPHA,
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_alpha",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = viewModel::onAlphaPressed,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SciKey(
                        primary = "CALC",
                        shift = "SOLVE",
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_calc",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = viewModel::onCalcOrSolveKey,
                    )
                    SciKey(
                        primary = "∫dx",
                        shift = "d/dx",
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_integral",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = viewModel::onIntegralKey,
                    )
                }
            }

            // Center 4-way REPLAY D-Pad
            ReplayDPad(
                palette = palette,
                haptics = state.hapticsEnabled,
                onUp = viewModel::onMoveUp,
                onDown = viewModel::onMoveDown,
                onLeft = viewModel::onMoveLeft,
                onRight = viewModel::onMoveRight,
                onCenter = viewModel::onEquals,
                modifier = Modifier.size(width = 88.dp, height = 68.dp),
            )

            Column(
                modifier = Modifier.weight(1.1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    PillControlKey(
                        label = "MODE",
                        textColor = palette.sciKeyText,
                        active = state.overlay == ActiveOverlay.ModeMenu,
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_mode",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = viewModel::onModePressed,
                        shiftLabel = "SETUP",
                    )
                    PillControlKey(
                        label = "CONST",
                        textColor = palette.shiftAccent,
                        active = state.overlay == ActiveOverlay.ConstPicker || state.overlay == ActiveOverlay.ConvPicker,
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_const",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shiftLabel = "CONV",
                        onClick = viewModel::onConstOrConvKey,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    SciKey(
                        primary = "x⁻¹",
                        shift = "x!",
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_inv",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = viewModel::onReciprocalKey,
                    )
                    SciKey(
                        primary = "logₐb",
                        shift = "Σ",
                        palette = palette,
                        haptics = state.hapticsEnabled,
                        tag = "key_log_base",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = viewModel::onLogBaseKey,
                    )
                }
            }
        }

        // Tier 2: Scientific Functions (6 columns × 3 rows)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.42f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Sci Row 1
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SciKey("a/b", shift = "a b/c", palette = palette, haptics = state.hapticsEnabled, tag = "key_frac", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onFractionKey)
                SciKey("√□", shift = "³√", palette = palette, haptics = state.hapticsEnabled, tag = "key_sqrt", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onSqrtKey)
                SciKey("x²", shift = "x³", palette = palette, haptics = state.hapticsEnabled, tag = "key_sq", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onSquareKey)
                SciKey("x^□", shift = "ⁿ√", palette = palette, haptics = state.hapticsEnabled, tag = "key_pow", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onPowerKey)
                SciKey("log", shift = "10^x", palette = palette, haptics = state.hapticsEnabled, tag = "key_log10", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onLog10Key)
                SciKey("ln", shift = "e^x", palette = palette, haptics = state.hapticsEnabled, tag = "key_ln", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onLnKey)
            }
            // Sci Row 2
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SciKey("(-)", alpha = "A", palette = palette, haptics = state.hapticsEnabled, tag = "key_neg", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onUnaryMinusKey)
                SciKey("°′″", alpha = "B", palette = palette, haptics = state.hapticsEnabled, tag = "key_dms", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onDmsKey)
                SciKey("hyp", alpha = "C", palette = palette, haptics = state.hapticsEnabled, tag = "key_hyp", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onHypPressed)
                SciKey("sin", shift = "sin⁻¹", alpha = "D", palette = palette, haptics = state.hapticsEnabled, tag = "key_sin", modifier = Modifier.weight(1f).fillMaxHeight()) { viewModel.onTrigKey(MathFunction.SIN) }
                SciKey("cos", shift = "cos⁻¹", alpha = "E", palette = palette, haptics = state.hapticsEnabled, tag = "key_cos", modifier = Modifier.weight(1f).fillMaxHeight()) { viewModel.onTrigKey(MathFunction.COS) }
                SciKey("tan", shift = "tan⁻¹", alpha = "F", palette = palette, haptics = state.hapticsEnabled, tag = "key_tan", modifier = Modifier.weight(1f).fillMaxHeight()) { viewModel.onTrigKey(MathFunction.TAN) }
            }
            // Sci Row 3
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                SciKey("RCL", shift = "STO", palette = palette, haptics = state.hapticsEnabled, tag = "key_rcl", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onRclKey)
                SciKey("ENG", shift = "←", alpha = "|x|", palette = palette, haptics = state.hapticsEnabled, tag = "key_eng", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onEngKey)
                SciKey("(", shift = "%", palette = palette, haptics = state.hapticsEnabled, tag = "key_lparen", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onLeftParenKey)
                SciKey(")", shift = ",", alpha = "X", palette = palette, haptics = state.hapticsEnabled, tag = "key_rparen", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onRightParenKey)
                SciKey("S⇔D", shift = "Abs", alpha = "Y", palette = palette, haptics = state.hapticsEnabled, tag = "key_s_d", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onSToDKey)
                SciKey("M+", shift = "M-", alpha = "M", palette = palette, haptics = state.hapticsEnabled, tag = "key_mplus", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onMPlusKey)
            }
        }

        // Tier 3: Lower Numpad Section (5 columns × 4 rows)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.58f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumKey("7", palette, state.hapticsEnabled, "key_7", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('7') }
                NumKey("8", palette, state.hapticsEnabled, "key_8", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('8') }
                NumKey("9", palette, state.hapticsEnabled, "key_9", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('9') }
                ActionKey("DEL", palette, state.hapticsEnabled, "key_del", Modifier.weight(1f).fillMaxHeight(), viewModel::onDelete)
                ActionKey("AC", palette, state.hapticsEnabled, "key_ac", Modifier.weight(1f).fillMaxHeight(), viewModel::onAllClear)
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumKey("4", palette, state.hapticsEnabled, "key_4", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('4') }
                NumKey("5", palette, state.hapticsEnabled, "key_5", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('5') }
                NumKey("6", palette, state.hapticsEnabled, "key_6", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('6') }
                OpKey("×", shift = "nPr", palette = palette, haptics = state.hapticsEnabled, tag = "key_mul", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onMulOrPermKey)
                OpKey("÷", shift = "nCr", palette = palette, haptics = state.hapticsEnabled, tag = "key_div", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onDivOrCombKey)
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumKey("1", palette, state.hapticsEnabled, "key_1", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('1') }
                NumKey("2", palette, state.hapticsEnabled, "key_2", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('2') }
                NumKey("3", palette, state.hapticsEnabled, "key_3", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('3') }
                OpKey("+", palette = palette, haptics = state.hapticsEnabled, tag = "key_add", modifier = Modifier.weight(1f).fillMaxHeight()) { viewModel.onOperator(MathOperator.ADD) }
                OpKey("−", palette = palette, haptics = state.hapticsEnabled, tag = "key_sub", modifier = Modifier.weight(1f).fillMaxHeight()) { viewModel.onOperator(MathOperator.SUBTRACT) }
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumKey("0", palette, state.hapticsEnabled, "key_0", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('0') }
                NumKey(".", palette, state.hapticsEnabled, "key_dot", Modifier.weight(1f).fillMaxHeight()) { viewModel.onDigit('.') }
                OpKey("×10ˣ", shift = "π", alpha = "e", fontSize = 14.sp, palette = palette, haptics = state.hapticsEnabled, tag = "key_exp", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onTenExpKey)
                OpKey("Ans", shift = "PreAns", fontSize = 14.sp, palette = palette, haptics = state.hapticsEnabled, tag = "key_ans", modifier = Modifier.weight(1f).fillMaxHeight(), onClick = viewModel::onAnsKey)
                EqualsKey("=", palette, state.hapticsEnabled, "key_equals", Modifier.weight(1f).fillMaxHeight(), viewModel::onEquals)
            }
        }
    }
}

@Composable
private fun ReplayDPad(
    palette: CalcPalette,
    haptics: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onCenter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    fun trigger(action: () -> Unit) {
        if (haptics) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        action()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(palette.dpadBg, palette.dpadBg.copy(alpha = 0.85f))
                )
            )
            .border(1.5.dp, palette.dpadBorder, RoundedCornerShape(24.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // UP
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 44.dp, height = 24.dp)
                .clickable { trigger(onUp) }
                .testTag("dpad_up"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up", tint = palette.dpadIcon, modifier = Modifier.size(18.dp))
        }
        // DOWN
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = 44.dp, height = 24.dp)
                .clickable { trigger(onDown) }
                .testTag("dpad_down"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Down", tint = palette.dpadIcon, modifier = Modifier.size(18.dp))
        }
        // LEFT
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(width = 28.dp, height = 40.dp)
                .clickable { trigger(onLeft) }
                .testTag("dpad_left"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move Left", tint = palette.dpadIcon, modifier = Modifier.size(18.dp))
        }
        // RIGHT
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .size(width = 28.dp, height = 40.dp)
                .clickable { trigger(onRight) }
                .testTag("dpad_right"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move Right", tint = palette.dpadIcon, modifier = Modifier.size(18.dp))
        }
        // Center OK dot
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(palette.dpadBorder.copy(alpha = 0.35f))
                .clickable { trigger(onCenter) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "OK",
                fontSize = 7.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                color = palette.dpadIcon,
            )
        }
    }
}

@Composable
private fun PillControlKey(
    label: String,
    textColor: Color,
    active: Boolean,
    palette: CalcPalette,
    haptics: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    shiftLabel: String? = null,
    onClick: () -> Unit,
) {
    TactileButtonSurface(
        topColor = palette.sciKeyTop,
        bottomColor = palette.sciKeyBottom,
        borderColor = if (active) textColor else palette.sciKeyBorder,
        borderWidth = if (active) 1.5f else 1f,
        cornerRadius = 8f,
        haptics = haptics,
        tag = tag,
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 2.dp),
        ) {
            if (shiftLabel != null) {
                Text(
                    text = shiftLabel,
                    fontSize = 7.sp,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    color = palette.shiftAccent,
                    maxLines = 1,
                )
            }
            Text(
                text = label,
                fontSize = 10.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun SciKey(
    primary: String,
    shift: String? = null,
    alpha: String? = null,
    palette: CalcPalette,
    haptics: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Sublabels row above keycap
        if (shift != null || alpha != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = shift.orEmpty(),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    color = palette.shiftAccent,
                    maxLines = 1,
                )
                Text(
                    text = alpha.orEmpty(),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    color = palette.alphaAccent,
                    maxLines = 1,
                )
            }
        } else {
            Spacer(Modifier.height(9.dp))
        }

        TactileButtonSurface(
            topColor = palette.sciKeyTop,
            bottomColor = palette.sciKeyBottom,
            borderColor = palette.sciKeyBorder,
            cornerRadius = 7f,
            haptics = haptics,
            tag = tag,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = onClick,
        ) {
            Text(
                text = primary,
                fontSize = if (primary.length >= 5) 9.5.sp else 11.5.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                color = palette.sciKeyText,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun NumKey(
    label: String,
    palette: CalcPalette,
    haptics: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(9.dp))
        TactileButtonSurface(
            topColor = palette.numKeyTop,
            bottomColor = palette.numKeyBottom,
            borderColor = palette.numKeyBorder,
            cornerRadius = 9f,
            haptics = haptics,
            tag = tag,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = onClick,
        ) {
            Text(
                text = label,
                fontSize = 20.sp,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                color = palette.numKeyText,
            )
        }
    }
}

@Composable
private fun OpKey(
    primary: String,
    shift: String? = null,
    alpha: String? = null,
    fontSize: TextUnit = 20.sp,
    palette: CalcPalette,
    haptics: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        if (shift != null || alpha != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = shift.orEmpty(),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    color = palette.shiftAccent,
                    maxLines = 1,
                )
                Text(
                    text = alpha.orEmpty(),
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Bold,
                    color = palette.alphaAccent,
                    maxLines = 1,
                )
            }
        } else {
            Spacer(Modifier.height(9.dp))
        }
        TactileButtonSurface(
            topColor = palette.opKeyTop,
            bottomColor = palette.opKeyBottom,
            borderColor = palette.numKeyBorder,
            cornerRadius = 9f,
            haptics = haptics,
            tag = tag,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = onClick,
        ) {
            Text(
                text = primary,
                fontSize = fontSize,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.SemiBold,
                color = palette.opKeyText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActionKey(
    label: String,
    palette: CalcPalette,
    haptics: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(9.dp))
        TactileButtonSurface(
            topColor = palette.actionKeyTop,
            bottomColor = palette.actionKeyBottom,
            borderColor = palette.actionKeyBottom,
            cornerRadius = 9f,
            haptics = haptics,
            tag = tag,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = onClick,
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.Bold,
                color = palette.actionKeyText,
            )
        }
    }
}

@Composable
private fun EqualsKey(
    label: String,
    palette: CalcPalette,
    haptics: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier = modifier) {
        Spacer(Modifier.height(9.dp))
        TactileButtonSurface(
            topColor = palette.equalsKeyTop,
            bottomColor = palette.equalsKeyBottom,
            borderColor = palette.equalsKeyBottom,
            cornerRadius = 9f,
            haptics = haptics,
            tag = tag,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = onClick,
        ) {
            Text(
                text = label,
                fontSize = 22.sp,
                fontFamily = JetBrainsMonoFamily,
                fontWeight = FontWeight.Bold,
                color = palette.equalsKeyText,
            )
        }
    }
}

@Composable
private fun TactileButtonSurface(
    topColor: Color,
    bottomColor: Color,
    borderColor: Color,
    borderWidth: Float = 1f,
    cornerRadius: Float,
    haptics: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .graphicsLayer {
                val scale = if (pressed) 0.94f else 1f
                scaleX = scale
                scaleY = scale
                translationY = if (pressed) 1.5f else 0f
            }
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.verticalGradient(
                    if (pressed) listOf(bottomColor, topColor) else listOf(topColor, bottomColor)
                )
            )
            .border(borderWidth.dp, borderColor, RoundedCornerShape(cornerRadius.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = true),
            ) {
                if (haptics) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                onClick()
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
