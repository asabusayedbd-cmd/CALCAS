package com.example.engine

import com.example.ast.ExprSlot
import com.example.ast.MathConstant
import com.example.ast.MathFunction
import com.example.ast.MathVariable
import com.example.serializer.AstToEngineParser
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

data class CalcContext(
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val numberFormat: NumberFormatMode = NumberFormatMode.Norm1,
    val variables: Map<MathVariable, BigDecimal> = emptyMap(),
)

data class CompEvalResult(
    val decimal: BigDecimal,
    val exact: ExactSymbolic? = null,
    val secondaryPolar: Pair<BigDecimal, BigDecimal>? = null, // For Pol(x,y) -> (r, θ) or Rec(r,θ) -> (X, Y)
    val secondaryLabels: Pair<String, String>? = null,
) {
    fun formatVariations(
        format: NumberFormatMode,
        engShift: Int = 0,
    ): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        if (exact != null) {
            list.add("EXACT" to exact.formatExact())
            val rat = exact.rationalOrNull()
            if (rat != null && !rat.isInteger) {
                val mixed = rat.formatMixed()
                val imp = rat.formatImproper()
                if (mixed != imp) {
                    list.add("MIXED" to mixed)
                }
            }
        }
        val decStr = BigMath.formatDisplay(decimal, format, engShift)
        if (list.none { it.second == decStr }) {
            list.add("DEC" to decStr)
        }
        return list
    }
}

/**
 * Core `COMP` evaluator combining exact symbolic algebra ([ExactSymbolic]) and 34-digit
 * arbitrary-precision decimal evaluation ([BigMath]), plus `CALC` variable discovery and `SOLVE`.
 */
object CompEvaluator {

    fun evaluate(slot: ExprSlot, ctx: CalcContext): CompEvalResult {
        val expr = AstToEngineParser.parse(slot)
        return evaluateExpr(expr, ctx)
    }

    fun evaluateExpr(expr: EngineExpr, ctx: CalcContext): CompEvalResult {
        // Check for Pol / Rec top-level dual output
        if (expr is EngineExpr.Call && (expr.function == MathFunction.POL || expr.function == MathFunction.REC)) {
            val a1 = evalDecimal(expr.arg, ctx)
            val a2 = evalDecimal(expr.secondArg!!, ctx)
            return if (expr.function == MathFunction.POL) {
                val r = BigMath.sqrt(BigMath.add(BigMath.mul(a1, a1), BigMath.mul(a2, a2)))
                val theta = BigMath.atan2(a2, a1, ctx.angleUnit)
                CompEvalResult(
                    decimal = r,
                    secondaryPolar = r to theta,
                    secondaryLabels = "r" to "θ",
                )
            } else {
                val x = BigMath.mul(a1, BigMath.cos(a2, ctx.angleUnit))
                val y = BigMath.mul(a1, BigMath.sin(a2, ctx.angleUnit))
                CompEvalResult(
                    decimal = BigMath.snapZero(x),
                    secondaryPolar = BigMath.snapZero(x) to BigMath.snapZero(y),
                    secondaryLabels = "X" to "Y",
                )
            }
        }

        val exact = tryEvalExact(expr, ctx)
        val decimal = exact?.toBigDecimal() ?: evalDecimal(expr, ctx)
        return CompEvalResult(decimal = BigMath.snapZero(decimal), exact = exact)
    }

    fun solveForX(
        exprSlot: ExprSlot,
        initialGuess: BigDecimal,
        ctx: CalcContext,
    ): NumericalMethods.SolveResult {
        val parsed = AstToEngineParser.parse(exprSlot)
        return NumericalMethods.solve(
            f = { xVal ->
                val localCtx = ctx.copy(variables = ctx.variables + (MathVariable.X to xVal))
                evalDecimal(parsed, localCtx)
            },
            initialGuess = initialGuess,
        )
    }

    fun collectFreeVariables(slot: ExprSlot): List<MathVariable> {
        val expr = try {
            AstToEngineParser.parse(slot)
        } catch (_: Exception) {
            return emptyList()
        }
        val found = linkedSetOf<MathVariable>()
        scanVars(expr, found, boundX = false)
        return found.toList()
    }

