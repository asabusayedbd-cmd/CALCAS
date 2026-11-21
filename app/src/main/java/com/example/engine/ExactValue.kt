package com.example.engine

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Exact rational number `num / den` with `den > 0` and `gcd(|num|, den) = 1`.
 */
@ConsistentCopyVisibility
data class ExactRational private constructor(
    val num: BigInteger,
    val den: BigInteger,
) {
    val isZero: Boolean get() = num == BigInteger.ZERO
    val isInteger: Boolean get() = den == BigInteger.ONE

    operator fun plus(other: ExactRational): ExactRational =
        of(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: ExactRational): ExactRational =
        of(num * other.den - other.num * den, den * other.den)

    operator fun times(other: ExactRational): ExactRational =
        of(num * other.num, den * other.den)

    operator fun div(other: ExactRational): ExactRational {
        if (other.isZero) throw CalcEngineException(ErrorKind.MATH, "Division by zero")
        return of(num * other.den, den * other.num)
    }

    operator fun unaryMinus(): ExactRational = ExactRational(-num, den)

    fun toBigDecimal(): BigDecimal = if (den == BigInteger.ONE) {
        BigDecimal(num)
    } else {
        BigDecimal(num).divide(BigDecimal(den), BigMath.MC_INTERNAL)
    }

    fun formatImproper(): String = if (den == BigInteger.ONE) num.toString() else "$num/$den"

    fun formatMixed(): String {
        if (den == BigInteger.ONE) return num.toString()
        val absNum = num.abs()
        if (absNum < den) return "$num/$den"
        val whole = num / den
        val rem = absNum % den
        return "$whole $rem/$den"
    }

    companion object {
        val ZERO = ExactRational(BigInteger.ZERO, BigInteger.ONE)
        val ONE = ExactRational(BigInteger.ONE, BigInteger.ONE)
        val HALF = ExactRational(BigInteger.ONE, BigInteger.TWO)

        fun of(num: BigInteger, den: BigInteger = BigInteger.ONE): ExactRational {
            if (den == BigInteger.ZERO) throw CalcEngineException(ErrorKind.MATH, "Division by zero")
            if (num == BigInteger.ZERO) return ZERO
            val signedNum = if (den < BigInteger.ZERO) -num else num
            val posDen = den.abs()
            val g = signedNum.abs().gcd(posDen)
            return ExactRational(signedNum / g, posDen / g)
        }

        fun of(num: Long, den: Long = 1L): ExactRational =
            of(BigInteger.valueOf(num), BigInteger.valueOf(den))

        fun fromDecimalStringOrNull(raw: String): ExactRational? {
            val bd = raw.toBigDecimalOrNull() ?: return null
            val norm = bd.stripTrailingZeros()
            if (norm.scale() <= 0) {
                return if (norm.precision() <= 24) of(norm.toBigIntegerExact(), BigInteger.ONE) else null
            }
            if (norm.scale() > 16) return null
            return of(norm.unscaledValue(), BigInteger.TEN.pow(norm.scale()))
        }
    }
}

/**
 * Exact linear combination of square-free radicals:
 * `sum_{r in terms.keys} terms[r] * sqrt(r)`, where `r == 1` is the rational term.
 */
data class ExactRadicalSum(val terms: Map<BigInteger, ExactRational>) {

    val isZero: Boolean get() = terms.isEmpty()
    val isPureRational: Boolean get() = terms.isEmpty() || (terms.size == 1 && terms.containsKey(BigInteger.ONE))

    fun rationalOrNull(): ExactRational? = when {
        terms.isEmpty() -> ExactRational.ZERO
        terms.size == 1 && terms.containsKey(BigInteger.ONE) -> terms[BigInteger.ONE]
        else -> null
    }

    operator fun unaryMinus(): ExactRadicalSum =
        ExactRadicalSum(terms.mapValues { (_, c) -> -c })

    operator fun plus(other: ExactRadicalSum): ExactRadicalSum {
        val merged = terms.toMutableMap()
        for ((rad, coeff) in other.terms) {
            val sum = (merged[rad] ?: ExactRational.ZERO) + coeff
            if (sum.isZero) merged.remove(rad) else merged[rad] = sum
        }
        return ExactRadicalSum(merged)
    }

    operator fun minus(other: ExactRadicalSum): ExactRadicalSum = this + (-other)

    operator fun times(other: ExactRadicalSum): ExactRadicalSum {
        if (isZero || other.isZero) return ZERO
        val acc = mutableMapOf<BigInteger, ExactRational>()
        for ((r1, c1) in terms) {
            for ((r2, c2) in other.terms) {
                val (squareOut, squareFree) = simplifyRadicalProduct(r1, r2)
                val coeff = c1 * c2 * ExactRational.of(squareOut)
                val updated = (acc[squareFree] ?: ExactRational.ZERO) + coeff
                if (updated.isZero) acc.remove(squareFree) else acc[squareFree] = updated
            }
        }
        return ExactRadicalSum(acc)
    }

