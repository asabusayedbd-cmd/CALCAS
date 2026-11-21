package com.example.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ast.AstDocument
import com.example.ast.CursorEngine
import com.example.ast.CursorPosition
import com.example.ast.ExprSlot
import com.example.ast.MathConstant
import com.example.ast.MathFunction
import com.example.ast.MathOperator
import com.example.ast.MathVariable
import com.example.ast.OperatorNode
import com.example.ast.ROOT_NODE_ID
import com.example.ast.SlotRole
import com.example.ast.SumProdKind
import com.example.ast.VariableNode
import com.example.data.CalcDatabase
import com.example.data.CalcHistoryEntity
import com.example.data.CalcRepository
import com.example.engine.AngleUnit
import com.example.engine.BaseNEngine
import com.example.engine.BigMath
import com.example.engine.BitWidth
import com.example.engine.CalcContext
import com.example.engine.CalcEngineException
import com.example.engine.CalcMatrix
import com.example.engine.CalcVector
import com.example.engine.CatalogAction
import com.example.engine.CatalogCommand
import com.example.engine.CompEvaluator
import com.example.engine.ComplexEvaluator
import com.example.engine.ErrorKind
import com.example.engine.MatrixEvalValue
import com.example.engine.MatrixEvaluator
import com.example.engine.NumberBase
import com.example.engine.NumberFormatMode
import com.example.engine.ScientificConstantInfo
import com.example.engine.StatDataRow
import com.example.engine.UnitConversionInfo
import com.example.serializer.AstJsonCodec
import com.example.serializer.AstLatexSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode

class CalculatorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CalcRepository by lazy {
        val dao = CalcDatabase.getInstance(application).calcDao()
        CalcRepository(dao)
    }

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.history.collect { list ->
                _uiState.update { it.copy(history = list) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val saved = repository.loadMemoriesOnce()
            if (saved.isNotEmpty()) {
                val restoredVars = _uiState.value.variables.toMutableMap()
                for (entry in saved) {
                    val v = MathVariable.entries.firstOrNull { it.name == entry.name }
                    val bd = entry.value.toBigDecimalOrNull()
                    if (v != null && bd != null) {
                        restoredVars[v] = bd
                    }
                }
                _uiState.update { it.copy(variables = restoredVars) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hardware Modifiers & Mode / Setup
    // -------------------------------------------------------------------------

    fun onShiftPressed() {
        _uiState.update { state ->
            state.copy(
                modifier = if (state.modifier == ModifierKey.SHIFT) ModifierKey.NONE else ModifierKey.SHIFT,
            )
        }
    }

    fun onAlphaPressed() {
        _uiState.update { state ->
            state.copy(
                modifier = if (state.modifier == ModifierKey.ALPHA) ModifierKey.NONE else ModifierKey.ALPHA,
            )
        }
    }

    fun onHypPressed() {
        _uiState.update { state ->
            if (state.modifier == ModifierKey.ALPHA) {
                insertVarWithClearIfEvaluated(state, MathVariable.C)
            } else {
                state.copy(hypActive = !state.hypActive, modifier = ModifierKey.NONE)
            }
        }
    }

    fun onModePressed() {
        _uiState.update { state ->
            when (state.modifier) {
                ModifierKey.SHIFT -> state.copy(modifier = ModifierKey.NONE, overlay = ActiveOverlay.SetupMenu)
                ModifierKey.ALPHA -> state.copy(modifier = ModifierKey.NONE, overlay = ActiveOverlay.FunctionCatalog)
                else -> state.copy(
                    modifier = ModifierKey.NONE,
                    overlay = if (state.overlay == ActiveOverlay.ModeMenu) ActiveOverlay.None else ActiveOverlay.ModeMenu,
                )
            }
        }
    }

    fun selectMode(mode: CalcMode) {
        val nextOverlay = when (mode) {
            CalcMode.STAT -> ActiveOverlay.StatSheet
            CalcMode.EQN -> ActiveOverlay.EqnSheet
            CalcMode.MATRIX -> ActiveOverlay.MatrixSheet
            CalcMode.VECTOR -> ActiveOverlay.VectorSheet
            CalcMode.TABLE -> ActiveOverlay.TableSheet
            else -> ActiveOverlay.None
        }
        _uiState.update {
            it.copy(
                mode = mode,
                document = AstDocument.empty(),
                result = null,
                error = null,
                hasEvaluatedCurrent = false,
                baseNResult = null,
                modifier = ModifierKey.NONE,
                overlay = nextOverlay,
            )
        }
    }

    fun dismissOverlay() {
        _uiState.update { it.copy(overlay = ActiveOverlay.None) }
    }

    fun openOverlay(overlay: ActiveOverlay) {
        _uiState.update { it.copy(overlay = overlay, modifier = ModifierKey.NONE) }
    }

    fun setAngleUnit(unit: AngleUnit) {
        _uiState.update { it.copy(angleUnit = unit) }
    }

    fun setNumberFormat(format: NumberFormatMode) {
        _uiState.update { state ->
            val updatedRes = state.result?.let { res ->
                val dec = res.numericValue ?: return@let res
                val decText = BigMath.formatDisplay(dec, format, state.engShift)
                val updatedVars = res.variations.map { (k, v) -> if (k == "DEC") k to decText else k to v }
                val activeText = updatedVars.getOrNull(res.variationIndex)?.second ?: decText
                res.copy(primaryText = activeText, variations = updatedVars)
            }
            state.copy(numberFormat = format, result = updatedRes)
        }
    }

    fun setTheme(theme: CalcThemePreset) {
        _uiState.update { it.copy(theme = theme) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hapticsEnabled = enabled) }
    }

    fun toggleHistoryExpanded() {
        _uiState.update { it.copy(historyExpanded = !it.historyExpanded) }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearHistory()
        }
    }

    fun clearAllMemories() {
        _uiState.update { state ->
            val zeroed = state.variables.mapValues { BigDecimal.ZERO }
            state.copy(variables = zeroed, modifier = ModifierKey.NONE)
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearMemories()
        }
    }

    // -------------------------------------------------------------------------
    // D-Pad (REPLAY) Navigation & Direct Slot Tap
    // -------------------------------------------------------------------------

    fun onMoveLeft() {
        _uiState.update { state ->
            if (state.error != null) {
                return@update jumpToErrorLocation(state)
            }
            state.copy(
                document = CursorEngine.moveLeft(state.document),
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onMoveRight() {
        _uiState.update { state ->
            if (state.error != null) {
                return@update jumpToErrorLocation(state)
            }
            state.copy(
                document = CursorEngine.moveRight(state.document),
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onMoveUp() {
        _uiState.update { state ->
            if (state.error != null) return@update state
            state.copy(
                document = CursorEngine.moveUp(state.document),
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onMoveDown() {
        _uiState.update { state ->
            if (state.error != null) return@update state
            state.copy(
                document = CursorEngine.moveDown(state.document),
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onTapSlot(ownerId: Long, role: SlotRole, index: Int, charOffset: Int = 0) {
        _uiState.update { state ->
            state.copy(
                document = state.document.copy(
                    cursor = CursorPosition(ownerId, role, index, charOffset)
                ),
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    private fun jumpToErrorLocation(state: CalculatorUiState): CalculatorUiState {
        val err = state.error ?: return state
        val targetCursor = if (err.errorOwnerId != null && err.errorRole != null) {
            CursorPosition(
                ownerId = err.errorOwnerId,
                role = err.errorRole,
                index = (err.errorIndex ?: 0).coerceAtLeast(0),
                charOffset = 0,
            )
        } else {
            state.document.cursor
        }
        return state.copy(
            error = null,
            document = state.document.copy(cursor = targetCursor),
            hasEvaluatedCurrent = false,
        )
    }

    // -------------------------------------------------------------------------
    // Core Keypad Actions (Sub-16ms Synchronous AST Edits)
    // -------------------------------------------------------------------------

    fun onDigit(digit: Char) {
        _uiState.update { state ->
            val mod = state.modifier
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = when {
                mod == ModifierKey.NONE -> CursorEngine.insertDigitOrDecimal(baseDoc, digit)
                else -> CursorEngine.insertDigitOrDecimal(baseDoc, digit)
            }
            state.copy(
                document = nextDoc,
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onHexDigit(hexChar: Char) {
        _uiState.update { state ->
            if (state.mode != CalcMode.BASE_N) return@update state
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            // Store hex digits A-F as characters inside NumberNode for BASE-N parser
            val cur = baseDoc.cursor
            val slot = com.example.ast.AstTreeOps.findSlot(baseDoc.root, cur.ownerId, cur.role) ?: return@update state
            val updatedDoc = if (cur.charOffset == 0 && cur.index > 0 && slot.children[cur.index - 1] is com.example.ast.NumberNode) {
                val target = slot.children[cur.index - 1] as com.example.ast.NumberNode
                val newRoot = com.example.ast.AstTreeOps.updateSlot(baseDoc.root, cur.ownerId, cur.role) { s ->
                    val list = s.children.toMutableList()
                    list[cur.index - 1] = target.copy(value = target.value + hexChar)
                    ExprSlot(list)
                }
                baseDoc.copy(root = newRoot)
            } else {
                val (newId, docWithId) = baseDoc.allocateId()
                val newRoot = com.example.ast.AstTreeOps.updateSlot(docWithId.root, cur.ownerId, cur.role) { s ->
                    val list = s.children.toMutableList()
                    list.add(cur.index.coerceIn(0, list.size), com.example.ast.NumberNode(newId, hexChar.toString()))
                    ExprSlot(list)
                }
                docWithId.copy(root = newRoot, cursor = cur.copy(index = cur.index + 1, charOffset = 0))
            }
            state.copy(document = updatedDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onOperator(op: MathOperator) {
        _uiState.update { state ->
            val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
            state.copy(
                document = CursorEngine.insertOperator(baseDoc, op),
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onUnaryMinusKey() {
        _uiState.update { state ->
            when (state.modifier) {
                ModifierKey.ALPHA -> insertVarWithClearIfEvaluated(state, MathVariable.A)
                ModifierKey.STO -> storeLastAnsInto(state, MathVariable.A)
                ModifierKey.RCL -> insertVarWithClearIfEvaluated(state, MathVariable.A)
                else -> {
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(
                        document = CursorEngine.insertOperator(baseDoc, MathOperator.UNARY_MINUS),
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
        }
    }

    fun onDmsKey() {
        val current = _uiState.value
        if (current.modifier == ModifierKey.SHIFT) {
            onPrimeFactorize()
            return
        }
        _uiState.update { state ->
            when (state.modifier) {
                ModifierKey.ALPHA -> insertVarWithClearIfEvaluated(state, MathVariable.B)
                ModifierKey.STO -> storeLastAnsInto(state, MathVariable.B)
                ModifierKey.RCL -> insertVarWithClearIfEvaluated(state, MathVariable.B)
                else -> {
                    if (state.hasEvaluatedCurrent && state.result?.numericValue != null) {
                        val nextDms = !state.dmsDisplayActive
                        val dec = state.result.numericValue
                        val formatted = if (nextDms) formatDms(dec) else BigMath.formatDisplay(dec, state.numberFormat, state.engShift)
                        return@update state.copy(
                            dmsDisplayActive = nextDms,
                            result = state.result.copy(
                                primaryText = formatted,
                                formatBadge = if (nextDms) "DMS" else "DEC",
                            ),
                        )
                    }
                    state.copy(
                        document = CursorEngine.insertOperator(state.document, MathOperator.DMS),
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
        }
    }

    fun onDotKey() {
        _uiState.update { state ->
            when (state.modifier) {
                ModifierKey.SHIFT -> {
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(
                        document = CursorEngine.insertFunction(baseDoc, MathFunction.RAN),
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
                ModifierKey.ALPHA -> {
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(
                        document = CursorEngine.insertFunction(baseDoc, MathFunction.RAN_INT),
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
                else -> {
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(
                        document = CursorEngine.insertDigitOrDecimal(baseDoc, '.'),
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
        }
    }

    fun onAddOrPolKey() {
        _uiState.update { state ->
            if (state.modifier == ModifierKey.SHIFT) {
                val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                state.copy(
                    document = CursorEngine.insertFunction(baseDoc, MathFunction.POL),
                    modifier = ModifierKey.NONE,
                    error = null,
                    hasEvaluatedCurrent = false,
                )
            } else {
                val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
                state.copy(
                    document = CursorEngine.insertOperator(baseDoc, MathOperator.ADD),
                    modifier = ModifierKey.NONE,
                    error = null,
                    hasEvaluatedCurrent = false,
                )
            }
        }
    }

    fun onSubOrRecKey() {
        _uiState.update { state ->
            if (state.modifier == ModifierKey.SHIFT) {
                val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                state.copy(
                    document = CursorEngine.insertFunction(baseDoc, MathFunction.REC),
                    modifier = ModifierKey.NONE,
                    error = null,
                    hasEvaluatedCurrent = false,
                )
            } else {
                val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
                state.copy(
                    document = CursorEngine.insertOperator(baseDoc, MathOperator.SUBTRACT),
                    modifier = ModifierKey.NONE,
                    error = null,
                    hasEvaluatedCurrent = false,
                )
            }
        }
    }

    fun onPrimeFactorize() {
        _uiState.update { state ->
            val targetVal = try {
                if (!state.document.root.isEmpty && !state.hasEvaluatedCurrent) {
                    val ctx = CalcContext(state.angleUnit, state.numberFormat, state.variables)
                    CompEvaluator.evaluate(state.document.root, ctx).decimal
                } else {
                    state.result?.numericValue ?: state.variables[MathVariable.ANS]
                }
            } catch (e: CalcEngineException) {
                return@update state.copy(
                    modifier = ModifierKey.NONE,
                    error = ErrorDisplay(e.kind, e.message, e.errorOwnerId, e.errorRole, e.errorIndex),
                )
            }

            if (targetVal == null) {
                return@update state.copy(modifier = ModifierKey.NONE)
            }
            val factored = BigMath.primeFactorizeOrNull(targetVal)
                ?: return@update state.copy(
                    modifier = ModifierKey.NONE,
                    error = ErrorDisplay(ErrorKind.MATH, "FACT requires integer 0..10¹²"),
                )
            state.copy(
                modifier = ModifierKey.NONE,
                hasEvaluatedCurrent = true,
                error = null,
                result = ResultDisplay(
                    primaryText = factored,
                    numericValue = targetVal,
                    secondaryLine = BigMath.formatDisplay(targetVal, state.numberFormat),
                    formatBadge = "FACT",
                ),
            )
        }
    }

    fun onTrigKey(base: MathFunction) {
        _uiState.update { state ->
            val mod = state.modifier
            if (mod == ModifierKey.ALPHA || mod == ModifierKey.RCL || mod == ModifierKey.STO) {
                val targetVar = when (base) {
                    MathFunction.SIN -> MathVariable.D
                    MathFunction.COS -> MathVariable.E
                    MathFunction.TAN -> MathVariable.F
                    else -> MathVariable.D
                }
                return@update if (mod == ModifierKey.STO) {
                    storeLastAnsInto(state, targetVar)
                } else {
                    insertVarWithClearIfEvaluated(state, targetVar)
                }
            }
            val fn = when {
                mod == ModifierKey.SHIFT && state.hypActive -> when (base) {
                    MathFunction.SIN -> MathFunction.ASINH
                    MathFunction.COS -> MathFunction.ACOSH
                    MathFunction.TAN -> MathFunction.ATANH
                    else -> base
                }
                mod == ModifierKey.SHIFT -> when (base) {
                    MathFunction.SIN -> MathFunction.ASIN
                    MathFunction.COS -> MathFunction.ACOS
                    MathFunction.TAN -> MathFunction.ATAN
                    else -> base
                }
                state.hypActive -> when (base) {
                    MathFunction.SIN -> MathFunction.SINH
                    MathFunction.COS -> MathFunction.COSH
                    MathFunction.TAN -> MathFunction.TANH
                    else -> base
                }
                else -> base
            }
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            state.copy(
                document = CursorEngine.insertFunction(baseDoc, fn),
                modifier = ModifierKey.NONE,
                hypActive = false,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onFractionKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = if (state.modifier == ModifierKey.SHIFT) {
                CursorEngine.insertMixedFraction(baseDoc)
            } else {
                CursorEngine.insertFraction(baseDoc)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onSqrtKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = if (state.modifier == ModifierKey.SHIFT) {
                CursorEngine.insertSqrt(baseDoc, withCustomIndex = true, presetIndex = "3")
            } else {
                CursorEngine.insertSqrt(baseDoc)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onSquareKey() {
        _uiState.update { state ->
            val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
            val expLit = if (state.modifier == ModifierKey.SHIFT) "3" else "2"
            state.copy(
                document = CursorEngine.insertFixedPower(baseDoc, expLit),
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onPowerKey() {
        _uiState.update { state ->
            val mod = state.modifier
            val baseDoc = if (mod == ModifierKey.SHIFT) {
                if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            } else {
                prepareDocForContinuation(state, continueWithAns = true)
            }
            val nextDoc = if (mod == ModifierKey.SHIFT) {
                CursorEngine.insertSqrt(baseDoc, withCustomIndex = true)
            } else {
                CursorEngine.insertPower(baseDoc)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onReciprocalKey() {
        _uiState.update { state ->
            val mod = state.modifier
            if (mod == ModifierKey.SHIFT) {
                val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
                return@update state.copy(
                    document = CursorEngine.insertOperator(baseDoc, MathOperator.FACTORIAL),
                    modifier = ModifierKey.NONE,
                    error = null,
                    hasEvaluatedCurrent = false,
                )
            }
            val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
            state.copy(
                document = CursorEngine.insertFixedPower(baseDoc, "-1"),
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onLogBaseKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = when (state.modifier) {
                ModifierKey.SHIFT -> CursorEngine.insertSumProd(baseDoc, SumProdKind.SUM)
                ModifierKey.ALPHA -> CursorEngine.insertSumProd(baseDoc, SumProdKind.PRODUCT)
                else -> CursorEngine.insertLog(baseDoc)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onLog10Key() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = if (state.modifier == ModifierKey.SHIFT) {
                var d = CursorEngine.insertNumberLiteral(baseDoc, "10")
                d = CursorEngine.insertPower(d)
                d
            } else {
                CursorEngine.insertFunction(baseDoc, MathFunction.LOG10)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onLnKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = if (state.modifier == ModifierKey.SHIFT) {
                var d = CursorEngine.insertConstant(baseDoc, MathConstant.EulerE)
                d = CursorEngine.insertPower(d)
                d
            } else {
                CursorEngine.insertFunction(baseDoc, MathFunction.LN)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onIntegralKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = if (state.modifier == ModifierKey.SHIFT) {
                CursorEngine.insertDerivative(baseDoc)
            } else {
                CursorEngine.insertIntegral(baseDoc)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onConstOrConvKey() {
        _uiState.update { state ->
            val target = if (state.modifier == ModifierKey.SHIFT) ActiveOverlay.ConvPicker else ActiveOverlay.ConstPicker
            state.copy(modifier = ModifierKey.NONE, overlay = target)
        }
    }

    fun onAbsOrConstKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            state.copy(
                document = CursorEngine.insertAbs(baseDoc),
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onLeftParenKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = if (state.modifier == ModifierKey.SHIFT) {
                val contDoc = prepareDocForContinuation(state, continueWithAns = true)
                CursorEngine.insertOperator(contDoc, MathOperator.PERCENT)
            } else {
                CursorEngine.insertParens(baseDoc)
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onRightParenKey() {
        _uiState.update { state ->
            when (state.modifier) {
                ModifierKey.ALPHA -> insertVarWithClearIfEvaluated(state, MathVariable.X)
                ModifierKey.STO -> storeLastAnsInto(state, MathVariable.X)
                ModifierKey.RCL -> insertVarWithClearIfEvaluated(state, MathVariable.X)
                ModifierKey.SHIFT -> {
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(
                        document = CursorEngine.insertOperator(baseDoc, MathOperator.COMMA),
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
                else -> {
                    // Step right out of current parenthesis/slot if inside one
                    state.copy(
                        document = CursorEngine.moveRight(state.document),
                        modifier = ModifierKey.NONE,
                        error = null,
                    )
                }
            }
        }
    }

    fun onRclKey() {
        _uiState.update { state ->
            val nextMod = if (state.modifier == ModifierKey.SHIFT) ModifierKey.STO else ModifierKey.RCL
            state.copy(modifier = nextMod)
        }
    }

    fun onEngKey() {
        _uiState.update { state ->
            if (state.mode == CalcMode.CMPLX) {
                val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                val nextDoc = if (state.modifier == ModifierKey.SHIFT) {
                    CursorEngine.insertOperator(baseDoc, MathOperator.ANGLE)
                } else {
                    CursorEngine.insertConstant(baseDoc, MathConstant.ImaginaryI)
                }
                return@update state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
            }

            val res = state.result
            if (res?.numericValue != null) {
                val delta = if (state.modifier == ModifierKey.SHIFT) -1 else 1
                val newShift = state.engShift + delta
                val text = BigMath.formatDisplay(res.numericValue, state.numberFormat, newShift)
                return@update state.copy(
                    engShift = newShift,
                    modifier = ModifierKey.NONE,
                    result = res.copy(primaryText = text, formatBadge = "ENG"),
                )
            }
            state.copy(modifier = ModifierKey.NONE)
        }
    }

    fun onSToDKey() {
        _uiState.update { state ->
            when (state.modifier) {
                ModifierKey.SHIFT -> {
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    return@update state.copy(
                        document = CursorEngine.insertAbs(baseDoc),
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
                ModifierKey.ALPHA -> return@update insertVarWithClearIfEvaluated(state, MathVariable.Y)
                ModifierKey.STO -> return@update storeLastAnsInto(state, MathVariable.Y)
                ModifierKey.RCL -> return@update insertVarWithClearIfEvaluated(state, MathVariable.Y)
                else -> Unit
            }

            if (state.mode == CalcMode.CMPLX && state.hasEvaluatedCurrent) {
                val nextPolar = !state.cmplxPolarOutput
                val ans = state.complexAns
                val text = if (nextPolar) {
                    ans.formatPolar(state.angleUnit, state.numberFormat)
                } else {
                    ans.formatRectangular(state.numberFormat)
                }
                return@update state.copy(
                    cmplxPolarOutput = nextPolar,
                    result = state.result?.copy(
                        primaryText = text,
                        formatBadge = if (nextPolar) "POLAR" else "RECT",
                    ),
                )
            }

            val res = state.result ?: return@update state.copy(modifier = ModifierKey.NONE)
            if (res.variations.size <= 1) return@update state.copy(modifier = ModifierKey.NONE)
            val nextIdx = (res.variationIndex + 1) % res.variations.size
            val (badge, text) = res.variations[nextIdx]
            state.copy(
                modifier = ModifierKey.NONE,
                dmsDisplayActive = false,
                result = res.copy(
                    primaryText = text,
                    variationIndex = nextIdx,
                    formatBadge = badge,
                ),
            )
        }
    }

    fun onMPlusKey() {
        _uiState.update { state ->
            when (state.modifier) {
                ModifierKey.ALPHA -> return@update insertVarWithClearIfEvaluated(state, MathVariable.M)
                ModifierKey.STO -> return@update storeLastAnsInto(state, MathVariable.M)
                ModifierKey.RCL -> return@update insertVarWithClearIfEvaluated(state, MathVariable.M)
                else -> Unit
            }
            val isMinus = state.modifier == ModifierKey.SHIFT
            // Evaluate current expression if non-empty, else use numeric result
            val valueToAdd = try {
                if (!state.document.root.isEmpty) {
                    val ctx = CalcContext(state.angleUnit, state.numberFormat, state.variables)
                    CompEvaluator.evaluate(state.document.root, ctx).decimal
                } else {
                    state.result?.numericValue ?: state.variables[MathVariable.ANS] ?: BigDecimal.ZERO
                }
            } catch (e: CalcEngineException) {
                return@update state.copy(
                    modifier = ModifierKey.NONE,
                    error = ErrorDisplay(e.kind, e.message, e.errorOwnerId, e.errorRole, e.errorIndex),
                )
            }

            val curM = state.variables[MathVariable.M] ?: BigDecimal.ZERO
            val newM = if (isMinus) BigMath.sub(curM, valueToAdd) else BigMath.add(curM, valueToAdd)
            persistMemory(MathVariable.M, newM)
            state.copy(
                modifier = ModifierKey.NONE,
                variables = state.variables + (MathVariable.M to newM),
                hasEvaluatedCurrent = true,
                result = ResultDisplay(
                    primaryText = BigMath.formatDisplay(valueToAdd, state.numberFormat),
                    numericValue = valueToAdd,
                    formatBadge = if (isMinus) "M-" else "M+",
                ),
            )
        }
    }

    fun onTenExpKey() {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = when (state.modifier) {
                ModifierKey.SHIFT -> CursorEngine.insertConstant(baseDoc, MathConstant.Pi)
                ModifierKey.ALPHA -> CursorEngine.insertConstant(baseDoc, MathConstant.EulerE)
                else -> {
                    var d = if (baseDoc.root.isEmpty) baseDoc else CursorEngine.insertOperator(baseDoc, MathOperator.MULTIPLY)
                    d = CursorEngine.insertNumberLiteral(d, "10")
                    CursorEngine.insertPower(d)
                }
            }
            state.copy(document = nextDoc, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
        }
    }

    fun onAnsKey() {
        _uiState.update { state ->
            val targetVar = if (state.modifier == ModifierKey.SHIFT) MathVariable.PRE_ANS else MathVariable.ANS
            insertVarWithClearIfEvaluated(state, targetVar)
        }
    }

    fun onMulOrPermKey() {
        _uiState.update { state ->
            val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
            val op = if (state.modifier == ModifierKey.SHIFT) MathOperator.PERMUTATION else MathOperator.MULTIPLY
            state.copy(
                document = CursorEngine.insertOperator(baseDoc, op),
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onDivOrCombKey() {
        _uiState.update { state ->
            val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
            val op = if (state.modifier == ModifierKey.SHIFT) MathOperator.COMBINATION else MathOperator.DIVIDE
            state.copy(
                document = CursorEngine.insertOperator(baseDoc, op),
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onCalcOrSolveKey() {
        val state = _uiState.value
        if (state.modifier == ModifierKey.SHIFT) {
            _uiState.update {
                it.copy(
                    modifier = ModifierKey.NONE,
                    overlay = ActiveOverlay.SolvePrompt(initialGuessDraft = "0"),
                )
            }
        } else {
            val vars = CompEvaluator.collectFreeVariables(state.document.root)
            if (vars.isEmpty()) {
                onEquals()
            } else {
                val initVal = state.variables[vars.first()] ?: BigDecimal.ZERO
                _uiState.update {
                    it.copy(
                        modifier = ModifierKey.NONE,
                        overlay = ActiveOverlay.CalcPrompt(
                            variables = vars,
                            currentIndex = 0,
                            values = state.variables,
                            draft = initVal.stripTrailingZeros().toPlainString(),
                        ),
                    )
                }
            }
        }
    }

    fun onDelete() {
        _uiState.update { state ->
            if (state.error != null) {
                return@update state.copy(error = null)
            }
            state.copy(
                document = CursorEngine.deleteBeforeCursor(state.document),
                modifier = ModifierKey.NONE,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun onAllClear() {
        _uiState.update { state ->
            state.copy(
                document = CursorEngine.clearAll(),
                modifier = ModifierKey.NONE,
                hypActive = false,
                result = null,
                error = null,
                hasEvaluatedCurrent = false,
                engShift = 0,
                dmsDisplayActive = false,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Evaluation (`=`)
    // -------------------------------------------------------------------------

    fun onEquals() {
        val snapshot = _uiState.value
        if (snapshot.document.root.isEmpty) return

        // Handle explicit `Pol` / `Rec` or heavy calculus on background thread safely
        viewModelScope.launch {
            val evalOutcome = withContext(Dispatchers.Default) {
                runEvaluation(snapshot)
            }
            _uiState.update { current ->
                evalOutcome(current)
            }
        }
    }

    private fun runEvaluation(snapshot: CalculatorUiState): (CalculatorUiState) -> CalculatorUiState {
        return try {
            val ctx = CalcContext(snapshot.angleUnit, snapshot.numberFormat, snapshot.variables)
            val reducer: (CalculatorUiState) -> CalculatorUiState = when (snapshot.mode) {
                CalcMode.BASE_N -> {
                    val ansLong = (snapshot.variables[MathVariable.ANS] ?: BigDecimal.ZERO).toLong()
                    val baseRes = BaseNEngine.evaluate(snapshot.document.root, snapshot.baseNBase, snapshot.baseNWidth, ansLong)
                    val text = baseRes.format(snapshot.baseNBase)
                    val newAns = BigDecimal.valueOf(baseRes.rawValue)
                    val preAns = snapshot.variables[MathVariable.ANS] ?: BigDecimal.ZERO
                    saveHistoryEntry(snapshot.document.root, null, text, snapshot.mode.label);
                    { state ->
                        state.copy(
                            baseNResult = baseRes,
                            hasEvaluatedCurrent = true,
                            error = null,
                            variables = state.variables + (MathVariable.PRE_ANS to preAns) + (MathVariable.ANS to newAns),
                            result = ResultDisplay(
                                primaryText = text,
                                formatBadge = snapshot.baseNBase.label,
                                numericValue = newAns,
                            ),
                        )
                    }
                }

                CalcMode.CMPLX -> {
                    val cVal = ComplexEvaluator.evaluate(
                        snapshot.document.root,
                        ctx,
                        mapOf(MathVariable.ANS to snapshot.complexAns),
                    )
                    val rectText = cVal.formatRectangular(snapshot.numberFormat)
                    val polarText = cVal.formatPolar(snapshot.angleUnit, snapshot.numberFormat)
                    val primary = if (snapshot.cmplxPolarOutput) polarText else rectText
                    val secondary = if (snapshot.cmplxPolarOutput) rectText else polarText
                    saveHistoryEntry(snapshot.document.root, rectText, polarText, snapshot.mode.label);
                    { state ->
                        state.copy(
                            complexAns = cVal,
                            hasEvaluatedCurrent = true,
                            error = null,
                            result = ResultDisplay(
                                primaryText = primary,
                                secondaryLine = secondary,
                                formatBadge = if (snapshot.cmplxPolarOutput) "POLAR" else "CMPLX",
                                numericValue = if (cVal.isReal) cVal.re else null,
                            ),
                        )
                    }
                }

                CalcMode.MATRIX -> {
                    val mVal = MatrixEvaluator.evaluate(snapshot.document.root, ctx, snapshot.matrices)
                    when (mVal) {
                        is MatrixEvalValue.Mat -> {
                            val m = mVal.matrix
                            val summary = "${m.rows}×${m.cols} Matrix"
                            val fn: (CalculatorUiState) -> CalculatorUiState = { state ->
                                state.copy(
                                    matrices = state.matrices + (MathVariable.MAT_ANS to m),
                                    hasEvaluatedCurrent = true,
                                    error = null,
                                    result = ResultDisplay(
                                        primaryText = summary,
                                        formatBadge = "MAT",
                                        matrixResult = m,
                                    ),
                                )
                            }
                            fn
                        }
                        is MatrixEvalValue.Scalar -> {
                            val decStr = BigMath.formatDisplay(mVal.value, snapshot.numberFormat)
                            val fn: (CalculatorUiState) -> CalculatorUiState = { state ->
                                state.copy(
                                    hasEvaluatedCurrent = true,
                                    error = null,
                                    result = ResultDisplay(
                                        primaryText = decStr,
                                        formatBadge = "DEC",
                                        numericValue = mVal.value,
                                    ),
                                )
                            }
                            fn
                        }
                    }
                }

                else -> {
                    val compRes = CompEvaluator.evaluate(snapshot.document.root, ctx)
                    val variations = compRes.formatVariations(snapshot.numberFormat)
                    val first = variations.firstOrNull() ?: ("DEC" to BigMath.formatDisplay(compRes.decimal, snapshot.numberFormat))
                    val secondary = compRes.secondaryPolar?.let { (v1, v2) ->
                        val (l1, l2) = compRes.secondaryLabels ?: ("r" to "θ")
                        "$l1 = ${BigMath.formatDisplay(v1, snapshot.numberFormat)},  $l2 = ${BigMath.formatDisplay(v2, snapshot.numberFormat)}"
                    }
                    val preAns = snapshot.variables[MathVariable.ANS] ?: BigDecimal.ZERO
                    persistMemory(MathVariable.ANS, compRes.decimal)
                    saveHistoryEntry(
                        snapshot.document.root,
                        compRes.exact?.formatExact(),
                        BigMath.formatDisplay(compRes.decimal, snapshot.numberFormat),
                        snapshot.mode.label,
                    );
                    { state ->
                        state.copy(
                            hasEvaluatedCurrent = true,
                            error = null,
                            engShift = 0,
                            dmsDisplayActive = false,
                            variables = state.variables + (MathVariable.PRE_ANS to preAns) + (MathVariable.ANS to compRes.decimal),
                            result = ResultDisplay(
                                primaryText = first.second,
                                exactSymbolic = if (first.first == "EXACT") compRes.exact else null,
                                variations = variations,
                                variationIndex = 0,
                                secondaryLine = secondary,
                                formatBadge = first.first,
                                numericValue = compRes.decimal,
                            ),
                        )
                    }
                }
            }
            reducer
        } catch (e: CalcEngineException) {
            { state ->
                state.copy(
                    error = ErrorDisplay(e.kind, e.message, e.errorOwnerId, e.errorRole, e.errorIndex),
                    modifier = ModifierKey.NONE,
                )
            }
        } catch (e: Exception) {
            { state ->
                state.copy(
                    error = ErrorDisplay(ErrorKind.MATH, e.message ?: "Evaluation error"),
                    modifier = ModifierKey.NONE,
                )
            }
        }
    }

    // -------------------------------------------------------------------------
    // Constants, Conversions, CALC, SOLVE, History & Mode Updaters
    // -------------------------------------------------------------------------

    fun insertScientificConstant(info: ScientificConstantInfo) {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            val nextDoc = CursorEngine.insertConstant(
                baseDoc,
                MathConstant.PhysicalConstant(info.code, info.symbol),
            )
            state.copy(
                document = nextDoc,
                overlay = ActiveOverlay.None,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun applyUnitConversion(conv: UnitConversionInfo) {
        _uiState.update { state ->
            val baseVal = try {
                if (!state.document.root.isEmpty && !state.hasEvaluatedCurrent) {
                    val ctx = CalcContext(state.angleUnit, state.numberFormat, state.variables)
                    CompEvaluator.evaluate(state.document.root, ctx).decimal
                } else {
                    state.result?.numericValue ?: state.variables[MathVariable.ANS] ?: BigDecimal.ZERO
                }
            } catch (e: CalcEngineException) {
                return@update state.copy(
                    overlay = ActiveOverlay.None,
                    error = ErrorDisplay(e.kind, e.message, e.errorOwnerId, e.errorRole, e.errorIndex),
                )
            }
            val converted = conv.convert(baseVal)
            val text = BigMath.formatDisplay(converted, state.numberFormat)
            state.copy(
                overlay = ActiveOverlay.None,
                hasEvaluatedCurrent = true,
                variables = state.variables + (MathVariable.ANS to converted),
                result = ResultDisplay(
                    primaryText = text,
                    numericValue = converted,
                    secondaryLine = "${BigMath.formatDisplay(baseVal, state.numberFormat)} (${conv.label})",
                    formatBadge = "CONV",
                ),
            )
        }
    }

    fun updateCalcDraft(draft: String) {
        _uiState.update { state ->
            val overlay = state.overlay as? ActiveOverlay.CalcPrompt ?: return@update state
            state.copy(overlay = overlay.copy(draft = draft))
        }
    }

    fun confirmCalcStep() {
        val state = _uiState.value
        val overlay = state.overlay as? ActiveOverlay.CalcPrompt ?: return
        val value = overlay.draft.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val currentVar = overlay.variables[overlay.currentIndex]
        val updatedValues = overlay.values + (currentVar to value)
        persistMemory(currentVar, value)

        if (overlay.currentIndex + 1 < overlay.variables.size) {
            val nextVar = overlay.variables[overlay.currentIndex + 1]
            val nextDefault = updatedValues[nextVar]?.stripTrailingZeros()?.toPlainString() ?: "0"
            _uiState.update {
                it.copy(
                    variables = updatedValues,
                    overlay = overlay.copy(
                        currentIndex = overlay.currentIndex + 1,
                        values = updatedValues,
                        draft = nextDefault,
                    ),
                )
            }
        } else {
            _uiState.update {
                it.copy(variables = updatedValues, overlay = ActiveOverlay.None)
            }
            onEquals()
        }
    }

    fun updateSolveGuess(draft: String) {
        _uiState.update { state ->
            val overlay = state.overlay as? ActiveOverlay.SolvePrompt ?: return@update state
            state.copy(overlay = overlay.copy(initialGuessDraft = draft, errorMessage = null))
        }
    }

    fun runSolve() {
        val state = _uiState.value
        val overlay = state.overlay as? ActiveOverlay.SolvePrompt ?: return
        val guess = overlay.initialGuessDraft.toBigDecimalOrNull() ?: BigDecimal.ZERO
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.Default) {
                    val ctx = CalcContext(state.angleUnit, state.numberFormat, state.variables)
                    CompEvaluator.solveForX(state.document.root, guess, ctx)
                }
                persistMemory(MathVariable.X, res.root)
                _uiState.update { current ->
                    current.copy(
                        variables = current.variables + (MathVariable.X to res.root) + (MathVariable.ANS to res.root),
                        overlay = overlay.copy(result = res, errorMessage = null),
                        hasEvaluatedCurrent = true,
                        result = ResultDisplay(
                            primaryText = BigMath.formatDisplay(res.root, current.numberFormat),
                            numericValue = res.root,
                            secondaryLine = "L-R = ${BigMath.formatDisplay(res.residualLR, current.numberFormat)}",
                            formatBadge = "SOLVE",
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update { current ->
                    current.copy(
                        overlay = overlay.copy(errorMessage = e.message ?: "Can't Solve"),
                    )
                }
            }
        }
    }

    fun restoreFromHistory(entry: CalcHistoryEntity) {
        val decoded = AstJsonCodec.decodeSlot(entry.expressionAstJson) ?: return
        _uiState.update { state ->
            var nextId = state.document.nextId
            val reindexed = AstJsonCodec.reindexNodes(decoded) { nextId++ }
            val newDoc = AstDocument(
                root = reindexed,
                cursor = CursorPosition(ROOT_NODE_ID, SlotRole.Root, reindexed.size, 0),
                nextId = nextId,
            )
            state.copy(
                document = newDoc,
                error = null,
                hasEvaluatedCurrent = false,
                historyExpanded = false,
            )
        }
    }

    fun insertResultFromHistory(valueStr: String) {
        val clean = valueStr.replace(",", "")
        if (clean.any { it !in '0'..'9' && it != '.' && it != '-' }) return
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            state.copy(
                document = CursorEngine.insertNumberLiteral(baseDoc, clean),
                error = null,
                hasEvaluatedCurrent = false,
                historyExpanded = false,
            )
        }
    }

    // Mode-specific state setters
    fun setBaseNBase(base: NumberBase) {
        _uiState.update { state ->
            val baseRes = state.baseNResult
            val updatedText = baseRes?.format(base) ?: state.result?.primaryText
            state.copy(
                baseNBase = base,
                result = if (updatedText != null) state.result?.copy(primaryText = updatedText, formatBadge = base.label) else state.result,
            )
        }
    }

    fun toggleBaseNWidth() {
        _uiState.update { state ->
            val nextW = if (state.baseNWidth == BitWidth.BIT_32) BitWidth.BIT_64 else BitWidth.BIT_32
            state.copy(baseNWidth = nextW)
        }
    }

    fun insertBaseNOp(op: MathOperator) = onOperator(op)

    fun insertBaseNFunc(fn: MathFunction) {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            state.copy(
                document = CursorEngine.insertFunction(baseDoc, fn),
                modifier = ModifierKey.NONE,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun insertMatrixOrVectorVar(v: MathVariable) {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            state.copy(
                document = CursorEngine.insertVariable(baseDoc, v),
                overlay = ActiveOverlay.None,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun insertFuncAndCloseOverlay(fn: MathFunction) {
        _uiState.update { state ->
            val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
            state.copy(
                document = CursorEngine.insertFunction(baseDoc, fn),
                overlay = ActiveOverlay.None,
                error = null,
                hasEvaluatedCurrent = false,
            )
        }
    }

    fun updateMatrix(variable: MathVariable, matrix: CalcMatrix) {
        _uiState.update { it.copy(matrices = it.matrices + (variable to matrix)) }
    }

    fun updateVector(variable: MathVariable, vector: CalcVector) {
        _uiState.update { it.copy(vectors = it.vectors + (variable to vector)) }
    }

    fun updateStatData(rows: List<StatDataRow>, twoVar: Boolean) {
        _uiState.update { it.copy(statRows = rows, statTwoVar = twoVar) }
    }

    fun executeCatalogAction(action: CatalogAction) {
        when (action) {
            is CatalogAction.InsertFunction -> {
                _uiState.update { state ->
                    val targetMode = action.mode ?: state.mode
                    val baseDoc = if (state.hasEvaluatedCurrent || targetMode != state.mode) AstDocument.empty() else state.document
                    state.copy(
                        mode = targetMode,
                        document = CursorEngine.insertFunction(baseDoc, action.fn),
                        overlay = ActiveOverlay.None,
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
            is CatalogAction.InsertOperator -> {
                _uiState.update { state ->
                    val targetMode = action.mode ?: state.mode
                    val baseDoc = if (targetMode != state.mode) AstDocument.empty() else prepareDocForContinuation(state, continueWithAns = true)
                    state.copy(
                        mode = targetMode,
                        document = CursorEngine.insertOperator(baseDoc, action.op),
                        overlay = ActiveOverlay.None,
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
            is CatalogAction.InsertConstant -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(
                        document = CursorEngine.insertConstant(baseDoc, action.constant),
                        overlay = ActiveOverlay.None,
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
            is CatalogAction.InsertVariable -> {
                _uiState.update { state ->
                    val targetMode = action.mode ?: state.mode
                    val baseDoc = if (state.hasEvaluatedCurrent || targetMode != state.mode) AstDocument.empty() else state.document
                    state.copy(
                        mode = targetMode,
                        document = CursorEngine.insertVariable(baseDoc, action.variable),
                        overlay = ActiveOverlay.None,
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
            is CatalogAction.InsertFraction -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    val nextDoc = if (action.mixed) CursorEngine.insertMixedFraction(baseDoc) else CursorEngine.insertFraction(baseDoc)
                    state.copy(document = nextDoc, overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            is CatalogAction.InsertSqrt -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    val nextDoc = CursorEngine.insertSqrt(baseDoc, action.customIndex, action.presetIndex)
                    state.copy(document = nextDoc, overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            is CatalogAction.InsertPower -> {
                _uiState.update { state ->
                    val baseDoc = prepareDocForContinuation(state, continueWithAns = true)
                    val nextDoc = if (action.fixedExponent != null) {
                        CursorEngine.insertFixedPower(baseDoc, action.fixedExponent)
                    } else {
                        CursorEngine.insertPower(baseDoc)
                    }
                    state.copy(document = nextDoc, overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            is CatalogAction.InsertBasePower -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    var d = if (action.baseLiteral == "e") {
                        CursorEngine.insertConstant(baseDoc, MathConstant.EulerE)
                    } else {
                        CursorEngine.insertNumberLiteral(baseDoc, action.baseLiteral)
                    }
                    d = CursorEngine.insertPower(d)
                    state.copy(document = d, overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            is CatalogAction.InsertNumber -> {
                _uiState.update { state ->
                    val switchToBase = state.mode != CalcMode.BASE_N && action.literal.any { it in 'A'..'F' }
                    val baseDoc = if (state.hasEvaluatedCurrent || switchToBase) AstDocument.empty() else state.document
                    var updated = state.copy(
                        mode = if (switchToBase) CalcMode.BASE_N else state.mode,
                        baseNBase = if (switchToBase) NumberBase.HEX else state.baseNBase,
                        document = baseDoc,
                        overlay = ActiveOverlay.None,
                        hasEvaluatedCurrent = false,
                    )
                    _uiState.value = updated
                    for (ch in action.literal) {
                        if (ch in 'A'..'F') onHexDigit(ch) else onDigit(ch)
                    }
                    _uiState.value
                }
            }
            CatalogAction.InsertLogBase -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(document = CursorEngine.insertLog(baseDoc), overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            CatalogAction.InsertParens -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(document = CursorEngine.insertParens(baseDoc), overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            CatalogAction.InsertAbs -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(document = CursorEngine.insertAbs(baseDoc), overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            CatalogAction.InsertIntegral -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(document = CursorEngine.insertIntegral(baseDoc), overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            CatalogAction.InsertDerivative -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(document = CursorEngine.insertDerivative(baseDoc), overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            is CatalogAction.InsertSumProd -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(document = CursorEngine.insertSumProd(baseDoc, action.kind), overlay = ActiveOverlay.None, modifier = ModifierKey.NONE, error = null, hasEvaluatedCurrent = false)
                }
            }
            is CatalogAction.InsertMatrix -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent || state.mode != CalcMode.MATRIX) AstDocument.empty() else state.document
                    state.copy(
                        mode = CalcMode.MATRIX,
                        document = CursorEngine.insertMatrix(baseDoc, action.rows, action.cols),
                        overlay = ActiveOverlay.None,
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
            is CatalogAction.InsertTemplate -> {
                _uiState.update { state ->
                    val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
                    state.copy(
                        document = action.build(baseDoc),
                        overlay = ActiveOverlay.None,
                        modifier = ModifierKey.NONE,
                        error = null,
                        hasEvaluatedCurrent = false,
                    )
                }
            }
            is CatalogAction.ApplyConversion -> applyUnitConversion(action.info)
            is CatalogAction.SwitchMode -> {
                selectMode(action.mode)
                if (!action.openSheet) dismissOverlay()
            }
            is CatalogAction.SetAngleUnit -> {
                _uiState.update { it.copy(angleUnit = action.unit, overlay = ActiveOverlay.None) }
            }
            is CatalogAction.SetNumberFormat -> {
                setNumberFormat(action.format)
                dismissOverlay()
            }
            is CatalogAction.SetBaseN -> {
                _uiState.update { it.copy(mode = CalcMode.BASE_N, baseNBase = action.base, overlay = ActiveOverlay.None) }
            }
            is CatalogAction.SetTheme -> {
                _uiState.update { it.copy(theme = action.preset, overlay = ActiveOverlay.None) }
            }
            is CatalogAction.RunCommand -> {
                dismissOverlay()
                when (action.command) {
                    CatalogCommand.PRIME_FACTOR -> onPrimeFactorize()
                    CatalogCommand.TOGGLE_SD -> onSToDKey()
                    CatalogCommand.TOGGLE_DMS -> {
                        _uiState.update { state ->
                            val dec = state.result?.numericValue ?: return@update state
                            val nextDms = !state.dmsDisplayActive
                            val formatted = if (nextDms) formatDms(dec) else BigMath.formatDisplay(dec, state.numberFormat, state.engShift)
                            state.copy(dmsDisplayActive = nextDms, result = state.result.copy(primaryText = formatted, formatBadge = if (nextDms) "DMS" else "DEC"))
                        }
                    }
                    CatalogCommand.ENG_RIGHT -> _uiState.update { it.copy(modifier = ModifierKey.NONE) }.also { onEngKey() }
                    CatalogCommand.ENG_LEFT -> _uiState.update { it.copy(modifier = ModifierKey.SHIFT) }.also { onEngKey() }
                    CatalogCommand.OPEN_CALC -> _uiState.update { it.copy(modifier = ModifierKey.NONE) }.also { onCalcOrSolveKey() }
                    CatalogCommand.OPEN_SOLVE -> _uiState.update { it.copy(modifier = ModifierKey.SHIFT) }.also { onCalcOrSolveKey() }
                    CatalogCommand.TOGGLE_COMPLEX_POLAR -> _uiState.update { it.copy(mode = CalcMode.CMPLX) }.also { onSToDKey() }
                    CatalogCommand.TOGGLE_HISTORY -> toggleHistoryExpanded()
                    CatalogCommand.CLEAR_MEMORY -> clearAllMemories()
                    CatalogCommand.DELETE_CHAR -> onDelete()
                    CatalogCommand.ALL_CLEAR -> onAllClear()
                    CatalogCommand.EVALUATE -> onEquals()
                    CatalogCommand.M_PLUS -> _uiState.update { it.copy(modifier = ModifierKey.NONE) }.also { onMPlusKey() }
                    CatalogCommand.M_MINUS -> _uiState.update { it.copy(modifier = ModifierKey.SHIFT) }.also { onMPlusKey() }
                    CatalogCommand.TOGGLE_BASE_WIDTH -> {
                        _uiState.update { it.copy(mode = CalcMode.BASE_N) }
                        toggleBaseNWidth()
                    }
                    CatalogCommand.OPEN_STAT_SHEET -> openOverlay(ActiveOverlay.StatSheet)
                    CatalogCommand.OPEN_EQN_SHEET -> openOverlay(ActiveOverlay.EqnSheet)
                    CatalogCommand.OPEN_MATRIX_SHEET -> openOverlay(ActiveOverlay.MatrixSheet)
                    CatalogCommand.OPEN_VECTOR_SHEET -> openOverlay(ActiveOverlay.VectorSheet)
                    CatalogCommand.OPEN_TABLE_SHEET -> openOverlay(ActiveOverlay.TableSheet)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun prepareDocForContinuation(state: CalculatorUiState, continueWithAns: Boolean): AstDocument {
        if (!state.hasEvaluatedCurrent) return state.document
        if (!continueWithAns) return AstDocument.empty()
        val fresh = AstDocument.empty()
        return CursorEngine.insertVariable(fresh, MathVariable.ANS)
    }

    private fun insertVarWithClearIfEvaluated(state: CalculatorUiState, variable: MathVariable): CalculatorUiState {
        val baseDoc = if (state.hasEvaluatedCurrent) AstDocument.empty() else state.document
        return state.copy(
            document = CursorEngine.insertVariable(baseDoc, variable),
            modifier = ModifierKey.NONE,
            error = null,
            hasEvaluatedCurrent = false,
        )
    }

    private fun storeLastAnsInto(state: CalculatorUiState, target: MathVariable): CalculatorUiState {
        val valueToStore = state.result?.numericValue
            ?: state.variables[MathVariable.ANS]
            ?: BigDecimal.ZERO
        persistMemory(target, valueToStore)
        return state.copy(
            modifier = ModifierKey.NONE,
            variables = state.variables + (target to valueToStore),
            hasEvaluatedCurrent = true,
            result = ResultDisplay(
                primaryText = BigMath.formatDisplay(valueToStore, state.numberFormat),
                numericValue = valueToStore,
                secondaryLine = "Ans → ${target.symbol}",
                formatBadge = "STO",
            ),
        )
    }

    private fun persistMemory(variable: MathVariable, value: BigDecimal) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveMemory(variable.name, value.toPlainString())
        }
    }

    private fun saveHistoryEntry(
        root: ExprSlot,
        exactStr: String?,
        decimalStr: String,
        modeLabel: String,
    ) {
        val plain = AstLatexSerializer.toDisplayText(root)
        val json = AstJsonCodec.encodeSlot(root)
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveHistory(
                CalcHistoryEntity(
                    expressionPlain = plain,
                    expressionAstJson = json,
                    resultExact = exactStr,
                    resultDecimal = decimalStr,
                    modeName = modeLabel,
                )
            )
        }
    }

    private fun formatDms(decimalDeg: BigDecimal): String {
        val neg = decimalDeg < BigDecimal.ZERO
        val absVal = decimalDeg.abs()
        val deg = absVal.toBigInteger()
        val remMin = absVal.subtract(BigDecimal(deg)).multiply(BigDecimal("60"))
        val min = remMin.toBigInteger()
        val sec = remMin.subtract(BigDecimal(min)).multiply(BigDecimal("60"))
            .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
        val sign = if (neg) "-" else ""
        return "$sign$deg°$min′${sec.toPlainString()}″"
    }
}
