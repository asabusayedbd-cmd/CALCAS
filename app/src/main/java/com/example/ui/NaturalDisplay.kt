package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ast.AbsNode
import com.example.ast.AstDocument
import com.example.ast.AstNode
import com.example.ast.ConstantNode
import com.example.ast.CursorPosition
import com.example.ast.DerivativeNode
import com.example.ast.ExprSlot
import com.example.ast.FractionNode
import com.example.ast.FunctionNode
import com.example.ast.IntegralNode
import com.example.ast.LogNode
import com.example.ast.MatrixNode
import com.example.ast.NumberNode
import com.example.ast.OperatorNode
import com.example.ast.ParenNode
import com.example.ast.PowerNode
import com.example.ast.ROOT_NODE_ID
import com.example.ast.SlotRole
import com.example.ast.SqrtNode
import com.example.ast.SumProdNode
import com.example.ast.VariableNode
import com.example.engine.ExactRadicalSum
import com.example.engine.ExactSymbolic
import com.example.ui.theme.CalcPalette
import com.example.ui.theme.JetBrainsMonoFamily
import java.math.BigInteger

/**
 * Native 60/120fps Jetpack Compose 2D Natural Textbook Display renderer.
 * Renders nested fractions, powers, radicals, integrals, derivatives, summations, logs,
 * absolute value bars, and matrices directly from [AstDocument] with a smooth blinking cursor.
 */
@Composable
fun NaturalExpressionView(
    document: AstDocument,
    palette: CalcPalette,
    fontSize: TextUnit = 21.sp,
    onTapSlot: (ownerId: Long, role: SlotRole, index: Int, charOffset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onTapSlot(ROOT_NODE_ID, SlotRole.Root, document.root.size, 0)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        SlotView(
            slot = document.root,
            ownerId = ROOT_NODE_ID,
            role = SlotRole.Root,
            cursor = document.cursor,
            cursorAlpha = cursorAlpha,
            palette = palette,
            fontSize = fontSize,
            onTapSlot = onTapSlot,
        )
    }
}

@Composable
private fun SlotView(
    slot: ExprSlot,
    ownerId: Long,
    role: SlotRole,
    cursor: CursorPosition,
    cursorAlpha: Float,
    palette: CalcPalette,
    fontSize: TextUnit,
    onTapSlot: (Long, SlotRole, Int, Int) -> Unit,
) {
    val activeHere = cursor.ownerId == ownerId && cursor.role == role
    val isRoot = ownerId == ROOT_NODE_ID

    if (slot.isEmpty) {
        val boxWidth = (fontSize.value * 0.72f).dp.coerceAtLeast(11.dp)
        val boxHeight = (fontSize.value * 1.1f).dp.coerceAtLeast(16.dp)
        Box(
            modifier = Modifier
                .padding(horizontal = 1.dp)
                .size(width = boxWidth, height = boxHeight)
                .background(
                    color = if (isRoot) Color.Transparent else palette.lcdSlotEmptyBg,
                    shape = RoundedCornerShape(3.dp),
                )
                .then(
                    if (!isRoot) {
                        Modifier.border(
                            width = if (activeHere) 1.5.dp else 1.dp,
                            color = if (activeHere) palette.lcdSlotActiveBorder else palette.lcdInkMuted.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(3.dp),
                        )
                    } else Modifier
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    onTapSlot(ownerId, role, 0, 0)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (activeHere) {
                CursorCaret(height = boxHeight * 0.85f, color = palette.lcdCursor, alpha = cursorAlpha)
            }
        }
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                onTapSlot(ownerId, role, slot.size, 0)
            },
    ) {
        slot.children.forEachIndexed { idx, node ->
            if (activeHere && cursor.index == idx && cursor.charOffset == 0) {
                CursorCaret(
                    height = (fontSize.value * 1.2f).dp,
                    color = palette.lcdCursor,
                    alpha = cursorAlpha,
                )
            }
            NodeView(
                node = node,
                ownerId = ownerId,
                role = role,
                indexInSlot = idx,
                cursor = cursor,
                cursorAlpha = cursorAlpha,
                palette = palette,
                fontSize = fontSize,
                onTapSlot = onTapSlot,
            )
        }
        if (activeHere && cursor.index == slot.size) {
            CursorCaret(
                height = (fontSize.value * 1.2f).dp,
                color = palette.lcdCursor,
                alpha = cursorAlpha,
            )
        }
    }
}