    fun divOrNull(other: ExactRadicalSum): ExactRadicalSum? {
        if (other.isZero) throw CalcEngineException(ErrorKind.MATH, "Division by zero")
        // 1 term: a * sqrt(r)
        if (other.terms.size == 1) {
            val (r, c) = other.terms.entries.first()
            val inv = ExactRadicalSum(mapOf(r to (ExactRational.ONE / (c * ExactRational.of(r)))))
            return this * inv
        }
        // 2 terms: rationalize denominator via conjugate (a + b√r)(a - b√r) = a^2 - b^2*r
        if (other.terms.size == 2) {
            val entries = other.terms.entries.toList()
            val (r1, c1) = entries[0]
            val (r2, c2) = entries[1]
            val conj = ExactRadicalSum(mapOf(r1 to c1, r2 to -c2))
            val denom = other * conj
            val denomRat = denom.rationalOrNull() ?: return null
            return (this * conj) * ExactRadicalSum(mapOf(BigInteger.ONE to (ExactRational.ONE / denomRat)))
        }
        return null
    }

    fun toBigDecimal(): BigDecimal {
        var sum = BigDecimal.ZERO
        for ((rad, coeff) in terms) {
            val cBd = coeff.toBigDecimal()
            val rBd = if (rad == BigInteger.ONE) BigDecimal.ONE else BigMath.sqrt(BigDecimal(rad))
            sum = BigMath.add(sum, BigMath.mul(cBd, rBd))
        }
        return sum
    }

    fun formatExact(): String {
        if (terms.isEmpty()) return "0"
        // Factor out common denominator across all terms when useful for crisp Casio-style display: (a + b√r)/d
        val lcmDen = terms.values.fold(BigInteger.ONE) { acc, c ->
            acc.divide(acc.gcd(c.den)).multiply(c.den)
        }
        val sorted = terms.entries.sortedBy { it.key }
        if (sorted.size == 1) {
            val (rad, coeff) = sorted.first()
            return formatSingleTerm(coeff, rad)
        }

        // Render with common denominator if denominator > 1
        if (lcmDen > BigInteger.ONE) {
            val numStr = buildNumeratorString(sorted, lcmDen)
            return "($numStr)/$lcmDen"
        }
        return buildNumeratorString(sorted, BigInteger.ONE)
    }

    private fun buildNumeratorString(
        sorted: List<Map.Entry<BigInteger, ExactRational>>,
        scaleDen: BigInteger,
    ): String {
        val sb = StringBuilder()
        sorted.forEachIndexed { idx, (rad, coeff) ->
            val scaledNum = coeff.num * (scaleDen / coeff.den)
            val isNeg = scaledNum < BigInteger.ZERO
            val absNum = scaledNum.abs()
            if (idx == 0) {
                if (isNeg) sb.append("-")
            } else {
                sb.append(if (isNeg) " - " else " + ")
            }
            if (rad == BigInteger.ONE) {
                sb.append(absNum)
            } else {
                if (absNum != BigInteger.ONE) sb.append(absNum)
                sb.append("√").append(rad)
            }
        }
        return sb.toString()
    }

    private fun formatSingleTerm(coeff: ExactRational, rad: BigInteger): String {
        if (rad == BigInteger.ONE) return coeff.formatImproper()
        val neg = coeff.num < BigInteger.ZERO
        val absNum = coeff.num.abs()
        val sign = if (neg) "-" else ""
        val numPart = if (absNum == BigInteger.ONE) "√$rad" else "${absNum}√$rad"
        return if (coeff.den == BigInteger.ONE) "$sign$numPart" else "$sign$numPart/${coeff.den}"
    }

