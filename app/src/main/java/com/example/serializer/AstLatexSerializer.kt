package com.example.serializer

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
import com.example.ast.SumProdKind
import com.example.ast.SumProdNode
import com.example.ast.VariableNode

object AstLatexSerializer {

    fun toLatex(doc: AstDocument, includeCursor: Boolean = true): String {
        val sb = StringBuilder()
        appendSlotLatex(
            slot = doc.root,
            ownerId = ROOT_NODE_ID,
            role = SlotRole.Root,
            cursor = if (includeCursor) doc.cursor else null,
            sb = sb,
        )
        return sb.toString()
    }

    fun toLatex(slot: ExprSlot): String {
        val sb = StringBuilder()
        appendSlotLatex(slot, ROOT_NODE_ID, SlotRole.Root, cursor = null, sb = sb)
        return sb.toString()
    }

    fun toDisplayText(slot: ExprSlot): String {
        val sb = StringBuilder()
        appendSlotPlain(slot, sb)
        return sb.toString()
    }

    private fun appendSlotLatex(
        slot: ExprSlot,
        ownerId: Long,
        role: SlotRole,
        cursor: CursorPosition?,
        sb: StringBuilder,
    ) {
        val isCursorHere = cursor != null && cursor.ownerId == ownerId && cursor.role == role
        if (slot.isEmpty) {
            if (isCursorHere) {
                sb.append("\\cursor")
            } else {
                sb.append("\\square")
            }
            return
        }

        slot.children.forEachIndexed { idx, node ->
            if (isCursorHere && cursor!!.index == idx && cursor.charOffset == 0) {
                sb.append("\\cursor ")
            }
            val insideNumber = isCursorHere && cursor!!.index == idx && cursor.charOffset > 0
            appendNodeLatex(node, insideNumber, cursor?.charOffset ?: 0, cursor, sb)
        }
        if (isCursorHere && cursor!!.index == slot.size) {
            sb.append("\\cursor")
        }
    }

    private fun appendNodeLatex(
        node: AstNode,
        cursorInsideLeaf: Boolean,
        charOffset: Int,
        cursor: CursorPosition?,
        sb: StringBuilder,
    ) {
        when (node) {
            is NumberNode -> {
                if (cursorInsideLeaf && charOffset in 1 until node.value.length) {
                    sb.append(node.value.substring(0, charOffset))
                    sb.append("\\cursor ")
                    sb.append(node.value.substring(charOffset))
                } else {
                    sb.append(node.value)
                }
            }
            is OperatorNode -> sb.append(
                when (node.operator) {
                    com.example.ast.MathOperator.MULTIPLY -> "\\times "
                    com.example.ast.MathOperator.DIVIDE -> "\\div "
                    com.example.ast.MathOperator.SUBTRACT -> "-"
                    com.example.ast.MathOperator.UNARY_MINUS -> "-"
                    com.example.ast.MathOperator.PERMUTATION -> "\\mathbf{P}"
                    com.example.ast.MathOperator.COMBINATION -> "\\mathbf{C}"
                    com.example.ast.MathOperator.ANGLE -> "\\angle "
                    com.example.ast.MathOperator.DMS -> "^\\circ "
                    com.example.ast.MathOperator.PERCENT -> "\\%"
                    else -> node.operator.symbol
                }
            )
            is ConstantNode -> sb.append(
                when (node.constant) {
                    com.example.ast.MathConstant.Pi -> "\\pi "
                    com.example.ast.MathConstant.EulerE -> "e"
                    com.example.ast.MathConstant.ImaginaryI -> "i"
                    is com.example.ast.MathConstant.PhysicalConstant -> "\\text{${node.constant.symbol}}"
                }
            )
            is VariableNode -> sb.append(
                if (node.variable.symbol.length > 1) "\\text{${node.variable.symbol}}" else node.variable.symbol
            )
            is ParenNode -> {
                sb.append("\\left(")
                appendSlotLatex(node.body, node.id, SlotRole.Body, cursor, sb)
                sb.append("\\right)")
            }
            is AbsNode -> {
                sb.append("\\left|")
                appendSlotLatex(node.body, node.id, SlotRole.Body, cursor, sb)
                sb.append("\\right|")
            }
            is FractionNode -> {
                if (node.whole != null) {
                    appendSlotLatex(node.whole, node.id, SlotRole.Whole, cursor, sb)
                }
                sb.append("\\frac{")
                appendSlotLatex(node.numerator, node.id, SlotRole.Numerator, cursor, sb)
                sb.append("}{")
                appendSlotLatex(node.denominator, node.id, SlotRole.Denominator, cursor, sb)
                sb.append("}")
            }
            is PowerNode -> {
                sb.append("{")
                appendSlotLatex(node.base, node.id, SlotRole.Base, cursor, sb)
                sb.append("}^{")
                appendSlotLatex(node.exponent, node.id, SlotRole.Exponent, cursor, sb)
                sb.append("}")
            }
            is SqrtNode -> {
                sb.append("\\sqrt")
                if (node.index != null) {
                    sb.append("[")
                    appendSlotLatex(node.index, node.id, SlotRole.Index, cursor, sb)
                    sb.append("]")
                }
                sb.append("{")
                appendSlotLatex(node.radicand, node.id, SlotRole.Radicand, cursor, sb)
                sb.append("}")
            }
            is LogNode -> {
                sb.append("\\log_{")
                appendSlotLatex(node.base, node.id, SlotRole.Base, cursor, sb)
                sb.append("}\\left(")
                appendSlotLatex(node.arg, node.id, SlotRole.Arg, cursor, sb)
                sb.append("\\right)")
            }
            is FunctionNode -> {
                sb.append("\\text{${node.function.displayName}}\\left(")
                appendSlotLatex(node.arg, node.id, SlotRole.Arg, cursor, sb)
                if (node.secondArg != null) {
                    sb.append(", ")
                    appendSlotLatex(node.secondArg, node.id, SlotRole.SecondArg, cursor, sb)
                }
                sb.append("\\right)")
            }
            is IntegralNode -> {
                sb.append("\\int_{")
                appendSlotLatex(node.lower, node.id, SlotRole.Lower, cursor, sb)
                sb.append("}^{")
                appendSlotLatex(node.upper, node.id, SlotRole.Upper, cursor, sb)
                sb.append("}\\left(")
                appendSlotLatex(node.expr, node.id, SlotRole.Expr, cursor, sb)
                sb.append("\\right)\\,dx")
            }
            is DerivativeNode -> {
                sb.append("\\left.\\frac{d}{dx}\\left(")
                appendSlotLatex(node.expr, node.id, SlotRole.Expr, cursor, sb)
                sb.append("\\right)\\right|_{x=")
                appendSlotLatex(node.atX, node.id, SlotRole.AtX, cursor, sb)
                sb.append("}")
            }
            is SumProdNode -> {
                val cmd = if (node.kind == SumProdKind.SUM) "\\sum" else "\\prod"
                sb.append("${cmd}_{x=")
                appendSlotLatex(node.lower, node.id, SlotRole.Lower, cursor, sb)
                sb.append("}^{")
                appendSlotLatex(node.upper, node.id, SlotRole.Upper, cursor, sb)
                sb.append("}\\left(")
                appendSlotLatex(node.expr, node.id, SlotRole.Expr, cursor, sb)
                sb.append("\\right)")
            }
            is MatrixNode -> {
                sb.append("\\begin{pmatrix}")
                for (r in 0 until node.rows) {
                    if (r > 0) sb.append(" \\\\ ")
                    for (c in 0 until node.cols) {
                        if (c > 0) sb.append(" & ")
                        appendSlotLatex(
                            node.cells[r * node.cols + c],
                            node.id,
                            SlotRole.MatrixCell(r, c),
                            cursor,
                            sb,
                        )
                    }
                }
                sb.append("\\end{pmatrix}")
            }
        }
    }

