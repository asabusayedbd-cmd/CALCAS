package com.example.engine

import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.cbrt
import kotlin.math.sqrt

sealed interface EqnSolution {
    data class LinearUnique(val values: List<BigDecimal>) : EqnSolution
    data class PolynomialRoots(val roots: List<ComplexVal>) : EqnSolution
    data class Special(val message: String) : EqnSolution
}

/**
 * Solves:
 * 1. Simultaneous linear equations (2x2 and 3x3) via Gaussian elimination with partial pivoting
 * 2. Quadratic equations: ax^2 + bx + c = 0 (real and complex roots)
 * 3. Cubic equations: ax^3 + bx^2 + cx + d = 0 (real and complex roots)
 */
object EqnSolver {

    fun solveLinear2x2(
        a1: BigDecimal, b1: BigDecimal, c1: BigDecimal,
        a2: BigDecimal, b2: BigDecimal, c2: BigDecimal,
    ): EqnSolution = solveLinearSystem(
        arrayOf(
            arrayOf(a1, b1),
            arrayOf(a2, b2),
        ),
        arrayOf(c1, c2),
    )

    fun solveLinear3x3(
        matrix: Array<Array<BigDecimal>>,
        rhs: Array<BigDecimal>,
    ): EqnSolution = solveLinearSystem(matrix, rhs)

    fun solveLinearSystem(
        aIn: Array<Array<BigDecimal>>,
        bIn: Array<BigDecimal>,
    ): EqnSolution {
        val n = bIn.size
        val a = Array(n) { r -> Array(n) { c -> aIn[r][c] } }
        val b = Array(n) { r -> bIn[r] }
        val eps = BigDecimal("1E-24")

        for (col in 0 until n) {
            var pivotRow = col
            var maxVal = a[col][col].abs()
            for (r in col + 1 until n) {
                val cand = a[r][col].abs()
                if (cand > maxVal) {
                    maxVal = cand
                    pivotRow = r
                }
            }
            if (maxVal < eps) {
                // Check consistency
                return EqnSolution.Special("No / Infinite Solutions")
            }
            if (pivotRow != col) {
                val tmpRow = a[col]; a[col] = a[pivotRow]; a[pivotRow] = tmpRow
                val tmpB = b[col]; b[col] = b[pivotRow]; b[pivotRow] = tmpB
            }
            val pivot = a[col][col]
            for (r in col + 1 until n) {
                val factor = BigMath.div(a[r][col], pivot)
                for (c in col until n) {
                    a[r][c] = BigMath.sub(a[r][c], BigMath.mul(factor, a[col][c]))
                }
                b[r] = BigMath.sub(b[r], BigMath.mul(factor, b[col]))
            }
        }

        val x = Array(n) { BigDecimal.ZERO }
        for (r in n - 1 downTo 0) {
            var sum = b[r]
            for (c in r + 1 until n) {
                sum = BigMath.sub(sum, BigMath.mul(a[r][c], x[c]))
            }
            if (a[r][r].abs() < eps) {
                return EqnSolution.Special("No / Infinite Solutions")
            }
            x[r] = BigMath.snapZero(BigMath.div(sum, a[r][r]))
        }
        return EqnSolution.LinearUnique(x.toList())
    }

