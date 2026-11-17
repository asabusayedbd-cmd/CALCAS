package com.example.engine

import com.example.ast.ExprSlot
import com.example.ast.MathFunction
import com.example.ast.MathVariable
import com.example.serializer.AstToEngineParser
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class CalcMatrix(
    val rows: Int,
    val cols: Int,
    val values: List<BigDecimal> = List(rows * cols) { BigDecimal.ZERO },
) {
    init {
        require(rows in 1..4 && cols in 1..4) { "Matrix size must be 1..4" }
        require(values.size == rows * cols)
    }

    operator fun get(r: Int, c: Int): BigDecimal = values[r * cols + c]

    fun transpose(): CalcMatrix {
        val out = MutableList(rows * cols) { BigDecimal.ZERO }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                out[c * rows + r] = this[r, c]
            }
        }
        return CalcMatrix(cols, rows, out)
    }

    operator fun plus(other: CalcMatrix): CalcMatrix {
        if (rows != other.rows || cols != other.cols) {
            throw CalcEngineException(ErrorKind.DIMENSION, "Matrix dimension mismatch")
        }
        return CalcMatrix(rows, cols, values.zip(other.values) { a, b -> BigMath.add(a, b) })
    }

    operator fun minus(other: CalcMatrix): CalcMatrix {
        if (rows != other.rows || cols != other.cols) {
            throw CalcEngineException(ErrorKind.DIMENSION, "Matrix dimension mismatch")
        }
        return CalcMatrix(rows, cols, values.zip(other.values) { a, b -> BigMath.sub(a, b) })
    }

    fun scale(s: BigDecimal): CalcMatrix =
        CalcMatrix(rows, cols, values.map { BigMath.mul(it, s) })

    operator fun times(other: CalcMatrix): CalcMatrix {
        if (cols != other.rows) {
            throw CalcEngineException(ErrorKind.DIMENSION, "Incompatible matrix dimensions for multiplication")
        }
        val out = MutableList(rows * other.cols) { BigDecimal.ZERO }
        for (r in 0 until rows) {
            for (c in 0 until other.cols) {
                var sum = BigDecimal.ZERO
                for (k in 0 until cols) {
                    sum = BigMath.add(sum, BigMath.mul(this[r, k], other[k, c]))
                }
                out[r * other.cols + c] = cleanMatrixValue(sum)
            }
        }
        return CalcMatrix(rows, other.cols, out)
    }

    fun determinant(): BigDecimal {
        if (rows != cols) throw CalcEngineException(ErrorKind.DIMENSION, "Determinant requires square matrix")
        val n = rows
        val a = Array(n) { r -> Array(n) { c -> this[r, c] } }
        var sign = 1
        val eps = BigDecimal("1E-24")
        var det = BigDecimal.ONE

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
            if (maxVal < eps) return BigDecimal.ZERO
            if (pivotRow != col) {
                val tmp = a[col]; a[col] = a[pivotRow]; a[pivotRow] = tmp
                sign = -sign
            }
            val pivot = a[col][col]
            det = BigMath.mul(det, pivot)
            for (r in col + 1 until n) {
                val factor = BigMath.div(a[r][col], pivot)
                for (c in col + 1 until n) {
                    a[r][c] = BigMath.sub(a[r][c], BigMath.mul(factor, a[col][c]))
                }
            }
        }
        val signedDet = if (sign < 0) det.negate() else det
        return cleanMatrixValue(signedDet)
    }

    fun inverse(): CalcMatrix {
        if (rows != cols) throw CalcEngineException(ErrorKind.DIMENSION, "Inverse requires square matrix")
        val n = rows
        val aug = Array(n) { r ->
            Array(2 * n) { c ->
                if (c < n) this[r, c] else if (c - n == r) BigDecimal.ONE else BigDecimal.ZERO
            }
        }
        val eps = BigDecimal("1E-24")

        for (col in 0 until n) {
            var pivotRow = col
            var maxVal = aug[col][col].abs()
            for (r in col + 1 until n) {
                val cand = aug[r][col].abs()
                if (cand > maxVal) {
                    maxVal = cand
                    pivotRow = r
                }
            }
            if (maxVal < eps) throw CalcEngineException(ErrorKind.MATH, "Singular matrix (det = 0)")
            if (pivotRow != col) {
                val tmp = aug[col]; aug[col] = aug[pivotRow]; aug[pivotRow] = tmp
            }
            val pivot = aug[col][col]
            for (c in 0 until 2 * n) {
                aug[col][c] = BigMath.div(aug[col][c], pivot)
            }
            for (r in 0 until n) {
                if (r != col) {
                    val factor = aug[r][col]
                    for (c in 0 until 2 * n) {
                        aug[r][c] = BigMath.sub(aug[r][c], BigMath.mul(factor, aug[col][c]))
                    }
                }
            }
        }
        val out = buildList(n * n) {
            for (r in 0 until n) {
                for (c in n until 2 * n) {
                    add(cleanMatrixValue(aug[r][c]))
                }
            }
        }
        return CalcMatrix(n, n, out)
    }

    private companion object {
        val MC_MATRIX_CLEAN = MathContext(24, RoundingMode.HALF_UP)

        fun cleanMatrixValue(x: BigDecimal): BigDecimal {
            val snapped = BigMath.snapZero(x).round(MC_MATRIX_CLEAN)
            return if (snapped.compareTo(BigDecimal.ZERO) == 0) BigDecimal.ZERO else snapped.stripTrailingZeros()
        }
    }
}

