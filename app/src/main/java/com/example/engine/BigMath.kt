package com.example.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

enum class AngleUnit(val shortLabel: String) {
    DEG("D"),
    RAD("R"),
    GRAD("G"),
}

sealed interface NumberFormatMode {
    val label: String

    data object Norm1 : NumberFormatMode {
        override val label: String = "Norm1"
    }

    data object Norm2 : NumberFormatMode {
        override val label: String = "Norm2"
    }

    data class Fix(val digits: Int) : NumberFormatMode {
        override val label: String = "Fix$digits"
    }

    data class Sci(val digits: Int) : NumberFormatMode {
        override val label: String = "Sci$digits"
    }
}

/**
 * Arbitrary-precision decimal math engine (34–40 internal guard digits, zero raw Double artifacts
 * for core arithmetic, trig, logs, roots, and factorials).
 */
object BigMath {

    val MC_DISPLAY: MathContext = MathContext(15, RoundingMode.HALF_UP)
    val MC_INTERNAL: MathContext = MathContext(34, RoundingMode.HALF_UP)
    val MC_WORK: MathContext = MathContext(44, RoundingMode.HALF_UP)

    val ZERO: BigDecimal = BigDecimal.ZERO
    val ONE: BigDecimal = BigDecimal.ONE
    val TWO: BigDecimal = BigDecimal.valueOf(2)
    val TEN: BigDecimal = BigDecimal.TEN

    val PI: BigDecimal = BigDecimal("3.1415926535897932384626433832795028841971693993751")
    val TWO_PI: BigDecimal = PI.multiply(TWO, MC_WORK)
    val HALF_PI: BigDecimal = PI.divide(TWO, MC_WORK)
    val E: BigDecimal = BigDecimal("2.7182818284590452353602874713526624977572470936999")
    val LN2: BigDecimal = BigDecimal("0.6931471805599453094172321214581765680755001343602")
    val LN10: BigDecimal = BigDecimal("2.3025850929940456840179914546843642076011014886287")

    private val EPSILON_ZERO = BigDecimal("1E-26")
    private val DEG_TO_RAD = PI.divide(BigDecimal("180"), MC_WORK)
    private val GRAD_TO_RAD = PI.divide(BigDecimal("200"), MC_WORK)
    private val RAD_TO_DEG = BigDecimal("180").divide(PI, MC_WORK)
    private val RAD_TO_GRAD = BigDecimal("200").divide(PI, MC_WORK)

    fun snapZero(x: BigDecimal): BigDecimal =
        if (x.abs() < EPSILON_ZERO) ZERO else x

    fun add(a: BigDecimal, b: BigDecimal): BigDecimal = a.add(b, MC_INTERNAL)
    fun sub(a: BigDecimal, b: BigDecimal): BigDecimal = a.subtract(b, MC_INTERNAL)
    fun mul(a: BigDecimal, b: BigDecimal): BigDecimal = a.multiply(b, MC_INTERNAL)

    fun div(a: BigDecimal, b: BigDecimal): BigDecimal {
        if (b.compareTo(ZERO) == 0) {
            throw CalcEngineException(ErrorKind.MATH, "Division by zero")
        }
        return a.divide(b, MC_INTERNAL)
    }

    fun sqrt(x: BigDecimal): BigDecimal {
        val cmp = x.compareTo(ZERO)
        if (cmp < 0) throw CalcEngineException(ErrorKind.MATH, "Square root of negative number")
        if (cmp == 0) return ZERO
        // Newton-Raphson with 44 working digits starting from double estimate
        val d = x.toDouble()
        var guess = if (d.isFinite() && d > 0.0) {
            BigDecimal(kotlin.math.sqrt(d), MC_WORK)
        } else {
            BigDecimal(BigInteger.ONE.shiftLeft(x.toBigInteger().bitLength() / 2), MC_WORK).max(ONE)
        }
        repeat(10) {
            val next = guess.add(x.divide(guess, MC_WORK), MC_WORK).divide(TWO, MC_WORK)
            if (next.subtract(guess).abs() < BigDecimal("1E-40")) {
                return next.round(MC_INTERNAL)
            }
            guess = next
        }
        return guess.round(MC_INTERNAL)
    }

