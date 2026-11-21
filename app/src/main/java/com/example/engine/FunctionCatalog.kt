package com.example.engine

import com.example.ast.AstDocument
import com.example.ast.CursorEngine
import com.example.ast.MathConstant
import com.example.ast.MathFunction
import com.example.ast.MathOperator
import com.example.ast.MathVariable
import com.example.ast.SumProdKind
import com.example.state.CalcMode
import com.example.state.CalcThemePreset
import java.math.BigDecimal

enum class CatalogCategory(val label: String) {
    ALL("All"),
    TRIG("Trig & Angles"),
    HYPERBOLIC("Hyperbolic"),
    POWERS_LOGS("Powers & Logs"),
    CALCULUS("Calculus & Solvers"),
    NUMBER_THEORY("Number & Random"),
    PROBABILITY("Prob & Stats"),
    COMPLEX("Complex"),
    MATRIX_VECTOR("Matrix & Vector"),
    BASE_N("Base-N & Logic"),
    FORMULAS("2D Formulas"),
    CONSTANTS("60 Constants"),
    CONVERSIONS("80 Conversions"),
    MEMORY_SETUP("Mem & Setup"),
}

enum class CatalogCommand {
    PRIME_FACTOR,
    TOGGLE_SD,
    TOGGLE_DMS,
    ENG_RIGHT,
    ENG_LEFT,
    OPEN_CALC,
    OPEN_SOLVE,
    TOGGLE_COMPLEX_POLAR,
    TOGGLE_HISTORY,
    CLEAR_MEMORY,
    DELETE_CHAR,
    ALL_CLEAR,
    EVALUATE,
    M_PLUS,
    M_MINUS,
    TOGGLE_BASE_WIDTH,
    OPEN_STAT_SHEET,
    OPEN_EQN_SHEET,
    OPEN_MATRIX_SHEET,
    OPEN_VECTOR_SHEET,
    OPEN_TABLE_SHEET,
}

sealed interface CatalogAction {
    data class InsertFunction(val fn: MathFunction, val mode: CalcMode? = null) : CatalogAction
    data class InsertOperator(val op: MathOperator, val mode: CalcMode? = null) : CatalogAction
    data class InsertConstant(val constant: MathConstant) : CatalogAction
    data class InsertVariable(val variable: MathVariable, val mode: CalcMode? = null) : CatalogAction
    data class InsertFraction(val mixed: Boolean = false) : CatalogAction
    data class InsertSqrt(val customIndex: Boolean = false, val presetIndex: String? = null) : CatalogAction
    data class InsertPower(val fixedExponent: String? = null) : CatalogAction
    data class InsertBasePower(val baseLiteral: String) : CatalogAction
    data class InsertNumber(val literal: String) : CatalogAction
    data object InsertLogBase : CatalogAction
    data object InsertParens : CatalogAction
    data object InsertAbs : CatalogAction
    data object InsertIntegral : CatalogAction
    data object InsertDerivative : CatalogAction
    data class InsertSumProd(val kind: SumProdKind) : CatalogAction
    data class InsertMatrix(val rows: Int, val cols: Int) : CatalogAction
    data class InsertTemplate(val build: (AstDocument) -> AstDocument) : CatalogAction
    data class ApplyConversion(val info: UnitConversionInfo) : CatalogAction
    data class SwitchMode(val mode: CalcMode, val openSheet: Boolean = false) : CatalogAction
    data class SetAngleUnit(val unit: AngleUnit) : CatalogAction
    data class SetNumberFormat(val format: NumberFormatMode) : CatalogAction
    data class SetBaseN(val base: NumberBase) : CatalogAction
    data class SetTheme(val preset: CalcThemePreset) : CatalogAction
    data class RunCommand(val command: CatalogCommand) : CatalogAction
}

data class CatalogEntry(
    val id: Int,
    val symbol: String,
    val name: String,
    val description: String,
    val category: CatalogCategory,
    val action: CatalogAction,
)

/**
 * Full 414+ Scientific Calculator Function Catalog covering all Casio fx-991ES Plus
 * functions, extended trig/hyperbolic/number-theory/probability functions, 60 CODATA
 * constants, 80 unit conversions, all 8 hardware modes, and editable 2D AST formulas.
 */
object FunctionCatalog {

    val entries: List<CatalogEntry> by lazy { buildCatalog() }