    companion object {
        val ZERO = ExactRadicalSum(emptyMap())
        val ONE = ExactRadicalSum(mapOf(BigInteger.ONE to ExactRational.ONE))

        fun rational(r: ExactRational): ExactRadicalSum =
            if (r.isZero) ZERO else ExactRadicalSum(mapOf(BigInteger.ONE to r))

        fun sqrtOfRationalOrNull(r: ExactRational): ExactRadicalSum? {
            if (r.num < BigInteger.ZERO) return null
            if (r.isZero) return ZERO
            // sqrt(p/q) = sqrt(p*q) / q
            val prod = r.num * r.den
            if (prod.bitLength() > 48) return null
            val (sqOut, sqFree) = factorOutSquares(prod.toLong())
            val coeff = ExactRational.of(BigInteger.valueOf(sqOut), r.den)
            return ExactRadicalSum(mapOf(BigInteger.valueOf(sqFree) to coeff))
        }

        private fun simplifyRadicalProduct(r1: BigInteger, r2: BigInteger): Pair<BigInteger, BigInteger> {
            val g = r1.gcd(r2)
            val rest = (r1 / g) * (r2 / g)
            if (rest.bitLength() > 48) return g to rest
            val (sq, sf) = factorOutSquares(rest.toLong())
            return (g * BigInteger.valueOf(sq)) to BigInteger.valueOf(sf)
        }

        fun factorOutSquares(n: Long): Pair<Long, Long> {
            if (n <= 1L) return 1L to n
            var rem = n
            var out = 1L
            var d = 2L
            while (d * d <= rem && d <= 100_000L) {
                var count = 0
                while (rem % d == 0L) {
                    rem /= d
                    count++
                }
                if (count >= 2) {
                    var pow = 1L
                    repeat(count / 2) { pow *= d }
                    out *= pow
                }
                if (count % 2 == 1) {
                    // keep one factor in sf
                    // multiply back below via sf accumulator
                }
                d += if (d == 2L) 1L else 2L
            }
            // Recompute square-free cleanly: n = out^2 * sf
            val sf = n / (out * out)
            return out to sf
        }
    }
}

/**
 * Exact symbolic form `piCoeff * π + constPart` where both coefficients are [ExactRadicalSum].
 */
data class ExactSymbolic(
    val piCoeff: ExactRadicalSum = ExactRadicalSum.ZERO,
    val constPart: ExactRadicalSum = ExactRadicalSum.ZERO,
) {
    val isPureRational: Boolean get() = piCoeff.isZero && constPart.isPureRational
    val isRadicalOnly: Boolean get() = piCoeff.isZero

    fun rationalOrNull(): ExactRational? =
        if (piCoeff.isZero) constPart.rationalOrNull() else null

    operator fun unaryMinus(): ExactSymbolic = ExactSymbolic(-piCoeff, -constPart)

    operator fun plus(other: ExactSymbolic): ExactSymbolic =
        ExactSymbolic(piCoeff + other.piCoeff, constPart + other.constPart)

    operator fun minus(other: ExactSymbolic): ExactSymbolic =
        ExactSymbolic(piCoeff - other.piCoeff, constPart - other.constPart)

    fun timesOrNull(other: ExactSymbolic): ExactSymbolic? {
        if (!piCoeff.isZero && !other.piCoeff.isZero) return null // no π² exact representation
        return ExactSymbolic(
            piCoeff = piCoeff * other.constPart + other.piCoeff * constPart,
            constPart = constPart * other.constPart,
        )
    }

    fun divOrNull(other: ExactSymbolic): ExactSymbolic? {
        if (other.piCoeff.isZero) {
            val newPi = piCoeff.divOrNull(other.constPart) ?: return null
            val newConst = constPart.divOrNull(other.constPart) ?: return null
            return ExactSymbolic(newPi, newConst)
        }
        // (a * π) / (b * π) = a / b
        if (constPart.isZero && other.constPart.isZero) {
            val ratio = piCoeff.divOrNull(other.piCoeff) ?: return null
            return ExactSymbolic(ExactRadicalSum.ZERO, ratio)
        }
        return null
    }

    fun toBigDecimal(): BigDecimal {
        val piTerm = if (piCoeff.isZero) BigDecimal.ZERO else BigMath.mul(piCoeff.toBigDecimal(), BigMath.PI)
        return BigMath.add(piTerm, constPart.toBigDecimal())
    }

    fun formatExact(): String {
        if (piCoeff.isZero) return constPart.formatExact()
        val piStr = formatPiTerm(piCoeff)
        if (constPart.isZero) return piStr
        val constStr = constPart.formatExact()
        return if (constStr.startsWith("-")) "$piStr - ${constStr.drop(1)}" else "$piStr + $constStr"
    }

    private fun formatPiTerm(coeff: ExactRadicalSum): String {
        val rat = coeff.rationalOrNull()
        if (rat != null) {
            if (rat == ExactRational.ONE) return "π"
            if (rat == -ExactRational.ONE) return "-π"
            if (rat.den == BigInteger.ONE) return "${rat.num}π"
            val sign = if (rat.num < BigInteger.ZERO) "-" else ""
            val absNum = rat.num.abs()
            val numPart = if (absNum == BigInteger.ONE) "π" else "${absNum}π"
            return "$sign$numPart/${rat.den}"
        }
        return "(${coeff.formatExact()})π"
    }

    companion object {
        val ZERO = ExactSymbolic()
        val ONE = ExactSymbolic(constPart = ExactRadicalSum.ONE)
        val PI = ExactSymbolic(piCoeff = ExactRadicalSum.ONE)

        fun fromRational(r: ExactRational): ExactSymbolic =
            ExactSymbolic(constPart = ExactRadicalSum.rational(r))

        fun fromRadical(r: ExactRadicalSum): ExactSymbolic =
            ExactSymbolic(constPart = r)
    }
}
