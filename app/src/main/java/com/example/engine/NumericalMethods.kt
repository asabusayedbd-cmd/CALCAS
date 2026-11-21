package com.example.engine

import com.example.ast.SumProdKind
import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.abs

/**
 * Numerical methods for `COMP` mode:
 * - Definite integral: Adaptive 15-point Gauss-Kronrod quadrature (G7-K15)
 * - Numerical derivative: Ridders' extrapolated central difference
 * - Summation / Product: Iterative arbitrary-precision accumulation
 * - SOLVE (`f(x) = 0`): Newton-Raphson + Brent's method fallback with L-R residual
 */
object NumericalMethods {

    // 15-point Gauss-Kronrod abscissas on [-1, 1] (symmetric: 0 and 7 positive nodes)
    private val GK_X = doubleArrayOf(
        0.0,
        0.20778495500789847,
        0.40584515137739717,
        0.5860872354676911,
        0.7415311855993945,
        0.8648644233597691,
        0.9491079123427585,
        0.9914553711208126,
    )

    // 15-point Kronrod weights matching GK_X
    private val GK_W15 = doubleArrayOf(
        0.20948214108472782,
        0.20443294007529889,
        0.19035057806478541,
        0.1690047266392679,
        0.14065325971552592,
        0.10479001032225018,
        0.06309209262997856,
        0.022935322010529224,
    )

    // 7-point Gauss weights at odd indices (1, 3, 5, 7) and 0
    private val GK_W7 = doubleArrayOf(
        0.4179591836734694,
        0.0,
        0.3818300505051189,
        0.0,
        0.27970539148927664,
        0.0,
        0.1294849661688697,
        0.0,
    )

    fun integrateGaussKronrod(
        f: (BigDecimal) -> BigDecimal,
        lower: BigDecimal,
        upper: BigDecimal,
        tol: BigDecimal = BigDecimal("1E-11"),
    ): BigDecimal {
        if (lower.compareTo(upper) == 0) return BigDecimal.ZERO
        val reversed = lower > upper
        val a = if (reversed) upper else lower
        val b = if (reversed) lower else upper
        val result = gkRecursive(f, a, b, tol, depth = 0, maxDepth = 14)
        return BigMath.snapZero(if (reversed) result.negate() else result)
    }

    private fun gkRecursive(
        f: (BigDecimal) -> BigDecimal,
        a: BigDecimal,
        b: BigDecimal,
        tol: BigDecimal,
        depth: Int,
        maxDepth: Int,
    ): BigDecimal {
        val mid = a.add(b, BigMath.MC_INTERNAL).divide(BigMath.TWO, BigMath.MC_INTERNAL)
        val halfLen = b.subtract(a, BigMath.MC_INTERNAL).divide(BigMath.TWO, BigMath.MC_INTERNAL)

        val fCenter = f(mid)
        var sum15 = fCenter.multiply(BigDecimal.valueOf(GK_W15[0]), BigMath.MC_INTERNAL)
        var sum7 = fCenter.multiply(BigDecimal.valueOf(GK_W7[0]), BigMath.MC_INTERNAL)

        for (i in 1 until GK_X.size) {
            val offset = halfLen.multiply(BigDecimal.valueOf(GK_X[i]), BigMath.MC_INTERNAL)
            val fSum = f(mid.subtract(offset, BigMath.MC_INTERNAL))
                .add(f(mid.add(offset, BigMath.MC_INTERNAL)), BigMath.MC_INTERNAL)
            sum15 = sum15.add(fSum.multiply(BigDecimal.valueOf(GK_W15[i]), BigMath.MC_INTERNAL), BigMath.MC_INTERNAL)
            if (GK_W7[i] != 0.0) {
                sum7 = sum7.add(fSum.multiply(BigDecimal.valueOf(GK_W7[i]), BigMath.MC_INTERNAL), BigMath.MC_INTERNAL)
            }
        }

        val res15 = sum15.multiply(halfLen, BigMath.MC_INTERNAL)
        val res7 = sum7.multiply(halfLen, BigMath.MC_INTERNAL)
        val err = res15.subtract(res7).abs()
        val scaleTol = tol.max(tol.multiply(res15.abs(), BigMath.MC_INTERNAL))

        if (err <= scaleTol || depth >= maxDepth) {
            return res15
        }

        val halfTol = tol.divide(BigMath.TWO, BigMath.MC_INTERNAL)
        val left = gkRecursive(f, a, mid, halfTol, depth + 1, maxDepth)
        val right = gkRecursive(f, mid, b, halfTol, depth + 1, maxDepth)
        return left.add(right, BigMath.MC_INTERNAL)
    }