    fun solveQuadratic(a: BigDecimal, b: BigDecimal, c: BigDecimal): EqnSolution {
        if (a.compareTo(BigDecimal.ZERO) == 0) {
            if (b.compareTo(BigDecimal.ZERO) == 0) {
                return EqnSolution.Special(if (c.compareTo(BigDecimal.ZERO) == 0) "Infinite Solutions" else "No Solution")
            }
            val x = BigMath.snapZero(BigMath.div(c.negate(), b))
            return EqnSolution.PolynomialRoots(listOf(ComplexVal(x, BigDecimal.ZERO)))
        }

        val twoA = BigMath.mul(BigMath.TWO, a)
        val disc = BigMath.sub(BigMath.mul(b, b), BigMath.mul(BigDecimal("4"), BigMath.mul(a, c)))
        return if (disc >= BigDecimal.ZERO) {
            val sqrtD = BigMath.sqrt(disc)
            val x1 = BigMath.snapZero(BigMath.div(BigMath.add(b.negate(), sqrtD), twoA))
            val x2 = BigMath.snapZero(BigMath.div(BigMath.sub(b.negate(), sqrtD), twoA))
            EqnSolution.PolynomialRoots(
                listOf(
                    ComplexVal(x1, BigDecimal.ZERO),
                    ComplexVal(x2, BigDecimal.ZERO),
                )
            )
        } else {
            val sqrtAbsD = BigMath.sqrt(disc.negate())
            val re = BigMath.snapZero(BigMath.div(b.negate(), twoA))
            val im = BigMath.snapZero(BigMath.div(sqrtAbsD, twoA).abs())
            EqnSolution.PolynomialRoots(
                listOf(
                    ComplexVal(re, im),
                    ComplexVal(re, im.negate()),
                )
            )
        }
    }

    fun solveCubic(aBd: BigDecimal, bBd: BigDecimal, cBd: BigDecimal, dBd: BigDecimal): EqnSolution {
        if (aBd.compareTo(BigDecimal.ZERO) == 0) {
            return solveQuadratic(bBd, cBd, dBd)
        }
        val a = aBd.toDouble()
        val b = bBd.toDouble() / a
        val c = cBd.toDouble() / a
        val d = dBd.toDouble() / a

        // Depressed cubic t^3 + p*t + q = 0 with x = t - b/3
        val p = c - b * b / 3.0
        val q = (2.0 * b * b * b) / 27.0 - (b * c) / 3.0 + d
        val disc = (q * q) / 4.0 + (p * p * p) / 27.0
        val shift = -b / 3.0

        val roots = if (disc > 1e-13) {
            val sqrtDisc = sqrt(disc)
            val u = cbrt(-q / 2.0 + sqrtDisc)
            val v = cbrt(-q / 2.0 - sqrtDisc)
            val x1 = u + v + shift
            val realPart = -0.5 * (u + v) + shift
            val imagPart = (sqrt(3.0) / 2.0) * kotlin.math.abs(u - v)
            listOf(
                ComplexVal(BigDecimal.valueOf(x1), BigDecimal.ZERO),
                ComplexVal(BigDecimal.valueOf(realPart), BigDecimal.valueOf(imagPart)),
                ComplexVal(BigDecimal.valueOf(realPart), BigDecimal.valueOf(-imagPart)),
            )
        } else if (kotlin.math.abs(disc) <= 1e-13) {
            if (kotlin.math.abs(p) <= 1e-13) {
                val x = BigDecimal.valueOf(shift)
                listOf(ComplexVal(x, BigDecimal.ZERO), ComplexVal(x, BigDecimal.ZERO), ComplexVal(x, BigDecimal.ZERO))
            } else {
                val u = cbrt(-q / 2.0)
                val x1 = 2.0 * u + shift
                val x2 = -u + shift
                listOf(
                    ComplexVal(BigDecimal.valueOf(x1), BigDecimal.ZERO),
                    ComplexVal(BigDecimal.valueOf(x2), BigDecimal.ZERO),
                    ComplexVal(BigDecimal.valueOf(x2), BigDecimal.ZERO),
                )
            }
        } else {
            val r = sqrt(-(p * p * p) / 27.0)
            val phi = acos((-q / (2.0 * r)).coerceIn(-1.0, 1.0))
            val m = 2.0 * sqrt(-p / 3.0)
            val x1 = m * cos(phi / 3.0) + shift
            val x2 = m * cos((phi + 2.0 * PI) / 3.0) + shift
            val x3 = m * cos((phi + 4.0 * PI) / 3.0) + shift
            listOf(
                ComplexVal(BigDecimal.valueOf(x1), BigDecimal.ZERO),
                ComplexVal(BigDecimal.valueOf(x2), BigDecimal.ZERO),
                ComplexVal(BigDecimal.valueOf(x3), BigDecimal.ZERO),
            )
        }
        return EqnSolution.PolynomialRoots(roots)
    }
}