    private fun scanVars(expr: EngineExpr, out: MutableSet<MathVariable>, boundX: Boolean) {
        when (expr) {
            is EngineExpr.Var -> {
                if (expr.variable in listOf(
                        MathVariable.A, MathVariable.B, MathVariable.C,
                        MathVariable.D, MathVariable.E, MathVariable.F,
                        MathVariable.X, MathVariable.Y,
                    )
                ) {
                    if (!(boundX && expr.variable == MathVariable.X)) {
                        out.add(expr.variable)
                    }
                }
            }
            is EngineExpr.UnaryMinus -> scanVars(expr.operand, out, boundX)
            is EngineExpr.Factorial -> scanVars(expr.operand, out, boundX)
            is EngineExpr.Percent -> scanVars(expr.operand, out, boundX)
            is EngineExpr.Dms -> {
                scanVars(expr.degrees, out, boundX)
                expr.minutes?.let { scanVars(it, out, boundX) }
                expr.seconds?.let { scanVars(it, out, boundX) }
            }
            is EngineExpr.Add -> { scanVars(expr.left, out, boundX); scanVars(expr.right, out, boundX) }
            is EngineExpr.Sub -> { scanVars(expr.left, out, boundX); scanVars(expr.right, out, boundX) }
            is EngineExpr.Mul -> { scanVars(expr.left, out, boundX); scanVars(expr.right, out, boundX) }
            is EngineExpr.Div -> { scanVars(expr.left, out, boundX); scanVars(expr.right, out, boundX) }
            is EngineExpr.MixedFrac -> {
                scanVars(expr.whole, out, boundX)
                scanVars(expr.num, out, boundX)
                scanVars(expr.den, out, boundX)
            }
            is EngineExpr.Pow -> { scanVars(expr.base, out, boundX); scanVars(expr.exp, out, boundX) }
            is EngineExpr.Sqrt -> scanVars(expr.radicand, out, boundX)
            is EngineExpr.NthRoot -> { scanVars(expr.index, out, boundX); scanVars(expr.radicand, out, boundX) }
            is EngineExpr.LogBase -> { scanVars(expr.base, out, boundX); scanVars(expr.arg, out, boundX) }
            is EngineExpr.Abs -> scanVars(expr.body, out, boundX)
            is EngineExpr.Call -> {
                scanVars(expr.arg, out, boundX)
                expr.secondArg?.let { scanVars(it, out, boundX) }
            }
            is EngineExpr.Permutation -> { scanVars(expr.n, out, boundX); scanVars(expr.r, out, boundX) }
            is EngineExpr.Combination -> { scanVars(expr.n, out, boundX); scanVars(expr.r, out, boundX) }
            is EngineExpr.Integral -> {
                scanVars(expr.expr, out, boundX = true)
                scanVars(expr.lower, out, boundX)
                scanVars(expr.upper, out, boundX)
            }
            is EngineExpr.Derivative -> {
                scanVars(expr.expr, out, boundX = true)
                scanVars(expr.atX, out, boundX)
            }
            is EngineExpr.SumProd -> {
                scanVars(expr.expr, out, boundX = true)
                scanVars(expr.lower, out, boundX)
                scanVars(expr.upper, out, boundX)
            }
            else -> Unit
        }
    }

    // -------------------------------------------------------------------------
    // Exact Symbolic Evaluator
    // -------------------------------------------------------------------------

