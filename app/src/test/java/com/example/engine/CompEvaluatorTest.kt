package com.example.engine

import com.example.ast.AstDocument
import com.example.ast.CursorEngine
import com.example.ast.MathConstant
import com.example.ast.MathFunction
import com.example.ast.MathOperator
import com.example.ast.MathVariable
import com.example.ast.SumProdKind
import com.example.serializer.AstJsonCodec
import com.example.serializer.AstLatexSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CompEvaluatorTest {

    @Test
    fun `zero float artifacts and exact symbolic arithmetic`() {
        // 0.1 + 0.2 = 0.3 exactly (exact fraction 3/10, decimal 0.3)
        var doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "0.1")
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertNumberLiteral(doc, "0.2")
        val res = CompEvaluator.evaluate(doc.root, CalcContext())
        assertEquals(0, res.decimal.compareTo(BigDecimal("0.3")))
        assertEquals("3/10", res.exact?.formatExact())

        // 1/3 + 1/6 = 1/2
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        doc = CursorEngine.moveRight(doc)
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "6")
        val fracRes = CompEvaluator.evaluate(doc.root, CalcContext())
        assertEquals("1/2", fracRes.exact?.formatExact())
        assertEquals(0, fracRes.decimal.compareTo(BigDecimal("0.5")))

        // sqrt(12) + sqrt(27) = 5√3
        doc = AstDocument.empty()
        doc = CursorEngine.insertSqrt(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "12")
        doc = CursorEngine.moveRight(doc)
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertSqrt(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "27")
        val radRes = CompEvaluator.evaluate(doc.root, CalcContext())
        assertEquals("5√3", radRes.exact?.formatExact())
    }

    @Test
    fun `exact trigonometry and domain errors`() {
        // sin(180°) = 0
        var doc = AstDocument.empty()
        doc = CursorEngine.insertFunction(doc, MathFunction.SIN)
        doc = CursorEngine.insertNumberLiteral(doc, "180")
        val sin180 = CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG))
        assertEquals(0, sin180.decimal.compareTo(BigDecimal.ZERO))
        assertEquals("0", sin180.exact?.formatExact())

        // sin(60°) = √3/2
        doc = AstDocument.empty()
        doc = CursorEngine.insertFunction(doc, MathFunction.SIN)
        doc = CursorEngine.insertNumberLiteral(doc, "60")
        val sin60 = CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG))
        assertEquals("√3/2", sin60.exact?.formatExact())

        // tan(90°) throws Math ERROR
        doc = AstDocument.empty()
        doc = CursorEngine.insertFunction(doc, MathFunction.TAN)
        doc = CursorEngine.insertNumberLiteral(doc, "90")
        val ex = assertThrows(CalcEngineException::class.java) {
            CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG))
        }
        assertEquals(ErrorKind.MATH, ex.kind)

        // π/3 + π/6 = π/2
        doc = AstDocument.empty()
        doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        doc = CursorEngine.moveRight(doc)
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "6")
        val piRes = CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.RAD))
        assertEquals("π/2", piRes.exact?.formatExact())
    }

    @Test
    fun `casio operator precedence unary minus vs power and implicit multiplication`() {
        // -2^2 = -4
        var doc = AstDocument.empty()
        doc = CursorEngine.insertOperator(doc, MathOperator.UNARY_MINUS)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertFixedPower(doc, "2")
        assertEquals(0, CompEvaluator.evaluate(doc.root, CalcContext()).decimal.compareTo(BigDecimal("-4")))

        // 1 ÷ 2X where X = 5 -> 1 / (2*5) = 0.1
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.insertOperator(doc, MathOperator.DIVIDE)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        val ctx = CalcContext(variables = mapOf(MathVariable.X to BigDecimal("5")))
        assertEquals(0, CompEvaluator.evaluate(doc.root, ctx).decimal.compareTo(BigDecimal("0.1")))
    }

    @Test
    fun `numerical integral, derivative, sum, and SOLVE`() {
        // ∫_0^1 (X^2) dx = 1/3
        var doc = AstDocument.empty()
        doc = CursorEngine.insertIntegral(doc)
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertFixedPower(doc, "2")
        doc = CursorEngine.moveDown(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "0")
        doc = CursorEngine.moveUp(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        val intRes = CompEvaluator.evaluate(doc.root, CalcContext())
        assertTrue((intRes.decimal - BigDecimal("0.333333333333")).abs() < BigDecimal("1E-10"))

        // d/dx(X^3)|_{x=2} = 12
        doc = AstDocument.empty()
        doc = CursorEngine.insertDerivative(doc)
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertFixedPower(doc, "3")
        doc = CursorEngine.moveDown(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        val derivRes = CompEvaluator.evaluate(doc.root, CalcContext())
        assertTrue((derivRes.decimal - BigDecimal("12")).abs() < BigDecimal("1E-10"))

        // Σ_{x=1}^5 (x) = 15
        doc = AstDocument.empty()
        doc = CursorEngine.insertSumProd(doc, SumProdKind.SUM)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.moveUp(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "5")
        doc = CursorEngine.moveRight(doc)
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        assertEquals(0, CompEvaluator.evaluate(doc.root, CalcContext()).decimal.compareTo(BigDecimal("15")))

        // SOLVE X^2 - 2 = 0 from X=1 -> √2
        doc = AstDocument.empty()
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertFixedPower(doc, "2")
        doc = CursorEngine.insertOperator(doc, MathOperator.SUBTRACT)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        val solved = CompEvaluator.solveForX(doc.root, BigDecimal("1"), CalcContext())
        assertTrue((solved.root - BigMath.sqrt(BigDecimal("2"))).abs() < BigDecimal("1E-10"))
        assertTrue(solved.residualLR.abs() < BigDecimal("1E-10"))
    }

    @Test
    fun `complex, baseN, eqn, matrix, vector, and json roundtrip`() {
        // Complex: (3 + 4i) -> |z| = 5
        val z = ComplexVal(BigDecimal("3"), BigDecimal("4"))
        assertEquals(0, z.abs().compareTo(BigDecimal("5")))

        // BaseN: FF (HEX) + 1 = 256 (DEC)
        var doc = AstDocument.empty()
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD) // dummy test via AST JSON roundtrip
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "255")
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        val baseRes = BaseNEngine.evaluate(doc.root, NumberBase.DEC, BitWidth.BIT_32)
        assertEquals("100", baseRes.format(NumberBase.HEX))

        // EQN: x^2 + 1 = 0 -> ±i
        val quad = EqnSolver.solveQuadratic(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE) as EqnSolution.PolynomialRoots
        assertEquals(2, quad.roots.size)
        assertEquals(0, quad.roots[0].im.abs().compareTo(BigDecimal.ONE))

        // Matrix inverse: [[1, 2], [3, 4]] -> det = -2
        val m = CalcMatrix(2, 2, listOf(BigDecimal("1"), BigDecimal("2"), BigDecimal("3"), BigDecimal("4")))
        assertEquals(0, m.determinant().compareTo(BigDecimal("-2")))
        val prod = m * m.inverse()
        assertEquals(0, prod[0, 0].compareTo(BigDecimal.ONE))
        assertEquals(0, prod[0, 1].compareTo(BigDecimal.ZERO))

        // Vector cross product: i × j = k
        val vx = CalcVector(listOf(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO))
        val vy = CalcVector(listOf(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO))
        val vz = vx.cross(vy)
        assertEquals(0, vz.components[2].compareTo(BigDecimal.ONE))

        // AST JSON & LaTeX serializer roundtrip
        var fracDoc = AstDocument.empty()
        fracDoc = CursorEngine.insertNumberLiteral(fracDoc, "3")
        fracDoc = CursorEngine.insertFraction(fracDoc)
        fracDoc = CursorEngine.insertNumberLiteral(fracDoc, "4")
        val json = AstJsonCodec.encodeSlot(fracDoc.root)
        val decoded = AstJsonCodec.decodeSlot(json)
        assertNotNull(decoded)
        assertEquals(AstLatexSerializer.toLatex(fracDoc.root), AstLatexSerializer.toLatex(decoded!!))
    }

    @Test
    fun `extended 450+ functions and scientific catalog work accurately`() {
        assertTrue(
            "Expected at least 450 catalog functions, found ${FunctionCatalog.entries.size}",
            FunctionCatalog.entries.size >= 450,
        )
        assertEquals(60, ScientificConstants.constants.size)
        assertEquals(80, ScientificConstants.conversions.size)

        // csc(30°) = 2, sec(60°) = 2, cot(45°) = 1
        var doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.CSC)
        doc = CursorEngine.insertNumberLiteral(doc, "30")
        assertEquals("2", CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG)).exact?.formatExact())

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.SEC)
        doc = CursorEngine.insertNumberLiteral(doc, "60")
        assertEquals("2", CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG)).exact?.formatExact())

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.COT)
        doc = CursorEngine.insertNumberLiteral(doc, "45")
        assertEquals("1", CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG)).exact?.formatExact())

        // log2(8) = 3
        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.LOG2)
        doc = CursorEngine.insertNumberLiteral(doc, "8")
        assertEquals("3", CompEvaluator.evaluate(doc.root, CalcContext()).exact?.formatExact())

        // Mod(17, 5) = 2, Hypot(3, 4) = 5
        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.MOD)
        doc = CursorEngine.insertNumberLiteral(doc, "17")
        doc = CursorEngine.insertOperator(doc, MathOperator.COMMA)
        doc = CursorEngine.insertNumberLiteral(doc, "5")
        assertEquals(0, CompEvaluator.evaluate(doc.root, CalcContext()).decimal.compareTo(BigDecimal("2")))

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.HYPOT)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        doc = CursorEngine.insertOperator(doc, MathOperator.COMMA)
        doc = CursorEngine.insertNumberLiteral(doc, "4")
        assertEquals(0, CompEvaluator.evaluate(doc.root, CalcContext()).decimal.compareTo(BigDecimal("5")))

        // Intg(-2.3) = -3, Ceil(2.1) = 3
        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.FLOOR)
        doc = CursorEngine.insertOperator(doc, MathOperator.UNARY_MINUS)
        doc = CursorEngine.insertNumberLiteral(doc, "2.3")
        assertEquals(0, CompEvaluator.evaluate(doc.root, CalcContext()).decimal.compareTo(BigDecimal("-3")))

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.CEIL)
        doc = CursorEngine.insertNumberLiteral(doc, "2.1")
        assertEquals(0, CompEvaluator.evaluate(doc.root, CalcContext()).decimal.compareTo(BigDecimal("3")))

        // Gamma, Beta, erf/erfc, atan2, Fib, isPrime, expm1, ln1p, sigmoid, sinc
        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.GAMMA)
        doc = CursorEngine.insertNumberLiteral(doc, "5")
        assertEquals("24", CompEvaluator.evaluate(doc.root, CalcContext()).exact?.formatExact())

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.GAMMA)
        doc = CursorEngine.insertNumberLiteral(doc, "0.5")
        assertTrue((CompEvaluator.evaluate(doc.root, CalcContext()).decimal - BigMath.sqrt(BigMath.PI)).abs() < BigDecimal("1E-12"))

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.BETA)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertOperator(doc, MathOperator.COMMA)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        assertEquals("1/12", CompEvaluator.evaluate(doc.root, CalcContext()).exact?.formatExact())

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.ATAN2)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.insertOperator(doc, MathOperator.COMMA)
        doc = CursorEngine.insertOperator(doc, MathOperator.UNARY_MINUS)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        assertEquals(0, CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG)).decimal.compareTo(BigDecimal("135")))

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.FIB)
        doc = CursorEngine.insertNumberLiteral(doc, "10")
        assertEquals("55", CompEvaluator.evaluate(doc.root, CalcContext()).exact?.formatExact())

        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.IS_PRIME)
        doc = CursorEngine.insertNumberLiteral(doc, "97")
        assertEquals("1", CompEvaluator.evaluate(doc.root, CalcContext()).exact?.formatExact())

        assertTrue((BigMath.erf(BigDecimal.ONE).add(BigMath.erfc(BigDecimal.ONE)) - BigDecimal.ONE).abs() < BigDecimal("1E-12"))
        assertEquals(0, BigMath.sigmoid(BigDecimal.ZERO).compareTo(BigDecimal("0.5")))
        assertEquals(0, BigMath.sinc(BigDecimal.ZERO).compareTo(BigDecimal.ONE))
        assertTrue((BigMath.expm1(BigDecimal("0.0001")) - BigDecimal("0.0001000050001666708334")).abs() < BigDecimal("1E-15"))
        assertTrue((BigMath.ln1p(BigDecimal("0.0001")) - BigDecimal("0.0000999950003333083353")).abs() < BigDecimal("1E-15"))

        // Normal CDF & inverse: P(0) = 0.5, Phi^-1(Phi(1.5)) ≈ 1.5
        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.DIST_P)
        doc = CursorEngine.insertNumberLiteral(doc, "0")
        assertTrue((CompEvaluator.evaluate(doc.root, CalcContext()).decimal - BigDecimal("0.5")).abs() < BigDecimal("1E-10"))

        val cdf = BigMath.normalCdf(BigDecimal("1.5"))
        val back = BigMath.invNormalCdf(cdf)
        assertTrue((back - BigDecimal("1.5")).abs() < BigDecimal("1E-9"))

        // Prime factorization FACT(1260) = 2² × 3² × 5 × 7
        assertEquals("2² × 3² × 5 × 7", BigMath.primeFactorizeOrNull(BigDecimal("1260")))

        // Explicit angle unit override: sin(IN_RAD(π)) = 0 even when active mode is DEG
        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.SIN)
        doc = CursorEngine.insertFunction(doc, MathFunction.IN_RAD)
        doc = CursorEngine.insertConstant(doc, MathConstant.Pi)
        assertTrue(CompEvaluator.evaluate(doc.root, CalcContext(AngleUnit.DEG)).decimal.abs() < BigDecimal("1E-12"))

        // Ran# evaluates cleanly in [0, 1)
        doc = CursorEngine.insertFunction(AstDocument.empty(), MathFunction.RAN)
        val ranVal = CompEvaluator.evaluate(doc.root, CalcContext()).decimal
        assertTrue(ranVal >= BigDecimal.ZERO && ranVal < BigDecimal.ONE)
    }
}