    /**
     * Ridders' method for high-accuracy numerical derivative at [x].
     */
    fun differentiateRidders(
        f: (BigDecimal) -> BigDecimal,
        x: BigDecimal,
    ): BigDecimal {
        val con = BigDecimal("1.4")
        val con2 = con.multiply(con, BigMath.MC_INTERNAL)
        val safe = BigDecimal("2.0")
        val nTab = 10
        val table = Array(nTab) { arrayOfNulls<BigDecimal>(nTab) }

        var hh = BigDecimal("1E-4").max(x.abs().multiply(BigDecimal("1E-5"), BigMath.MC_INTERNAL))
        table[0][0] = centralDiff(f, x, hh)
        var err = BigDecimal("1E30")
        var ans = table[0][0]!!

        for (i in 1 until nTab) {
            hh = hh.divide(con, BigMath.MC_INTERNAL)
            table[0][i] = centralDiff(f, x, hh)
            var fac = con2
            for (j in 1..i) {
                val t1 = table[j - 1][i]!!
                val t0 = table[j - 1][i - 1]!!
                table[j][i] = t1.multiply(fac, BigMath.MC_INTERNAL)
                    .subtract(t0, BigMath.MC_INTERNAL)
                    .divide(fac.subtract(BigDecimal.ONE, BigMath.MC_INTERNAL), BigMath.MC_INTERNAL)
                fac = fac.multiply(con2, BigMath.MC_INTERNAL)

                val errT = table[j][i]!!.subtract(t1).abs()
                    .max(table[j][i]!!.subtract(t0).abs())
                if (errT <= err) {
                    err = errT
                    ans = table[j][i]!!
                }
            }
            if (table[i][i]!!.subtract(table[i - 1][i - 1]!!).abs() >= safe.multiply(err, BigMath.MC_INTERNAL)) {
                break
            }
        }
        return BigMath.snapZero(ans)
    }

    private fun centralDiff(f: (BigDecimal) -> BigDecimal, x: BigDecimal, h: BigDecimal): BigDecimal {
        val fp = f(x.add(h, BigMath.MC_INTERNAL))
        val fm = f(x.subtract(h, BigMath.MC_INTERNAL))
        return fp.subtract(fm, BigMath.MC_INTERNAL)
            .divide(h.multiply(BigMath.TWO, BigMath.MC_INTERNAL), BigMath.MC_INTERNAL)
    }

    fun sumOrProd(
        kind: SumProdKind,
        f: (BigDecimal) -> BigDecimal,
        lower: BigDecimal,
        upper: BigDecimal,
    ): BigDecimal {
        val lo = try {
            lower.stripTrailingZeros().toBigIntegerExact()
        } catch (_: ArithmeticException) {
            throw CalcEngineException(ErrorKind.MATH, "Bounds must be integers")
        }
        val hi = try {
            upper.stripTrailingZeros().toBigIntegerExact()
        } catch (_: ArithmeticException) {
            throw CalcEngineException(ErrorKind.MATH, "Bounds must be integers")
        }
        if (lo > hi) throw CalcEngineException(ErrorKind.MATH, "Lower bound exceeds upper bound")
        if (hi - lo > BigInteger.valueOf(100_000L)) {
            throw CalcEngineException(ErrorKind.MATH, "Iteration limit exceeded (max 100,000)")
        }

        var acc = if (kind == SumProdKind.SUM) BigDecimal.ZERO else BigDecimal.ONE
        var i = lo
        while (i <= hi) {
            val v = f(BigDecimal(i))
            acc = if (kind == SumProdKind.SUM) {
                BigMath.add(acc, v)
            } else {
                BigMath.mul(acc, v)
            }
            i = i.add(BigInteger.ONE)
        }
        return BigMath.snapZero(acc)
    }

    data class SolveResult(
        val root: BigDecimal,
        val residualLR: BigDecimal,
    )

