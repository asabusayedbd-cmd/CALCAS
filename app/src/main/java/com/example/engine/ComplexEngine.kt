package com.example.engine

import com.example.ast.ExprSlot
import com.example.ast.MathConstant
import com.example.ast.MathFunction
import com.example.ast.MathVariable
import com.example.serializer.AstToEngineParser
import java.math.BigDecimal

data class ComplexVal(
    val re: BigDecimal,
    val im: BigDecimal,
) {
    val isReal: Boolean get() = BigMath.snapZero(im).compareTo(BigDecimal.ZERO) == 0

    operator fun plus(o: ComplexVal) = ComplexVal(BigMath.add(re, o.re), BigMath.add(im, o.im))
    operator fun minus(o: ComplexVal) = ComplexVal(BigMath.sub(re, o.re), BigMath.sub(im, o.im))
    operator fun unaryMinus() = ComplexVal(re.negate(), im.negate())

    operator fun times(o: ComplexVal): ComplexVal = ComplexVal(
        re = BigMath.sub(BigMath.mul(re, o.re), BigMath.mul(im, o.im)),
        im = BigMath.add(BigMath.mul(re, o.im), BigMath.mul(im, o.re)),
    )

    operator fun div(o: ComplexVal): ComplexVal {
        val denom = BigMath.add(BigMath.mul(o.re, o.re), BigMath.mul(o.im, o.im))
        if (denom.compareTo(BigDecimal.ZERO) == 0) {
            throw CalcEngineException(ErrorKind.MATH, "Complex division by zero")
        }
        val numRe = BigMath.add(BigMath.mul(re, o.re), BigMath.mul(im, o.im))
        val numIm = BigMath.sub(BigMath.mul(im, o.re), BigMath.mul(re, o.im))
        return ComplexVal(BigMath.div(numRe, denom), BigMath.div(numIm, denom))
    }

    fun conjugate(): ComplexVal = ComplexVal(re, im.negate())

    fun abs(): BigDecimal = BigMath.sqrt(BigMath.add(BigMath.mul(re, re), BigMath.mul(im, im)))

    fun arg(unit: AngleUnit): BigDecimal = BigMath.atan2(im, re, unit)

    fun sqrt(): ComplexVal {
        val r = abs()
        if (r.compareTo(BigDecimal.ZERO) == 0) return ComplexVal(BigDecimal.ZERO, BigDecimal.ZERO)
        val newRe = BigMath.sqrt(BigMath.div(BigMath.add(r, re), BigMath.TWO))
        val rawIm = BigMath.sqrt(BigMath.div(BigMath.sub(r, re).max(BigDecimal.ZERO), BigMath.TWO))
        val newIm = if (im < BigDecimal.ZERO) rawIm.negate() else rawIm
        return ComplexVal(BigMath.snapZero(newRe), BigMath.snapZero(newIm))
    }

    fun powInt(n: Int): ComplexVal {
        if (n == 0) return ComplexVal(BigDecimal.ONE, BigDecimal.ZERO)
        val absN = kotlin.math.abs(n)
        if (absN > 1000) throw CalcEngineException(ErrorKind.MATH, "Complex exponent overflow")
        var res = ComplexVal(BigDecimal.ONE, BigDecimal.ZERO)
        var base = this
        var k = absN
        while (k > 0) {
            if (k and 1 == 1) res *= base
            base *= base
            k = k shr 1
        }
        return if (n < 0) ComplexVal(BigDecimal.ONE, BigDecimal.ZERO) / res else res
    }

    fun formatRectangular(fmt: NumberFormatMode): String {
        val rClean = BigMath.snapZero(re)
        val iClean = BigMath.snapZero(im)
        val rZero = rClean.compareTo(BigDecimal.ZERO) == 0
        val iZero = iClean.compareTo(BigDecimal.ZERO) == 0
        if (iZero) return BigMath.formatDisplay(rClean, fmt)
        val iAbsStr = if (iClean.abs().compareTo(BigDecimal.ONE) == 0) "" else BigMath.formatDisplay(iClean.abs(), fmt)
        if (rZero) {
            val sign = if (iClean < BigDecimal.ZERO) "-" else ""
            return "$sign${iAbsStr}i"
        }
        val rStr = BigMath.formatDisplay(rClean, fmt)
        val op = if (iClean < BigDecimal.ZERO) " - " else " + "
        return "$rStr$op${iAbsStr}i"
    }

    fun formatPolar(unit: AngleUnit, fmt: NumberFormatMode): String {
        val r = BigMath.formatDisplay(abs(), fmt)
        val theta = BigMath.formatDisplay(arg(unit), fmt)
        return "$r∠$theta"
    }

    companion object {
        fun fromPolar(r: BigDecimal, theta: BigDecimal, unit: AngleUnit): ComplexVal = ComplexVal(
            re = BigMath.snapZero(BigMath.mul(r, BigMath.cos(theta, unit))),
            im = BigMath.snapZero(BigMath.mul(r, BigMath.sin(theta, unit))),
        )
    }
}

