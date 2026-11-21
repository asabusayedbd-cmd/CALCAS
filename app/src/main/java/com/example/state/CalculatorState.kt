package com.example.state

import com.example.ast.AstDocument
import com.example.ast.MathVariable
import com.example.ast.SlotRole
import com.example.data.CalcHistoryEntity
import com.example.engine.AngleUnit
import com.example.engine.BaseNResult
import com.example.engine.BitWidth
import com.example.engine.CalcMatrix
import com.example.engine.CalcVector
import com.example.engine.ComplexVal
import com.example.engine.ErrorKind
import com.example.engine.ExactSymbolic
import com.example.engine.NumberBase
import com.example.engine.NumberFormatMode
import com.example.engine.NumericalMethods
import com.example.engine.StatDataRow
import java.math.BigDecimal

enum class CalcMode(val id: Int, val label: String, val subtitle: String) {
    COMP(1, "COMP", "Standard & Calculus"),
    CMPLX(2, "CMPLX", "Complex Numbers"),
    STAT(3, "STAT", "Statistics & Regression"),
    BASE_N(4, "BASE-N", "DEC / HEX / BIN / OCT"),
    EQN(5, "EQN", "Equation Solvers"),
    MATRIX(6, "MATRIX", "Matrix Algebra (4×4)"),
    TABLE(7, "TABLE", "Function Table f(x)"),
    VECTOR(8, "VECTOR", "2D & 3D Vectors"),
}

enum class CalcThemePreset(val label: String) {
    CLASSIC_METALLIC("Classic Metallic"),
    DARK_OLED("Dark OLED"),
    MINIMALIST_FLAT("Minimalist Flat"),
}

enum class ModifierKey {
    NONE,
    SHIFT,
    ALPHA,
    STO,
    RCL,
}

sealed interface ActiveOverlay {
    data object None : ActiveOverlay
    data object ModeMenu : ActiveOverlay
    data object SetupMenu : ActiveOverlay
    data object ConstPicker : ActiveOverlay
    data object ConvPicker : ActiveOverlay
    data object FunctionCatalog : ActiveOverlay
    data class CalcPrompt(
        val variables: List<MathVariable>,
        val currentIndex: Int,
        val values: Map<MathVariable, BigDecimal>,
        val draft: String,
    ) : ActiveOverlay

    data class SolvePrompt(
        val initialGuessDraft: String,
        val result: NumericalMethods.SolveResult? = null,
        val errorMessage: String? = null,
    ) : ActiveOverlay

    data object StatSheet : ActiveOverlay
    data object EqnSheet : ActiveOverlay
    data object MatrixSheet : ActiveOverlay
    data object VectorSheet : ActiveOverlay
    data object TableSheet : ActiveOverlay
}

data class ResultDisplay(
    val primaryText: String,
    val exactSymbolic: ExactSymbolic? = null,
    val variations: List<Pair<String, String>> = emptyList(),
    val variationIndex: Int = 0,
    val secondaryLine: String? = null,
    val formatBadge: String = "DEC",
    val numericValue: BigDecimal? = null,
    val matrixResult: CalcMatrix? = null,
    val vectorResult: CalcVector? = null,
)

data class ErrorDisplay(
    val kind: ErrorKind,
    val message: String,
    val errorOwnerId: Long? = null,
    val errorRole: SlotRole? = null,
    val errorIndex: Int? = null,
)

data class CalculatorUiState(
    val document: AstDocument = AstDocument.empty(),
    val mode: CalcMode = CalcMode.COMP,
    val modifier: ModifierKey = ModifierKey.NONE,
    val hypActive: Boolean = false,
    val angleUnit: AngleUnit = AngleUnit.DEG,
    val numberFormat: NumberFormatMode = NumberFormatMode.Norm1,
    val baseNBase: NumberBase = NumberBase.DEC,
    val baseNWidth: BitWidth = BitWidth.BIT_32,
    val baseNResult: BaseNResult? = null,
    val cmplxPolarOutput: Boolean = false,
    val engShift: Int = 0,
    val dmsDisplayActive: Boolean = false,
    val variables: Map<MathVariable, BigDecimal> = mapOf(
        MathVariable.A to BigDecimal.ZERO,
        MathVariable.B to BigDecimal.ZERO,
        MathVariable.C to BigDecimal.ZERO,
        MathVariable.D to BigDecimal.ZERO,
        MathVariable.E to BigDecimal.ZERO,
        MathVariable.F to BigDecimal.ZERO,
        MathVariable.X to BigDecimal.ZERO,
        MathVariable.Y to BigDecimal.ZERO,
        MathVariable.M to BigDecimal.ZERO,
        MathVariable.ANS to BigDecimal.ZERO,
        MathVariable.PRE_ANS to BigDecimal.ZERO,
    ),
    val complexAns: ComplexVal = ComplexVal(BigDecimal.ZERO, BigDecimal.ZERO),
    val matrices: Map<MathVariable, CalcMatrix> = mapOf(
        MathVariable.MAT_A to CalcMatrix(2, 2, listOf(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE)),
        MathVariable.MAT_B to CalcMatrix(2, 2, listOf(BigDecimal.ONE, BigDecimal("2"), BigDecimal("3"), BigDecimal("4"))),
        MathVariable.MAT_C to CalcMatrix(3, 3),
    ),
    val vectors: Map<MathVariable, CalcVector> = mapOf(
        MathVariable.VCT_A to CalcVector(listOf(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO)),
        MathVariable.VCT_B to CalcVector(listOf(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO)),
        MathVariable.VCT_C to CalcVector(listOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE)),
    ),
    val statRows: List<StatDataRow> = emptyList(),
    val statTwoVar: Boolean = false,
    val result: ResultDisplay? = null,
    val error: ErrorDisplay? = null,
    val hasEvaluatedCurrent: Boolean = false,
    val overlay: ActiveOverlay = ActiveOverlay.None,
    val theme: CalcThemePreset = CalcThemePreset.CLASSIC_METALLIC,
    val hapticsEnabled: Boolean = true,
    val historyExpanded: Boolean = false,
    val history: List<CalcHistoryEntity> = emptyList(),
) {
    val hasMemory: Boolean
        get() = (variables[MathVariable.M] ?: BigDecimal.ZERO).compareTo(BigDecimal.ZERO) != 0
}