    /**
     * Solves `f(x) = 0` starting from [initialGuess] using damped Newton-Raphson with
     * automatic bracketing + Brent's method fallback.
     */
    fun solve(
        f: (BigDecimal) -> BigDecimal,
        initialGuess: BigDecimal,
    ): SolveResult {
        val tol = BigDecimal("1E-13")

        // Phase 1: Damped Newton-Raphson
        var x = initialGuess
        var fx = tryEval(f, x)
        if (fx != null && fx.abs() <= tol) {
            return SolveResult(BigMath.snapZero(x), BigMath.snapZero(fx))
        }

        if (fx != null) {
            for (iter in 0 until 60) {
                val dfx = try {
                    differentiateRidders(f, x)
                } catch (_: Exception) {
                    null
                }
                if (dfx == null || dfx.abs() < BigDecimal("1E-22")) break

                var step = fx!!.divide(dfx, BigMath.MC_INTERNAL)
                // Cap huge jumps and backtrack if residual increases
                var lambda = BigDecimal.ONE
                var accepted = false
                for (bt in 0 until 8) {
                    val cand = x.subtract(step.multiply(lambda, BigMath.MC_INTERNAL), BigMath.MC_INTERNAL)
                    val fCand = tryEval(f, cand)
                    if (fCand != null && (fCand.abs() < fx!!.abs() || bt == 7)) {
                        x = cand
                        fx = fCand
                        accepted = true
                        break
                    }
                    lambda = lambda.divide(BigMath.TWO, BigMath.MC_INTERNAL)
                }
                if (!accepted) break
                if (fx!!.abs() <= tol) {
                    return SolveResult(BigMath.snapZero(x), BigMath.snapZero(fx!!))
                }
            }
        }

        // Phase 2: Bracket search around initialGuess -> Brent's method
        val bracket = findSignChangeBracket(f, initialGuess)
        if (bracket != null) {
            val root = brentSolve(f, bracket.first, bracket.second, tol)
            val res = f(root)
            return SolveResult(BigMath.snapZero(root), BigMath.snapZero(res))
        }

        // If Newton got very close (< 1e-8), accept it
        if (fx != null && fx.abs() < BigDecimal("1E-8")) {
            return SolveResult(BigMath.snapZero(x), BigMath.snapZero(fx))
        }

        throw CalcEngineException(ErrorKind.MATH, "Can't Solve")
    }

    private fun tryEval(f: (BigDecimal) -> BigDecimal, x: BigDecimal): BigDecimal? = try {
        f(x)
    } catch (_: Exception) {
        null
    }

    private fun findSignChangeBracket(
        f: (BigDecimal) -> BigDecimal,
        center: BigDecimal,
    ): Pair<BigDecimal, BigDecimal>? {
        val offsets = listOf(
            "0.01", "0.1", "0.5", "1", "2", "5", "10", "25", "50", "100", "500", "1000"
        )
        for (offStr in offsets) {
            val off = BigDecimal(offStr)
            val a = center.subtract(off, BigMath.MC_INTERNAL)
            val b = center.add(off, BigMath.MC_INTERNAL)
            val fa = tryEval(f, a)
            val fb = tryEval(f, b)
            if (fa != null && fb != null) {
                if (fa.signum() == 0) return a to a
                if (fb.signum() == 0) return b to b
                if (fa.signum() != fb.signum()) return a to b
            }
            val fc = tryEval(f, center)
            if (fc != null && fa != null && fa.signum() != fc.signum()) return a to center
            if (fc != null && fb != null && fc.signum() != fb.signum()) return center to b
        }
        return null
    }

    private fun brentSolve(
        f: (BigDecimal) -> BigDecimal,
        a0: BigDecimal,
        b0: BigDecimal,
        tol: BigDecimal,
    ): BigDecimal {
        if (a0.compareTo(b0) == 0) return a0
        var a = a0.toDouble()
        var b = b0.toDouble()
        var fa = f(BigDecimal.valueOf(a)).toDouble()
        var fb = f(BigDecimal.valueOf(b)).toDouble()
        var c = a
        var fc = fa
        var d = b - a
        var e = d

        for (iter in 0 until 100) {
            if ((fb > 0 && fc > 0) || (fb < 0 && fc < 0)) {
                c = a
                fc = fa
                d = b - a
                e = d
            }
            if (abs(fc) < abs(fb)) {
                a = b; b = c; c = a
                fa = fb; fb = fc; fc = fa
            }
            val tol1 = 2.0 * 1e-15 * abs(b) + 0.5 * tol.toDouble()
            val xm = 0.5 * (c - b)
            if (abs(xm) <= tol1 || fb == 0.0) {
                return BigDecimal(b, MathContext(15, RoundingMode.HALF_UP))
            }
            if (abs(e) >= tol1 && abs(fa) > abs(fb)) {
                val s = fb / fa
                var p: Double
                var q: Double
                if (a == c) {
                    p = 2.0 * xm * s
                    q = 1.0 - s
                } else {
                    q = fa / fc
                    val r = fb / fc
                    p = s * (2.0 * xm * q * (q - r) - (b - a) * (r - 1.0))
                    q = (q - 1.0) * (r - 1.0) * (s - 1.0)
                }
                if (p > 0) q = -q
                p = abs(p)
                val min1 = 3.0 * xm * q - abs(tol1 * q)
                val min2 = abs(e * q)
                if (2.0 * p < minOf(min1, min2)) {
                    e = d
                    d = p / q
                } else {
                    d = xm
                    e = d
                }
            } else {
                d = xm
                e = d
            }
            a = b
            fa = fb
            b += if (abs(d) > tol1) d else if (xm >= 0) tol1 else -tol1
            fb = f(BigDecimal.valueOf(b)).toDouble()
        }
        return BigDecimal(b, MathContext(15, RoundingMode.HALF_UP))
    }
}
