package com.example.serializer

import com.example.ast.AbsNode
import com.example.ast.AstNode
import com.example.ast.ConstantNode
import com.example.ast.DerivativeNode
import com.example.ast.ExprSlot
import com.example.ast.FractionNode
import com.example.ast.FunctionNode
import com.example.ast.IntegralNode
import com.example.ast.LogNode
import com.example.ast.MathOperator
import com.example.ast.MatrixNode
import com.example.ast.NumberNode
import com.example.ast.OperatorNode
import com.example.ast.ParenNode
import com.example.ast.PowerNode
import com.example.ast.ROOT_NODE_ID
import com.example.ast.SlotRole
import com.example.ast.SqrtNode
import com.example.ast.SumProdNode
import com.example.ast.VariableNode
import com.example.engine.CalcEngineException
import com.example.engine.EngineExpr
import com.example.engine.ErrorKind

/**
 * Translates an AST [ExprSlot] tree into an [EngineExpr] tree enforcing Casio fx-991ES Plus
 * precedence (implicit multiplication binds tighter than explicit ÷/×; unary minus binds
 * looser than 2D powers so `-2^2 = -4`).
 */
object AstToEngineParser {

    fun parse(root: ExprSlot): EngineExpr = parseSlot(root, ROOT_NODE_ID, SlotRole.Root)

    fun parseSlot(slot: ExprSlot, ownerId: Long, role: SlotRole): EngineExpr {
        if (slot.isEmpty) {
            throw CalcEngineException(
                kind = ErrorKind.SYNTAX,
                message = "Empty expression slot",
                errorOwnerId = ownerId,
                errorRole = role,
                errorIndex = 0,
            )
        }
        val parser = SlotParser(slot.children, ownerId, role)
        val expr = parser.parseExpression(minPrec = 0)
        if (!parser.isAtEnd()) {
            val bad = parser.peek()!!
            throw CalcEngineException(
                kind = ErrorKind.SYNTAX,
                message = "Unexpected token",
                errorNodeId = bad.id,
                errorOwnerId = ownerId,
                errorRole = role,
                errorIndex = parser.pos,
            )
        }
        return expr
    }

    private enum class Prec(val level: Int) {
        LOGIC_OR(10),      // or, xor, xnor
        LOGIC_AND(20),     // and
        POLAR_ANGLE(25),   // ∠
        ADD_SUB(30),       // +, -
        MUL_DIV(40),       // ×, ÷
        UNARY(50),         // unary -, unary +
        PERM_COMB(60),     // nPr, nCr
        IMPLICIT_MUL(70),  // 2X, 2π, 2(3+4), 2sin(30)
        POSTFIX(80),       // !, %, °′″
    }

