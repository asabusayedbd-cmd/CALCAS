package com.example.engine

import com.example.ast.ExprSlot
import com.example.ast.MathVariable
import com.example.serializer.AstToEngineParser
import java.math.BigDecimal
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

data class StatDataRow(
    val x: BigDecimal,
    val y: BigDecimal? = null,
    val freq: BigDecimal = BigDecimal.ONE,
)

data class OneVarStats(
    val n: BigDecimal,
    val sumX: BigDecimal,
    val sumX2: BigDecimal,
    val meanX: BigDecimal,
    val popStdDev: BigDecimal,
    val sampleStdDev: BigDecimal,
    val minX: BigDecimal,
    val maxX: BigDecimal,
)

enum class RegressionType(val label: String, val formula: String) {
    LINEAR("A + Bx", "y = A + Bx"),
    QUADRATIC("A + Bx + Cx²", "y = A + Bx + Cx²"),
    LOGARITHMIC("A + B·ln(x)", "y = A + B·ln(x)"),
    EXP_E("A·e^(Bx)", "y = A·e^(Bx)"),
    EXP_AB("A·B^x", "y = A·B^x"),
    POWER("A·x^B", "y = A·x^B"),
    INVERSE("A + B/x", "y = A + B/x"),
}

data class RegressionResult(
    val type: RegressionType,
    val a: BigDecimal,
    val b: BigDecimal,
    val c: BigDecimal? = null,
    val r: BigDecimal? = null,
    val n: BigDecimal,
) {
    fun estimateY(x: BigDecimal): BigDecimal {
        val xd = x.toDouble()
        val ad = a.toDouble()
        val bd = b.toDouble()
        val cd = c?.toDouble() ?: 0.0
        val y = when (type) {
            RegressionType.LINEAR -> ad + bd * xd
            RegressionType.QUADRATIC -> ad + bd * xd + cd * xd * xd
            RegressionType.LOGARITHMIC -> ad + bd * ln(xd)
            RegressionType.EXP_E -> ad * exp(bd * xd)
            RegressionType.EXP_AB -> ad * bd.pow(xd)
            RegressionType.POWER -> ad * xd.pow(bd)
            RegressionType.INVERSE -> ad + bd / xd
        }
        return BigDecimal.valueOf(y).round(BigMath.MC_DISPLAY)
    }
}

object StatEngine {

    fun compute1Var(rows: List<StatDataRow>): OneVarStats {
        if (rows.isEmpty()) throw CalcEngineException(ErrorKind.MATH, "No statistical data")
        var n = BigDecimal.ZERO
        var sumX = BigDecimal.ZERO
        var sumX2 = BigDecimal.ZERO
        var minX = rows.first().x
        var maxX = rows.first().x

        for (row in rows) {
            val f = row.freq
            if (f <= BigDecimal.ZERO) continue
            n = BigMath.add(n, f)
            sumX = BigMath.add(sumX, BigMath.mul(row.x, f))
            sumX2 = BigMath.add(sumX2, BigMath.mul(BigMath.mul(row.x, row.x), f))
            if (row.x < minX) minX = row.x
            if (row.x > maxX) maxX = row.x
        }
        if (n.compareTo(BigDecimal.ZERO) == 0) throw CalcEngineException(ErrorKind.MATH, "Empty frequency sum")

        val mean = BigMath.div(sumX, n)
        val varPop = BigMath.sub(BigMath.div(sumX2, n), BigMath.mul(mean, mean)).max(BigDecimal.ZERO)
        val popSigma = BigMath.sqrt(varPop)
        val sampleSigma = if (n > BigDecimal.ONE) {
            val ss = BigMath.sub(sumX2, BigMath.div(BigMath.mul(sumX, sumX), n)).max(BigDecimal.ZERO)
            BigMath.sqrt(BigMath.div(ss, BigMath.sub(n, BigDecimal.ONE)))
        } else {
            BigDecimal.ZERO
        }
        return OneVarStats(n, sumX, sumX2, mean, popSigma, sampleSigma, minX, maxX)
    }