    private fun tryEvalExact(expr: EngineExpr, ctx: CalcContext): ExactSymbolic? {
        return when (expr) {
            is EngineExpr.NumberLit -> {
                val rat = ExactRational.fromDecimalStringOrNull(expr.raw) ?: return null
                ExactSymbolic.fromRational(rat)
            }
            is EngineExpr.Const -> when (expr.constant) {
                MathConstant.Pi -> ExactSymbolic.PI
                else -> null
            }
            is EngineExpr.Var -> {
                val v = ctx.variables[expr.variable] ?: return null
                val rat = BigMath.decimalToRationalOrNull(v, maxDen = 10_000L) ?: return null
                ExactSymbolic.fromRational(ExactRational.of(rat.first, rat.second))
            }
            is EngineExpr.UnaryMinus -> tryEvalExact(expr.operand, ctx)?.unaryMinus()
            is EngineExpr.Percent -> tryEvalExact(expr.operand, ctx)?.divOrNull(
                ExactSymbolic.fromRational(ExactRational.of(100))
            )
            is EngineExpr.Add -> {
                val l = tryEvalExact(expr.left, ctx) ?: return null
                val r = tryEvalExact(expr.right, ctx) ?: return null
                l + r
            }
            is EngineExpr.Sub -> {
                val l = tryEvalExact(expr.left, ctx) ?: return null
                val r = tryEvalExact(expr.right, ctx) ?: return null
                l - r
            }
            is EngineExpr.Mul -> {
                val l = tryEvalExact(expr.left, ctx) ?: return null
                val r = tryEvalExact(expr.right, ctx) ?: return null
                l.timesOrNull(r)
            }
            is EngineExpr.Div -> {
                val l = tryEvalExact(expr.left, ctx) ?: return null
                val r = tryEvalExact(expr.right, ctx) ?: return null
                l.divOrNull(r)
            }
            is EngineExpr.MixedFrac -> {
                val w = tryEvalExact(expr.whole, ctx) ?: return null
                val n = tryEvalExact(expr.num, ctx) ?: return null
                val d = tryEvalExact(expr.den, ctx) ?: return null
                val frac = n.divOrNull(d) ?: return null
                val wRat = w.rationalOrNull()
                if (wRat != null && wRat.num < BigInteger.ZERO) w - frac else w + frac
            }
            is EngineExpr.Sqrt -> {
                val inner = tryEvalExact(expr.radicand, ctx) ?: return null
                val rat = inner.rationalOrNull() ?: return null
                if (rat.num < BigInteger.ZERO) throw CalcEngineException(ErrorKind.MATH, "Square root of negative number", expr.sourceId)
                val rad = ExactRadicalSum.sqrtOfRationalOrNull(rat) ?: return null
                ExactSymbolic.fromRadical(rad)
            }
            is EngineExpr.Pow -> {
                val b = tryEvalExact(expr.base, ctx) ?: return null
                val e = tryEvalExact(expr.exp, ctx) ?: return null
                val eRat = e.rationalOrNull() ?: return null
                if (eRat == ExactRational.HALF && b.isPureRational) {
                    val bRat = b.rationalOrNull()!!
                    if (bRat.num < BigInteger.ZERO) return null
                    val rad = ExactRadicalSum.sqrtOfRationalOrNull(bRat) ?: return null
                    return ExactSymbolic.fromRadical(rad)
                }
                if (!eRat.isInteger) return null
                val n = eRat.num.toIntOrNull() ?: return null
                if (kotlin.math.abs(n) > 12 || !b.isRadicalOnly) return null
                powExactRadical(b.constPart, n)?.let { ExactSymbolic.fromRadical(it) }
            }
            is EngineExpr.Abs -> {
                val inner = tryEvalExact(expr.body, ctx) ?: return null
                val dec = inner.toBigDecimal()
                if (dec < BigDecimal.ZERO) -inner else inner
            }
            is EngineExpr.Call -> tryEvalExactFunction(expr, ctx)
            else -> null
        }
    }

    private fun powExactRadical(base: ExactRadicalSum, n: Int): ExactRadicalSum? {
        if (n == 0) return ExactRadicalSum.ONE
        if (base.isZero) {
            if (n < 0) throw CalcEngineException(ErrorKind.MATH, "Division by zero")
            return ExactRadicalSum.ZERO
        }
        var acc = ExactRadicalSum.ONE
        val absN = kotlin.math.abs(n)
        repeat(absN) {
            acc *= base
            if (acc.terms.size > 4) return null
        }
        return if (n > 0) acc else ExactRadicalSum.ONE.divOrNull(acc)
    }

    private fun tryEvalExactFunction(expr: EngineExpr.Call, ctx: CalcContext): ExactSymbolic? {
        val argExact = tryEvalExact(expr.arg, ctx) ?: return null
        return when (expr.function) {
            MathFunction.SIN, MathFunction.COS, MathFunction.TAN -> {
                val deg = exactAngleInDegrees(argExact, ctx.angleUnit) ?: return null
                exactTrigDegrees(expr.function, deg, expr.sourceId)
            }
            MathFunction.ASIN, MathFunction.ACOS, MathFunction.ATAN -> {
                exactInverseTrig(expr.function, argExact, ctx.angleUnit)
            }
            MathFunction.LN -> {
                val r = argExact.rationalOrNull()
                if (r == ExactRational.ONE) ExactSymbolic.ZERO else null
            }
            MathFunction.LOG10 -> {
                val r = argExact.rationalOrNull() ?: return null
                if (r.isInteger && r.num > BigInteger.ZERO) {
                    val bd = BigDecimal(r.num).stripTrailingZeros()
                    if (bd.unscaledValue() == BigInteger.ONE) {
                        ExactSymbolic.fromRational(ExactRational.of((-bd.scale()).toLong()))
                    } else null
                } else null
            }
            else -> null
        }
    }

