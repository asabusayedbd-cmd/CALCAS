package com.example.engine

import com.example.ast.MathConstant
import com.example.ast.MathFunction
import com.example.ast.MathVariable
import com.example.ast.SlotRole
import com.example.ast.SumProdKind

enum class ErrorKind(val title: String) {
    SYNTAX("Syntax ERROR"),
    MATH("Math ERROR"),
    STACK("Stack ERROR"),
    DIMENSION("Dimension ERROR"),
    VARIABLE("Variable ERROR"),
}

class CalcEngineException(
    val kind: ErrorKind,
    override val message: String,
    val errorNodeId: Long? = null,
    val errorOwnerId: Long? = null,
    val errorRole: SlotRole? = null,
    val errorIndex: Int? = null,
) : RuntimeException(message)

sealed interface EngineExpr {
    val sourceId: Long

    data class NumberLit(val raw: String, override val sourceId: Long) : EngineExpr
    data class Const(val constant: MathConstant, override val sourceId: Long) : EngineExpr
    data class Var(val variable: MathVariable, override val sourceId: Long) : EngineExpr

    data class UnaryMinus(val operand: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Factorial(val operand: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Percent(val operand: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Dms(
        val degrees: EngineExpr,
        val minutes: EngineExpr?,
        val seconds: EngineExpr?,
        override val sourceId: Long,
    ) : EngineExpr

    data class Add(val left: EngineExpr, val right: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Sub(val left: EngineExpr, val right: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Mul(
        val left: EngineExpr,
        val right: EngineExpr,
        val isImplicit: Boolean = false,
        override val sourceId: Long,
    ) : EngineExpr

    data class Div(
        val left: EngineExpr,
        val right: EngineExpr,
        val isFraction: Boolean = false,
        override val sourceId: Long,
    ) : EngineExpr

    data class MixedFrac(
        val whole: EngineExpr,
        val num: EngineExpr,
        val den: EngineExpr,
        override val sourceId: Long,
    ) : EngineExpr

    data class Pow(val base: EngineExpr, val exp: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Sqrt(val radicand: EngineExpr, override val sourceId: Long) : EngineExpr
    data class NthRoot(val index: EngineExpr, val radicand: EngineExpr, override val sourceId: Long) : EngineExpr
    data class LogBase(val base: EngineExpr, val arg: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Abs(val body: EngineExpr, override val sourceId: Long) : EngineExpr

    data class Call(
        val function: MathFunction,
        val arg: EngineExpr,
        val secondArg: EngineExpr? = null,
        override val sourceId: Long,
    ) : EngineExpr

    data class Permutation(val n: EngineExpr, val r: EngineExpr, override val sourceId: Long) : EngineExpr
    data class Combination(val n: EngineExpr, val r: EngineExpr, override val sourceId: Long) : EngineExpr
    data class PolarAngle(val r: EngineExpr, val theta: EngineExpr, override val sourceId: Long) : EngineExpr

    data class BitAnd(val left: EngineExpr, val right: EngineExpr, override val sourceId: Long) : EngineExpr
    data class BitOr(val left: EngineExpr, val right: EngineExpr, override val sourceId: Long) : EngineExpr
    data class BitXor(val left: EngineExpr, val right: EngineExpr, override val sourceId: Long) : EngineExpr
    data class BitXnor(val left: EngineExpr, val right: EngineExpr, override val sourceId: Long) : EngineExpr

    data class Integral(
        val expr: EngineExpr,
        val lower: EngineExpr,
        val upper: EngineExpr,
        override val sourceId: Long,
    ) : EngineExpr

    data class Derivative(
        val expr: EngineExpr,
        val atX: EngineExpr,
        override val sourceId: Long,
    ) : EngineExpr

    data class SumProd(
        val kind: SumProdKind,
        val expr: EngineExpr,
        val lower: EngineExpr,
        val upper: EngineExpr,
        override val sourceId: Long,
    ) : EngineExpr

    data class MatrixLit(
        val rows: Int,
        val cols: Int,
        val cells: List<EngineExpr>,
        override val sourceId: Long,
    ) : EngineExpr
}
