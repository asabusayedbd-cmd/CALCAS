package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ast.MathFunction
import com.example.ast.MathVariable
import com.example.engine.BigMath
import com.example.engine.CalcContext
import com.example.engine.CalcMatrix
import com.example.engine.CalcVector
import com.example.engine.EqnSolution
import com.example.engine.EqnSolver
import com.example.engine.NumberFormatMode
import com.example.engine.RegressionType
import com.example.engine.StatDataRow
import com.example.engine.StatEngine
import com.example.engine.TableEngine
import com.example.engine.TableRow
import com.example.serializer.AstLatexSerializer
import com.example.state.CalculatorUiState
import com.example.state.CalculatorViewModel
import com.example.ui.theme.CalcPalette
import com.example.ui.theme.JetBrainsMonoFamily
import java.math.BigDecimal

// -----------------------------------------------------------------------------
// 1. STAT Mode Sheet (Data Table + 1-Var Stats + 7 Regressions)
// -----------------------------------------------------------------------------

@Composable
fun StatSheetDialog(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    val rows = remember {
        mutableStateListOf<StatDataRow>().apply {
            if (state.statRows.isEmpty()) {
                add(StatDataRow(BigDecimal("1"), BigDecimal("2")))
                add(StatDataRow(BigDecimal("2"), BigDecimal("4")))
                add(StatDataRow(BigDecimal("3"), BigDecimal("6")))
            } else {
                addAll(state.statRows)
            }
        }
    }
    var twoVar by remember { mutableStateOf(state.statTwoVar) }
    var xInput by remember { mutableStateOf("") }
    var yInput by remember { mutableStateOf("") }
    var fInput by remember { mutableStateOf("1") }
    var regType by remember { mutableStateOf(RegressionType.LINEAR) }
    var xPred by remember { mutableStateOf("") }

    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("STAT — Data & Regression", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.sheetText)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("2-Var", fontSize = 11.sp, color = palette.sheetTextMuted)
                    Spacer(Modifier.width(4.dp))
                    Switch(checked = twoVar, onCheckedChange = {
                        twoVar = it
                        viewModel.updateStatData(rows.toList(), twoVar)
                    })
                }
            }

            // Input row
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = xInput, onValueChange = { xInput = it }, label = { Text("X") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                if (twoVar) {
                    OutlinedTextField(
                        value = yInput, onValueChange = { yInput = it }, label = { Text("Y") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    value = fInput, onValueChange = { fInput = it }, label = { Text("Freq") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(0.75f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = {
                    val x = xInput.toBigDecimalOrNull() ?: return@Button
                    val y = if (twoVar) yInput.toBigDecimalOrNull() ?: BigDecimal.ZERO else null
                    val f = fInput.toBigDecimalOrNull() ?: BigDecimal.ONE
                    rows.add(StatDataRow(x, y, f))
                    xInput = ""; yInput = ""
                    viewModel.updateStatData(rows.toList(), twoVar)
                }) { Text("Add Row") }
                TextButton(onClick = {
                    if (rows.isNotEmpty()) rows.removeAt(rows.lastIndex)
                    viewModel.updateStatData(rows.toList(), twoVar)
                }) { Text("Remove Last") }
            }

            // Data Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.sheetSurface)
                    .padding(6.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                rows.forEachIndexed { idx, row ->
                    Text(
                        text = "#${idx + 1}:  X=${row.x.toPlainString()}" +
                            (if (twoVar) "  Y=${row.y?.toPlainString() ?: "0"}" else "") +
                            "  (f=${row.freq.toPlainString()})",
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFamily,
                        color = palette.sheetText,
                    )
                }
            }

            // 1-Var Summary
            val oneVarResult = runCatching { StatEngine.compute1Var(rows) }.getOrNull()
            if (oneVarResult != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.lcdBg)
                        .padding(8.dp),
                ) {
                    Text(
                        text = "n=${oneVarResult.n.toPlainString()}   x̄=${BigMath.formatDisplay(oneVarResult.meanX, state.numberFormat)}   Σx=${BigMath.formatDisplay(oneVarResult.sumX, state.numberFormat)}",
                        fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = palette.lcdInk,
                    )
                    Text(
                        text = "σx=${BigMath.formatDisplay(oneVarResult.popStdDev, state.numberFormat)}   sx=${BigMath.formatDisplay(oneVarResult.sampleStdDev, state.numberFormat)}   min=${oneVarResult.minX.toPlainString()}   max=${oneVarResult.maxX.toPlainString()}",
                        fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = palette.lcdInk,
                    )
                }
            }

            if (twoVar) {
                Text("Regression Model", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = palette.sheetTextMuted)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    RegressionType.entries.forEach { t ->
                        ChoiceChip(t.label, regType == t, palette, Modifier.width(102.dp)) { regType = t }
                    }
                }
                val regRes = runCatching { StatEngine.computeRegression(rows, regType) }.getOrNull()
                if (regRes != null) {
                    Column(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.lcdBg).padding(8.dp),
                    ) {
                        Text(regType.formula, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = palette.lcdInk)
                        Text(
                            "A=${BigMath.formatDisplay(regRes.a)}  B=${BigMath.formatDisplay(regRes.b)}" +
                                (regRes.c?.let { "  C=${BigMath.formatDisplay(it)}" } ?: "") +
                                (regRes.r?.let { "  r=${BigMath.formatDisplay(it)}" } ?: ""),
                            fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = palette.lcdInk,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = xPred, onValueChange = { xPred = it }, label = { Text("Predict ŷ(x)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f),
                            )
                            val yHat = xPred.toBigDecimalOrNull()?.let { runCatching { regRes.estimateY(it) }.getOrNull() }
                            Text(
                                text = yHat?.let { "ŷ = ${BigMath.formatDisplay(it)}" } ?: "",
                                fontSize = 12.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, color = palette.lcdInk,
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::dismissOverlay) { Text("Close") }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 2. EQN Mode Sheet (2x2 Linear, 3x3 Linear, Quadratic, Cubic)
// -----------------------------------------------------------------------------

@Composable
fun EqnSheetDialog(
    format: NumberFormatMode,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    var kind by remember { mutableIntStateOf(2) } // 0:2x2, 1:3x3, 2:Quad, 3:Cubic
    val fields = remember { mutableStateListOf("1", "-5", "6", "0", "0", "0", "0", "0", "0", "0", "0", "0") }
    var outputLines by remember { mutableStateOf<List<String>>(emptyList()) }

    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("EQN — Equation Solver", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.sheetText)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip("2×2 Lin", kind == 0, palette, Modifier.weight(1f)) { kind = 0; outputLines = emptyList() }
                ChoiceChip("3×3 Lin", kind == 1, palette, Modifier.weight(1f)) { kind = 1; outputLines = emptyList() }
                ChoiceChip("ax²+bx+c", kind == 2, palette, Modifier.weight(1f)) { kind = 2; outputLines = emptyList() }
                ChoiceChip("ax³+...", kind == 3, palette, Modifier.weight(1f)) { kind = 3; outputLines = emptyList() }
            }

            when (kind) {
                0 -> {
                    Text("aₙx + bₙy = cₙ", fontSize = 11.sp, color = palette.sheetTextMuted)
                    EqnRowInputs(listOf("a1" to 0, "b1" to 1, "c1" to 2), fields)
                    EqnRowInputs(listOf("a2" to 3, "b2" to 4, "c2" to 5), fields)
                }
                1 -> {
                    Text("aₙx + bₙy + cₙz = dₙ", fontSize = 11.sp, color = palette.sheetTextMuted)
                    EqnRowInputs(listOf("a1" to 0, "b1" to 1, "c1" to 2, "d1" to 3), fields)
                    EqnRowInputs(listOf("a2" to 4, "b2" to 5, "c2" to 6, "d2" to 7), fields)
                    EqnRowInputs(listOf("a3" to 8, "b3" to 9, "c3" to 10, "d3" to 11), fields)
                }
                2 -> {
                    Text("ax² + bx + c = 0", fontSize = 11.sp, color = palette.sheetTextMuted)
                    EqnRowInputs(listOf("a" to 0, "b" to 1, "c" to 2), fields)
                }
                3 -> {
                    Text("ax³ + bx² + cx + d = 0", fontSize = 11.sp, color = palette.sheetTextMuted)
                    EqnRowInputs(listOf("a" to 0, "b" to 1, "c" to 2, "d" to 3), fields)
                }
            }

            Button(onClick = {
                fun bd(idx: Int) = fields[idx].toBigDecimalOrNull() ?: BigDecimal.ZERO
                val sol = when (kind) {
                    0 -> EqnSolver.solveLinear2x2(bd(0), bd(1), bd(2), bd(3), bd(4), bd(5))
                    1 -> EqnSolver.solveLinear3x3(
                        arrayOf(
                            arrayOf(bd(0), bd(1), bd(2)),
                            arrayOf(bd(4), bd(5), bd(6)),
                            arrayOf(bd(8), bd(9), bd(10)),
                        ),
                        arrayOf(bd(3), bd(7), bd(11)),
                    )
                    2 -> EqnSolver.solveQuadratic(bd(0), bd(1), bd(2))
                    else -> EqnSolver.solveCubic(bd(0), bd(1), bd(2), bd(3))
                }
                outputLines = when (sol) {
                    is EqnSolution.LinearUnique -> {
                        val names = listOf("X", "Y", "Z")
                        sol.values.mapIndexed { i, v -> "${names[i]} = ${BigMath.formatDisplay(v, format)}" }
                    }
                    is EqnSolution.PolynomialRoots -> sol.roots.mapIndexed { i, r ->
                        "X${i + 1} = ${r.formatRectangular(format)}"
                    }
                    is EqnSolution.Special -> listOf(sol.message)
                }
            }) { Text("Solve") }

            if (outputLines.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.lcdBg).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    outputLines.forEach { line ->
                        Text(line, fontSize = 13.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, color = palette.lcdInk)
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::dismissOverlay) { Text("Close") }
            }
        }
    }
}

@Composable
private fun EqnRowInputs(items: List<Pair<String, Int>>, fields: MutableList<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        items.forEach { (label, idx) ->
            OutlinedTextField(
                value = fields[idx],
                onValueChange = { fields[idx] = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// -----------------------------------------------------------------------------
// 3. MATRIX Mode Sheet (MatA / MatB / MatC up to 4x4)
// -----------------------------------------------------------------------------

@Composable
fun MatrixSheetDialog(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    var activeVar by remember { mutableStateOf(MathVariable.MAT_A) }
    val curMat = state.matrices[activeVar] ?: CalcMatrix(2, 2)
    var rows by remember(activeVar) { mutableIntStateOf(curMat.rows) }
    var cols by remember(activeVar) { mutableIntStateOf(curMat.cols) }
    val cells = remember(activeVar, rows, cols) {
        mutableStateListOf<String>().apply {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val v = if (r < curMat.rows && c < curMat.cols) curMat[r, c] else BigDecimal.ZERO
                    add(v.stripTrailingZeros().toPlainString())
                }
            }
        }
    }

    fun saveMatrix() {
        val bdList = cells.map { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }
        viewModel.updateMatrix(activeVar, CalcMatrix(rows, cols, bdList))
    }

    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("MATRIX Editor (up to 4×4)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.sheetText)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(MathVariable.MAT_A, MathVariable.MAT_B, MathVariable.MAT_C).forEach { v ->
                    ChoiceChip(v.symbol, activeVar == v, palette, Modifier.weight(1f)) {
                        saveMatrix()
                        activeVar = v
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Size:", fontSize = 12.sp, color = palette.sheetTextMuted)
                (1..4).forEach { r ->
                    ChoiceChip("${r}R", rows == r, palette, Modifier.weight(1f)) { rows = r }
                }
                (1..4).forEach { c ->
                    ChoiceChip("${c}C", cols == c, palette, Modifier.weight(1f)) { cols = c }
                }
            }

            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                    for (c in 0 until cols) {
                        val idx = r * cols + c
                        OutlinedTextField(
                            value = cells[idx],
                            onValueChange = { cells[idx] = it; saveMatrix() },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { saveMatrix(); viewModel.insertMatrixOrVectorVar(activeVar) }, modifier = Modifier.weight(1f)) {
                    Text("Insert ${activeVar.symbol}")
                }
                Button(onClick = { saveMatrix(); viewModel.insertFuncAndCloseOverlay(MathFunction.DET) }, modifier = Modifier.weight(0.7f)) {
                    Text("Det")
                }
                Button(onClick = { saveMatrix(); viewModel.insertFuncAndCloseOverlay(MathFunction.TRN) }, modifier = Modifier.weight(0.7f)) {
                    Text("Trn")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { saveMatrix(); viewModel.dismissOverlay() }) { Text("Done") }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 4. VECTOR Mode Sheet (VctA / VctB / VctC 2D/3D + Dot/Cross/Angle)
// -----------------------------------------------------------------------------

@Composable
fun VectorSheetDialog(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    var activeVar by remember { mutableStateOf(MathVariable.VCT_A) }
    val curVct = state.vectors[activeVar] ?: CalcVector(listOf(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
    var dim by remember(activeVar) { mutableIntStateOf(curVct.dim) }
    val comps = remember(activeVar, dim) {
        mutableStateListOf<String>().apply {
            for (i in 0 until dim) {
                add(curVct.components.getOrElse(i) { BigDecimal.ZERO }.stripTrailingZeros().toPlainString())
            }
        }
    }
    var opResult by remember { mutableStateOf("") }

    fun saveVec() {
        viewModel.updateVector(activeVar, CalcVector(comps.map { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }))
    }

    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(14.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("VECTOR Studio (2D / 3D)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.sheetText)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(MathVariable.VCT_A, MathVariable.VCT_B, MathVariable.VCT_C).forEach { v ->
                    ChoiceChip(v.symbol, activeVar == v, palette, Modifier.weight(1f)) {
                        saveVec(); activeVar = v
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("2D", dim == 2, palette, Modifier.weight(1f)) { dim = 2 }
                ChoiceChip("3D", dim == 3, palette, Modifier.weight(1f)) { dim = 3 }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val labels = listOf("x", "y", "z")
                for (i in 0 until dim) {
                    OutlinedTextField(
                        value = comps[i],
                        onValueChange = { comps[i] = it; saveVec() },
                        label = { Text(labels[i]) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                val a = state.vectors[MathVariable.VCT_A]!!
                val b = state.vectors[MathVariable.VCT_B]!!
                ChoiceChip("A·B", false, palette, Modifier.weight(1f)) {
                    saveVec()
                    opResult = runCatching { "VctA · VctB = ${BigMath.formatDisplay(a.dot(b), state.numberFormat)}" }
                        .getOrElse { it.message ?: "Error" }
                }
                ChoiceChip("A×B", false, palette, Modifier.weight(1f)) {
                    saveVec()
                    opResult = runCatching {
                        val c = a.cross(b)
                        "VctA × VctB = [${c.components.joinToString(", ") { BigMath.formatDisplay(it, state.numberFormat) }}]"
                    }.getOrElse { it.message ?: "Error" }
                }
                ChoiceChip("|${activeVar.symbol}|", false, palette, Modifier.weight(1f)) {
                    saveVec()
                    val v = state.vectors[activeVar]!!
                    opResult = "|${activeVar.symbol}| = ${BigMath.formatDisplay(v.magnitude(), state.numberFormat)}"
                }
                ChoiceChip("∠(A,B)", false, palette, Modifier.weight(1f)) {
                    saveVec()
                    opResult = runCatching {
                        "∠(A,B) = ${BigMath.formatDisplay(a.angleBetween(b, state.angleUnit), state.numberFormat)}"
                    }.getOrElse { it.message ?: "Error" }
                }
            }

            if (opResult.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(palette.lcdBg).padding(10.dp),
                ) {
                    Text(opResult, fontSize = 13.sp, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, color = palette.lcdInk)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { saveVec(); viewModel.dismissOverlay() }) { Text("Close") }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 5. TABLE Mode Sheet (f(x), optional g(x), Start, End, Step)
// -----------------------------------------------------------------------------

@Composable
fun TableSheetDialog(
    state: CalculatorUiState,
    palette: CalcPalette,
    viewModel: CalculatorViewModel,
) {
    val fPreview = remember(state.document.root) {
        if (state.document.root.isEmpty) "Enter f(X) on main keypad first (e.g. X² - 2)"
        else "f(X) = ${AstLatexSerializer.toDisplayText(state.document.root)}"
    }
    var startStr by remember { mutableStateOf("-2") }
    var endStr by remember { mutableStateOf("5") }
    var stepStr by remember { mutableStateOf("1") }
    var rows by remember { mutableStateOf<List<TableRow>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = viewModel::dismissOverlay) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(palette.sheetBg)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("TABLE — f(X) Tabulator", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = palette.sheetText)
            Text(fPreview, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily, color = palette.sheetTextMuted)

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(startStr, { startStr = it }, label = { Text("Start") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(endStr, { endStr = it }, label = { Text("End") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(stepStr, { stepStr = it }, label = { Text("Step") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }

            Button(onClick = {
                val start = startStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val end = endStr.toBigDecimalOrNull() ?: BigDecimal("5")
                val step = stepStr.toBigDecimalOrNull() ?: BigDecimal.ONE
                val ctx = CalcContext(state.angleUnit, state.numberFormat, state.variables)
                runCatching {
                    TableEngine.generate(state.document.root, null, start, end, step, ctx)
                }.onSuccess {
                    rows = it
                    errorMsg = null
                }.onFailure {
                    errorMsg = it.message
                    rows = emptyList()
                }
            }) { Text("Generate Table") }

            if (errorMsg != null) {
                Text(errorMsg!!, color = palette.actionKeyTop, fontSize = 12.sp)
            }

            if (rows.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.lcdBg)
                        .padding(8.dp),
                ) {
                    item {
                        Row(Modifier.fillMaxWidth()) {
                            Text("X", fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily, color = palette.lcdInk, modifier = Modifier.weight(1f))
                            Text("f(X)", fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily, color = palette.lcdInk, modifier = Modifier.weight(1.4f))
                        }
                        HorizontalDivider(color = palette.lcdInk.copy(alpha = 0.25f))
                    }
                    items(rows, key = { it.index }) { row ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(BigMath.formatDisplay(row.x, state.numberFormat), fontSize = 12.sp, fontFamily = JetBrainsMonoFamily, color = palette.lcdInk, modifier = Modifier.weight(1f))
                            Text(row.fxDisplay, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily, color = palette.lcdInk, modifier = Modifier.weight(1.4f))
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = viewModel::dismissOverlay) { Text("Close") }
            }
        }
    }
}
