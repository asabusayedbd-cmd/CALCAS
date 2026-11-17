package com.example.engine

import com.example.ast.ExprSlot
import com.example.ast.MathFunction
import com.example.serializer.AstToEngineParser

enum class NumberBase(val radix: Int, val label: String) {
    DEC(10, "DEC"),
    HEX(16, "HEX"),
    BIN(2, "BIN"),
    OCT(8, "OCT"),
}

enum class BitWidth(val bits: Int, val label: String) {
    BIT_32(32, "32-bit"),
    BIT_64(64, "64-bit"),
}

data class BaseNResult(
    val rawValue: Long,
    val bitWidth: BitWidth,
) {
    fun format(base: NumberBase): String {
        return if (bitWidth == BitWidth.BIT_32) {
            val v32 = rawValue.toInt()
            when (base) {
                NumberBase.DEC -> v32.toString()
                NumberBase.HEX -> Integer.toHexString(v32).uppercase()
                NumberBase.BIN -> Integer.toBinaryString(v32)
                NumberBase.OCT -> Integer.toOctalString(v32)
            }
        } else {
            when (base) {
                NumberBase.DEC -> rawValue.toString()
                NumberBase.HEX -> java.lang.Long.toHexString(rawValue).uppercase()
                NumberBase.BIN -> java.lang.Long.toBinaryString(rawValue)
                NumberBase.OCT -> java.lang.Long.toOctalString(rawValue)
            }
        }
    }
}

object BaseNEngine {

    fun evaluate(
        slot: ExprSlot,
        base: NumberBase,
        bitWidth: BitWidth = BitWidth.BIT_32,
        ans: Long = 0L,
    ): BaseNResult {
        val expr = AstToEngineParser.parse(slot)
        val raw = evalExpr(expr, base, bitWidth, ans)
        val masked = mask(raw, bitWidth)
        return BaseNResult(masked, bitWidth)
    }

    private fun evalExpr(
        expr: EngineExpr,
        base: NumberBase,
        bitWidth: BitWidth,
        ans: Long,
    ): Long {
        return when (expr) {
            is EngineExpr.NumberLit -> parseLiteral(expr.raw, base, bitWidth, expr.sourceId)
            is EngineExpr.Var -> if (expr.variable == com.example.ast.MathVariable.ANS) ans else 0L
            is EngineExpr.UnaryMinus -> mask(-evalExpr(expr.operand, base, bitWidth, ans), bitWidth)
            is EngineExpr.Add -> mask(evalExpr(expr.left, base, bitWidth, ans) + evalExpr(expr.right, base, bitWidth, ans), bitWidth)
            is EngineExpr.Sub -> mask(evalExpr(expr.left, base, bitWidth, ans) - evalExpr(expr.right, base, bitWidth, ans), bitWidth)
            is EngineExpr.Mul -> mask(evalExpr(expr.left, base, bitWidth, ans) * evalExpr(expr.right, base, bitWidth, ans), bitWidth)
            is EngineExpr.Div -> {
                val r = evalExpr(expr.right, base, bitWidth, ans)
                if (r == 0L) throw CalcEngineException(ErrorKind.MATH, "Division by zero", expr.sourceId)
                mask(evalExpr(expr.left, base, bitWidth, ans) / r, bitWidth)
            }
            is EngineExpr.BitAnd -> mask(evalExpr(expr.left, base, bitWidth, ans) and evalExpr(expr.right, base, bitWidth, ans), bitWidth)
            is EngineExpr.BitOr -> mask(evalExpr(expr.left, base, bitWidth, ans) or evalExpr(expr.right, base, bitWidth, ans), bitWidth)
            is EngineExpr.BitXor -> mask(evalExpr(expr.left, base, bitWidth, ans) xor evalExpr(expr.right, base, bitWidth, ans), bitWidth)
            is EngineExpr.BitXnor -> mask((evalExpr(expr.left, base, bitWidth, ans) xor evalExpr(expr.right, base, bitWidth, ans)).inv(), bitWidth)
            is EngineExpr.Call -> {
                val a = evalExpr(expr.arg, base, bitWidth, ans)
                when (expr.function) {
                    MathFunction.BIT_NOT -> mask(a.inv(), bitWidth)
                    MathFunction.BIT_NEG -> mask(-a, bitWidth)
                    else -> throw CalcEngineException(ErrorKind.SYNTAX, "${expr.function.displayName} invalid in BASE-N", expr.sourceId)
                }
            }
            else -> throw CalcEngineException(ErrorKind.SYNTAX, "Operation invalid in BASE-N mode", expr.sourceId)
        }
    }

    private fun parseLiteral(raw: String, base: NumberBase, bitWidth: BitWidth, sourceId: Long): Long {
        if (raw.contains('.')) {
            throw CalcEngineException(ErrorKind.SYNTAX, "Decimals not allowed in BASE-N", sourceId)
        }
        return try {
            val uLong = java.lang.Long.parseUnsignedLong(raw, base.radix)
            mask(uLong, bitWidth)
        } catch (_: NumberFormatException) {
            throw CalcEngineException(ErrorKind.SYNTAX, "Invalid ${base.label} digit in '$raw'", sourceId)
        }
    }

    private fun mask(value: Long, bitWidth: BitWidth): Long =
        if (bitWidth == BitWidth.BIT_32) value.toInt().toLong() else value
}