    private fun exactAngleInDegrees(sym: ExactSymbolic, unit: AngleUnit): ExactRational? {
        return when (unit) {
            AngleUnit.DEG -> sym.rationalOrNull()
            AngleUnit.GRAD -> sym.rationalOrNull()?.let { it * ExactRational.of(9, 10) }
            AngleUnit.RAD -> {
                if (sym.constPart.isZero) {
                    sym.piCoeff.rationalOrNull()?.let { it * ExactRational.of(180) }
                } else null
            }
        }
    }

    private fun exactTrigDegrees(fn: MathFunction, deg: ExactRational, sourceId: Long): ExactSymbolic? {
        if (!deg.isInteger) return null
        val mod = ((deg.num.mod(BigInteger.valueOf(360))).toInt() + 360) % 360
        if (mod % 15 != 0) return null

        val sinRad = sin15DegExact(mod)
        val cosRad = sin15DegExact((mod + 90) % 360)
        return when (fn) {
            MathFunction.SIN -> ExactSymbolic.fromRadical(sinRad)
            MathFunction.COS -> ExactSymbolic.fromRadical(cosRad)
            MathFunction.TAN -> {
                if (cosRad.isZero) throw CalcEngineException(ErrorKind.MATH, "Tangent undefined", sourceId)
                val t = sinRad.divOrNull(cosRad) ?: return null
                ExactSymbolic.fromRadical(t)
            }
            else -> null
        }
    }