@Composable
private fun CursorCaret(
    height: androidx.compose.ui.unit.Dp,
    color: Color,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(height)
            .alpha(alpha)
            .background(color, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun NodeView(
    node: AstNode,
    ownerId: Long,
    role: SlotRole,
    indexInSlot: Int,
    cursor: CursorPosition,
    cursorAlpha: Float,
    palette: CalcPalette,
    fontSize: TextUnit,
    onTapSlot: (Long, SlotRole, Int, Int) -> Unit,
) {
    val activeLeaf = cursor.ownerId == ownerId && cursor.role == role && cursor.index == indexInSlot
    val childSize = (fontSize.value * 0.78f).coerceAtLeast(12f).sp
    val tinySize = (fontSize.value * 0.64f).coerceAtLeast(10f).sp

    when (node) {
        is NumberNode -> {
            val valStr = node.value
            if (activeLeaf && cursor.charOffset in 1 until valStr.length) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MathTokenText(valStr.substring(0, cursor.charOffset), fontSize, palette.lcdInk)
                    CursorCaret((fontSize.value * 1.15f).dp, palette.lcdCursor, cursorAlpha)
                    MathTokenText(valStr.substring(cursor.charOffset), fontSize, palette.lcdInk)
                }
            } else {
                MathTokenText(
                    text = valStr,
                    fontSize = fontSize,
                    color = palette.lcdInk,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        onTapSlot(ownerId, role, indexInSlot + 1, 0)
                    },
                )
            }
        }

        is OperatorNode -> {
            val txt = when (node.operator) {
                com.example.ast.MathOperator.ADD -> " + "
                com.example.ast.MathOperator.SUBTRACT -> " − "
                com.example.ast.MathOperator.MULTIPLY -> " × "
                com.example.ast.MathOperator.DIVIDE -> " ÷ "
                com.example.ast.MathOperator.UNARY_MINUS -> "−"
                com.example.ast.MathOperator.COMMA -> ", "
                else -> node.operator.symbol
            }
            MathTokenText(txt, fontSize, palette.lcdInk)
        }

        is ConstantNode -> {
            MathTokenText(node.constant.symbol, fontSize, palette.lcdInk)
        }

        is VariableNode -> {
            MathTokenText(node.variable.symbol, fontSize, palette.lcdInk)
        }

        is ParenNode -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathTokenText("(", (fontSize.value * 1.05f).sp, palette.lcdInkMuted)
                SlotView(node.body, node.id, SlotRole.Body, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                MathTokenText(")", (fontSize.value * 1.05f).sp, palette.lcdInkMuted)
            }
        }

        is AbsNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(IntrinsicSize.Min),
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.6.dp)
                        .fillMaxHeight()
                        .background(palette.lcdInk)
                )
                SlotView(node.body, node.id, SlotRole.Body, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.6.dp)
                        .fillMaxHeight()
                        .background(palette.lcdInk)
                )
            }
        }

        is FractionNode -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (node.whole != null) {
                    SlotView(node.whole, node.id, SlotRole.Whole, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                    Spacer(Modifier.width(2.dp))
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .width(IntrinsicSize.Max),
                ) {
                    Box(
                        modifier = Modifier.padding(bottom = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SlotView(node.numerator, node.id, SlotRole.Numerator, cursor, cursorAlpha, palette, childSize, onTapSlot)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(min = 18.dp)
                            .height(1.6.dp)
                            .background(palette.lcdInk)
                    )
                    Box(
                        modifier = Modifier.padding(top = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        SlotView(node.denominator, node.id, SlotRole.Denominator, cursor, cursorAlpha, palette, childSize, onTapSlot)
                    }
                }
            }
        }

        is PowerNode -> {
            Row(verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.padding(top = (fontSize.value * 0.22f).dp)) {
                    SlotView(node.base, node.id, SlotRole.Base, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                }
                Box(modifier = Modifier.padding(bottom = (fontSize.value * 0.35f).dp)) {
                    SlotView(node.exponent, node.id, SlotRole.Exponent, cursor, cursorAlpha, palette, childSize, onTapSlot)
                }
            }
        }

        is SqrtNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                if (node.index != null) {
                    Box(modifier = Modifier.padding(bottom = (fontSize.value * 0.45f).dp, end = 1.dp)) {
                        SlotView(node.index, node.id, SlotRole.Index, cursor, cursorAlpha, palette, tinySize, onTapSlot)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .drawBehind {
                            val strokeW = 1.7.dp.toPx()
                            val radW = 10.dp.toPx()
                            val h = size.height
                            val w = size.width
                            val path = Path().apply {
                                moveTo(0f, h * 0.56f)
                                lineTo(radW * 0.28f, h * 0.48f)
                                lineTo(radW * 0.52f, h * 0.92f)
                                lineTo(radW * 0.95f, 2.dp.toPx())
                                lineTo(w, 2.dp.toPx())
                            }
                            drawPath(
                                path = path,
                                color = palette.lcdInk,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            )
                        }
                        .padding(start = 12.dp, top = 4.dp, end = 3.dp, bottom = 1.dp),
                ) {
                    SlotView(node.radicand, node.id, SlotRole.Radicand, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                }
            }
        }

        is LogNode -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathTokenText("log", fontSize, palette.lcdInk)
                Box(modifier = Modifier.padding(top = (fontSize.value * 0.4f).dp, end = 2.dp)) {
                    SlotView(node.base, node.id, SlotRole.Base, cursor, cursorAlpha, palette, tinySize, onTapSlot)
                }
                MathTokenText("(", fontSize, palette.lcdInkMuted)
                SlotView(node.arg, node.id, SlotRole.Arg, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                MathTokenText(")", fontSize, palette.lcdInkMuted)
            }
        }

        is FunctionNode -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathTokenText(node.function.displayName, fontSize, palette.lcdInk)
                if (node.function.arity > 0) {
                    MathTokenText("(", fontSize, palette.lcdInkMuted)
                    SlotView(node.arg, node.id, SlotRole.Arg, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                    if (node.secondArg != null) {
                        MathTokenText(", ", fontSize, palette.lcdInkMuted)
                        SlotView(node.secondArg, node.id, SlotRole.SecondArg, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                    }
                    MathTokenText(")", fontSize, palette.lcdInkMuted)
                }
            }
        }

        is IntegralNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 2.dp),
            ) {
                Text(
                    text = "∫",
                    fontSize = (fontSize.value * 1.65f).sp,
                    color = palette.lcdInk,
                    fontFamily = JetBrainsMonoFamily,
                )
                Column(
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.padding(end = 3.dp),
                ) {
                    SlotView(node.upper, node.id, SlotRole.Upper, cursor, cursorAlpha, palette, tinySize, onTapSlot)
                    Spacer(Modifier.height(6.dp))
                    SlotView(node.lower, node.id, SlotRole.Lower, cursor, cursorAlpha, palette, tinySize, onTapSlot)
                }
                MathTokenText("(", fontSize, palette.lcdInkMuted)
                SlotView(node.expr, node.id, SlotRole.Expr, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                MathTokenText(")dx", (fontSize.value * 0.85f).sp, palette.lcdInkMuted)
            }
        }

        is DerivativeNode -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(end = 2.dp)) {
                    MathTokenText("d", tinySize, palette.lcdInk)
                    Box(Modifier.width(14.dp).height(1.2.dp).background(palette.lcdInk))
                    MathTokenText("dx", tinySize, palette.lcdInk)
                }
                MathTokenText("(", fontSize, palette.lcdInkMuted)
                SlotView(node.expr, node.id, SlotRole.Expr, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                MathTokenText(")|", fontSize, palette.lcdInkMuted)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = (fontSize.value * 0.42f).dp),
                ) {
                    MathTokenText("x=", tinySize, palette.lcdInkMuted)
                    SlotView(node.atX, node.id, SlotRole.AtX, cursor, cursorAlpha, palette, tinySize, onTapSlot)
                }
            }
        }

        is SumProdNode -> {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 2.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SlotView(node.upper, node.id, SlotRole.Upper, cursor, cursorAlpha, palette, tinySize, onTapSlot)
                    MathTokenText(node.kind.symbol, (fontSize.value * 1.25f).sp, palette.lcdInk)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MathTokenText("x=", tinySize, palette.lcdInkMuted)
                        SlotView(node.lower, node.id, SlotRole.Lower, cursor, cursorAlpha, palette, tinySize, onTapSlot)
                    }
                }
                MathTokenText("(", fontSize, palette.lcdInkMuted)
                SlotView(node.expr, node.id, SlotRole.Expr, cursor, cursorAlpha, palette, fontSize, onTapSlot)
                MathTokenText(")", fontSize, palette.lcdInkMuted)
            }
        }

        is MatrixNode -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .padding(horizontal = 3.dp),
            ) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(palette.lcdInk))
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    for (r in 0 until node.rows) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in 0 until node.cols) {
                                SlotView(
                                    slot = node.cells[r * node.cols + c],
                                    ownerId = node.id,
                                    role = SlotRole.MatrixCell(r, c),
                                    cursor = cursor,
                                    cursorAlpha = cursorAlpha,
                                    palette = palette,
                                    fontSize = childSize,
                                    onTapSlot = onTapSlot,
                                )
                            }
                        }
                    }
                }
                Box(Modifier.width(2.dp).fillMaxHeight().background(palette.lcdInk))
            }
        }
    }
}