    private fun buildCatalog(): List<CatalogEntry> {
        val list = mutableListOf<CatalogEntry>()
        var nextId = 1

        fun add(
            symbol: String,
            name: String,
            description: String,
            category: CatalogCategory,
            action: CatalogAction,
        ) {
            list.add(CatalogEntry(nextId++, symbol, name, description, category, action))
        }

        // 1. Trigonometry & Angles (24)
        add("sin(", "Sine", "Trigonometric sine in active angle unit", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.SIN))
        add("cos(", "Cosine", "Trigonometric cosine in active angle unit", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.COS))
        add("tan(", "Tangent", "Trigonometric tangent in active angle unit", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.TAN))
        add("sin⁻¹(", "Arcsine", "Inverse sine returning active angle unit", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.ASIN))
        add("cos⁻¹(", "Arccosine", "Inverse cosine returning active angle unit", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.ACOS))
        add("tan⁻¹(", "Arctangent", "Inverse tangent returning active angle unit", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.ATAN))
        add("csc(", "Cosecant", "Reciprocal sine 1/sin(x)", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.CSC))
        add("sec(", "Secant", "Reciprocal cosine 1/cos(x)", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.SEC))
        add("cot(", "Cotangent", "Reciprocal tangent cos(x)/sin(x)", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.COT))
        add("csc⁻¹(", "Arccosecant", "Inverse cosecant", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.ACSC))
        add("sec⁻¹(", "Arcsecant", "Inverse secant", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.ASEC))
        add("cot⁻¹(", "Arccotangent", "Inverse cotangent", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.ACOT))
        add("°→(", "Degree Input", "Interpret argument in degrees regardless of mode", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.IN_DEG))
        add("ʳ→(", "Radian Input", "Interpret argument in radians regardless of mode", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.IN_RAD))
        add("ᵍ→(", "Gradian Input", "Interpret argument in gradians regardless of mode", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.IN_GRAD))
        add("rad(", "Deg to Rad", "Convert degrees value to radians", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.TO_RAD))
        add("deg(", "Rad to Deg", "Convert radians value to degrees", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.TO_DEG))
        add("grad(", "Rad to Grad", "Convert radians value to gradians", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.TO_GRAD))
        add("atan2(", "Four-Quadrant Atan", "atan2(y, x) angle in active angle unit", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.ATAN2))
        add("sinc(", "Sinc Function sin(x)/x", "Unnormalized sinc(x) in radians (1 at 0)", CatalogCategory.TRIG, CatalogAction.InsertFunction(MathFunction.SINC))
        add("°′″", "Sexagesimal DMS", "Enter degrees-minutes-seconds literal", CatalogCategory.TRIG, CatalogAction.InsertOperator(MathOperator.DMS))
        add("▸DMS", "Toggle DMS Result", "Format current result as D°M′S″", CatalogCategory.TRIG, CatalogAction.RunCommand(CatalogCommand.TOGGLE_DMS))
        add("DEG", "Degree Mode", "Set angle unit to Degrees (360°)", CatalogCategory.TRIG, CatalogAction.SetAngleUnit(AngleUnit.DEG))
        add("RAD", "Radian Mode", "Set angle unit to Radians (2π)", CatalogCategory.TRIG, CatalogAction.SetAngleUnit(AngleUnit.RAD))
        add("GRAD", "Gradian Mode", "Set angle unit to Gradians (400g)", CatalogCategory.TRIG, CatalogAction.SetAngleUnit(AngleUnit.GRAD))
        add("sin²x", "Sine Squared", "Insert (sin(x))² template", CatalogCategory.TRIG, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.SIN)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("cos²x", "Cosine Squared", "Insert (cos(x))² template", CatalogCategory.TRIG, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.COS)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("tan²x", "Tangent Squared", "Insert (tan(x))² template", CatalogCategory.TRIG, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.TAN)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("sec²x", "Secant Squared", "Insert (sec(x))² template", CatalogCategory.TRIG, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.SEC)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("csc²x", "Cosecant Squared", "Insert (csc(x))² template", CatalogCategory.TRIG, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.CSC)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("cot²x", "Cotangent Squared", "Insert (cot(x))² template", CatalogCategory.TRIG, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.COT)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })

        // 2. Hyperbolic Functions (12)
        add("sinh(", "Hyperbolic Sine", "(e^x − e^-x) / 2", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.SINH))
        add("cosh(", "Hyperbolic Cosine", "(e^x + e^-x) / 2", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.COSH))
        add("tanh(", "Hyperbolic Tangent", "sinh(x) / cosh(x)", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.TANH))
        add("sinh⁻¹(", "Inverse Hyp. Sine", "ln(x + √(x² + 1))", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.ASINH))
        add("cosh⁻¹(", "Inverse Hyp. Cosine", "ln(x + √(x² − 1)), x ≥ 1", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.ACOSH))
        add("tanh⁻¹(", "Inverse Hyp. Tangent", "½ ln((1 + x)/(1 − x)), |x| < 1", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.ATANH))
        add("csch(", "Hyperbolic Cosecant", "1 / sinh(x)", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.CSCH))
        add("sech(", "Hyperbolic Secant", "1 / cosh(x)", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.SECH))
        add("coth(", "Hyperbolic Cotangent", "cosh(x) / sinh(x)", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.COTH))
        add("csch⁻¹(", "Inverse Hyp. Cosecant", "asinh(1/x)", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.ACSCH))
        add("sech⁻¹(", "Inverse Hyp. Secant", "acosh(1/x), 0 < x ≤ 1", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.ASECH))
        add("coth⁻¹(", "Inverse Hyp. Cotangent", "atanh(1/x), |x| > 1", CatalogCategory.HYPERBOLIC, CatalogAction.InsertFunction(MathFunction.ACOTH))

        // 3. Powers, Roots & Logarithms (22)
        add("a/b", "Fraction", "2D stacked numerator/denominator fraction", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFraction(false))
        add("a b/c", "Mixed Fraction", "2D mixed fraction template", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFraction(true))
        add("x²", "Square", "Raise preceding term to power 2", CatalogCategory.POWERS_LOGS, CatalogAction.InsertPower("2"))
        add("x³", "Cube", "Raise preceding term to power 3", CatalogCategory.POWERS_LOGS, CatalogAction.InsertPower("3"))
        add("x⁴", "Fourth Power", "Raise preceding term to power 4", CatalogCategory.POWERS_LOGS, CatalogAction.InsertPower("4"))
        add("x⁻¹", "Reciprocal", "1 / x inverse power", CatalogCategory.POWERS_LOGS, CatalogAction.InsertPower("-1"))
        add("x⁻²", "Inverse Square", "x⁻² power template", CatalogCategory.POWERS_LOGS, CatalogAction.InsertPower("-2"))
        add("x^□", "Exponent", "General power xʸ", CatalogCategory.POWERS_LOGS, CatalogAction.InsertPower(null))
        add("√□", "Square Root", "Principal square root", CatalogCategory.POWERS_LOGS, CatalogAction.InsertSqrt(false))
        add("³√□", "Cube Root", "Real cube root template", CatalogCategory.POWERS_LOGS, CatalogAction.InsertSqrt(true, "3"))
        add("⁴√□", "Fourth Root", "Fourth root template", CatalogCategory.POWERS_LOGS, CatalogAction.InsertSqrt(true, "4"))
        add("ⁿ√□", "n-th Root", "General n-th root template", CatalogCategory.POWERS_LOGS, CatalogAction.InsertSqrt(true))
        add("log(", "Common Log", "Base-10 logarithm log₁₀(x)", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFunction(MathFunction.LOG10))
        add("log₂(", "Binary Log", "Base-2 logarithm log₂(x)", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFunction(MathFunction.LOG2))
        add("ln(", "Natural Log", "Base-e logarithm ln(x)", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFunction(MathFunction.LN))
        add("logₐb", "Log Base a", "Arbitrary base logarithm log_a(b)", CatalogCategory.POWERS_LOGS, CatalogAction.InsertLogBase)
        add("10^x", "Power of 10", "10 raised to exponent", CatalogCategory.POWERS_LOGS, CatalogAction.InsertBasePower("10"))
        add("e^x", "Exponential e^x", "Natural exponential e raised to x", CatalogCategory.POWERS_LOGS, CatalogAction.InsertBasePower("e"))
        add("2^x", "Power of 2", "2 raised to exponent", CatalogCategory.POWERS_LOGS, CatalogAction.InsertBasePower("2"))
        add("expm1(", "Exp Minus 1 (e^x−1)", "High-precision e^x − 1 near x = 0", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFunction(MathFunction.EXPM1))
        add("ln1p(", "Log(1+x)", "High-precision ln(1 + x) near x = 0", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFunction(MathFunction.LN1P))
        add("sigm(", "Logistic Sigmoid σ(x)", "1 / (1 + e^(-x)) activation function", CatalogCategory.POWERS_LOGS, CatalogAction.InsertFunction(MathFunction.SIGMOID))
        add("|x|", "Absolute Value", "2D absolute value bars", CatalogCategory.POWERS_LOGS, CatalogAction.InsertAbs)
        add("(%)", "Percent", "Divide value by 100", CatalogCategory.POWERS_LOGS, CatalogAction.InsertOperator(MathOperator.PERCENT))
        add("S⇔D", "Exact ⇔ Decimal", "Cycle Exact / Mixed / Decimal output", CatalogCategory.POWERS_LOGS, CatalogAction.RunCommand(CatalogCommand.TOGGLE_SD))

        // 4. Calculus & Solvers (14)
        add("∫dx", "Definite Integral", "Adaptive Gauss-Kronrod quadrature", CatalogCategory.CALCULUS, CatalogAction.InsertIntegral)
        add("d/dx", "Derivative", "Ridders numerical derivative at x", CatalogCategory.CALCULUS, CatalogAction.InsertDerivative)
        add("Σ(", "Summation", "Discrete sum Σ f(x) from lower to upper", CatalogCategory.CALCULUS, CatalogAction.InsertSumProd(SumProdKind.SUM))
        add("Π(", "Product", "Discrete product Π f(x) from lower to upper", CatalogCategory.CALCULUS, CatalogAction.InsertSumProd(SumProdKind.PRODUCT))
        add("CALC", "Variable Evaluator", "Prompt sequentially for A–F, X, Y", CatalogCategory.CALCULUS, CatalogAction.RunCommand(CatalogCommand.OPEN_CALC))
        add("SOLVE", "Root Solver", "Solve f(X) = 0 with Newton + Brent", CatalogCategory.CALCULUS, CatalogAction.RunCommand(CatalogCommand.OPEN_SOLVE))
        add("Pol(", "Rect → Polar", "Pol(x, y) computes (r, θ)", CatalogCategory.CALCULUS, CatalogAction.InsertFunction(MathFunction.POL))
        add("Rec(", "Polar → Rect", "Rec(r, θ) computes (X, Y)", CatalogCategory.CALCULUS, CatalogAction.InsertFunction(MathFunction.REC))
        add("Hypot(", "Hypotenuse", "√(a² + b²) without overflow", CatalogCategory.CALCULUS, CatalogAction.InsertFunction(MathFunction.HYPOT))
        add("TABLE", "Function Table", "Tabulate f(x) and g(x) over a range", CatalogCategory.CALCULUS, CatalogAction.SwitchMode(CalcMode.TABLE, true))
        add("2×2 EQN", "2×2 Linear System", "Solve a₁x + b₁y = c₁ system", CatalogCategory.CALCULUS, CatalogAction.SwitchMode(CalcMode.EQN, true))
        add("3×3 EQN", "3×3 Linear System", "Solve 3-unknown linear system", CatalogCategory.CALCULUS, CatalogAction.SwitchMode(CalcMode.EQN, true))
        add("ax²+bx+c", "Quadratic Solver", "Solve real & complex roots of quadratic", CatalogCategory.CALCULUS, CatalogAction.SwitchMode(CalcMode.EQN, true))
        add("ax³+bx²+cx+d", "Cubic Solver", "Solve real & complex roots of cubic", CatalogCategory.CALCULUS, CatalogAction.SwitchMode(CalcMode.EQN, true))

        // 5. Number Theory, Rounding & Random (22)
        add("x!", "Factorial", "n! for non-negative integer n", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertOperator(MathOperator.FACTORIAL))
        add("nPr", "Permutations", "n! / (n − r)!", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertOperator(MathOperator.PERMUTATION))
        add("nCr", "Combinations", "Binomial coefficient C(n, r)", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertOperator(MathOperator.COMBINATION))
        add("GCD(", "Greatest Common Div", "GCD(a, b) for integers", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.GCD))
        add("LCM(", "Least Common Mult", "LCM(a, b) for integers", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.LCM))
        add("Mod(", "Modulo Remainder", "Euclidean remainder a mod b", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.MOD))
        add("FACT", "Prime Factorization", "Factor current integer result into primes", CatalogCategory.NUMBER_THEORY, CatalogAction.RunCommand(CatalogCommand.PRIME_FACTOR))
        add("Rnd(", "Round Display", "Round to 10 significant digits", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.ROUND))
        add("Int(", "Integer Part", "Truncate fractional digits toward 0", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.INT_PART))
        add("Intg(", "Floor ⌊x⌋", "Greatest integer ≤ x", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.FLOOR))
        add("Ceil(", "Ceiling ⌈x⌉", "Smallest integer ≥ x", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.CEIL))
        add("Frac(", "Fractional Part", "x − Int(x)", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.FRAC_PART))
        add("sgn(", "Signum Function", "Returns -1, 0, or +1", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.SGN))
        add("Min(", "Minimum", "Smaller of two values Min(a, b)", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.MIN2))
        add("Max(", "Maximum", "Larger of two values Max(a, b)", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.MAX2))
        add("Fib(", "Fibonacci Number Fₙ", "Exact n-th Fibonacci integer (n ≤ 1000)", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.FIB))
        add("isPrime(", "Primality Test", "Returns 1 if integer n is prime, 0 otherwise", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.IS_PRIME))
        add("Γ(", "Euler Gamma Function", "Generalized factorial Γ(x), exact on (n−1)!", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.GAMMA))
        add("lnΓ(", "Log Gamma Function", "Natural logarithm ln Γ(x) for x > 0", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.LNGAMMA))
        add("Beta(", "Euler Beta B(a,b)", "Γ(a)·Γ(b) / Γ(a+b) with exact rationals", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.BETA))
        add("Ran#", "Random Decimal", "Uniform pseudo-random 0.000–0.999", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.RAN))
        add("RanInt#(", "Random Integer", "Uniform integer in [a, b]", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertFunction(MathFunction.RAN_INT))
        add("π", "Pi Constant", "3.141592653589793...", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertConstant(MathConstant.Pi))
        add("e", "Euler Number", "2.718281828459045...", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertConstant(MathConstant.EulerE))
        add("(-)", "Unary Negation", "Prefix negative sign", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertOperator(MathOperator.UNARY_MINUS))
        add(",", "Argument Separator", "Comma separator for multi-arg functions", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertOperator(MathOperator.COMMA))
        add("()", "Parentheses", "Grouped expression container", CatalogCategory.NUMBER_THEORY, CatalogAction.InsertParens)

        // 6. Probability Distributions & Statistics (23)
        add("P(t)", "Normal CDF Φ(t)", "Lower-tail standard normal probability P(Z ≤ t)", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.DIST_P))
        add("Q(t)", "Central Normal Q(t)", "Probability from 0 to t: Φ(t) − 0.5", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.DIST_Q))
        add("R(t)", "Upper Tail R(t)", "Upper-tail normal probability P(Z > t)", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.DIST_R))
        add("φ(t)", "Normal PDF", "Standard normal density e^(-t²/2)/√(2π)", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.NORM_PDF))
        add("Φ⁻¹(p)", "Inverse Normal CDF", "Quantile z such that P(Z ≤ z) = p", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.INV_NORM))
        add("erf(", "Gauss Error Function", "erf(x) = (2/√π)∫₀ˣ e^(-t²) dt", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.ERF))
        add("erfc(", "Complementary Erf", "erfc(x) = 1 − erf(x) tail integral", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.ERFC))
        add("Mean(", "Arithmetic Mean (a,b)", "(a + b) / 2 exact average", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.MEAN2))
        add("GMean(", "Geometric Mean (a,b)", "√(a · b) geometric average", CatalogCategory.PROBABILITY, CatalogAction.InsertFunction(MathFunction.GMEAN2))
        add("STAT", "Statistics Mode", "Open 1-Var & 2-Var data sheet", CatalogCategory.PROBABILITY, CatalogAction.SwitchMode(CalcMode.STAT, true))
        add("1-VAR", "1-Variable Stats", "Compute x̄, Σx, Σx², σx, sx, n, min, max", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("LinReg", "Linear Regression", "Fit y = A + Bx with correlation r", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("QuadReg", "Quadratic Regression", "Fit y = A + Bx + Cx²", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("LogReg", "Logarithmic Reg.", "Fit y = A + B·ln(x)", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("ExpReg", "Exponential Reg. e", "Fit y = A·e^(Bx)", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("ABExpReg", "Exponential Reg. A·B^x", "Fit y = A·B^x", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("PwrReg", "Power Regression", "Fit y = A·x^B", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("InvReg", "Inverse Regression", "Fit y = A + B/x", CatalogCategory.PROBABILITY, CatalogAction.RunCommand(CatalogCommand.OPEN_STAT_SHEET))
        add("P(−1)", "1σ Lower Tail", "Compute P(−1) ≈ 0.158655", CatalogCategory.PROBABILITY, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.DIST_P)
            CursorEngine.insertNumberLiteral(doc, "-1")
        })
        add("P(1.96)", "97.5% Z-Value", "Compute P(1.96) ≈ 0.975002", CatalogCategory.PROBABILITY, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.DIST_P)
            CursorEngine.insertNumberLiteral(doc, "1.96")
        })
        add("Φ⁻¹(0.95)", "95% Quantile", "Compute Φ⁻¹(0.95) ≈ 1.64485", CatalogCategory.PROBABILITY, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.INV_NORM)
            CursorEngine.insertNumberLiteral(doc, "0.95")
        })
        add("Φ⁻¹(0.975)", "97.5% Quantile", "Compute Φ⁻¹(0.975) ≈ 1.95996", CatalogCategory.PROBABILITY, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.INV_NORM)
            CursorEngine.insertNumberLiteral(doc, "0.975")
        })

        // 7. Complex Mode (10)
        add("CMPLX", "Complex Mode", "Switch to a + bi / r∠θ mode", CatalogCategory.COMPLEX, CatalogAction.SwitchMode(CalcMode.CMPLX))
        add("i", "Imaginary Unit i", "√(-1) in CMPLX mode", CatalogCategory.COMPLEX, CatalogAction.InsertConstant(MathConstant.ImaginaryI))
        add("∠", "Polar Angle Operator", "Enter polar complex r∠θ", CatalogCategory.COMPLEX, CatalogAction.InsertOperator(MathOperator.ANGLE, CalcMode.CMPLX))
        add("Conjg(", "Complex Conjugate", "a − bi conjugate of a + bi", CatalogCategory.COMPLEX, CatalogAction.InsertFunction(MathFunction.CONJG, CalcMode.CMPLX))
        add("arg(", "Complex Argument", "Principal phase angle θ in active unit", CatalogCategory.COMPLEX, CatalogAction.InsertFunction(MathFunction.ARG, CalcMode.CMPLX))
        add("|z|", "Complex Modulus", "Magnitude √(a² + b²)", CatalogCategory.COMPLEX, CatalogAction.InsertAbs)
        add("r∠θ⇔a+bi", "Toggle Polar/Rect", "Switch complex result format", CatalogCategory.COMPLEX, CatalogAction.RunCommand(CatalogCommand.TOGGLE_COMPLEX_POLAR))
        add("√z", "Complex Square Root", "Principal complex square root", CatalogCategory.COMPLEX, CatalogAction.InsertSqrt(false))
        add("z²", "Complex Square", "Square complex expression", CatalogCategory.COMPLEX, CatalogAction.InsertPower("2"))
        add("z⁻¹", "Complex Inverse", "Reciprocal 1/z", CatalogCategory.COMPLEX, CatalogAction.InsertPower("-1"))

        // 8. Matrix & Vector (18)
        add("MATRIX", "Matrix Studio", "Open 4×4 Matrix editor", CatalogCategory.MATRIX_VECTOR, CatalogAction.SwitchMode(CalcMode.MATRIX, true))
        add("MatA", "Matrix A", "Insert Matrix A reference", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertVariable(MathVariable.MAT_A, CalcMode.MATRIX))
        add("MatB", "Matrix B", "Insert Matrix B reference", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertVariable(MathVariable.MAT_B, CalcMode.MATRIX))
        add("MatC", "Matrix C", "Insert Matrix C reference", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertVariable(MathVariable.MAT_C, CalcMode.MATRIX))
        add("MatAns", "Matrix Answer", "Last matrix evaluation result", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertVariable(MathVariable.MAT_ANS, CalcMode.MATRIX))
        add("Det(", "Determinant", "Square matrix determinant Det(M)", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertFunction(MathFunction.DET, CalcMode.MATRIX))
        add("Trn(", "Transpose", "Matrix transpose Mᵀ", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertFunction(MathFunction.TRN, CalcMode.MATRIX))
        add("[2×2]", "Inline 2×2 Matrix", "Insert editable 2×2 matrix grid", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertMatrix(2, 2))
        add("[3×3]", "Inline 3×3 Matrix", "Insert editable 3×3 matrix grid", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertMatrix(3, 3))
        add("[4×4]", "Inline 4×4 Matrix", "Insert editable 4×4 matrix grid", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertMatrix(4, 4))
        add("MatA⁻¹", "Invert Matrix A", "Insert MatA⁻¹ template", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.MAT_A)
            CursorEngine.insertFixedPower(doc, "-1")
        })
        add("Det(MatA)", "Determinant of MatA", "Compute Det(MatA)", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            val doc = CursorEngine.insertFunction(d, MathFunction.DET)
            CursorEngine.insertVariable(doc, MathVariable.MAT_A)
        })
        add("Det(MatB)", "Determinant of MatB", "Compute Det(MatB)", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            val doc = CursorEngine.insertFunction(d, MathFunction.DET)
            CursorEngine.insertVariable(doc, MathVariable.MAT_B)
        })
        add("Trn(MatA)", "Transpose of MatA", "Compute Trn(MatA)", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            val doc = CursorEngine.insertFunction(d, MathFunction.TRN)
            CursorEngine.insertVariable(doc, MathVariable.MAT_A)
        })
        add("Trn(MatB)", "Transpose of MatB", "Compute Trn(MatB)", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            val doc = CursorEngine.insertFunction(d, MathFunction.TRN)
            CursorEngine.insertVariable(doc, MathVariable.MAT_B)
        })
        add("MatB⁻¹", "Invert Matrix B", "Insert MatB⁻¹ template", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            val doc = CursorEngine.insertVariable(d, MathVariable.MAT_B)
            CursorEngine.insertFixedPower(doc, "-1")
        })
        add("MatA×MatB", "Multiply MatA × MatB", "Matrix product MatA × MatB", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.MAT_A)
            doc = CursorEngine.insertOperator(doc, MathOperator.MULTIPLY)
            CursorEngine.insertVariable(doc, MathVariable.MAT_B)
        })
        add("MatA+MatB", "Add MatA + MatB", "Matrix sum MatA + MatB", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.MAT_A)
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            CursorEngine.insertVariable(doc, MathVariable.MAT_B)
        })
        add("MatA−MatB", "Subtract MatA − MatB", "Matrix difference MatA − MatB", CatalogCategory.MATRIX_VECTOR, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.MAT_A)
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            CursorEngine.insertVariable(doc, MathVariable.MAT_B)
        })
        add("VECTOR", "Vector Studio", "Open 2D/3D Vector editor & ops", CatalogCategory.MATRIX_VECTOR, CatalogAction.SwitchMode(CalcMode.VECTOR, true))
        add("VctA", "Vector A", "Open Vector Studio on VctA", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))
        add("VctB", "Vector B", "Open Vector Studio on VctB", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))
        add("VctC", "Vector C", "Open Vector Studio on VctC", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))
        add("|V|", "Vector Magnitude", "Euclidean norm |V| in Vector Studio", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))
        add("u·v", "Dot Product", "Scalar dot product in Vector Studio", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))
        add("u×v", "Cross Product", "3D vector cross product in Vector Studio", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))
        add("∠(u,v)", "Angle Between Vectors", "Angle cos⁻¹((u·v)/(|u||v|))", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))
        add("u+v", "Vector Addition", "Add vectors in Vector Studio", CatalogCategory.MATRIX_VECTOR, CatalogAction.RunCommand(CatalogCommand.OPEN_VECTOR_SHEET))

        // 9. Base-N & Bitwise Logic (16)
        add("BASE-N", "Base-N Mode", "Switch to DEC/HEX/BIN/OCT programmer mode", CatalogCategory.BASE_N, CatalogAction.SwitchMode(CalcMode.BASE_N))
        add("DEC", "Decimal Base (10)", "Display & enter Base-10 integers", CatalogCategory.BASE_N, CatalogAction.SetBaseN(NumberBase.DEC))
        add("HEX", "Hexadecimal Base (16)", "Display & enter Base-16 integers", CatalogCategory.BASE_N, CatalogAction.SetBaseN(NumberBase.HEX))
        add("BIN", "Binary Base (2)", "Display & enter Base-2 integers", CatalogCategory.BASE_N, CatalogAction.SetBaseN(NumberBase.BIN))
        add("OCT", "Octal Base (8)", "Display & enter Base-8 integers", CatalogCategory.BASE_N, CatalogAction.SetBaseN(NumberBase.OCT))
        add("AND", "Bitwise AND", "a AND b", CatalogCategory.BASE_N, CatalogAction.InsertOperator(MathOperator.LOGIC_AND, CalcMode.BASE_N))
        add("OR", "Bitwise OR", "a OR b", CatalogCategory.BASE_N, CatalogAction.InsertOperator(MathOperator.LOGIC_OR, CalcMode.BASE_N))
        add("XOR", "Bitwise XOR", "a XOR b", CatalogCategory.BASE_N, CatalogAction.InsertOperator(MathOperator.LOGIC_XOR, CalcMode.BASE_N))
        add("XNOR", "Bitwise XNOR", "a XNOR b", CatalogCategory.BASE_N, CatalogAction.InsertOperator(MathOperator.LOGIC_XNOR, CalcMode.BASE_N))
        add("Not(", "Bitwise Complement", "One's complement NOT", CatalogCategory.BASE_N, CatalogAction.InsertFunction(MathFunction.BIT_NOT, CalcMode.BASE_N))
        add("Neg(", "Two's Complement", "Two's complement negation", CatalogCategory.BASE_N, CatalogAction.InsertFunction(MathFunction.BIT_NEG, CalcMode.BASE_N))
        add("32/64b", "Toggle Bit Width", "Switch 32-bit / 64-bit signed arithmetic", CatalogCategory.BASE_N, CatalogAction.RunCommand(CatalogCommand.TOGGLE_BASE_WIDTH))
        add("HEX A", "Hex Digit A (10)", "Insert hex digit A in BASE-N", CatalogCategory.BASE_N, CatalogAction.InsertNumber("A"))
        add("HEX B", "Hex Digit B (11)", "Insert hex digit B in BASE-N", CatalogCategory.BASE_N, CatalogAction.InsertNumber("B"))
        add("HEX C", "Hex Digit C (12)", "Insert hex digit C in BASE-N", CatalogCategory.BASE_N, CatalogAction.InsertNumber("C"))
        add("HEX D", "Hex Digit D (13)", "Insert hex digit D in BASE-N", CatalogCategory.BASE_N, CatalogAction.InsertNumber("D"))
        add("HEX E", "Hex Digit E (14)", "Insert hex digit E in BASE-N", CatalogCategory.BASE_N, CatalogAction.InsertNumber("E"))
        add("HEX F", "Hex Digit F (15)", "Insert hex digit F in BASE-N", CatalogCategory.BASE_N, CatalogAction.InsertNumber("F"))

        // 10. Editable 2D Formula Templates (40)
        add("(-b+√Δ)/2a", "Quadratic Root (+)", "(-B + √(B² − 4AC)) / (2A)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertOperator(s, MathOperator.UNARY_MINUS)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    cur = CursorEngine.insertSqrt(cur)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.insertFixedPower(cur, "2")
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    cur = CursorEngine.insertNumberLiteral(cur, "4")
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    cur = CursorEngine.insertVariable(cur, MathVariable.C)
                    CursorEngine.moveRight(cur)
                },
                den = { s ->
                    val cur = CursorEngine.insertNumberLiteral(s, "2")
                    CursorEngine.insertVariable(cur, MathVariable.A)
                },
            )
        })
        add("(-b-√Δ)/2a", "Quadratic Root (−)", "(-B − √(B² − 4AC)) / (2A)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertOperator(s, MathOperator.UNARY_MINUS)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    cur = CursorEngine.insertSqrt(cur)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.insertFixedPower(cur, "2")
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    cur = CursorEngine.insertNumberLiteral(cur, "4")
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    cur = CursorEngine.insertVariable(cur, MathVariable.C)
                    CursorEngine.moveRight(cur)
                },
                den = { s ->
                    val cur = CursorEngine.insertNumberLiteral(s, "2")
                    CursorEngine.insertVariable(cur, MathVariable.A)
                },
            )
        })
        add("√(A²+B²)", "Pythagorean Hypotenuse", "√(A² + B²)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("√(B²-4AC)", "Discriminant √Δ", "√(B² − 4AC)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            doc = CursorEngine.insertNumberLiteral(doc, "4")
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            CursorEngine.insertVariable(doc, MathVariable.C)
        })
        add("πX²", "Circle Area", "π · r² using X as radius", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertConstant(d, MathConstant.Pi)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("2πX", "Circle Circumference", "2 · π · r using X", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertNumberLiteral(d, "2")
            doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
            CursorEngine.insertVariable(doc, MathVariable.X)
        })
        add("4πX²", "Sphere Surface Area", "4πr² using X", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertNumberLiteral(d, "4")
            doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("4/3πX³", "Sphere Volume", "(4/3)πr³ using X", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(d, { CursorEngine.insertNumberLiteral(it, "4") }, { CursorEngine.insertNumberLiteral(it, "3") })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            CursorEngine.insertFixedPower(doc, "3")
        })
        add("1/3πX²Y", "Cone Volume", "(1/3)πr²h using X=r, Y=h", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(d, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertNumberLiteral(it, "3") })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.insertFixedPower(doc, "2")
            CursorEngine.insertVariable(doc, MathVariable.Y)
        })
        add("πX²Y", "Cylinder Volume", "πr²h using X=r, Y=h", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertConstant(d, MathConstant.Pi)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.insertFixedPower(doc, "2")
            CursorEngine.insertVariable(doc, MathVariable.Y)
        })
        add("1/2XY", "Triangle Area", "½ · base · height (X, Y)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(d, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertNumberLiteral(it, "2") })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            CursorEngine.insertVariable(doc, MathVariable.Y)
        })
        add("√((C-A)²+(D-B)²)", "2D Distance", "Distance between (A,B) and (C,D)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertParens(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.C)
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertParens(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.D)
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("(D-B)/(C-A)", "Line Slope", "Slope m = (Y₂−Y₁)/(X₂−X₁) via (A,B),(C,D)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.D)
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.C)
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    CursorEngine.insertVariable(cur, MathVariable.A)
                },
            )
        })
        add("A(1+B)^C", "Compound Interest", "Principal A, rate B, periods C", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertParens(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertPower(doc)
            CursorEngine.insertVariable(doc, MathVariable.C)
        })
        add("A·e^(BC)", "Continuous Growth", "A·e^(rate B · time C)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertConstant(doc, MathConstant.EulerE)
            doc = CursorEngine.insertPower(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            CursorEngine.insertVariable(doc, MathVariable.C)
        })
        add("1/2AB²", "Kinetic Energy", "½ · m · v² (A=mass, B=velocity)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(d, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertNumberLiteral(it, "2") })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("AB/(A+B)", "Parallel Resistance", "R₁∥R₂ = (A·B)/(A+B)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
            )
        })
        add("1/(1+e^-X)", "Logistic Sigmoid", "σ(X) = 1 / (1 + e⁻ˣ)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "1")
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    cur = CursorEngine.insertConstant(cur, MathConstant.EulerE)
                    cur = CursorEngine.insertPower(cur)
                    cur = CursorEngine.insertOperator(cur, MathOperator.UNARY_MINUS)
                    CursorEngine.insertVariable(cur, MathVariable.X)
                },
            )
        })
        add("e^(-X²/2)", "Gaussian Kernel", "exp(-X² / 2)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertConstant(d, MathConstant.EulerE)
            doc = CursorEngine.insertPower(doc)
            doc = CursorEngine.insertOperator(doc, MathOperator.UNARY_MINUS)
            buildFractionTemplate(
                doc,
                num = { s ->
                    val cur = CursorEngine.insertVariable(s, MathVariable.X)
                    CursorEngine.insertFixedPower(cur, "2")
                },
                den = { CursorEngine.insertNumberLiteral(it, "2") },
            )
        })
        add("X·ln(X)", "Entropy Term", "X · ln(X)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.X)
            doc = CursorEngine.insertFunction(doc, MathFunction.LN)
            CursorEngine.insertVariable(doc, MathVariable.X)
        })
        add("sin²+cos²", "Trig Identity Check", "sin(X)² + cos(X)² = 1", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertFunction(d, MathFunction.SIN)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertFunction(doc, MathFunction.COS)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("∫₀¹ X² dx", "Sample Integral", "Definite integral of X² on [0, 1]", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertIntegral(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.moveDown(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "0")
            doc = CursorEngine.moveUp(doc)
            CursorEngine.insertNumberLiteral(doc, "1")
        })
        add("Σ₁¹⁰ X", "Sample Sum 1..10", "Sum of integers 1 to 10 (= 55)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSumProd(d, SumProdKind.SUM)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.moveUp(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "10")
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertVariable(doc, MathVariable.X)
        })
        add("Π₁⁵ X", "Sample Product 5!", "Product 1·2·3·4·5 (= 120)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSumProd(d, SumProdKind.PRODUCT)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.moveUp(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "5")
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertVariable(doc, MathVariable.X)
        })
        add("d/dx(X³)|₂", "Sample Derivative", "Derivative of X³ at X = 2 (= 12)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertDerivative(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.insertFixedPower(doc, "3")
            doc = CursorEngine.moveDown(doc)
            CursorEngine.insertNumberLiteral(doc, "2")
        })
        add("2π√(A/g)", "Pendulum Period", "T = 2π√(L / g) using A=Length", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertNumberLiteral(d, "2")
            doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
            doc = CursorEngine.insertSqrt(doc)
            val gInfo = ScientificConstants.byCode(35)!!
            buildFractionTemplate(
                doc,
                num = { CursorEngine.insertVariable(it, MathVariable.A) },
                den = { CursorEngine.insertConstant(it, MathConstant.PhysicalConstant(gInfo.code, gInfo.symbol)) },
            )
        })
        add("X·c0²", "Mass–Energy E=mc²", "Energy using X=mass in kg", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val cInfo = ScientificConstants.byCode(28)!!
            var doc = CursorEngine.insertVariable(d, MathVariable.X)
            doc = CursorEngine.insertConstant(doc, MathConstant.PhysicalConstant(cInfo.code, cInfo.symbol))
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("(A+B)/2", "Arithmetic Mean (A,B)", "Average of A and B", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { CursorEngine.insertNumberLiteral(it, "2") },
            )
        })
        add("√(AB)", "Geometric Mean (A,B)", "√(A · B)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            CursorEngine.insertVariable(doc, MathVariable.B)
        })
        add("2AB/(A+B)", "Harmonic Mean (A,B)", "2AB / (A + B)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "2")
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
            )
        })
        add("((B-A)/A)×100", "Percent Change", "% change from A to B", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.B)
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    CursorEngine.insertVariable(cur, MathVariable.A)
                },
                den = { CursorEngine.insertVariable(it, MathVariable.A) },
            )
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertOperator(doc, MathOperator.MULTIPLY)
            CursorEngine.insertNumberLiteral(doc, "100")
        })
        add("A(1-B/100)", "Discount Price", "Price A minus B% discount", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertParens(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            buildFractionTemplate(
                doc,
                num = { CursorEngine.insertVariable(it, MathVariable.B) },
                den = { CursorEngine.insertNumberLiteral(it, "100") },
            )
        })
        add("A(1+B/100)", "Tax / Markup Price", "Price A plus B% tax", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertParens(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            buildFractionTemplate(
                doc,
                num = { CursorEngine.insertVariable(it, MathVariable.B) },
                den = { CursorEngine.insertNumberLiteral(it, "100") },
            )
        })
        add("ln(X)/ln(2)", "Binary Log via ln", "Change-of-base log₂(X)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    val cur = CursorEngine.insertFunction(s, MathFunction.LN)
                    CursorEngine.insertVariable(cur, MathVariable.X)
                },
                den = { s ->
                    val cur = CursorEngine.insertFunction(s, MathFunction.LN)
                    CursorEngine.insertNumberLiteral(cur, "2")
                },
            )
        })
        add("C²=A²+B²-2ABcos", "Law of Cosines", "√(A² + B² − 2AB·cos(C))", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            doc = CursorEngine.insertNumberLiteral(doc, "2")
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.insertFunction(doc, MathFunction.COS)
            CursorEngine.insertVariable(doc, MathVariable.C)
        })
        add("(1+1/X)^X", "Euler Limit Approx", "(1 + 1/X)^X → e as X→∞", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertParens(d)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = buildFractionTemplate(doc, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertVariable(it, MathVariable.X) })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertPower(doc)
            CursorEngine.insertVariable(doc, MathVariable.X)
        })
        add("X/(1+X)", "Saturation Curve", "Michaelis–Menten normalized X/(1+X)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertVariable(it, MathVariable.X) },
                den = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "1")
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    CursorEngine.insertVariable(cur, MathVariable.X)
                },
            )
        })
        add("√(A²+B²+C²)", "3D Magnitude", "Euclidean norm √(A² + B² + C²)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.C)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("A×B/C", "Rule of Three", "Proportional scaling (A × B) ÷ C", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertOperator(cur, MathOperator.MULTIPLY)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { CursorEngine.insertVariable(it, MathVariable.C) },
            )
        })
        add("sinc(X)", "Sinc Function", "sin(X) / X", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    val cur = CursorEngine.insertFunction(s, MathFunction.SIN)
                    CursorEngine.insertVariable(cur, MathVariable.X)
                },
                den = { CursorEngine.insertVariable(it, MathVariable.X) },
            )
        })
        add("A·e^(-BC)", "Exponential Decay", "A · e^(-B·C)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertConstant(doc, MathConstant.EulerE)
            doc = CursorEngine.insertPower(doc)
            doc = CursorEngine.insertOperator(doc, MathOperator.UNARY_MINUS)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            CursorEngine.insertVariable(doc, MathVariable.C)
        })
        add("A/(1+B)^C", "Present Value", "Future value A discounted by (1+B)^C", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertVariable(it, MathVariable.A) },
                den = { s ->
                    var cur = CursorEngine.insertParens(s)
                    cur = CursorEngine.insertNumberLiteral(cur, "1")
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.moveRight(cur)
                    cur = CursorEngine.insertPower(cur)
                    CursorEngine.insertVariable(cur, MathVariable.C)
                },
            )
        })
        add("√(1-X²)", "Unit Semicircle", "Upper unit circle y = √(1 − X²)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("1/√(2π)", "Gaussian Norm Factor", "Standard normal prefactor 1 / √(2π)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    var cur = CursorEngine.insertSqrt(s)
                    cur = CursorEngine.insertNumberLiteral(cur, "2")
                    CursorEngine.insertConstant(cur, MathConstant.Pi)
                },
            )
        })
        add("A²−B²", "Diff of Squares", "A² − B² algebraic identity", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("(A+B)²", "Square of Sum", "(A + B)² binomial expansion", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertParens(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.moveRight(doc)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("1/A+1/B", "Inverse Sum", "1/A + 1/B (thin lens / resistors)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(d, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertVariable(it, MathVariable.A) })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            buildFractionTemplate(doc, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertVariable(it, MathVariable.B) })
        })
        add("∫₀^π sin X", "Half-Wave Sine Area", "∫₀^π sin(X) dx (= 2 in RAD)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertIntegral(d)
            doc = CursorEngine.insertFunction(doc, MathFunction.SIN)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.moveDown(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "0")
            doc = CursorEngine.moveUp(doc)
            CursorEngine.insertConstant(doc, MathConstant.Pi)
        })
        add("Σ₁¹⁰ X²", "Sum of Squares 1..10", "Σ X² for X = 1..10 (= 385)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSumProd(d, SumProdKind.SUM)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.moveUp(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "10")
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("d/dx(e^X)|₀", "Exp Slope at 0", "d/dx(e^X) at X = 0 (= 1)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertDerivative(d)
            doc = CursorEngine.insertConstant(doc, MathConstant.EulerE)
            doc = CursorEngine.insertPower(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.moveDown(doc)
            CursorEngine.insertNumberLiteral(doc, "0")
        })
        add("d/dx(sin X)|₀", "Sine Slope at 0", "d/dx(sin X) at X = 0 (= 1 in RAD)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertDerivative(d)
            doc = CursorEngine.insertFunction(doc, MathFunction.SIN)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.moveDown(doc)
            CursorEngine.insertNumberLiteral(doc, "0")
        })
        add("A·sin(BX+C)", "Harmonic Wave", "Amplitude A · sin(B·X + C)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertFunction(doc, MathFunction.SIN)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            CursorEngine.insertVariable(doc, MathVariable.C)
        })
        add("X·ln(X)−X", "Antiderivative of ln", "X·ln(X) − X", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.X)
            doc = CursorEngine.insertFunction(doc, MathFunction.LN)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            CursorEngine.insertVariable(doc, MathVariable.X)
        })
        add("1/nCr", "Hypergeometric Unit", "Binomial weight C(A, B)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertVariable(d, MathVariable.A)
            doc = CursorEngine.insertOperator(doc, MathOperator.COMBINATION)
            CursorEngine.insertVariable(doc, MathVariable.B)
        })
        add("2π√(A³/B)", "Kepler Period", "2π · √(a³ / μ) orbital period", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertNumberLiteral(d, "2")
            doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
            doc = CursorEngine.insertSqrt(doc)
            buildFractionTemplate(
                doc,
                num = { s ->
                    val cur = CursorEngine.insertVariable(s, MathVariable.A)
                    CursorEngine.insertFixedPower(cur, "3")
                },
                den = { CursorEngine.insertVariable(it, MathVariable.B) },
            )
        })
        add("(1+√5)/2", "Golden Ratio Exact", "Exact radical form (1 + √5)/2", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "1")
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    cur = CursorEngine.insertSqrt(cur)
                    CursorEngine.insertNumberLiteral(cur, "5")
                },
                den = { CursorEngine.insertNumberLiteral(it, "2") },
            )
        })
        add("1/√(1-v²/c²)", "Lorentz Factor γ", "Special relativity γ with A = velocity m/s", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val cInfo = ScientificConstants.byCode(28)!!
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    var cur = CursorEngine.insertSqrt(s)
                    cur = CursorEngine.insertNumberLiteral(cur, "1")
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    buildFractionTemplate(
                        cur,
                        num = { n ->
                            val v = CursorEngine.insertVariable(n, MathVariable.A)
                            CursorEngine.insertFixedPower(v, "2")
                        },
                        den = { dn ->
                            val c = CursorEngine.insertConstant(dn, MathConstant.PhysicalConstant(cInfo.code, cInfo.symbol))
                            CursorEngine.insertFixedPower(c, "2")
                        },
                    )
                },
            )
        })
        add("1/(2π√(AB))", "LC Resonant Freq", "f₀ = 1/(2π√(L·C)) with A=L(H), B=C(F)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "2")
                    cur = CursorEngine.insertConstant(cur, MathConstant.Pi)
                    cur = CursorEngine.insertSqrt(cur)
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
            )
        })
        add("1/(2πABC)", "RC Cutoff Freq", "f_c = 1/(2π·R·C) with A=R(Ω), B=C(F), C=1", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "2")
                    cur = CursorEngine.insertConstant(cur, MathConstant.Pi)
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
            )
        })
        add("A²/B", "Ohmic Power V²/R", "Electrical power P = V²/R (A=V, B=R)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    val cur = CursorEngine.insertVariable(s, MathVariable.A)
                    CursorEngine.insertFixedPower(cur, "2")
                },
                den = { CursorEngine.insertVariable(it, MathVariable.B) },
            )
        })
        add("10log(B/A)", "Power Gain (dB)", "Decibel gain 10·log₁₀(P₂ / P₁)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertNumberLiteral(d, "10")
            doc = CursorEngine.insertFunction(doc, MathFunction.LOG10)
            buildFractionTemplate(
                doc,
                num = { CursorEngine.insertVariable(it, MathVariable.B) },
                den = { CursorEngine.insertVariable(it, MathVariable.A) },
            )
        })
        add("20log(B/A)", "Voltage Gain (dB)", "Amplitude decibel 20·log₁₀(V₂ / V₁)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertNumberLiteral(d, "20")
            doc = CursorEngine.insertFunction(doc, MathFunction.LOG10)
            buildFractionTemplate(
                doc,
                num = { CursorEngine.insertVariable(it, MathVariable.B) },
                den = { CursorEngine.insertVariable(it, MathVariable.A) },
            )
        })
        add("A·B/(B+C)", "Voltage Divider", "V_out = V_in·R₂/(R₁+R₂) (A=Vin, B=R2, C=R1)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    val cur = CursorEngine.insertVariable(s, MathVariable.A)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.B)
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    CursorEngine.insertVariable(cur, MathVariable.C)
                },
            )
        })
        add("√(A²+B²)", "Impedance |Z|", "AC impedance √(R² + X²) (A=R, B=X)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("1/2AB²", "Capacitor Energy", "E = ½·C·V² (A=Capacitance, B=Voltage)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(d, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertNumberLiteral(it, "2") })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            CursorEngine.insertFixedPower(doc, "2")
        })
        add("h·X", "Photon Energy E=hν", "Planck constant h × frequency X (Hz)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val hInfo = ScientificConstants.byCode(6)!!
            val doc = CursorEngine.insertConstant(d, MathConstant.PhysicalConstant(hInfo.code, hInfo.symbol))
            CursorEngine.insertVariable(doc, MathVariable.X)
        })
        add("h·c0/X", "Photon Energy (λ)", "E = h·c₀ / λ using wavelength X (m)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val hInfo = ScientificConstants.byCode(6)!!
            val cInfo = ScientificConstants.byCode(28)!!
            buildFractionTemplate(
                d,
                num = { s ->
                    val cur = CursorEngine.insertConstant(s, MathConstant.PhysicalConstant(hInfo.code, hInfo.symbol))
                    CursorEngine.insertConstant(cur, MathConstant.PhysicalConstant(cInfo.code, cInfo.symbol))
                },
                den = { CursorEngine.insertVariable(it, MathVariable.X) },
            )
        })
        add("σ·A·X⁴", "Stefan-Boltzmann", "Blackbody power P = σ·Area(A)·T(X)⁴", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val sig = ScientificConstants.byCode(31)!!
            var doc = CursorEngine.insertConstant(d, MathConstant.PhysicalConstant(sig.code, sig.symbol))
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertVariable(doc, MathVariable.X)
            CursorEngine.insertFixedPower(doc, "4")
        })
        add("√(2GM/R)", "Escape Velocity", "v_esc = √(2·G·A / B) (A=mass, B=radius)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val gConst = ScientificConstants.byCode(39)!!
            var doc = CursorEngine.insertSqrt(d)
            buildFractionTemplate(
                doc,
                num = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "2")
                    cur = CursorEngine.insertConstant(cur, MathConstant.PhysicalConstant(gConst.code, gConst.symbol))
                    CursorEngine.insertVariable(cur, MathVariable.A)
                },
                den = { CursorEngine.insertVariable(it, MathVariable.B) },
            )
        })
        add("G·A·B/C²", "Newtonian Gravity", "Force F = G·m₁·m₂ / r² (A, B, C)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val gConst = ScientificConstants.byCode(39)!!
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertConstant(s, MathConstant.PhysicalConstant(gConst.code, gConst.symbol))
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { s ->
                    val cur = CursorEngine.insertVariable(s, MathVariable.C)
                    CursorEngine.insertFixedPower(cur, "2")
                },
            )
        })
        add("A·R·X/B", "Ideal Gas Pressure", "P = nRT/V with A=n(mol), X=T(K), B=V(m³)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val rConst = ScientificConstants.byCode(27)!!
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertConstant(cur, MathConstant.PhysicalConstant(rConst.code, rConst.symbol))
                    CursorEngine.insertVariable(cur, MathVariable.X)
                },
                den = { CursorEngine.insertVariable(it, MathVariable.B) },
            )
        })
        add("√(A²+B²−2ABcosC)", "Triangle Side", "Cosine law c = √(A²+B²−2ABcos(C))", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSqrt(d)
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.insertFixedPower(doc, "2")
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            doc = CursorEngine.insertNumberLiteral(doc, "2")
            doc = CursorEngine.insertVariable(doc, MathVariable.A)
            doc = CursorEngine.insertVariable(doc, MathVariable.B)
            doc = CursorEngine.insertFunction(doc, MathFunction.COS)
            CursorEngine.insertVariable(doc, MathVariable.C)
        })
        add("(B/A)^(1/C)−1", "CAGR Growth Rate", "Annualized return (End B / Start A)^(1/C) − 1", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertParens(d)
            doc = buildFractionTemplate(doc, { CursorEngine.insertVariable(it, MathVariable.B) }, { CursorEngine.insertVariable(it, MathVariable.A) })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertPower(doc)
            doc = buildFractionTemplate(doc, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertVariable(it, MathVariable.C) })
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
            CursorEngine.insertNumberLiteral(doc, "1")
        })
        add("A·B(1+B)^C/..", "Loan EMI Payment", "Monthly payment P=A, rate/mo=B, months=C", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.insertParens(cur)
                    cur = CursorEngine.insertNumberLiteral(cur, "1")
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.moveRight(cur)
                    cur = CursorEngine.insertPower(cur)
                    CursorEngine.insertVariable(cur, MathVariable.C)
                },
                den = { s ->
                    var cur = CursorEngine.insertParens(s)
                    cur = CursorEngine.insertNumberLiteral(cur, "1")
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.moveRight(cur)
                    cur = CursorEngine.insertPower(cur)
                    cur = CursorEngine.insertVariable(cur, MathVariable.C)
                    cur = CursorEngine.moveRight(cur)
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    CursorEngine.insertNumberLiteral(cur, "1")
                },
            )
        })
        add("(X−A)/B", "Standard Z-Score", "Z = (Value X − Mean A) / StdDev B", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.X)
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    CursorEngine.insertVariable(cur, MathVariable.A)
                },
                den = { CursorEngine.insertVariable(it, MathVariable.B) },
            )
        })
        add("1/√(2πB²)e^..", "Normal PDF μ=A,σ=B", "General Gaussian density at X", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = buildFractionTemplate(
                d,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.B)
                    cur = CursorEngine.insertSqrt(cur)
                    cur = CursorEngine.insertNumberLiteral(cur, "2")
                    CursorEngine.insertConstant(cur, MathConstant.Pi)
                },
            )
            doc = CursorEngine.moveRight(doc)
            doc = CursorEngine.insertConstant(doc, MathConstant.EulerE)
            doc = CursorEngine.insertPower(doc)
            doc = CursorEngine.insertOperator(doc, MathOperator.UNARY_MINUS)
            buildFractionTemplate(
                doc,
                num = { s ->
                    var cur = CursorEngine.insertParens(s)
                    cur = CursorEngine.insertVariable(cur, MathVariable.X)
                    cur = CursorEngine.insertOperator(cur, MathOperator.SUBTRACT)
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    cur = CursorEngine.moveRight(cur)
                    CursorEngine.insertFixedPower(cur, "2")
                },
                den = { s ->
                    var cur = CursorEngine.insertNumberLiteral(s, "2")
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    CursorEngine.insertFixedPower(cur, "2")
                },
            )
        })
        add("1/2mv²/g", "Projectile Max H", "Max height H = (A·sin(B))² / (2g)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val gInfo = ScientificConstants.byCode(35)!!
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertParens(s)
                    cur = CursorEngine.insertVariable(cur, MathVariable.A)
                    cur = CursorEngine.insertFunction(cur, MathFunction.SIN)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    cur = CursorEngine.moveRight(cur)
                    cur = CursorEngine.moveRight(cur)
                    CursorEngine.insertFixedPower(cur, "2")
                },
                den = { s ->
                    val cur = CursorEngine.insertNumberLiteral(s, "2")
                    CursorEngine.insertConstant(cur, MathConstant.PhysicalConstant(gInfo.code, gInfo.symbol))
                },
            )
        })
        add("A²sin(2B)/g", "Projectile Range", "Horizontal range R = v₀²·sin(2θ) / g", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val gInfo = ScientificConstants.byCode(35)!!
            buildFractionTemplate(
                d,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertFixedPower(cur, "2")
                    cur = CursorEngine.insertFunction(cur, MathFunction.SIN)
                    cur = CursorEngine.insertNumberLiteral(cur, "2")
                    CursorEngine.insertVariable(cur, MathVariable.B)
                },
                den = { CursorEngine.insertConstant(it, MathConstant.PhysicalConstant(gInfo.code, gInfo.symbol)) },
            )
        })
        add("√((A²+B²)/2)", "RMS Root-Mean-Sq", "RMS average √((A² + B²) / 2)", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            val doc = CursorEngine.insertSqrt(d)
            buildFractionTemplate(
                doc,
                num = { s ->
                    var cur = CursorEngine.insertVariable(s, MathVariable.A)
                    cur = CursorEngine.insertFixedPower(cur, "2")
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    cur = CursorEngine.insertVariable(cur, MathVariable.B)
                    CursorEngine.insertFixedPower(cur, "2")
                },
                den = { CursorEngine.insertNumberLiteral(it, "2") },
            )
        })
        add("1/((1/A)+(1/B))", "Thin Lens Focal", "Combined focal length (1/f₁ + 1/f₂)⁻¹", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    var cur = buildFractionTemplate(s, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertVariable(it, MathVariable.A) })
                    cur = CursorEngine.moveRight(cur)
                    cur = CursorEngine.insertOperator(cur, MathOperator.ADD)
                    buildFractionTemplate(cur, { CursorEngine.insertNumberLiteral(it, "1") }, { CursorEngine.insertVariable(it, MathVariable.B) })
                },
            )
        })
        add("ln(2)/X", "Half-Life Decay λ", "Decay constant λ = ln(2) / half-life X", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    val cur = CursorEngine.insertFunction(s, MathFunction.LN)
                    CursorEngine.insertNumberLiteral(cur, "2")
                },
                den = { CursorEngine.insertVariable(it, MathVariable.X) },
            )
        })
        add("∫₀^∞ e^(-X²)", "Gaussian Integral", "√π / 2 exact definite benchmark", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            buildFractionTemplate(
                d,
                num = { s ->
                    val cur = CursorEngine.insertSqrt(s)
                    CursorEngine.insertConstant(cur, MathConstant.Pi)
                },
                den = { CursorEngine.insertNumberLiteral(it, "2") },
            )
        })
        add("Σ₁⁸ 1/X²", "Basel Series Partial", "Σ 1/X² for X = 1..8 ≈ π²/6", CatalogCategory.FORMULAS, CatalogAction.InsertTemplate { d ->
            var doc = CursorEngine.insertSumProd(d, SumProdKind.SUM)
            doc = CursorEngine.insertNumberLiteral(doc, "1")
            doc = CursorEngine.moveUp(doc)
            doc = CursorEngine.insertNumberLiteral(doc, "8")
            doc = CursorEngine.moveRight(doc)
            buildFractionTemplate(
                doc,
                num = { CursorEngine.insertNumberLiteral(it, "1") },
                den = { s ->
                    val cur = CursorEngine.insertVariable(s, MathVariable.X)
                    CursorEngine.insertFixedPower(cur, "2")
                },
            )
        })

        // 11. 60 Scientific Constants
        ScientificConstants.constants.forEach { info ->
            add(
                symbol = info.symbol,
                name = "CONST ${info.code.toString().padStart(2, '0')}: ${info.name}",
                description = "${info.value.toEngineeringString()} ${info.unit}".trim(),
                category = CatalogCategory.CONSTANTS,
                action = CatalogAction.InsertConstant(MathConstant.PhysicalConstant(info.code, info.symbol)),
            )
        }

        // 12. 80 Unit Conversions
        ScientificConstants.conversions.forEach { conv ->
            add(
                symbol = conv.label,
                name = "CONV ${conv.code.toString().padStart(2, '0')}: ${conv.description}",
                description = "Convert current expression or Ans (${conv.label})",
                category = CatalogCategory.CONVERSIONS,
                action = CatalogAction.ApplyConversion(conv),
            )
        }

        // 13. Memory, Variables, Display Formats, Themes & Keypad Actions (52)
        listOf(
            MathVariable.A, MathVariable.B, MathVariable.C,
            MathVariable.D, MathVariable.E, MathVariable.F,
            MathVariable.X, MathVariable.Y, MathVariable.M,
            MathVariable.ANS, MathVariable.PRE_ANS,
        ).forEach { v ->
            add(v.symbol, "Variable ${v.symbol}", "Insert register ${v.symbol}", CatalogCategory.MEMORY_SETUP, CatalogAction.InsertVariable(v))
        }
        add("M+", "Memory Add", "Add expression/result to register M", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.M_PLUS))
        add("M-", "Memory Subtract", "Subtract expression/result from register M", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.M_MINUS))
        add("CLR MEM", "Clear All Memories", "Reset A–F, X, Y, M, Ans to 0", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.CLEAR_MEMORY))
        add("Norm1", "Normal Format 1", "Scientific notation below 10⁻²", CatalogCategory.MEMORY_SETUP, CatalogAction.SetNumberFormat(NumberFormatMode.Norm1))
        add("Norm2", "Normal Format 2", "Scientific notation below 10⁻⁹", CatalogCategory.MEMORY_SETUP, CatalogAction.SetNumberFormat(NumberFormatMode.Norm2))
        for (d in 0..9) {
            add("Fix $d", "Fixed $d Decimals", "Round display to $d decimal places", CatalogCategory.MEMORY_SETUP, CatalogAction.SetNumberFormat(NumberFormatMode.Fix(d)))
        }
        for (s in 1..9) {
            add("Sci $s", "Scientific $s Digits", "Display $s significant digits", CatalogCategory.MEMORY_SETUP, CatalogAction.SetNumberFormat(NumberFormatMode.Sci(s)))
        }
        add("ENG→", "Engineering Shift +", "Shift exponent by ×10⁻³", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.ENG_RIGHT))
        add("←ENG", "Engineering Shift −", "Shift exponent by ×10⁺³", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.ENG_LEFT))
        add("COMP", "COMP Mode", "Standard scientific & calculus mode", CatalogCategory.MEMORY_SETUP, CatalogAction.SwitchMode(CalcMode.COMP))
        add("EQN", "Equation Mode", "Open Equation Solver studio", CatalogCategory.MEMORY_SETUP, CatalogAction.SwitchMode(CalcMode.EQN, true))
        CalcThemePreset.entries.forEach { preset ->
            add("Theme", preset.label, "Switch chassis & LCD theme", CatalogCategory.MEMORY_SETUP, CatalogAction.SetTheme(preset))
        }
        add("HIST", "History Tape", "Toggle saved calculation tape", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.TOGGLE_HISTORY))
        add("DEL", "Delete", "Delete item before cursor", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.DELETE_CHAR))
        add("AC", "All Clear", "Clear expression while keeping memory", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.ALL_CLEAR))
        add("=", "Evaluate", "Execute current expression", CatalogCategory.MEMORY_SETUP, CatalogAction.RunCommand(CatalogCommand.EVALUATE))

        // Add binary arithmetic operators for completeness in catalog
        add("+", "Addition", "Add two expressions", CatalogCategory.POWERS_LOGS, CatalogAction.InsertOperator(MathOperator.ADD))
        add("−", "Subtraction", "Subtract two expressions", CatalogCategory.POWERS_LOGS, CatalogAction.InsertOperator(MathOperator.SUBTRACT))
        add("×", "Multiplication", "Multiply two expressions", CatalogCategory.POWERS_LOGS, CatalogAction.InsertOperator(MathOperator.MULTIPLY))
        add("÷", "Division", "Divide two expressions", CatalogCategory.POWERS_LOGS, CatalogAction.InsertOperator(MathOperator.DIVIDE))

        return list
    }

    private fun buildFractionTemplate(
        doc: AstDocument,
        num: (AstDocument) -> AstDocument,
        den: (AstDocument) -> AstDocument,
    ): AstDocument {
        var cur = CursorEngine.insertFraction(doc)
        cur = num(cur)
        cur = CursorEngine.moveDown(cur)
        return den(cur)
    }
}