    private fun sin15DegExact(deg: Int): ExactRadicalSum {
        val quadrantSign = if (deg in 181..359) -1 else 1
        val ref = when {
            deg <= 90 -> deg
            deg <= 180 -> 180 - deg
            deg <= 270 -> deg - 180
            else -> 360 - deg
        }
        val base = when (ref) {
            0 -> ExactRadicalSum.ZERO
            15 -> ExactRadicalSum(
                mapOf(
                    BigInteger.valueOf(6) to ExactRational.of(1, 4),
                    BigInteger.valueOf(2) to ExactRational.of(-1, 4),
                )
            )
            30 -> ExactRadicalSum.rational(ExactRational.HALF)
            45 -> ExactRadicalSum(mapOf(BigInteger.TWO to ExactRational.HALF))
            60 -> ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.HALF))
            75 -> ExactRadicalSum(
                mapOf(
                    BigInteger.valueOf(6) to ExactRational.of(1, 4),
                    BigInteger.valueOf(2) to ExactRational.of(1, 4),
                )
            )
            90 -> ExactRadicalSum.ONE
            else -> ExactRadicalSum.ZERO
        }
        return if (quadrantSign < 0) -base else base
    }

    private fun exactInverseTrig(
        fn: MathFunction,
        arg: ExactSymbolic,
        unit: AngleUnit,
    ): ExactSymbolic? {
        if (!arg.isRadicalOnly) return null
        val r = arg.constPart
        val degVal: Int? = when (fn) {
            MathFunction.ASIN -> when (r) {
                ExactRadicalSum.ZERO -> 0
                ExactRadicalSum.rational(ExactRational.HALF) -> 30
                ExactRadicalSum(mapOf(BigInteger.TWO to ExactRational.HALF)) -> 45
                ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.HALF)) -> 60
                ExactRadicalSum.ONE -> 90
                -ExactRadicalSum.rational(ExactRational.HALF) -> -30
                -ExactRadicalSum(mapOf(BigInteger.TWO to ExactRational.HALF)) -> -45
                -ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.HALF)) -> -60
                -ExactRadicalSum.ONE -> -90
                else -> null
            }
            MathFunction.ACOS -> when (r) {
                ExactRadicalSum.ONE -> 0
                ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.HALF)) -> 30
                ExactRadicalSum(mapOf(BigInteger.TWO to ExactRational.HALF)) -> 45
                ExactRadicalSum.rational(ExactRational.HALF) -> 60
                ExactRadicalSum.ZERO -> 90
                -ExactRadicalSum.rational(ExactRational.HALF) -> 120
                -ExactRadicalSum(mapOf(BigInteger.TWO to ExactRational.HALF)) -> 135
                -ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.HALF)) -> 150
                -ExactRadicalSum.ONE -> 180
                else -> null
            }
            MathFunction.ATAN -> when (r) {
                ExactRadicalSum.ZERO -> 0
                ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.of(1, 3))) -> 30
                ExactRadicalSum.ONE -> 45
                ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.ONE)) -> 60
                -ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.of(1, 3))) -> -30
                -ExactRadicalSum.ONE -> -45
                -ExactRadicalSum(mapOf(BigInteger.valueOf(3) to ExactRational.ONE)) -> -60
                else -> null
            }
            else -> null
        }
        val d = degVal ?: return null
        return when (unit) {
            AngleUnit.DEG -> ExactSymbolic.fromRational(ExactRational.of(d.toLong()))
            AngleUnit.GRAD -> ExactSymbolic.fromRational(ExactRational.of(d.toLong() * 10L, 9L))
            AngleUnit.RAD -> ExactSymbolic(
                piCoeff = ExactRadicalSum.rational(ExactRational.of(d.toLong(), 180L)),
            )
        }
    }

    // -------------------------------------------------------------------------
    // Arbitrary-Precision BigDecimal Evaluator
    // -------------------------------------------------------------------------

    fun evalDecimal(expr: EngineExpr, ctx: CalcContext): BigDecimal {
        return when (expr) {
            is EngineExpr.NumberLit -> expr.raw.toBigDecimalOrNull()
                ?: throw CalcEngineException(ErrorKind.SYNTAX, "Invalid number '${expr.raw}'", expr.sourceId)
            is EngineExpr.Const -> when (expr.constant) {
                MathConstant.Pi -> BigMath.PI.round(BigMath.MC_INTERNAL)
                MathConstant.EulerE -> BigMath.E.round(BigMath.MC_INTERNAL)
                MathConstant.ImaginaryI -> throw CalcEngineException(ErrorKind.MATH, "Imaginary unit in COMP mode", expr.sourceId)
                is MathConstant.PhysicalConstant ->
                    ScientificConstants.byCode(expr.constant.code)?.value
                        ?: throw CalcEngineException(ErrorKind.MATH, "Unknown constant ${expr.constant.symbol}", expr.sourceId)
            }
            is EngineExpr.Var -> ctx.variables[expr.variable] ?: BigDecimal.ZERO
            is EngineExpr.UnaryMinus -> evalDecimal(expr.operand, ctx).negate()
            is EngineExpr.Factorial -> BigMath.factorial(evalDecimal(expr.operand, ctx))
            is EngineExpr.Percent -> BigMath.div(evalDecimal(expr.operand, ctx), BigDecimal("100"))
            is EngineExpr.Dms -> {
                val d = evalDecimal(expr.degrees, ctx)
                val m = expr.minutes?.let { evalDecimal(it, ctx) } ?: BigDecimal.ZERO
                val s = expr.seconds?.let { evalDecimal(it, ctx) } ?: BigDecimal.ZERO
                val sign = if (d < BigDecimal.ZERO) -1 else 1
                val absD = d.abs()
                val res = BigMath.add(
                    absD,
                    BigMath.add(
                        BigMath.div(m.abs(), BigDecimal("60")),
                        BigMath.div(s.abs(), BigDecimal("3600")),
                    )
                )
                if (sign < 0) res.negate() else res
            }
            is EngineExpr.Add -> BigMath.add(evalDecimal(expr.left, ctx), evalDecimal(expr.right, ctx))
            is EngineExpr.Sub -> BigMath.sub(evalDecimal(expr.left, ctx), evalDecimal(expr.right, ctx))
            is EngineExpr.Mul -> BigMath.mul(evalDecimal(expr.left, ctx), evalDecimal(expr.right, ctx))
            is EngineExpr.Div -> BigMath.div(evalDecimal(expr.left, ctx), evalDecimal(expr.right, ctx))
            is EngineExpr.MixedFrac -> {
                val w = evalDecimal(expr.whole, ctx)
                val n = evalDecimal(expr.num, ctx)
                val d = evalDecimal(expr.den, ctx)
                val frac = BigMath.div(n, d)
                if (w < BigDecimal.ZERO) BigMath.sub(w, frac) else BigMath.add(w, frac)
            }
            is EngineExpr.Pow -> BigMath.pow(evalDecimal(expr.base, ctx), evalDecimal(expr.exp, ctx))
            is EngineExpr.Sqrt -> BigMath.sqrt(evalDecimal(expr.radicand, ctx))
            is EngineExpr.NthRoot -> BigMath.nthRoot(evalDecimal(expr.index, ctx), evalDecimal(expr.radicand, ctx))
            is EngineExpr.LogBase -> BigMath.logBase(evalDecimal(expr.base, ctx), evalDecimal(expr.arg, ctx))
            is EngineExpr.Abs -> evalDecimal(expr.body, ctx).abs()
            is EngineExpr.Permutation -> BigMath.permutation(evalDecimal(expr.n, ctx), evalDecimal(expr.r, ctx))
            is EngineExpr.Combination -> BigMath.combination(evalDecimal(expr.n, ctx), evalDecimal(expr.r, ctx))
            is EngineExpr.Call -> evalDecimalCall(expr, ctx)
            is EngineExpr.Integral -> {
                val lo = evalDecimal(expr.lower, ctx)
                val hi = evalDecimal(expr.upper, ctx)
                NumericalMethods.integrateGaussKronrod(
                    f = { x -> evalDecimal(expr.expr, ctx.copy(variables = ctx.variables + (MathVariable.X to x))) },
                    lower = lo,
                    upper = hi,
                )
            }
            is EngineExpr.Derivative -> {
                val atX = evalDecimal(expr.atX, ctx)
                NumericalMethods.differentiateRidders(
                    f = { x -> evalDecimal(expr.expr, ctx.copy(variables = ctx.variables + (MathVariable.X to x))) },
                    x = atX,
                )
            }
            is EngineExpr.SumProd -> {
                val lo = evalDecimal(expr.lower, ctx)
                val hi = evalDecimal(expr.upper, ctx)
                NumericalMethods.sumOrProd(
                    kind = expr.kind,
                    f = { x -> evalDecimal(expr.expr, ctx.copy(variables = ctx.variables + (MathVariable.X to x))) },
                    lower = lo,
                    upper = hi,
                )
            }
            is EngineExpr.PolarAngle,
            is EngineExpr.BitAnd,
            is EngineExpr.BitOr,
            is EngineExpr.BitXor,
            is EngineExpr.BitXnor,
            is EngineExpr.MatrixLit ->
                throw CalcEngineException(ErrorKind.SYNTAX, "Operation not supported in COMP mode", expr.sourceId)
        }
    }

    private fun evalDecimalCall(expr: EngineExpr.Call, ctx: CalcContext): BigDecimal {
        val a1 = evalDecimal(expr.arg, ctx)
        return when (expr.function) {
            MathFunction.SIN -> BigMath.sin(a1, ctx.angleUnit)
            MathFunction.COS -> BigMath.cos(a1, ctx.angleUnit)
            MathFunction.TAN -> BigMath.tan(a1, ctx.angleUnit)
            MathFunction.ASIN -> BigMath.asin(a1, ctx.angleUnit)
            MathFunction.ACOS -> BigMath.acos(a1, ctx.angleUnit)
            MathFunction.ATAN -> BigMath.atan(a1, ctx.angleUnit)
            MathFunction.SINH -> BigMath.sinh(a1)
            MathFunction.COSH -> BigMath.cosh(a1)
            MathFunction.TANH -> BigMath.tanh(a1)
            MathFunction.ASINH -> BigMath.asinh(a1)
            MathFunction.ACOSH -> BigMath.acosh(a1)
            MathFunction.ATANH -> BigMath.atanh(a1)
            MathFunction.LOG10 -> BigMath.log10(a1)
            MathFunction.LN -> BigMath.ln(a1)
            MathFunction.EXP -> BigMath.exp(a1)
            MathFunction.ROUND -> a1.round(MathContext(10, RoundingMode.HALF_UP))
            MathFunction.GCD -> BigMath.gcd(a1, evalDecimal(expr.secondArg!!, ctx))
            MathFunction.LCM -> BigMath.lcm(a1, evalDecimal(expr.secondArg!!, ctx))
            MathFunction.RAN_INT -> {
                val a2 = evalDecimal(expr.secondArg!!, ctx)
                val lo = minOf(a1.toLong(), a2.toLong())
                val hi = maxOf(a1.toLong(), a2.toLong())
                BigDecimal.valueOf((lo..hi).random())
            }
            else -> throw CalcEngineException(ErrorKind.SYNTAX, "${expr.function.displayName} requires specific mode", expr.sourceId)
        }
    }

    private fun BigInteger.toIntOrNull(): Int? =
        if (this in BigInteger.valueOf(Int.MIN_VALUE.toLong())..BigInteger.valueOf(Int.MAX_VALUE.toLong())) {
            this.toInt()
        } else null
}