@Composable
private fun MathTokenText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        fontSize = fontSize,
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * Renders an [ExactSymbolic] result cleanly as 2D stacked fractions and radicals inside the LCD result area.
 */
@Composable
fun ExactSymbolicResultView(
    exact: ExactSymbolic,
    palette: CalcPalette,
    fontSize: TextUnit = 24.sp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val rat = exact.rationalOrNull()
        if (rat != null) {
            if (rat.den == BigInteger.ONE) {
                MathTokenText(rat.num.toString(), fontSize, palette.lcdInk)
            } else {
                StackedFractionText(
                    numerator = rat.num.toString(),
                    denominator = rat.den.toString(),
                    palette = palette,
                    fontSize = (fontSize.value * 0.78f).sp,
                )
            }
            return@Box
        }

        // Check single-term radical (e.g. √2/2 or 2√3) or π-fraction (e.g. π/3)
        if (exact.piCoeff.isZero && exact.constPart.terms.size == 1) {
            val (rad, coeff) = exact.constPart.terms.entries.first()
            val neg = coeff.num < BigInteger.ZERO
            val absNum = coeff.num.abs()
            val radPart = if (absNum == BigInteger.ONE) "√$rad" else "${absNum}√$rad"
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (neg) MathTokenText("-", fontSize, palette.lcdInk)
                if (coeff.den == BigInteger.ONE) {
                    MathTokenText(radPart, fontSize, palette.lcdInk)
                } else {
                    StackedFractionText(radPart, coeff.den.toString(), palette, (fontSize.value * 0.78f).sp)
                }
            }
            return@Box
        }

        if (exact.constPart.isZero) {
            val piRat = exact.piCoeff.rationalOrNull()
            if (piRat != null) {
                val neg = piRat.num < BigInteger.ZERO
                val absNum = piRat.num.abs()
                val numStr = if (absNum == BigInteger.ONE) "π" else "${absNum}π"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (neg) MathTokenText("-", fontSize, palette.lcdInk)
                    if (piRat.den == BigInteger.ONE) {
                        MathTokenText(numStr, fontSize, palette.lcdInk)
                    } else {
                        StackedFractionText(numStr, piRat.den.toString(), palette, (fontSize.value * 0.78f).sp)
                    }
                }
                return@Box
            }
        }

        // Fallback to formatted symbolic text (e.g. (√6 - √2)/4)
        MathTokenText(exact.formatExact(), (fontSize.value * 0.85f).sp, palette.lcdInk)
    }
}

@Composable
private fun StackedFractionText(
    numerator: String,
    denominator: String,
    palette: CalcPalette,
    fontSize: TextUnit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(IntrinsicSize.Max),
    ) {
        Text(
            text = numerator,
            fontSize = fontSize,
            fontFamily = JetBrainsMonoFamily,
            color = palette.lcdInk,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(min = 16.dp)
                .height(1.6.dp)
                .background(palette.lcdInk)
        )
        Text(
            text = denominator,
            fontSize = fontSize,
            fontFamily = JetBrainsMonoFamily,
            color = palette.lcdInk,
        )
    }
}
