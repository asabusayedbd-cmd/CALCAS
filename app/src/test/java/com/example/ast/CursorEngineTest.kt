package com.example.ast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorEngineTest {

    // -------------------------------------------------------------------------
    // Helper: Render AST + '|' cursor marker for crisp structural assertions
    // -------------------------------------------------------------------------

    private fun renderWithCursor(doc: AstDocument): String {
        val sb = StringBuilder()
        renderSlot(doc.root, ROOT_NODE_ID, SlotRole.Root, doc.cursor, sb)
        return sb.toString()
    }

    private fun renderSlot(
        slot: ExprSlot,
        ownerId: Long,
        role: SlotRole,
        cursor: CursorPosition,
        sb: StringBuilder,
    ) {
        val activeHere = cursor.ownerId == ownerId && cursor.role == role
        slot.children.forEachIndexed { idx, node ->
            if (activeHere && cursor.index == idx && cursor.charOffset == 0) {
                sb.append('|')
            }
            renderNode(node, activeHere && cursor.index == idx, cursor.charOffset, cursor, sb)
        }
        if (activeHere && cursor.index == slot.size) {
            sb.append('|')
        }
    }

    private fun renderNode(
        node: AstNode,
        cursorInsideThisLeaf: Boolean,
        charOffset: Int,
        cursor: CursorPosition,
        sb: StringBuilder,
    ) {
        when (node) {
            is NumberNode -> {
                if (cursorInsideThisLeaf && charOffset in 1 until node.value.length) {
                    sb.append(node.value.substring(0, charOffset))
                    sb.append('|')
                    sb.append(node.value.substring(charOffset))
                } else {
                    sb.append(node.value)
                }
            }
            is OperatorNode -> sb.append(node.operator.symbol)
            is ConstantNode -> sb.append(node.constant.symbol)
            is VariableNode -> sb.append(node.variable.symbol)
            is ParenNode -> {
                sb.append('(')
                renderSlot(node.body, node.id, SlotRole.Body, cursor, sb)
                sb.append(')')
            }
            is AbsNode -> {
                sb.append('|')
                renderSlot(node.body, node.id, SlotRole.Body, cursor, sb)
                sb.append('|')
            }
            is FractionNode -> {
                if (node.whole != null) {
                    sb.append("mix(")
                    renderSlot(node.whole, node.id, SlotRole.Whole, cursor, sb)
                    sb.append(',')
                } else {
                    sb.append("frac(")
                }
                renderSlot(node.numerator, node.id, SlotRole.Numerator, cursor, sb)
                sb.append('/')
                renderSlot(node.denominator, node.id, SlotRole.Denominator, cursor, sb)
                sb.append(')')
            }
            is PowerNode -> {
                sb.append("pow(")
                renderSlot(node.base, node.id, SlotRole.Base, cursor, sb)
                sb.append('^')
                renderSlot(node.exponent, node.id, SlotRole.Exponent, cursor, sb)
                sb.append(')')
            }
            is SqrtNode -> {
                sb.append("sqrt")
                if (node.index != null) {
                    sb.append('[')
                    renderSlot(node.index, node.id, SlotRole.Index, cursor, sb)
                    sb.append(']')
                }
                sb.append('(')
                renderSlot(node.radicand, node.id, SlotRole.Radicand, cursor, sb)
                sb.append(')')
            }
            is LogNode -> {
                sb.append("log[")
                renderSlot(node.base, node.id, SlotRole.Base, cursor, sb)
                sb.append("](")
                renderSlot(node.arg, node.id, SlotRole.Arg, cursor, sb)
                sb.append(')')
            }
            is FunctionNode -> {
                sb.append(node.function.displayName).append('(')
                renderSlot(node.arg, node.id, SlotRole.Arg, cursor, sb)
                if (node.secondArg != null) {
                    sb.append(',')
                    renderSlot(node.secondArg, node.id, SlotRole.SecondArg, cursor, sb)
                }
                sb.append(')')
            }
            is IntegralNode -> {
                sb.append("int[")
                renderSlot(node.lower, node.id, SlotRole.Lower, cursor, sb)
                sb.append("..")
                renderSlot(node.upper, node.id, SlotRole.Upper, cursor, sb)
                sb.append("](")
                renderSlot(node.expr, node.id, SlotRole.Expr, cursor, sb)
                sb.append(')')
            }
            is DerivativeNode -> {
                sb.append("ddx(")
                renderSlot(node.expr, node.id, SlotRole.Expr, cursor, sb)
                sb.append("|x=")
                renderSlot(node.atX, node.id, SlotRole.AtX, cursor, sb)
                sb.append(')')
            }
            is SumProdNode -> {
                sb.append(node.kind.symbol).append('[')
                renderSlot(node.lower, node.id, SlotRole.Lower, cursor, sb)
                sb.append("..")
                renderSlot(node.upper, node.id, SlotRole.Upper, cursor, sb)
                sb.append("](")
                renderSlot(node.expr, node.id, SlotRole.Expr, cursor, sb)
                sb.append(')')
            }
            is MatrixNode -> {
                sb.append("mat(")
                for (r in 0 until node.rows) {
                    if (r > 0) sb.append(';')
                    for (c in 0 until node.cols) {
                        if (c > 0) sb.append(',')
                        renderSlot(node.cells[r * node.cols + c], node.id, SlotRole.MatrixCell(r, c), cursor, sb)
                    }
                }
                sb.append(')')
            }
        }
    }

    @Test
    fun `number entry, internal cursor movement, split, and delete merge`() {
        var doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "12.34")
        assertEquals("12.34|", renderWithCursor(doc))

        // Walk left inside NumberNode
        doc = CursorEngine.moveLeft(doc)
        assertEquals("12.3|4", renderWithCursor(doc))
        doc = CursorEngine.moveLeft(doc)
        assertEquals("12.|34", renderWithCursor(doc))
        doc = CursorEngine.moveLeft(doc)
        assertEquals("12|.34", renderWithCursor(doc))

        // Insert '+' at split point
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        assertEquals("12+|.34", renderWithCursor(doc))

        // Delete '+' -> adjacent numbers merge back if <= 1 decimal point
        doc = CursorEngine.deleteBeforeCursor(doc)
        assertEquals("12|.34", renderWithCursor(doc))
    }

    @Test
    fun `smart wrapping for fraction and power`() {
        // 5 + fraction key -> frac(5/|)
        var doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "5")
        doc = CursorEngine.insertFraction(doc)
        assertEquals("frac(5/|)", renderWithCursor(doc))

        // 2 + 5 + fraction key -> 2+frac(5/|)
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertNumberLiteral(doc, "5")
        doc = CursorEngine.insertFraction(doc)
        assertEquals("2+frac(5/|)", renderWithCursor(doc))

        // 2X + fraction key -> frac(2X/|)
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertFraction(doc)
        assertEquals("frac(2X/|)", renderWithCursor(doc))

        // 2X + power key -> 2pow(X^|)
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertPower(doc)
        assertEquals("2pow(X^|)", renderWithCursor(doc))

        // (2+3) + x^2 -> pow((2+3)^2)|
        doc = AstDocument.empty()
        doc = CursorEngine.insertParens(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        doc = CursorEngine.moveRight(doc) // exit parens
        doc = CursorEngine.insertFixedPower(doc, "2")
        assertEquals("pow((2+3)^2)|", renderWithCursor(doc))

        // Split number 25 at 2|5 then press x^□ -> pow(2^|)5
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "25")
        doc = CursorEngine.moveLeft(doc)
        doc = CursorEngine.insertPower(doc)
        assertEquals("pow(2^|)5", renderWithCursor(doc))
    }

    @Test
    fun `nested fraction and power linear traversal forward and backward`() {
        // Build: frac(1 / (2 + frac(3 / pow(X^25))))
        var doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertPower(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "25")

        assertEquals("frac(1/2+frac(3/pow(X^25|)))", renderWithCursor(doc))

        // Exit all the way to the very end
        doc = CursorEngine.moveRight(doc) // exit PowerNode -> end of inner denominator
        assertEquals("frac(1/2+frac(3/pow(X^25)|))", renderWithCursor(doc))
        doc = CursorEngine.moveRight(doc) // exit inner fraction -> end of outer denominator
        assertEquals("frac(1/2+frac(3/pow(X^25))|)", renderWithCursor(doc))
        doc = CursorEngine.moveRight(doc) // exit outer fraction -> end of root
        assertEquals("frac(1/2+frac(3/pow(X^25)))|", renderWithCursor(doc))

        // Walk backward step by step all the way to start, recording snapshots
        val backwardStates = mutableListOf(renderWithCursor(doc))
        repeat(30) {
            val next = CursorEngine.moveLeft(doc)
            if (next.cursor == doc.cursor) return@repeat
            doc = next
            backwardStates.add(renderWithCursor(doc))
        }

        assertEquals("|frac(1/2+frac(3/pow(X^25)))", backwardStates.last())

        // Now walk forward step by step and verify it visits the exact reverse sequence
        val forwardStates = mutableListOf(renderWithCursor(doc))
        repeat(30) {
            val next = CursorEngine.moveRight(doc)
            if (next.cursor == doc.cursor) return@repeat
            doc = next
            forwardStates.add(renderWithCursor(doc))
        }

        assertEquals(backwardStates.reversed(), forwardStates)
    }

    @Test
    fun `vertical up and down navigation across nested fractions, powers, roots, logs, and calculus nodes`() {
        // Nested: frac(pow(X^2) / 3)
        var doc = AstDocument.empty()
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertPower(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.moveRight(doc) // exit power, still in root
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        assertEquals("frac(pow(X^2)/3|)", renderWithCursor(doc))

        // UP from denominator -> enters numerator (at end of numerator)
        doc = CursorEngine.moveUp(doc)
        assertEquals("frac(pow(X^2)|/3)", renderWithCursor(doc))

        // Step left into Power.Exponent -> press DOWN -> goes to Power.Base!
        doc = CursorEngine.moveLeft(doc)
        assertEquals("frac(pow(X^2|)/3)", renderWithCursor(doc))
        doc = CursorEngine.moveDown(doc)
        assertEquals("frac(pow(X|^2)/3)", renderWithCursor(doc))

        // Press DOWN again from Power.Base -> bubbles up to FractionNode and jumps to Denominator!
        doc = CursorEngine.moveDown(doc)
        assertEquals("frac(pow(X^2)/3|)", renderWithCursor(doc))

        // N-th root: index <-> radicand
        doc = AstDocument.empty()
        doc = CursorEngine.insertSqrt(doc, withCustomIndex = true)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        doc = CursorEngine.moveDown(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "8")
        assertEquals("sqrt[3](8|)", renderWithCursor(doc))
        doc = CursorEngine.moveUp(doc)
        assertEquals("sqrt[3|](8)", renderWithCursor(doc))

        // Log_a(b): base <-> arg
        doc = AstDocument.empty()
        doc = CursorEngine.insertLog(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.moveUp(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "16")
        assertEquals("log[2](16|)", renderWithCursor(doc))
        doc = CursorEngine.moveDown(doc)
        assertEquals("log[2|](16)", renderWithCursor(doc))

        // Integral: expr -> lower <-> upper
        doc = AstDocument.empty()
        doc = CursorEngine.insertIntegral(doc)
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        assertEquals("int[..](X|)", renderWithCursor(doc))
        doc = CursorEngine.moveDown(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "0")
        assertEquals("int[0|..](X)", renderWithCursor(doc))
        doc = CursorEngine.moveUp(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        assertEquals("int[0..1|](X)", renderWithCursor(doc))
        doc = CursorEngine.moveDown(doc)
        assertEquals("int[0..1](X)", renderWithCursor(doc).replace("|", "").let {
            // Confirm cursor landed back in lower slot
            assertEquals(SlotRole.Lower, doc.cursor.role)
            "int[0..1](X)"
        })
    }

    @Test
    fun `matrix navigation with up, down, left, right`() {
        var doc = AstDocument.empty()
        doc = CursorEngine.insertMatrix(doc, rows = 2, cols = 2)
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.moveRight(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        doc = CursorEngine.moveDown(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "4")
        doc = CursorEngine.moveLeft(doc)
        doc = CursorEngine.moveLeft(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        assertEquals("mat(1,2;3|,4)", renderWithCursor(doc))

        doc = CursorEngine.moveUp(doc)
        assertEquals(SlotRole.MatrixCell(0, 0), doc.cursor.role)
        assertEquals("mat(1|,2;3,4)", renderWithCursor(doc))
    }

    @Test
    fun `delete unwraps templates and removes empty templates cleanly`() {
        // Delete empty template
        var doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "7")
        doc = CursorEngine.insertOperator(doc, MathOperator.ADD)
        doc = CursorEngine.insertFraction(doc) // empty numerator
        assertEquals("7+frac(|/)", renderWithCursor(doc))
        doc = CursorEngine.deleteBeforeCursor(doc)
        assertEquals("7+|", renderWithCursor(doc))

        // Unwrap non-empty fraction from start of denominator
        doc = AstDocument.empty()
        doc = CursorEngine.insertNumberLiteral(doc, "1")
        doc = CursorEngine.insertFraction(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "2")
        assertEquals("frac(1/2|)", renderWithCursor(doc))
        doc = CursorEngine.moveLeft(doc)
        assertEquals("frac(1/|2)", renderWithCursor(doc))
        doc = CursorEngine.deleteBeforeCursor(doc)
        assertEquals("1|2", renderWithCursor(doc))

        // Unwrap power from start of exponent
        doc = AstDocument.empty()
        doc = CursorEngine.insertVariable(doc, MathVariable.X)
        doc = CursorEngine.insertPower(doc)
        doc = CursorEngine.insertNumberLiteral(doc, "3")
        doc = CursorEngine.moveLeft(doc)
        assertEquals("pow(X^|3)", renderWithCursor(doc))
        doc = CursorEngine.deleteBeforeCursor(doc)
        assertEquals("X|3", renderWithCursor(doc))

        // AC clears entire document
        doc = CursorEngine.clearAll()
        assertTrue(doc.root.isEmpty)
        assertEquals(CursorPosition(ROOT_NODE_ID, SlotRole.Root, 0, 0), doc.cursor)
    }
}
