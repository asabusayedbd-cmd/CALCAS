package com.example.ast

const val ROOT_NODE_ID: Long = 0L

sealed interface SlotRole {
    data object Root : SlotRole
    data object Body : SlotRole
    data object Whole : SlotRole
    data object Numerator : SlotRole
    data object Denominator : SlotRole
    data object Index : SlotRole
    data object Radicand : SlotRole
    data object Base : SlotRole
    data object Exponent : SlotRole
    data object Arg : SlotRole
    data object SecondArg : SlotRole
    data object Expr : SlotRole
    data object Lower : SlotRole
    data object Upper : SlotRole
    data object AtX : SlotRole
    data class MatrixCell(val row: Int, val col: Int) : SlotRole
}

data class ExprSlot(val children: List<AstNode> = emptyList()) {
    val isEmpty: Boolean get() = children.isEmpty()
    val size: Int get() = children.size
}

enum class MathOperator(val symbol: String) {
    ADD("+"),
    SUBTRACT("−"),
    MULTIPLY("×"),
    DIVIDE("÷"),
    UNARY_MINUS("-"),
    PERCENT("%"),
    FACTORIAL("!"),
    PERMUTATION("P"),
    COMBINATION("C"),
    COMMA(","),
    ANGLE("∠"),
    DMS("°"),
    LOGIC_AND(" and "),
    LOGIC_OR(" or "),
    LOGIC_XOR(" xor "),
    LOGIC_XNOR(" xnor "),
}

sealed interface MathConstant {
    val symbol: String

    data object Pi : MathConstant {
        override val symbol: String = "π"
    }

    data object EulerE : MathConstant {
        override val symbol: String = "e"
    }

    data object ImaginaryI : MathConstant {
        override val symbol: String = "i"
    }

    data class PhysicalConstant(val code: Int, override val symbol: String) : MathConstant
}

enum class MathVariable(val symbol: String) {
    A("A"),
    B("B"),
    C("C"),
    D("D"),
    E("E"),
    F("F"),
    X("X"),
    Y("Y"),
    M("M"),
    ANS("Ans"),
    PRE_ANS("PreAns"),
    MAT_A("MatA"),
    MAT_B("MatB"),
    MAT_C("MatC"),
    MAT_ANS("MatAns"),
    VCT_A("VctA"),
    VCT_B("VctB"),
    VCT_C("VctC"),
    VCT_ANS("VctAns"),
}

enum class MathFunction(val displayName: String, val arity: Int = 1) {
    SIN("sin"),
    COS("cos"),
    TAN("tan"),
    ASIN("sin⁻¹"),
    ACOS("cos⁻¹"),
    ATAN("tan⁻¹"),
    SINH("sinh"),
    COSH("cosh"),
    TANH("tanh"),
    ASINH("sinh⁻¹"),
    ACOSH("cosh⁻¹"),
    ATANH("tanh⁻¹"),
    LOG10("log"),
    LN("ln"),
    EXP("e^"),
    CONJG("Conjg"),
    ARG("arg"),
    DET("Det"),
    TRN("Trn"),
    POL("Pol", arity = 2),
    REC("Rec", arity = 2),
    GCD("GCD", arity = 2),
    LCM("LCM", arity = 2),
    RAN_INT("RanInt#", arity = 2),
    ROUND("Rnd"),
    BIT_NOT("Not"),
    BIT_NEG("Neg"),
}

enum class SumProdKind(val symbol: String) {
    SUM("Σ"),
    PRODUCT("Π"),
}

sealed interface AstNode {
    val id: Long

    fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = emptyList()

    fun slotFor(role: SlotRole): ExprSlot? = orderedSlots().firstOrNull { it.first == role }?.second

    fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = this

    fun isComposite(): Boolean = orderedSlots().isNotEmpty()

    fun isEmptyTemplate(): Boolean {
        val slots = orderedSlots()
        return slots.isNotEmpty() && slots.all { it.second.isEmpty }
    }
}

data class NumberNode(
    override val id: Long,
    val value: String,
) : AstNode {
    init {
        require(value.isNotEmpty()) { "NumberNode value must not be empty" }
    }
}

data class OperatorNode(
    override val id: Long,
    val operator: MathOperator,
) : AstNode

data class ConstantNode(
    override val id: Long,
    val constant: MathConstant,
) : AstNode

data class VariableNode(
    override val id: Long,
    val variable: MathVariable,
) : AstNode

data class ParenNode(
    override val id: Long,
    val body: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = listOf(SlotRole.Body to body)

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode =
        if (role == SlotRole.Body) copy(body = newSlot) else this
}