    fun computeRegression(rows: List<StatDataRow>, type: RegressionType): RegressionResult {
        val pairs = rows.filter { it.y != null && it.freq > BigDecimal.ZERO }
        if (pairs.size < 2) throw CalcEngineException(ErrorKind.MATH, "Need at least 2 data points")
        if (type == RegressionType.QUADRATIC) {
            return computeQuadraticRegression(pairs)
        }

        // Transform to linear (u, v) = (transformX(x), transformY(y))
        var n = 0.0
        var su = 0.0
        var sv = 0.0
        var su2 = 0.0
        var sv2 = 0.0
        var suv = 0.0

        for (row in pairs) {
            val x = row.x.toDouble()
            val y = row.y!!.toDouble()
            val f = row.freq.toDouble()
            val u = when (type) {
                RegressionType.LINEAR, RegressionType.EXP_E, RegressionType.EXP_AB -> x
                RegressionType.LOGARITHMIC, RegressionType.POWER -> {
                    if (x <= 0.0) throw CalcEngineException(ErrorKind.MATH, "x must be > 0 for log/power")
                    ln(x)
                }
                RegressionType.INVERSE -> {
                    if (x == 0.0) throw CalcEngineException(ErrorKind.MATH, "x = 0 in inverse regression")
                    1.0 / x
                }
                RegressionType.QUADRATIC -> x
            }
            val v = when (type) {
                RegressionType.EXP_E, RegressionType.EXP_AB, RegressionType.POWER -> {
                    if (y <= 0.0) throw CalcEngineException(ErrorKind.MATH, "y must be > 0 for exp/power")
                    ln(y)
                }
                else -> y
            }
            n += f
            su += f * u
            sv += f * v
            su2 += f * u * u
            sv2 += f * v * v
            suv += f * u * v
        }

        val denom = n * su2 - su * su
        if (kotlin.math.abs(denom) < 1e-18) {
            throw CalcEngineException(ErrorKind.MATH, "Collinear data")
        }
        val linB = (n * suv - su * sv) / denom
        val linA = (sv - linB * su) / n
        val rDen = sqrt(((n * su2 - su * su) * (n * sv2 - sv * sv)).coerceAtLeast(0.0))
        val r = if (rDen > 1e-18) (n * suv - su * sv) / rDen else 0.0

        val (aOut, bOut) = when (type) {
            RegressionType.EXP_E -> exp(linA) to linB
            RegressionType.EXP_AB -> exp(linA) to exp(linB)
            RegressionType.POWER -> exp(linA) to linB
            else -> linA to linB
        }

        return RegressionResult(
            type = type,
            a = BigDecimal.valueOf(aOut).round(BigMath.MC_DISPLAY),
            b = BigDecimal.valueOf(bOut).round(BigMath.MC_DISPLAY),
            c = null,
            r = BigDecimal.valueOf(r).round(BigMath.MC_DISPLAY),
            n = BigDecimal.valueOf(n).stripTrailingZeros(),
        )
    }

    private fun computeQuadraticRegression(pairs: List<StatDataRow>): RegressionResult {
        if (pairs.size < 3) throw CalcEngineException(ErrorKind.MATH, "Quadratic regression needs 3+ points")
        var n = 0.0; var sx = 0.0; var sx2 = 0.0; var sx3 = 0.0; var sx4 = 0.0
        var sy = 0.0; var sxy = 0.0; var sx2y = 0.0
        for (row in pairs) {
            val x = row.x.toDouble()
            val y = row.y!!.toDouble()
            val f = row.freq.toDouble()
            val x2 = x * x
            n += f; sx += f * x; sx2 += f * x2; sx3 += f * x2 * x; sx4 += f * x2 * x2
            sy += f * y; sxy += f * x * y; sx2y += f * x2 * y
        }
        val sol = EqnSolver.solveLinear3x3(
            arrayOf(
                arrayOf(BigDecimal.valueOf(n), BigDecimal.valueOf(sx), BigDecimal.valueOf(sx2)),
                arrayOf(BigDecimal.valueOf(sx), BigDecimal.valueOf(sx2), BigDecimal.valueOf(sx3)),
                arrayOf(BigDecimal.valueOf(sx2), BigDecimal.valueOf(sx3), BigDecimal.valueOf(sx4)),
            ),
            arrayOf(BigDecimal.valueOf(sy), BigDecimal.valueOf(sxy), BigDecimal.valueOf(sx2y)),
        )
        if (sol !is EqnSolution.LinearUnique) {
            throw CalcEngineException(ErrorKind.MATH, "Singular quadratic fit")
        }
        return RegressionResult(
            type = RegressionType.QUADRATIC,
            a = sol.values[0],
            b = sol.values[1],
            c = sol.values[2],
            r = null,
            n = BigDecimal.valueOf(n).stripTrailingZeros(),
        )
    }
}

data class TableRow(
    val index: Int,
    val x: BigDecimal,
    val fxDisplay: String,
    val gxDisplay: String? = null,
)

object TableEngine {

    fun generate(
        fSlot: ExprSlot,
        gSlot: ExprSlot?,
        start: BigDecimal,
        end: BigDecimal,
        step: BigDecimal,
        ctx: CalcContext,
        maxRows: Int = 200,
    ): List<TableRow> {
        if (step <= BigDecimal.ZERO) throw CalcEngineException(ErrorKind.MATH, "Step must be > 0")
        if (end < start) throw CalcEngineException(ErrorKind.MATH, "End must be >= Start")

        val fExpr = AstToEngineParser.parse(fSlot)
        val gExpr = gSlot?.let { if (it.isEmpty) null else AstToEngineParser.parse(it) }

        val rows = mutableListOf<TableRow>()
        var x = start
        var idx = 1
        while (x <= end && idx <= maxRows) {
            val localCtx = ctx.copy(variables = ctx.variables + (MathVariable.X to x))
            val fxStr = try {
                BigMath.formatDisplay(CompEvaluator.evalDecimal(fExpr, localCtx), ctx.numberFormat)
            } catch (_: Exception) {
                "ERROR"
            }
            val gxStr = gExpr?.let {
                try {
                    BigMath.formatDisplay(CompEvaluator.evalDecimal(it, localCtx), ctx.numberFormat)
                } catch (_: Exception) {
                    "ERROR"
                }
            }
            rows.add(TableRow(idx, x, fxStr, gxStr))
            x = BigMath.add(x, step)
            idx++
        }
        return rows
    }
}