sealed interface MatrixEvalValue {
    data class Scalar(val value: BigDecimal) : MatrixEvalValue
    data class Mat(val matrix: CalcMatrix) : MatrixEvalValue
}

object MatrixEvaluator {

    fun evaluate(
        slot: ExprSlot,
        ctx: CalcContext,
        matrices: Map<MathVariable, CalcMatrix>,
    ): MatrixEvalValue {
        val expr = AstToEngineParser.parse(slot)
        return evalExpr(expr, ctx, matrices)
    }

    private fun evalExpr(
        expr: EngineExpr,
        ctx: CalcContext,
        matrices: Map<MathVariable, CalcMatrix>,
    ): MatrixEvalValue {
        return when (expr) {
            is EngineExpr.MatrixLit -> MatrixEvalValue.Mat(
                CalcMatrix(expr.rows, expr.cols, expr.cells.map {
                    val v = evalExpr(it, ctx, matrices)
                    (v as? MatrixEvalValue.Scalar)?.value
                        ?: throw CalcEngineException(ErrorKind.SYNTAX, "Nested matrix invalid", expr.sourceId)
                })
            )
            is EngineExpr.Var -> {
                val m = matrices[expr.variable]
                if (m != null) MatrixEvalValue.Mat(m)
                else MatrixEvalValue.Scalar(CompEvaluator.evalDecimal(expr, ctx))
            }
            is EngineExpr.UnaryMinus -> when (val v = evalExpr(expr.operand, ctx, matrices)) {
                is MatrixEvalValue.Mat -> MatrixEvalValue.Mat(v.matrix.scale(BigDecimal.ONE.negate()))
                is MatrixEvalValue.Scalar -> MatrixEvalValue.Scalar(v.value.negate())
            }
            is EngineExpr.Add -> {
                val l = evalExpr(expr.left, ctx, matrices)
                val r = evalExpr(expr.right, ctx, matrices)
                when {
                    l is MatrixEvalValue.Mat && r is MatrixEvalValue.Mat -> MatrixEvalValue.Mat(l.matrix + r.matrix)
                    l is MatrixEvalValue.Scalar && r is MatrixEvalValue.Scalar -> MatrixEvalValue.Scalar(BigMath.add(l.value, r.value))
                    else -> throw CalcEngineException(ErrorKind.DIMENSION, "Cannot add matrix and scalar", expr.sourceId)
                }
            }
            is EngineExpr.Sub -> {
                val l = evalExpr(expr.left, ctx, matrices)
                val r = evalExpr(expr.right, ctx, matrices)
                when {
                    l is MatrixEvalValue.Mat && r is MatrixEvalValue.Mat -> MatrixEvalValue.Mat(l.matrix - r.matrix)
                    l is MatrixEvalValue.Scalar && r is MatrixEvalValue.Scalar -> MatrixEvalValue.Scalar(BigMath.sub(l.value, r.value))
                    else -> throw CalcEngineException(ErrorKind.DIMENSION, "Cannot subtract matrix and scalar", expr.sourceId)
                }
            }
            is EngineExpr.Mul -> {
                val l = evalExpr(expr.left, ctx, matrices)
                val r = evalExpr(expr.right, ctx, matrices)
                when {
                    l is MatrixEvalValue.Mat && r is MatrixEvalValue.Mat -> MatrixEvalValue.Mat(l.matrix * r.matrix)
                    l is MatrixEvalValue.Scalar && r is MatrixEvalValue.Mat -> MatrixEvalValue.Mat(r.matrix.scale(l.value))
                    l is MatrixEvalValue.Mat && r is MatrixEvalValue.Scalar -> MatrixEvalValue.Mat(l.matrix.scale(r.value))
                    l is MatrixEvalValue.Scalar && r is MatrixEvalValue.Scalar -> MatrixEvalValue.Scalar(BigMath.mul(l.value, r.value))
                    else -> throw CalcEngineException(ErrorKind.SYNTAX, "Invalid matrix multiply", expr.sourceId)
                }
            }
            is EngineExpr.Pow -> {
                val b = evalExpr(expr.base, ctx, matrices)
                val e = evalExpr(expr.exp, ctx, matrices)
                if (b is MatrixEvalValue.Mat) {
                    val expVal = (e as? MatrixEvalValue.Scalar)?.value
                        ?: throw CalcEngineException(ErrorKind.DIMENSION, "Matrix exponent must be integer", expr.sourceId)
                    val expInt = try {
                        expVal.stripTrailingZeros().toBigIntegerExact().toInt()
                    } catch (_: Exception) {
                        throw CalcEngineException(ErrorKind.MATH, "Matrix exponent must be integer", expr.sourceId)
                    }
                    if (expInt == -1) return MatrixEvalValue.Mat(b.matrix.inverse())
                    if (expInt < 1 || expInt > 20) throw CalcEngineException(ErrorKind.MATH, "Matrix power range 1..20 or -1", expr.sourceId)
                    var acc = b.matrix
                    repeat(expInt - 1) { acc *= b.matrix }
                    MatrixEvalValue.Mat(acc)
                } else {
                    val s1 = (b as MatrixEvalValue.Scalar).value
                    val s2 = (e as? MatrixEvalValue.Scalar)?.value
                        ?: throw CalcEngineException(ErrorKind.DIMENSION, "Scalar cannot be raised to matrix", expr.sourceId)
                    MatrixEvalValue.Scalar(BigMath.pow(s1, s2))
                }
            }
            is EngineExpr.Call -> {
                val arg = evalExpr(expr.arg, ctx, matrices)
                when (expr.function) {
                    MathFunction.DET -> {
                        val m = (arg as? MatrixEvalValue.Mat)?.matrix
                            ?: throw CalcEngineException(ErrorKind.SYNTAX, "Det requires matrix", expr.sourceId)
                        MatrixEvalValue.Scalar(m.determinant())
                    }
                    MathFunction.TRN -> {
                        val m = (arg as? MatrixEvalValue.Mat)?.matrix
                            ?: throw CalcEngineException(ErrorKind.SYNTAX, "Trn requires matrix", expr.sourceId)
                        MatrixEvalValue.Mat(m.transpose())
                    }
                    else -> {
                        val s = (arg as? MatrixEvalValue.Scalar)?.value
                            ?: throw CalcEngineException(ErrorKind.SYNTAX, "Function requires scalar", expr.sourceId)
                        MatrixEvalValue.Scalar(CompEvaluator.evalDecimal(expr, ctx))
                    }
                }
            }
            else -> MatrixEvalValue.Scalar(CompEvaluator.evalDecimal(expr, ctx))
        }
    }
}