data class FractionNode(
    override val id: Long,
    val numerator: ExprSlot = ExprSlot(),
    val denominator: ExprSlot = ExprSlot(),
    val whole: ExprSlot? = null,
) : AstNode {
    val isMixed: Boolean get() = whole != null

    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = buildList {
        if (whole != null) add(SlotRole.Whole to whole)
        add(SlotRole.Numerator to numerator)
        add(SlotRole.Denominator to denominator)
    }

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Whole -> if (whole != null) copy(whole = newSlot) else this
        SlotRole.Numerator -> copy(numerator = newSlot)
        SlotRole.Denominator -> copy(denominator = newSlot)
        else -> this
    }
}

data class SqrtNode(
    override val id: Long,
    val index: ExprSlot? = null,
    val radicand: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = buildList {
        if (index != null) add(SlotRole.Index to index)
        add(SlotRole.Radicand to radicand)
    }

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Index -> if (index != null) copy(index = newSlot) else this
        SlotRole.Radicand -> copy(radicand = newSlot)
        else -> this
    }
}

data class PowerNode(
    override val id: Long,
    val base: ExprSlot = ExprSlot(),
    val exponent: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> =
        listOf(SlotRole.Base to base, SlotRole.Exponent to exponent)

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Base -> copy(base = newSlot)
        SlotRole.Exponent -> copy(exponent = newSlot)
        else -> this
    }
}

data class LogNode(
    override val id: Long,
    val base: ExprSlot = ExprSlot(),
    val arg: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> =
        listOf(SlotRole.Base to base, SlotRole.Arg to arg)

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Base -> copy(base = newSlot)
        SlotRole.Arg -> copy(arg = newSlot)
        else -> this
    }
}

data class FunctionNode(
    override val id: Long,
    val function: MathFunction,
    val arg: ExprSlot = ExprSlot(),
    val secondArg: ExprSlot? = null,
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = buildList {
        add(SlotRole.Arg to arg)
        if (secondArg != null) add(SlotRole.SecondArg to secondArg)
    }

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Arg -> copy(arg = newSlot)
        SlotRole.SecondArg -> if (secondArg != null) copy(secondArg = newSlot) else this
        else -> this
    }
}

data class IntegralNode(
    override val id: Long,
    val expr: ExprSlot = ExprSlot(),
    val lower: ExprSlot = ExprSlot(),
    val upper: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = listOf(
        SlotRole.Lower to lower,
        SlotRole.Upper to upper,
        SlotRole.Expr to expr,
    )

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Lower -> copy(lower = newSlot)
        SlotRole.Upper -> copy(upper = newSlot)
        SlotRole.Expr -> copy(expr = newSlot)
        else -> this
    }
}

data class DerivativeNode(
    override val id: Long,
    val expr: ExprSlot = ExprSlot(),
    val atX: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> =
        listOf(SlotRole.Expr to expr, SlotRole.AtX to atX)

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Expr -> copy(expr = newSlot)
        SlotRole.AtX -> copy(atX = newSlot)
        else -> this
    }
}

data class SumProdNode(
    override val id: Long,
    val kind: SumProdKind,
    val expr: ExprSlot = ExprSlot(),
    val lower: ExprSlot = ExprSlot(),
    val upper: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = listOf(
        SlotRole.Lower to lower,
        SlotRole.Upper to upper,
        SlotRole.Expr to expr,
    )

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode = when (role) {
        SlotRole.Lower -> copy(lower = newSlot)
        SlotRole.Upper -> copy(upper = newSlot)
        SlotRole.Expr -> copy(expr = newSlot)
        else -> this
    }
}

data class AbsNode(
    override val id: Long,
    val body: ExprSlot = ExprSlot(),
) : AstNode {
    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = listOf(SlotRole.Body to body)

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode =
        if (role == SlotRole.Body) copy(body = newSlot) else this
}

data class MatrixNode(
    override val id: Long,
    val rows: Int,
    val cols: Int,
    val cells: List<ExprSlot> = List(rows * cols) { ExprSlot() },
) : AstNode {
    init {
        require(rows >= 1 && cols >= 1) { "Matrix dimensions must be at least 1x1" }
        require(cells.size == rows * cols) { "Matrix cell count (${cells.size}) must equal rows*cols (${rows * cols})" }
    }

    override fun orderedSlots(): List<Pair<SlotRole, ExprSlot>> = buildList(rows * cols) {
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                add(SlotRole.MatrixCell(r, c) to cells[r * cols + c])
            }
        }
    }

    override fun withSlotUpdated(role: SlotRole, newSlot: ExprSlot): AstNode {
        if (role !is SlotRole.MatrixCell) return this
        if (role.row !in 0 until rows || role.col !in 0 until cols) return this
        val idx = role.row * cols + role.col
        val updated = cells.toMutableList().apply { set(idx, newSlot) }
        return copy(cells = updated)
    }
}