object ComplexEvaluator {

    fun evaluate(slot: ExprSlot, ctx: CalcContext, complexVars: Map<MathVariable, ComplexVal> = emptyMap()): ComplexVal {
        val expr = AstToEngineParser.parse(slot)
        return evalExpr(expr, ctx, complexVars)
    }

    private fun evalExpr(
        expr: EngineExpr,
        ctx: CalcContext,
        complexVars: Map<MathVariable, ComplexVal>,
    ): ComplexVal {
        return when (expr) {
            is EngineExpr.NumberLit -> ComplexVal(
                expr.raw.toBigDecimalOrNull() ?: throw CalcEngineException(ErrorKind.SYNTAX, "Invalid number", expr.sourceId),
                BigDecimal.ZERO,
            )
            is EngineExpr.Const -> when (expr.constant) {
                MathConstant.ImaginaryI -> ComplexVal(BigDecimal.ZERO, BigDecimal.ONE)
                MathConstant.Pi -> ComplexVal(BigMath.PI.round(BigMath.MC_INTERNAL), BigDecimal.ZERO)
                MathConstant.EulerE -> ComplexVal(BigMath.E.round(BigMath.MC_INTERNAL), BigDecimal.ZERO)
                is MathConstant.PhysicalConstant -> {
                    val v = ScientificConstants.byCode(expr.constant.code)?.value
                        ?: throw CalcEngineException(ErrorKind.MATH, "Unknown constant", expr.sourceId)
                    ComplexVal(v, BigDecimal.ZERO)
                }
            }
            is EngineExpr.Var -> complexVars[expr.variable]
                ?: ComplexVal(ctx.variables[expr.variable] ?: BigDecimal.ZERO, BigDecimal.ZERO)
            is EngineExpr.UnaryMinus -> -evalExpr(expr.operand, ctx, complexVars)
            is EngineExpr.Add -> evalExpr(expr.left, ctx, complexVars) + evalExpr(expr.right, ctx, complexVars)
            is EngineExpr.Sub -> evalExpr(expr.left, ctx, complexVars) - evalExpr(expr.right, ctx, complexVars)
            is EngineExpr.Mul -> evalExpr(expr.left, ctx, complexVars) * evalExpr(expr.right, ctx, complexVars)
            is EngineExpr.Div -> evalExpr(expr.left, ctx, complexVars) / evalExpr(expr.right, ctx, complexVars)
            is EngineExpr.PolarAngle -> {
                val r = evalExpr(expr.r, ctx, complexVars)
                val theta = evalExpr(expr.theta, ctx, complexVars)
                if (!r.isReal || !theta.isReal) {
                    throw CalcEngineException(ErrorKind.MATH, "Polar r∠θ requires real operands", expr.sourceId)
                }
                ComplexVal.fromPolar(r.re, theta.re, ctx.angleUnit)
            }
            is EngineExpr.Sqrt -> evalExpr(expr.radicand, ctx, complexVars).sqrt()
            is EngineExpr.Abs -> ComplexVal(evalExpr(expr.body, ctx, complexVars).abs(), BigDecimal.ZERO)
            is EngineExpr.Pow -> {
                val b = evalExpr(expr.base, ctx, complexVars)
                val e = evalExpr(expr.exp, ctx, complexVars)
                if (!e.isReal) throw CalcEngineException(ErrorKind.MATH, "Complex exponent unsupported", expr.sourceId)
                val eInt = try {
                    e.re.stripTrailingZeros().toBigIntegerExact().toInt()
                } catch (_: Exception) {
                    null
                }
                if (eInt != null) {
                    b.powInt(eInt)
                } else if (b.isReal) {
                    ComplexVal(BigMath.pow(b.re, e.re), BigDecimal.ZERO)
                } else {
                    // Polar power: r^e * (cos(e*θ) + i sin(e*θ))
                    val r = b.abs()
                    val thetaRad = b.arg(AngleUnit.RAD)
                    val newR = BigMath.pow(r, e.re)
                    val newTheta = BigMath.mul(thetaRad, e.re)
                    ComplexVal.fromPolar(newR, newTheta, AngleUnit.RAD)
                }
            }
            is EngineExpr.Call -> {
                val arg = evalExpr(expr.arg, ctx, complexVars)
                when (expr.function) {
                    MathFunction.CONJG -> arg.conjugate()
                    MathFunction.ARG -> ComplexVal(arg.arg(ctx.angleUnit), BigDecimal.ZERO)
                    else -> {
                        if (!arg.isReal) throw CalcEngineException(ErrorKind.MATH, "${expr.function.displayName} requires real argument", expr.sourceId)
                        ComplexVal(CompEvaluator.evalDecimal(expr, ctx), BigDecimal.ZERO)
                    }
                }
            }
            else -> ComplexVal(CompEvaluator.evalDecimal(expr, ctx), BigDecimal.ZERO)
        }
    }
}