    fun nthRoot(n: BigDecimal, x: BigDecimal): BigDecimal {
        if (n.compareTo(ZERO) == 0) throw CalcEngineException(ErrorKind.MATH, "0-th root undefined")
        val nInt = try {
            n.stripTrailingZeros().toBigIntegerExact()
        } catch (_: ArithmeticException) {
            null
        }
        if (x.compareTo(ZERO) < 0) {
            if (nInt != null && nInt.abs().mod(BigInteger.TWO) == BigInteger.ONE) {
                val absRoot = pow(x.abs(), div(ONE, n))
                return absRoot.negate()
            }
            throw CalcEngineException(ErrorKind.MATH, "Even root of negative number")
        }
        if (x.compareTo(ZERO) == 0) {
            if (n.compareTo(ZERO) < 0) throw CalcEngineException(ErrorKind.MATH, "Division by zero")
            return ZERO
        }
        return pow(x, div(ONE, n))
    }

    fun pow(base: BigDecimal, exp: BigDecimal): BigDecimal {
        if (exp.compareTo(ZERO) == 0) return ONE
        if (base.compareTo(ZERO) == 0) {
            if (exp.compareTo(ZERO) < 0) throw CalcEngineException(ErrorKind.MATH, "0 raised to negative power")
            return ZERO
        }

        // Exact integer exponent path
        val expInt = try {
            exp.stripTrailingZeros().toBigIntegerExact()
        } catch (_: ArithmeticException) {
            null
        }
        if (expInt != null) {
            if (expInt.abs() > BigInteger.valueOf(1_000_000L)) {
                throw CalcEngineException(ErrorKind.MATH, "Exponent overflow")
            }
            val n = expInt.toInt()
            return if (n >= 0) {
                base.pow(n, MC_INTERNAL)
            } else {
                ONE.divide(base.pow(-n, MC_WORK), MC_INTERNAL)
            }
        }

        // Handle rational exponents p/q for negative bases if q is odd (e.g. (-8)^(1/3) = -2)
        if (base.compareTo(ZERO) < 0) {
            val frac = decimalToRationalOrNull(exp, maxDen = 1000L)
            if (frac != null && frac.second.mod(BigInteger.TWO) == BigInteger.ONE) {
                val mag = exp(mul(ln(base.abs()), exp))
                return if (frac.first.abs().mod(BigInteger.TWO) == BigInteger.ONE) mag.negate() else mag
            }
            throw CalcEngineException(ErrorKind.MATH, "Negative base with non-integer exponent")
        }

        return exp(exp.multiply(ln(base), MC_WORK)).round(MC_INTERNAL)
    }

    fun exp(x: BigDecimal): BigDecimal {
        if (x.compareTo(ZERO) == 0) return ONE
        if (x.abs() > BigDecimal("2302")) {
            throw CalcEngineException(ErrorKind.MATH, "Exponential overflow")
        }
        // Range reduction: x = k * ln(2) + r, |r| <= ln(2)/2
        val k = x.divideToIntegralValue(LN2).toInt()
        val r = x.subtract(LN2.multiply(BigDecimal.valueOf(k.toLong()), MC_WORK), MC_WORK)

        // Taylor series for exp(r)
        var sum = ONE
        var term = ONE
        var n = 1
        while (term.abs() > BigDecimal("1E-42") && n < 200) {
            term = term.multiply(r, MC_WORK).divide(BigDecimal.valueOf(n.toLong()), MC_WORK)
            sum = sum.add(term, MC_WORK)
            n++
        }

        val scale = if (k >= 0) {
            TWO.pow(k, MC_WORK)
        } else {
            ONE.divide(TWO.pow(-k, MC_WORK), MC_WORK)
        }
        return sum.multiply(scale, MC_WORK).round(MC_INTERNAL)
    }