data class CalcVector(val components: List<BigDecimal>) {
    init {
        require(components.size in 2..3) { "Vector must be 2D or 3D" }
    }

    val dim: Int get() = components.size

    operator fun plus(other: CalcVector): CalcVector {
        if (dim != other.dim) throw CalcEngineException(ErrorKind.DIMENSION, "Vector dimension mismatch")
        return CalcVector(components.zip(other.components) { a, b -> BigMath.add(a, b) })
    }

    operator fun minus(other: CalcVector): CalcVector {
        if (dim != other.dim) throw CalcEngineException(ErrorKind.DIMENSION, "Vector dimension mismatch")
        return CalcVector(components.zip(other.components) { a, b -> BigMath.sub(a, b) })
    }

    fun scale(s: BigDecimal): CalcVector = CalcVector(components.map { BigMath.mul(it, s) })

    fun dot(other: CalcVector): BigDecimal {
        if (dim != other.dim) throw CalcEngineException(ErrorKind.DIMENSION, "Vector dimension mismatch")
        return components.zip(other.components) { a, b -> BigMath.mul(a, b) }
            .fold(BigDecimal.ZERO) { acc, v -> BigMath.add(acc, v) }
    }

    fun cross(other: CalcVector): CalcVector {
        val a = if (dim == 2) components + BigDecimal.ZERO else components
        val b = if (other.dim == 2) other.components + BigDecimal.ZERO else other.components
        return CalcVector(
            listOf(
                BigMath.sub(BigMath.mul(a[1], b[2]), BigMath.mul(a[2], b[1])),
                BigMath.sub(BigMath.mul(a[2], b[0]), BigMath.mul(a[0], b[2])),
                BigMath.sub(BigMath.mul(a[0], b[1]), BigMath.mul(a[1], b[0])),
            )
        )
    }

    fun magnitude(): BigDecimal = BigMath.sqrt(dot(this))

    fun angleBetween(other: CalcVector, unit: AngleUnit): BigDecimal {
        val magProd = BigMath.mul(magnitude(), other.magnitude())
        if (magProd.compareTo(BigDecimal.ZERO) == 0) {
            throw CalcEngineException(ErrorKind.MATH, "Angle undefined for zero vector")
        }
        val cosTheta = BigMath.div(dot(other), magProd).coerceIn(BigDecimal("-1"), BigDecimal.ONE)
        return BigMath.acos(cosTheta, unit)
    }
}