    private fun appendSlotPlain(slot: ExprSlot, sb: StringBuilder) {
        if (slot.isEmpty) {
            sb.append("□")
            return
        }
        for (node in slot.children) {
            when (node) {
                is NumberNode -> sb.append(node.value)
                is OperatorNode -> sb.append(node.operator.symbol)
                is ConstantNode -> sb.append(node.constant.symbol)
                is VariableNode -> sb.append(node.variable.symbol)
                is ParenNode -> {
                    sb.append("(")
                    appendSlotPlain(node.body, sb)
                    sb.append(")")
                }
                is AbsNode -> {
                    sb.append("|")
                    appendSlotPlain(node.body, sb)
                    sb.append("|")
                }
                is FractionNode -> {
                    if (node.whole != null) {
                        appendSlotPlain(node.whole, sb)
                        sb.append(" ")
                    }
                    sb.append("(")
                    appendSlotPlain(node.numerator, sb)
                    sb.append(")/(")
                    appendSlotPlain(node.denominator, sb)
                    sb.append(")")
                }
                is PowerNode -> {
                    appendSlotPlain(node.base, sb)
                    sb.append("^(")
                    appendSlotPlain(node.exponent, sb)
                    sb.append(")")
                }
                is SqrtNode -> {
                    if (node.index != null) {
                        appendSlotPlain(node.index, sb)
                        sb.append("√(")
                    } else {
                        sb.append("√(")
                    }
                    appendSlotPlain(node.radicand, sb)
                    sb.append(")")
                }
                is LogNode -> {
                    sb.append("log_(")
                    appendSlotPlain(node.base, sb)
                    sb.append(")(")
                    appendSlotPlain(node.arg, sb)
                    sb.append(")")
                }
                is FunctionNode -> {
                    sb.append(node.function.displayName).append("(")
                    appendSlotPlain(node.arg, sb)
                    if (node.secondArg != null) {
                        sb.append(", ")
                        appendSlotPlain(node.secondArg, sb)
                    }
                    sb.append(")")
                }
                is IntegralNode -> {
                    sb.append("∫(")
                    appendSlotPlain(node.expr, sb)
                    sb.append(", ")
                    appendSlotPlain(node.lower, sb)
                    sb.append("..")
                    appendSlotPlain(node.upper, sb)
                    sb.append(")")
                }
                is DerivativeNode -> {
                    sb.append("d/dx(")
                    appendSlotPlain(node.expr, sb)
                    sb.append(")|x=")
                    appendSlotPlain(node.atX, sb)
                }
                is SumProdNode -> {
                    sb.append(node.kind.symbol).append("(")
                    appendSlotPlain(node.expr, sb)
                    sb.append(", ")
                    appendSlotPlain(node.lower, sb)
                    sb.append("..")
                    appendSlotPlain(node.upper, sb)
                    sb.append(")")
                }
                is MatrixNode -> {
                    sb.append("[")
                    for (r in 0 until node.rows) {
                        if (r > 0) sb.append("; ")
                        for (c in 0 until node.cols) {
                            if (c > 0) sb.append(", ")
                            appendSlotPlain(node.cells[r * node.cols + c], sb)
                        }
                    }
                    sb.append("]")
                }
            }
        }
    }
}
