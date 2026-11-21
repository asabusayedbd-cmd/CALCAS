package com.example.serializer

import com.example.ast.AbsNode
import com.example.ast.AstNode
import com.example.ast.ConstantNode
import com.example.ast.DerivativeNode
import com.example.ast.ExprSlot
import com.example.ast.FractionNode
import com.example.ast.FunctionNode
import com.example.ast.IntegralNode
import com.example.ast.LogNode
import com.example.ast.MathConstant
import com.example.ast.MathFunction
import com.example.ast.MathOperator
import com.example.ast.MathVariable
import com.example.ast.MatrixNode
import com.example.ast.NumberNode
import com.example.ast.OperatorNode
import com.example.ast.ParenNode
import com.example.ast.PowerNode
import com.example.ast.SqrtNode
import com.example.ast.SumProdKind
import com.example.ast.SumProdNode
import com.example.ast.VariableNode

/**
 * Pure-Kotlin JSON codec for persisting and restoring 2D [ExprSlot] trees in Room history tape.
 */
object AstJsonCodec {

    fun encodeSlot(slot: ExprSlot): String {
        val sb = StringBuilder()
        writeSlot(slot, sb)
        return sb.toString()
    }

    fun decodeSlot(json: String): ExprSlot? {
        return try {
            val reader = JsonReader(json)
            readSlot(reader)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeSlot(slot: ExprSlot, sb: StringBuilder) {
        sb.append('[')
        slot.children.forEachIndexed { idx, node ->
            if (idx > 0) sb.append(',')
            writeNode(node, sb)
        }
        sb.append(']')
    }

    private fun writeNode(node: AstNode, sb: StringBuilder) {
        sb.append("{\"t\":")
        when (node) {
            is NumberNode -> {
                writeStr("num", sb)
                sb.append(",\"v\":"); writeStr(node.value, sb)
            }
            is OperatorNode -> {
                writeStr("op", sb)
                sb.append(",\"o\":"); writeStr(node.operator.name, sb)
            }
            is ConstantNode -> {
                writeStr("const", sb)
                when (val c = node.constant) {
                    MathConstant.Pi -> { sb.append(",\"k\":\"pi\"") }
                    MathConstant.EulerE -> { sb.append(",\"k\":\"e\"") }
                    MathConstant.ImaginaryI -> { sb.append(",\"k\":\"i\"") }
                    is MathConstant.PhysicalConstant -> {
                        sb.append(",\"k\":\"phys\",\"code\":").append(c.code)
                        sb.append(",\"sym\":"); writeStr(c.symbol, sb)
                    }
                }
            }
            is VariableNode -> {
                writeStr("var", sb)
                sb.append(",\"v\":"); writeStr(node.variable.name, sb)
            }
            is ParenNode -> {
                writeStr("paren", sb)
                sb.append(",\"b\":"); writeSlot(node.body, sb)
            }
            is AbsNode -> {
                writeStr("abs", sb)
                sb.append(",\"b\":"); writeSlot(node.body, sb)
            }
            is FractionNode -> {
                writeStr("frac", sb)
                sb.append(",\"n\":"); writeSlot(node.numerator, sb)
                sb.append(",\"d\":"); writeSlot(node.denominator, sb)
                if (node.whole != null) {
                    sb.append(",\"w\":"); writeSlot(node.whole, sb)
                }
            }
            is PowerNode -> {
                writeStr("pow", sb)
                sb.append(",\"b\":"); writeSlot(node.base, sb)
                sb.append(",\"e\":"); writeSlot(node.exponent, sb)
            }
            is SqrtNode -> {
                writeStr("sqrt", sb)
                sb.append(",\"r\":"); writeSlot(node.radicand, sb)
                if (node.index != null) {
                    sb.append(",\"i\":"); writeSlot(node.index, sb)
                }
            }
            is LogNode -> {
                writeStr("log", sb)
                sb.append(",\"b\":"); writeSlot(node.base, sb)
                sb.append(",\"a\":"); writeSlot(node.arg, sb)
            }
            is FunctionNode -> {
                writeStr("fn", sb)
                sb.append(",\"f\":"); writeStr(node.function.name, sb)
                sb.append(",\"a\":"); writeSlot(node.arg, sb)
                if (node.secondArg != null) {
                    sb.append(",\"a2\":"); writeSlot(node.secondArg, sb)
                }
            }
            is IntegralNode -> {
                writeStr("int", sb)
                sb.append(",\"e\":"); writeSlot(node.expr, sb)
                sb.append(",\"l\":"); writeSlot(node.lower, sb)
                sb.append(",\"u\":"); writeSlot(node.upper, sb)
            }
            is DerivativeNode -> {
                writeStr("ddx", sb)
                sb.append(",\"e\":"); writeSlot(node.expr, sb)
                sb.append(",\"x\":"); writeSlot(node.atX, sb)
            }
            is SumProdNode -> {
                writeStr("sum", sb)
                sb.append(",\"k\":"); writeStr(node.kind.name, sb)
                sb.append(",\"e\":"); writeSlot(node.expr, sb)
                sb.append(",\"l\":"); writeSlot(node.lower, sb)
                sb.append(",\"u\":"); writeSlot(node.upper, sb)
            }
            is MatrixNode -> {
                writeStr("mat", sb)
                sb.append(",\"r\":").append(node.rows).append(",\"c\":").append(node.cols)
                sb.append(",\"cells\":[")
                node.cells.forEachIndexed { idx, cell ->
                    if (idx > 0) sb.append(',')
                    writeSlot(cell, sb)
                }
                sb.append(']')
            }
        }
        sb.append('}')
    }

    private fun writeStr(s: String, sb: StringBuilder) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                else -> sb.append(ch)
            }
        }
        sb.append('"')
    }

    private class JsonReader(private val s: String) {
        var pos = 0
        var nextId = 1L

        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun expect(ch: Char) {
            skipWs()
            if (pos >= s.length || s[pos] != ch) error("Expected '$ch' at $pos")
            pos++
        }

        fun peek(): Char {
            skipWs()
            return if (pos < s.length) s[pos] else '\u0000'
        }

        fun readString(): String {
            expect('"')
            val sb = StringBuilder()
            while (pos < s.length && s[pos] != '"') {
                val c = s[pos++]
                if (c == '\\') {
                    val esc = s[pos++]
                    sb.append(when (esc) { 'n' -> '\n'; else -> esc })
                } else {
                    sb.append(c)
                }
            }
            expect('"')
            return sb.toString()
        }

        fun readInt(): Int {
            skipWs()
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '-')) pos++
            return s.substring(start, pos).toInt()
        }

        fun allocId(): Long = nextId++
    }

    private fun readSlot(r: JsonReader): ExprSlot {
        r.expect('[')
        val list = mutableListOf<AstNode>()
        if (r.peek() != ']') {
            while (true) {
                list.add(readNode(r))
                if (r.peek() == ',') {
                    r.expect(',')
                } else {
                    break
                }
            }
        }
        r.expect(']')
        return ExprSlot(list)
    }

    private fun readNode(r: JsonReader): AstNode {
        r.expect('{')
        var type = ""
        val fields = mutableMapOf<String, Any>()
        if (r.peek() != '}') {
            while (true) {
                val key = r.readString()
                r.expect(':')
                val value: Any = when (r.peek()) {
                    '"' -> r.readString()
                    '[' -> {
                        if (key == "cells") {
                            r.expect('[')
                            val cells = mutableListOf<ExprSlot>()
                            if (r.peek() != ']') {
                                while (true) {
                                    cells.add(readSlot(r))
                                    if (r.peek() == ',') r.expect(',') else break
                                }
                            }
                            r.expect(']')
                            cells
                        } else {
                            readSlot(r)
                        }
                    }
                    else -> r.readInt()
                }
                if (key == "t") type = value as String else fields[key] = value
                if (r.peek() == ',') r.expect(',') else break
            }
        }
        r.expect('}')
        val id = r.allocId()
        return when (type) {
            "num" -> NumberNode(id, fields["v"] as String)
            "op" -> OperatorNode(id, MathOperator.valueOf(fields["o"] as String))
            "const" -> ConstantNode(
                id,
                when (fields["k"] as String) {
                    "pi" -> MathConstant.Pi
                    "e" -> MathConstant.EulerE
                    "i" -> MathConstant.ImaginaryI
                    else -> MathConstant.PhysicalConstant(fields["code"] as Int, fields["sym"] as String)
                },
            )
            "var" -> VariableNode(id, MathVariable.valueOf(fields["v"] as String))
            "paren" -> ParenNode(id, fields["b"] as ExprSlot)
            "abs" -> AbsNode(id, fields["b"] as ExprSlot)
            "frac" -> FractionNode(
                id = id,
                numerator = fields["n"] as ExprSlot,
                denominator = fields["d"] as ExprSlot,
                whole = fields["w"] as? ExprSlot,
            )
            "pow" -> PowerNode(id, fields["b"] as ExprSlot, fields["e"] as ExprSlot)
            "sqrt" -> SqrtNode(id, fields["i"] as? ExprSlot, fields["r"] as ExprSlot)
            "log" -> LogNode(id, fields["b"] as ExprSlot, fields["a"] as ExprSlot)
            "fn" -> FunctionNode(
                id = id,
                function = MathFunction.valueOf(fields["f"] as String),
                arg = fields["a"] as ExprSlot,
                secondArg = fields["a2"] as? ExprSlot,
            )
            "int" -> IntegralNode(id, fields["e"] as ExprSlot, fields["l"] as ExprSlot, fields["u"] as ExprSlot)
            "ddx" -> DerivativeNode(id, fields["e"] as ExprSlot, fields["x"] as ExprSlot)
            "sum" -> SumProdNode(
                id = id,
                kind = SumProdKind.valueOf(fields["k"] as String),
                expr = fields["e"] as ExprSlot,
                lower = fields["l"] as ExprSlot,
                upper = fields["u"] as ExprSlot,
            )
            "mat" -> {
                @Suppress("UNCHECKED_CAST")
                MatrixNode(
                    id = id,
                    rows = fields["r"] as Int,
                    cols = fields["c"] as Int,
                    cells = fields["cells"] as List<ExprSlot>,
                )
            }
            else -> error("Unknown node type $type")
        }
    }

    /**
     * Re-assigns fresh IDs to an imported slot tree so it can be cleanly spliced into an active [com.example.ast.AstDocument].
     */
    fun reindexNodes(slot: ExprSlot, allocateId: () -> Long): ExprSlot {
        val updated = slot.children.map { node -> reindexNode(node, allocateId) }
        return ExprSlot(updated)
    }

    private fun reindexNode(node: AstNode, allocateId: () -> Long): AstNode {
        val newId = allocateId()
        return when (node) {
            is NumberNode -> node.copy(id = newId)
            is OperatorNode -> node.copy(id = newId)
            is ConstantNode -> node.copy(id = newId)
            is VariableNode -> node.copy(id = newId)
            is ParenNode -> node.copy(id = newId, body = reindexNodes(node.body, allocateId))
            is AbsNode -> node.copy(id = newId, body = reindexNodes(node.body, allocateId))
            is FractionNode -> node.copy(
                id = newId,
                numerator = reindexNodes(node.numerator, allocateId),
                denominator = reindexNodes(node.denominator, allocateId),
                whole = node.whole?.let { reindexNodes(it, allocateId) },
            )
            is PowerNode -> node.copy(
                id = newId,
                base = reindexNodes(node.base, allocateId),
                exponent = reindexNodes(node.exponent, allocateId),
            )
            is SqrtNode -> node.copy(
                id = newId,
                index = node.index?.let { reindexNodes(it, allocateId) },
                radicand = reindexNodes(node.radicand, allocateId),
            )
            is LogNode -> node.copy(
                id = newId,
                base = reindexNodes(node.base, allocateId),
                arg = reindexNodes(node.arg, allocateId),
            )
            is FunctionNode -> node.copy(
                id = newId,
                arg = reindexNodes(node.arg, allocateId),
                secondArg = node.secondArg?.let { reindexNodes(it, allocateId) },
            )
            is IntegralNode -> node.copy(
                id = newId,
                expr = reindexNodes(node.expr, allocateId),
                lower = reindexNodes(node.lower, allocateId),
                upper = reindexNodes(node.upper, allocateId),
            )
            is DerivativeNode -> node.copy(
                id = newId,
                expr = reindexNodes(node.expr, allocateId),
                atX = reindexNodes(node.atX, allocateId),
            )
            is SumProdNode -> node.copy(
                id = newId,
                expr = reindexNodes(node.expr, allocateId),
                lower = reindexNodes(node.lower, allocateId),
                upper = reindexNodes(node.upper, allocateId),
            )
            is MatrixNode -> node.copy(
                id = newId,
                cells = node.cells.map { reindexNodes(it, allocateId) },
            )
        }
    }
}