    fun ln(x: BigDecimal): BigDecimal {
        if (x.compareTo(ZERO) <= 0) throw CalcEngineException(ErrorKind.MATH, "Logarithm domain error")
        if (x.compareTo(ONE) == 0) return ZERO

        // Reduce x = m * 2^k with m in [0.707, 1.414]
        var m = x.round(MC_WORK)
        var k = 0
        val upper = BigDecimal("1.414213562373")
        val lower = BigDecimal("0.707106781186")
        while (m > upper) {
            m = m.divide(TWO, MC_WORK)
            k++
        }
        while (m < lower) {
            m = m.multiply(TWO, MC_WORK)
            k--
        }

        // ln(m) = 2 * sum_{j=0..inf} y^(2j+1)/(2j+1), y = (m-1)/(m+1)
        val y = m.subtract(ONE, MC_WORK).divide(m.add(ONE, MC_WORK), MC_WORK)
        val y2 = y.multiply(y, MC_WORK)
        var term = y
        var sum = y
        var j = 1
        while (term.abs() > BigDecimal("1E-42") && j < 200) {
            term = term.multiply(y2, MC_WORK)
            val contrib = term.divide(BigDecimal.valueOf((2 * j + 1).toLong()), MC_WORK)
            sum = sum.add(contrib, MC_WORK)
            j++
        }
        val lnM = sum.multiply(TWO, MC_WORK)
        return lnM.add(LN2.multiply(BigDecimal.valueOf(k.toLong()), MC_WORK), MC_WORK).round(MC_INTERNAL)
    }

    fun log10(x: BigDecimal): BigDecimal {
        if (x.compareTo(ZERO) <= 0) throw CalcEngineException(ErrorKind.MATH, "Logarithm domain error")
        // Fast exact check for integer powers of 10
        val norm = x.stripTrailingZeros()
        if (norm.unscaledValue() == BigInteger.ONE) {
            return BigDecimal.valueOf((-norm.scale()).toLong())
        }
        return ln(x).divide(LN10, MC_INTERNAL)
    }

    fun logBase(base: BigDecimal, arg: BigDecimal): BigDecimal {
        if (base.compareTo(ZERO) <= 0 || base.compareTo(ONE) == 0 || arg.compareTo(ZERO) <= 0) {
            throw CalcEngineException(ErrorKind.MATH, "Logarithm base/domain error")
        }
        return ln(arg).divide(ln(base), MC_INTERNAL)
    }

    // -------------------------------------------------------------------------
    // Trigonometry (exact quadrant reduction for DEG/RAD/GRAD)
    // -------------------------------------------------------------------------

    fun toRadians(angle: BigDecimal, unit: AngleUnit): BigDecimal = when (unit) {
        AngleUnit.RAD -> angle.round(MC_WORK)
        AngleUnit.DEG -> angle.multiply(DEG_TO_RAD, MC_WORK)
        AngleUnit.GRAD -> angle.multiply(GRAD_TO_RAD, MC_WORK)
    }

    fun fromRadians(rad: BigDecimal, unit: AngleUnit): BigDecimal = when (unit) {
        AngleUnit.RAD -> rad.round(MC_INTERNAL)
        AngleUnit.DEG -> rad.multiply(RAD_TO_DEG, MC_WORK).round(MC_INTERNAL)
        AngleUnit.GRAD -> rad.multiply(RAD_TO_GRAD, MC_WORK).round(MC_INTERNAL)
    }

    fun sin(angle: BigDecimal, unit: AngleUnit): BigDecimal {
        if (unit == AngleUnit.DEG) {
            val deg = modPositive(angle, BigDecimal("360"))
            exactTrigDeg(deg)?.first?.let { return it }
            return sinRad(toRadians(deg, AngleUnit.DEG))
        }
        if (unit == AngleUnit.GRAD) {
            val grad = modPositive(angle, BigDecimal("400"))
            val deg = grad.multiply(BigDecimal("0.9"), MC_WORK)
            exactTrigDeg(deg)?.first?.let { return it }
            return sinRad(toRadians(grad, AngleUnit.GRAD))
        }
        return sinRad(angle)
    }

    fun cos(angle: BigDecimal, unit: AngleUnit): BigDecimal {
        if (unit == AngleUnit.DEG) {
            val deg = modPositive(angle, BigDecimal("360"))
            exactTrigDeg(deg)?.second?.let { return it }
            return cosRad(toRadians(deg, AngleUnit.DEG))
        }
        if (unit == AngleUnit.GRAD) {
            val grad = modPositive(angle, BigDecimal("400"))
            val deg = grad.multiply(BigDecimal("0.9"), MC_WORK)
            exactTrigDeg(deg)?.second?.let { return it }
            return cosRad(toRadians(grad, AngleUnit.GRAD))
        }
        return cosRad(angle)
    }