    private class SlotParser(
        private val nodes: List<AstNode>,
        private val ownerId: Long,
        private val role: SlotRole,
    ) {
        var pos: Int = 0

        fun isAtEnd(): Boolean = pos >= nodes.size
        fun peek(): AstNode? = nodes.getOrNull(pos)

        fun parseExpression(minPrec: Int): EngineExpr {
            var left = parsePrefix()

            while (!isAtEnd()) {
                val next = peek()!!
                // Postfix operators (!, %, DMS)
                if (next is OperatorNode) {
                    val op = next.operator
                    if (op == MathOperator.FACTORIAL && Prec.POSTFIX.level >= minPrec) {
                        pos++
                        left = EngineExpr.Factorial(left, next.id)
                        continue
                    }
                    if (op == MathOperator.PERCENT && Prec.POSTFIX.level >= minPrec) {
                        pos++
                        left = EngineExpr.Percent(left, next.id)
                        continue
                    }
                    if (op == MathOperator.DMS && Prec.POSTFIX.level >= minPrec) {
                        left = parseDmsChain(left, next)
                        continue
                    }

                    val binaryPrec = infixPrecedence(op) ?: break
                    if (binaryPrec < minPrec) break

                    pos++ // consume operator
                    val right = parseExpression(binaryPrec + 1)
                    left = buildBinary(op, left, right, next.id)
                    continue
                }

                // Implicit multiplication if `next` can start a primary atom
                if (canStartImplicitPrimary(next) && Prec.IMPLICIT_MUL.level >= minPrec) {
                    val right = parseExpression(Prec.IMPLICIT_MUL.level + 1)
                    left = EngineExpr.Mul(left, right, isImplicit = true, sourceId = next.id)
                    continue
                }

                break
            }

            return left
        }

        private fun parseDmsChain(degrees: EngineExpr, firstDms: OperatorNode): EngineExpr {
            pos++ // consume first °
            if (isAtEnd() || !canStartPrimaryValue(peek()!!)) {
                return EngineExpr.Dms(degrees, null, null, firstDms.id)
            }
            val minutes = parseExpression(Prec.POSTFIX.level + 1)
            if (peek() is OperatorNode && (peek() as OperatorNode).operator == MathOperator.DMS) {
                pos++ // consume ′
            }
            if (isAtEnd() || !canStartPrimaryValue(peek()!!)) {
                return EngineExpr.Dms(degrees, minutes, null, firstDms.id)
            }
            val seconds = parseExpression(Prec.POSTFIX.level + 1)
            if (peek() is OperatorNode && (peek() as OperatorNode).operator == MathOperator.DMS) {
                pos++ // consume ″
            }
            return EngineExpr.Dms(degrees, minutes, seconds, firstDms.id)
        }

        private fun parsePrefix(): EngineExpr {
            if (isAtEnd()) {
                throw CalcEngineException(
                    kind = ErrorKind.SYNTAX,
                    message = "Unexpected end of expression",
                    errorOwnerId = ownerId,
                    errorRole = role,
                    errorIndex = pos,
                )
            }

            val node = nodes[pos]
            if (node is OperatorNode) {
                return when (node.operator) {
                    MathOperator.UNARY_MINUS, MathOperator.SUBTRACT -> {
                        pos++
                        val operand = parseExpression(Prec.UNARY.level)
                        EngineExpr.UnaryMinus(operand, node.id)
                    }
                    MathOperator.ADD -> {
                        pos++
                        parseExpression(Prec.UNARY.level)
                    }
                    else -> throw CalcEngineException(
                        kind = ErrorKind.SYNTAX,
                        message = "Unexpected operator ${node.operator.symbol}",
                        errorNodeId = node.id,
                        errorOwnerId = ownerId,
                        errorRole = role,
                        errorIndex = pos,
                    )
                }
            }

            pos++
            return parsePrimary(node)
        }

        private fun parsePrimary(node: AstNode): EngineExpr {
            return when (node) {
                is NumberNode -> {
                    if (node.value == "." || node.value.count { it == '.' } > 1) {
                        throw CalcEngineException(
                            kind = ErrorKind.SYNTAX,
                            message = "Malformed number '${node.value}'",
                            errorNodeId = node.id,
                            errorOwnerId = ownerId,
                            errorRole = role,
                            errorIndex = pos - 1,
                        )
                    }
                    EngineExpr.NumberLit(node.value, node.id)
                }
                is ConstantNode -> EngineExpr.Const(node.constant, node.id)
                is VariableNode -> EngineExpr.Var(node.variable, node.id)
                is ParenNode -> parseSlot(node.body, node.id, SlotRole.Body)
                is AbsNode -> EngineExpr.Abs(parseSlot(node.body, node.id, SlotRole.Body), node.id)
                is FractionNode -> {
                    val num = parseSlot(node.numerator, node.id, SlotRole.Numerator)
                    val den = parseSlot(node.denominator, node.id, SlotRole.Denominator)
                    if (node.whole != null) {
                        val whole = parseSlot(node.whole, node.id, SlotRole.Whole)
                        EngineExpr.MixedFrac(whole, num, den, node.id)
                    } else {
                        EngineExpr.Div(num, den, isFraction = true, sourceId = node.id)
                    }
                }
                is PowerNode -> {
                    val base = parseSlot(node.base, node.id, SlotRole.Base)
                    val exp = parseSlot(node.exponent, node.id, SlotRole.Exponent)
                    EngineExpr.Pow(base, exp, node.id)
                }
                is SqrtNode -> {
                    val rad = parseSlot(node.radicand, node.id, SlotRole.Radicand)
                    if (node.index != null) {
                        val idx = parseSlot(node.index, node.id, SlotRole.Index)
                        EngineExpr.NthRoot(idx, rad, node.id)
                    } else {
                        EngineExpr.Sqrt(rad, node.id)
                    }
                }
                is LogNode -> {
                    val base = parseSlot(node.base, node.id, SlotRole.Base)
                    val arg = parseSlot(node.arg, node.id, SlotRole.Arg)
                    EngineExpr.LogBase(base, arg, node.id)
                }
                is FunctionNode -> {
                    if (node.function.arity == 0) {
                        EngineExpr.Call(node.function, EngineExpr.NumberLit("0", node.id), null, node.id)
                    } else if (node.secondArg != null && !node.secondArg.isEmpty) {
                        val a1 = parseSlot(node.arg, node.id, SlotRole.Arg)
                        val a2 = parseSlot(node.secondArg, node.id, SlotRole.SecondArg)
                        EngineExpr.Call(node.function, a1, a2, node.id)
                    } else if (node.function.arity == 2) {
                        // Support comma-separated pair inside arg if entered via single slot
                        val parts = splitByComma(node.arg.children)
                        if (parts.size == 2) {
                            val a1 = parseSlot(ExprSlot(parts[0]), node.id, SlotRole.Arg)
                            val a2 = parseSlot(ExprSlot(parts[1]), node.id, SlotRole.Arg)
                            EngineExpr.Call(node.function, a1, a2, node.id)
                        } else if (node.secondArg != null) {
                            val a1 = parseSlot(node.arg, node.id, SlotRole.Arg)
                            val a2 = parseSlot(node.secondArg, node.id, SlotRole.SecondArg)
                            EngineExpr.Call(node.function, a1, a2, node.id)
                        } else {
                            throw CalcEngineException(
                                kind = ErrorKind.SYNTAX,
                                message = "${node.function.displayName} requires 2 arguments",
                                errorNodeId = node.id,
                                errorOwnerId = node.id,
                                errorRole = SlotRole.Arg,
                                errorIndex = 0,
                            )
                        }
                    } else {
                        val a1 = parseSlot(node.arg, node.id, SlotRole.Arg)
                        EngineExpr.Call(node.function, a1, null, node.id)
                    }
                }
                is IntegralNode -> EngineExpr.Integral(
                    expr = parseSlot(node.expr, node.id, SlotRole.Expr),
                    lower = parseSlot(node.lower, node.id, SlotRole.Lower),
                    upper = parseSlot(node.upper, node.id, SlotRole.Upper),
                    sourceId = node.id,
                )
                is DerivativeNode -> EngineExpr.Derivative(
                    expr = parseSlot(node.expr, node.id, SlotRole.Expr),
                    atX = parseSlot(node.atX, node.id, SlotRole.AtX),
                    sourceId = node.id,
                )
                is SumProdNode -> EngineExpr.SumProd(
                    kind = node.kind,
                    expr = parseSlot(node.expr, node.id, SlotRole.Expr),
                    lower = parseSlot(node.lower, node.id, SlotRole.Lower),
                    upper = parseSlot(node.upper, node.id, SlotRole.Upper),
                    sourceId = node.id,
                )
                is MatrixNode -> EngineExpr.MatrixLit(
                    rows = node.rows,
                    cols = node.cols,
                    cells = node.cells.mapIndexed { idx, cell ->
                        parseSlot(cell, node.id, SlotRole.MatrixCell(idx / node.cols, idx % node.cols))
                    },
                    sourceId = node.id,
                )
                is OperatorNode -> throw CalcEngineException(
                    kind = ErrorKind.SYNTAX,
                    message = "Unexpected operator",
                    errorNodeId = node.id,
                    errorOwnerId = ownerId,
                    errorRole = role,
                    errorIndex = pos - 1,
                )
            }
        }

        private fun splitByComma(children: List<AstNode>): List<List<AstNode>> {
            val result = mutableListOf<MutableList<AstNode>>(mutableListOf())
            for (child in children) {
                if (child is OperatorNode && child.operator == MathOperator.COMMA) {
                    result.add(mutableListOf())
                } else {
                    result.last().add(child)
                }
            }
            return result
        }

        private fun infixPrecedence(op: MathOperator): Int? = when (op) {
            MathOperator.LOGIC_OR, MathOperator.LOGIC_XOR, MathOperator.LOGIC_XNOR -> Prec.LOGIC_OR.level
            MathOperator.LOGIC_AND -> Prec.LOGIC_AND.level
            MathOperator.ANGLE -> Prec.POLAR_ANGLE.level
            MathOperator.ADD, MathOperator.SUBTRACT -> Prec.ADD_SUB.level
            MathOperator.MULTIPLY, MathOperator.DIVIDE -> Prec.MUL_DIV.level
            MathOperator.PERMUTATION, MathOperator.COMBINATION -> Prec.PERM_COMB.level
            else -> null
        }

        private fun buildBinary(
            op: MathOperator,
            left: EngineExpr,
            right: EngineExpr,
            id: Long,
        ): EngineExpr = when (op) {
            MathOperator.ADD -> EngineExpr.Add(left, right, id)
            MathOperator.SUBTRACT -> EngineExpr.Sub(left, right, id)
            MathOperator.MULTIPLY -> EngineExpr.Mul(left, right, isImplicit = false, sourceId = id)
            MathOperator.DIVIDE -> EngineExpr.Div(left, right, isFraction = false, sourceId = id)
            MathOperator.PERMUTATION -> EngineExpr.Permutation(left, right, id)
            MathOperator.COMBINATION -> EngineExpr.Combination(left, right, id)
            MathOperator.ANGLE -> EngineExpr.PolarAngle(left, right, id)
            MathOperator.LOGIC_AND -> EngineExpr.BitAnd(left, right, id)
            MathOperator.LOGIC_OR -> EngineExpr.BitOr(left, right, id)
            MathOperator.LOGIC_XOR -> EngineExpr.BitXor(left, right, id)
            MathOperator.LOGIC_XNOR -> EngineExpr.BitXnor(left, right, id)
            else -> throw CalcEngineException(ErrorKind.SYNTAX, "Unsupported binary operator $op", id)
        }

        private fun canStartImplicitPrimary(node: AstNode): Boolean = when (node) {
            is ConstantNode,
            is VariableNode,
            is ParenNode,
            is AbsNode,
            is SqrtNode,
            is PowerNode,
            is LogNode,
            is FunctionNode,
            is IntegralNode,
            is DerivativeNode,
            is SumProdNode,
            is MatrixNode,
            is FractionNode -> true
            else -> false
        }

        private fun canStartPrimaryValue(node: AstNode): Boolean =
            node is NumberNode || canStartImplicitPrimary(node)
    }
}