    fun tan(angle: BigDecimal, unit: AngleUnit): BigDecimal {
        val c = cos(angle, unit)
        if (c.abs() < BigDecimal("1E-26")) {
            throw CalcEngineException(ErrorKind.MATH, "Tangent undefined (cos = 0)")
        }
        val s = sin(angle, unit)
        return snapZero(s.divide(c, MC_INTERNAL))
    }

    private fun exactTrigDeg(degMod360: BigDecimal): Pair<BigDecimal, BigDecimal>? {
        val norm = degMod360.stripTrailingZeros()
        if (norm.scale() > 0) return null
        val d = norm.toInt()
        return when (d) {
            0, 360 -> ZERO to ONE
            30 -> BigDecimal("0.5") to sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL)
            45 -> sqrt(TWO).divide(TWO, MC_INTERNAL) to sqrt(TWO).divide(TWO, MC_INTERNAL)
            60 -> sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL) to BigDecimal("0.5")
            90 -> ONE to ZERO
            120 -> sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL) to BigDecimal("-0.5")
            135 -> sqrt(TWO).divide(TWO, MC_INTERNAL) to sqrt(TWO).divide(TWO, MC_INTERNAL).negate()
            150 -> BigDecimal("0.5") to sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL).negate()
            180 -> ZERO to ONE.negate()
            210 -> BigDecimal("-0.5") to sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL).negate()
            225 -> sqrt(TWO).divide(TWO, MC_INTERNAL).negate() to sqrt(TWO).divide(TWO, MC_INTERNAL).negate()
            240 -> sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL).negate() to BigDecimal("-0.5")
            270 -> ONE.negate() to ZERO
            300 -> sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL).negate() to BigDecimal("0.5")
            315 -> sqrt(TWO).divide(TWO, MC_INTERNAL).negate() to sqrt(TWO).divide(TWO, MC_INTERNAL)
            330 -> BigDecimal("-0.5") to sqrt(BigDecimal("3")).divide(TWO, MC_INTERNAL)
            else -> null
        }
    }

    fun sinRad(x: BigDecimal): BigDecimal {
        val (reduced, sign) = reduceAngleToFirstQuadrant(x)
        val raw = taylorSin(reduced).multiply(BigDecimal.valueOf(sign.toLong()), MC_WORK)
        return snapZero(raw.round(MC_INTERNAL))
    }

    fun cosRad(x: BigDecimal): BigDecimal {
        val shifted = x.add(HALF_PI, MC_WORK)
        return sinRad(shifted)
    }

    private fun reduceAngleToFirstQuadrant(x: BigDecimal): Pair<BigDecimal, Int> {
        var r = x.remainder(TWO_PI, MC_WORK)
        if (r < ZERO) r = r.add(TWO_PI, MC_WORK)
        return when {
            r <= HALF_PI -> r to 1
            r <= PI -> PI.subtract(r, MC_WORK) to 1
            r <= PI.add(HALF_PI, MC_WORK) -> r.subtract(PI, MC_WORK) to -1
            else -> TWO_PI.subtract(r, MC_WORK) to -1
        }
    }

    private fun taylorSin(x: BigDecimal): BigDecimal {
        // Reduce further if x > PI/4 using cos(PI/2 - x)
        if (x > HALF_PI.divide(TWO, MC_WORK)) {
            return taylorCos(HALF_PI.subtract(x, MC_WORK))
        }
        val x2 = x.multiply(x, MC_WORK)
        var term = x
        var sum = x
        var n = 1
        while (term.abs() > BigDecimal("1E-42") && n < 80) {
            val denom = BigDecimal.valueOf((2 * n).toLong() * (2 * n + 1).toLong())
            term = term.multiply(x2, MC_WORK).divide(denom, MC_WORK).negate()
            sum = sum.add(term, MC_WORK)
            n++
        }
        return sum
    }

    private fun taylorCos(x: BigDecimal): BigDecimal {
        val x2 = x.multiply(x, MC_WORK)
        var term = ONE
        var sum = ONE
        var n = 1
        while (term.abs() > BigDecimal("1E-42") && n < 80) {
            val denom = BigDecimal.valueOf((2 * n - 1).toLong() * (2 * n).toLong())
            term = term.multiply(x2, MC_WORK).divide(denom, MC_WORK).negate()
            sum = sum.add(term, MC_WORK)
            n++
        }
        return sum
    }

    fun asin(x: BigDecimal, unit: AngleUnit): BigDecimal {
        if (x.abs() > ONE) throw CalcEngineException(ErrorKind.MATH, "asin domain error (|x| > 1)")
        if (x.compareTo(ONE) == 0) return fromRadians(HALF_PI, unit)
        if (x.compareTo(ONE.negate()) == 0) return fromRadians(HALF_PI.negate(), unit)
        // asin(x) = atan(x / sqrt(1 - x^2))
        val denom = sqrt(ONE.subtract(x.multiply(x, MC_WORK), MC_WORK))
        val rad = atanRad(x.divide(denom, MC_WORK))
        return snapZero(fromRadians(rad, unit))
    }

    fun acos(x: BigDecimal, unit: AngleUnit): BigDecimal {
        if (x.abs() > ONE) throw CalcEngineException(ErrorKind.MATH, "acos domain error (|x| > 1)")
        val rad = HALF_PI.subtract(toRadians(asin(x, AngleUnit.RAD), AngleUnit.RAD), MC_WORK)
        return snapZero(fromRadians(rad, unit))
    }

    fun atan(x: BigDecimal, unit: AngleUnit): BigDecimal =
        snapZero(fromRadians(atanRad(x), unit))

    fun atan2(y: BigDecimal, x: BigDecimal, unit: AngleUnit): BigDecimal {
        val rad = when {
            x > ZERO -> atanRad(y.divide(x, MC_WORK))
            x < ZERO && y >= ZERO -> atanRad(y.divide(x, MC_WORK)).add(PI, MC_WORK)
            x < ZERO && y < ZERO -> atanRad(y.divide(x, MC_WORK)).subtract(PI, MC_WORK)
            x.compareTo(ZERO) == 0 && y > ZERO -> HALF_PI
            x.compareTo(ZERO) == 0 && y < ZERO -> HALF_PI.negate()
            else -> ZERO
        }
        return snapZero(fromRadians(rad, unit))
    }

    private fun atanRad(x: BigDecimal): BigDecimal {
        if (x.compareTo(ZERO) == 0) return ZERO
        val neg = x < ZERO
        var v = x.abs()
        var complement = false
        if (v > ONE) {
            v = ONE.divide(v, MC_WORK)
            complement = true
        }
        // Halve argument repeatedly using v = v / (1 + sqrt(1 + v^2)) so |v| < 0.2 for fast Taylor
        var halvings = 0
        val threshold = BigDecimal("0.15")
        while (v > threshold) {
            val root = sqrt(ONE.add(v.multiply(v, MC_WORK), MC_WORK))
            v = v.divide(ONE.add(root, MC_WORK), MC_WORK)
            halvings++
        }
        val v2 = v.multiply(v, MC_WORK)
        var term = v
        var sum = v
        var n = 1
        while (term.abs() > BigDecimal("1E-42") && n < 80) {
            term = term.multiply(v2, MC_WORK).negate()
            val contrib = term.divide(BigDecimal.valueOf((2 * n + 1).toLong()), MC_WORK)
            sum = sum.add(contrib, MC_WORK)
            n++
        }
        var res = sum.multiply(BigDecimal.valueOf(1L shl halvings), MC_WORK)
        if (complement) res = HALF_PI.subtract(res, MC_WORK)
        if (neg) res = res.negate()
        return res
    }

    // Hyperbolic functions
    fun sinh(x: BigDecimal): BigDecimal {
        val ex = exp(x)
        val enx = ONE.divide(ex, MC_WORK)
        return snapZero(ex.subtract(enx, MC_WORK).divide(TWO, MC_INTERNAL))
    }

    fun cosh(x: BigDecimal): BigDecimal {
        val ex = exp(x)
        val enx = ONE.divide(ex, MC_WORK)
        return ex.add(enx, MC_WORK).divide(TWO, MC_INTERNAL)
    }

    fun tanh(x: BigDecimal): BigDecimal =
        snapZero(sinh(x).divide(cosh(x), MC_INTERNAL))

    fun asinh(x: BigDecimal): BigDecimal =
        snapZero(ln(x.add(sqrt(x.multiply(x, MC_WORK).add(ONE, MC_WORK)), MC_WORK)))

    fun acosh(x: BigDecimal): BigDecimal {
        if (x < ONE) throw CalcEngineException(ErrorKind.MATH, "acosh domain error (x < 1)")
        return snapZero(ln(x.add(sqrt(x.multiply(x, MC_WORK).subtract(ONE, MC_WORK)), MC_WORK)))
    }

    fun atanh(x: BigDecimal): BigDecimal {
        if (x.abs() >= ONE) throw CalcEngineException(ErrorKind.MATH, "atanh domain error (|x| >= 1)")
        val ratio = ONE.add(x, MC_WORK).divide(ONE.subtract(x, MC_WORK), MC_WORK)
        return snapZero(ln(ratio).divide(TWO, MC_INTERNAL))
    }

    // Combinatorics
    fun factorial(x: BigDecimal): BigDecimal {
        val nInt = try {
            x.stripTrailingZeros().toBigIntegerExact()
        } catch (_: ArithmeticException) {
            throw CalcEngineException(ErrorKind.MATH, "Factorial requires non-negative integer")
        }
        if (nInt < BigInteger.ZERO || nInt > BigInteger.valueOf(200L)) {
            throw CalcEngineException(ErrorKind.MATH, "Factorial domain/overflow error")
        }
        var acc = BigInteger.ONE
        var i = 2
        val target = nInt.toInt()
        while (i <= target) {
            acc = acc.multiply(BigInteger.valueOf(i.toLong()))
            i++
        }
        return BigDecimal(acc)
    }

    fun permutation(n: BigDecimal, r: BigDecimal): BigDecimal {
        val nInt = requireNonNegInt(n, "nPr")
        val rInt = requireNonNegInt(r, "nPr")
        if (rInt > nInt || nInt > 200) throw CalcEngineException(ErrorKind.MATH, "nPr domain error")
        var acc = BigInteger.ONE
        for (i in 0 until rInt) {
            acc = acc.multiply(BigInteger.valueOf((nInt - i).toLong()))
        }
        return BigDecimal(acc)
    }

    fun combination(n: BigDecimal, r: BigDecimal): BigDecimal {
        val nInt = requireNonNegInt(n, "nCr")
        val rInt = requireNonNegInt(r, "nCr")
        if (rInt > nInt || nInt > 500) throw CalcEngineException(ErrorKind.MATH, "nCr domain error")
        val k = minOf(rInt, nInt - rInt)
        var acc = BigInteger.ONE
        for (i in 1..k) {
            acc = acc.multiply(BigInteger.valueOf((nInt - k + i).toLong()))
                .divide(BigInteger.valueOf(i.toLong()))
        }
        return BigDecimal(acc)
    }

    fun gcd(a: BigDecimal, b: BigDecimal): BigDecimal {
        val ia = requireInt(a, "GCD").abs()
        val ib = requireInt(b, "GCD").abs()
        return BigDecimal(ia.gcd(ib))
    }

    fun lcm(a: BigDecimal, b: BigDecimal): BigDecimal {
        val ia = requireInt(a, "LCM").abs()
        val ib = requireInt(b, "LCM").abs()
        if (ia == BigInteger.ZERO || ib == BigInteger.ZERO) return ZERO
        return BigDecimal(ia.divide(ia.gcd(ib)).multiply(ib))
    }

    private fun requireNonNegInt(x: BigDecimal, op: String): Int {
        val bi = requireInt(x, op)
        if (bi < BigInteger.ZERO || bi > BigInteger.valueOf(10_000L)) {
            throw CalcEngineException(ErrorKind.MATH, "$op integer domain error")
        }
        return bi.toInt()
    }

    private fun requireInt(x: BigDecimal, op: String): BigInteger = try {
        x.stripTrailingZeros().toBigIntegerExact()
    } catch (_: ArithmeticException) {
        throw CalcEngineException(ErrorKind.MATH, "$op requires integer arguments")
    }

    private fun modPositive(x: BigDecimal, m: BigDecimal): BigDecimal {
        val rem = x.remainder(m, MC_WORK)
        return if (rem < ZERO) rem.add(m, MC_WORK) else rem
    }

    fun decimalToRationalOrNull(x: BigDecimal, maxDen: Long = 10_000L): Pair<BigInteger, BigInteger>? {
        val norm = x.stripTrailingZeros()
        if (norm.scale() <= 0) return norm.toBigIntegerExact() to BigInteger.ONE
        val num = norm.unscaledValue()
        val den = BigInteger.TEN.pow(norm.scale())
        val g = num.abs().gcd(den)
        val reducedDen = den.divide(g)
        if (reducedDen <= BigInteger.valueOf(maxDen)) {
            return num.divide(g) to reducedDen
        }
        return null
    }

    fun formatDisplay(
        value: BigDecimal,
        format: NumberFormatMode = NumberFormatMode.Norm1,
        engShift: Int = 0,
    ): String {
        val clean = snapZero(value).round(MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros()
        if (clean.compareTo(ZERO) == 0) {
            return when (format) {
                is NumberFormatMode.Fix -> if (format.digits == 0) "0" else "0.${"0".repeat(format.digits)}"
                is NumberFormatMode.Sci -> {
                    val zeros = if (format.digits > 1) ".${"0".repeat(format.digits - 1)}" else ""
                    "0${zeros}×10^0"
                }
                else -> "0"
            }
        }

        if (engShift != 0) {
            return formatEngineering(clean, engShift)
        }

        return when (format) {
            is NumberFormatMode.Fix -> clean.setScale(format.digits, RoundingMode.HALF_UP).toPlainString()
            is NumberFormatMode.Sci -> formatScientific(clean, format.digits)
            is NumberFormatMode.Norm1 -> {
                val absVal = clean.abs()
                if (absVal >= BigDecimal("1E10") || absVal < BigDecimal("1E-2")) {
                    formatScientific(clean, sigDigits = null)
                } else {
                    clean.toPlainString()
                }
            }
            is NumberFormatMode.Norm2 -> {
                val absVal = clean.abs()
                if (absVal >= BigDecimal("1E10") || absVal < BigDecimal("1E-9")) {
                    formatScientific(clean, sigDigits = null)
                } else {
                    clean.toPlainString()
                }
            }
        }
    }

    private fun formatScientific(clean: BigDecimal, sigDigits: Int?): String {
        val neg = clean < ZERO
        val abs = clean.abs()
        val str = abs.toEngineeringString()
        val exp = kotlin.math.floor(kotlin.math.log10(abs.toDouble())).toInt()
        val mantissa = abs.movePointLeft(exp)
        val rounded = if (sigDigits != null) {
            mantissa.round(MathContext(sigDigits.coerceIn(1, 12), RoundingMode.HALF_UP))
                .let {
                    if (sigDigits > 1) it.setScale(sigDigits - 1, RoundingMode.HALF_UP).toPlainString()
                    else it.setScale(0, RoundingMode.HALF_UP).toPlainString()
                }
        } else {
            mantissa.round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString()
        }
        val sign = if (neg) "-" else ""
        return "$sign${rounded}×10^$exp"
    }

    private fun formatEngineering(clean: BigDecimal, engShift: Int): String {
        val neg = clean < ZERO
        val abs = clean.abs()
        val baseExp = kotlin.math.floor(kotlin.math.log10(abs.toDouble()) / 3.0).toInt() * 3
        val targetExp = baseExp + engShift * 3
        val mantissa = abs.movePointLeft(targetExp).round(MathContext(10, RoundingMode.HALF_UP)).stripTrailingZeros()
        val sign = if (neg) "-" else ""
        return "$sign${mantissa.toPlainString()}×10^$targetExp"
    }
}
