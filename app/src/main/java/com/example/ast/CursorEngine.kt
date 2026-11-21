package com.example.ast

/**
 * Pure, immutable 2D Expression AST editor and D-Pad (REPLAY) cursor navigation engine.
 */
object CursorEngine {

    // -------------------------------------------------------------------------
    // 1. D-Pad / REPLAY Traversal
    // -------------------------------------------------------------------------

    fun moveLeft(doc: AstDocument): AstDocument {
        val cursor = AstTreeOps.clampCursor(doc.root, doc.cursor)
        val slot = AstTreeOps.findSlot(doc.root, cursor.ownerId, cursor.role) ?: return doc

        // Step 1: Move within an active multi-character NumberNode.
        if (cursor.charOffset > 0) {
            val nextOffset = cursor.charOffset - 1
            return doc.copy(cursor = cursor.copy(charOffset = nextOffset))
        }

        // Step 2: Move left across the preceding sibling in the current slot.
        if (cursor.index > 0) {
            val prevNode = slot.children[cursor.index - 1]
            if (prevNode is NumberNode) {
                return if (prevNode.value.length > 1) {
                    doc.copy(
                        cursor = cursor.copy(
                            index = cursor.index - 1,
                            charOffset = prevNode.value.length - 1,
                        )
                    )
                } else {
                    doc.copy(cursor = cursor.copy(index = cursor.index - 1, charOffset = 0))
                }
            }
            val childSlots = prevNode.orderedSlots()
            if (childSlots.isNotEmpty()) {
                val (lastRole, lastSlot) = childSlots.last()
                return doc.copy(
                    cursor = CursorPosition(
                        ownerId = prevNode.id,
                        role = lastRole,
                        index = lastSlot.size,
                        charOffset = 0,
                    )
                )
            }
            return doc.copy(cursor = cursor.copy(index = cursor.index - 1, charOffset = 0))
        }

        // Step 3: At start of slot (index == 0, charOffset == 0). Move to previous slot or exit left.
        if (cursor.ownerId == ROOT_NODE_ID) return doc
        val chain = AstTreeOps.findAncestorChain(doc.root, cursor.ownerId, cursor.role)
        val step = chain.firstOrNull() ?: return doc
        val siblingSlots = step.childNode.orderedSlots()
        val currentSlotIdx = siblingSlots.indexOfFirst { it.first == cursor.role }
        if (currentSlotIdx > 0) {
            val (prevRole, prevSlot) = siblingSlots[currentSlotIdx - 1]
            return doc.copy(
                cursor = CursorPosition(
                    ownerId = step.childNode.id,
                    role = prevRole,
                    index = prevSlot.size,
                    charOffset = 0,
                )
            )
        }

        // Exit current composite node to its left boundary in the parent slot.
        return doc.copy(
            cursor = CursorPosition(
                ownerId = step.parentOwnerId,
                role = step.parentRole,
                index = step.indexInParent,
                charOffset = 0,
            )
        )
    }

    fun moveRight(doc: AstDocument): AstDocument {
        val cursor = AstTreeOps.clampCursor(doc.root, doc.cursor)
        val slot = AstTreeOps.findSlot(doc.root, cursor.ownerId, cursor.role) ?: return doc

        // Step 1: Advance through or out of a NumberNode at cursor.index.
        if (cursor.index < slot.size) {
            val nextNode = slot.children[cursor.index]
            if (nextNode is NumberNode) {
                val nextOffset = cursor.charOffset + 1
                return if (nextOffset < nextNode.value.length) {
                    doc.copy(cursor = cursor.copy(charOffset = nextOffset))
                } else {
                    doc.copy(cursor = cursor.copy(index = cursor.index + 1, charOffset = 0))
                }
            }

            // Step 2: Enter first child slot of a composite node, or step past a leaf node.
            val childSlots = nextNode.orderedSlots()
            if (childSlots.isNotEmpty()) {
                val (firstRole, _) = childSlots.first()
                return doc.copy(
                    cursor = CursorPosition(
                        ownerId = nextNode.id,
                        role = firstRole,
                        index = 0,
                        charOffset = 0,
                    )
                )
            }
            return doc.copy(cursor = cursor.copy(index = cursor.index + 1, charOffset = 0))
        }

        // Step 3: At end of slot (index == slot.size). Move to next sibling slot or exit right.
        if (cursor.ownerId == ROOT_NODE_ID) return doc
        val chain = AstTreeOps.findAncestorChain(doc.root, cursor.ownerId, cursor.role)
        val step = chain.firstOrNull() ?: return doc
        val siblingSlots = step.childNode.orderedSlots()
        val currentSlotIdx = siblingSlots.indexOfFirst { it.first == cursor.role }
        if (currentSlotIdx in 0 until siblingSlots.lastIndex) {
            val (nextRole, _) = siblingSlots[currentSlotIdx + 1]
            return doc.copy(
                cursor = CursorPosition(
                    ownerId = step.childNode.id,
                    role = nextRole,
                    index = 0,
                    charOffset = 0,
                )
            )
        }

        // Exit current composite node to its right boundary in the parent slot.
        return doc.copy(
            cursor = CursorPosition(
                ownerId = step.parentOwnerId,
                role = step.parentRole,
                index = step.indexInParent + 1,
                charOffset = 0,
            )
        )
    }

    fun moveUp(doc: AstDocument): AstDocument = moveVertical(doc, up = true)

    fun moveDown(doc: AstDocument): AstDocument = moveVertical(doc, up = false)

    private fun moveVertical(doc: AstDocument, up: Boolean): AstDocument {
        val cursor = AstTreeOps.clampCursor(doc.root, doc.cursor)
        if (cursor.ownerId == ROOT_NODE_ID) return doc
        val chain = AstTreeOps.findAncestorChain(doc.root, cursor.ownerId, cursor.role)

        for ((depth, step) in chain.withIndex()) {
            val targetRole = verticalTargetRole(step.childNode, step.roleInChild, up)
            if (targetRole != null) {
                val targetSlot = step.childNode.slotFor(targetRole) ?: continue
                // Preserve cursor index when stepping directly from the target node's own slot.
                val newIndex = if (depth == 0) {
                    cursor.index.coerceIn(0, targetSlot.size)
                } else {
                    targetSlot.size
                }
                return doc.copy(
                    cursor = CursorPosition(
                        ownerId = step.childNode.id,
                        role = targetRole,
                        index = newIndex,
                        charOffset = 0,
                    )
                )
            }
        }
        return doc
    }

    private fun verticalTargetRole(node: AstNode, currentRole: SlotRole, up: Boolean): SlotRole? {
        return when (node) {
            is FractionNode -> when {
                up && currentRole == SlotRole.Denominator -> SlotRole.Numerator
                !up && currentRole == SlotRole.Numerator -> SlotRole.Denominator
                !up && currentRole == SlotRole.Whole -> SlotRole.Denominator
                up && currentRole == SlotRole.Whole -> SlotRole.Numerator
                else -> null
            }

            is PowerNode -> when {
                up && currentRole == SlotRole.Base -> SlotRole.Exponent
                !up && currentRole == SlotRole.Exponent -> SlotRole.Base
                else -> null
            }

            is SqrtNode -> when {
                node.index == null -> null
                up && currentRole == SlotRole.Radicand -> SlotRole.Index
                !up && currentRole == SlotRole.Index -> SlotRole.Radicand
                else -> null
            }

            is LogNode -> when {
                up && currentRole == SlotRole.Base -> SlotRole.Arg
                !up && currentRole == SlotRole.Arg -> SlotRole.Base
                else -> null
            }

            is IntegralNode -> when {
                up && currentRole == SlotRole.Lower -> SlotRole.Upper
                !up && currentRole == SlotRole.Upper -> SlotRole.Lower
                up && currentRole == SlotRole.Expr -> SlotRole.Upper
                !up && currentRole == SlotRole.Expr -> SlotRole.Lower
                else -> null
            }

            is SumProdNode -> when {
                up && currentRole == SlotRole.Lower -> SlotRole.Upper
                !up && currentRole == SlotRole.Upper -> SlotRole.Lower
                up && currentRole == SlotRole.Expr -> SlotRole.Upper
                !up && currentRole == SlotRole.Expr -> SlotRole.Lower
                else -> null
            }

            is DerivativeNode -> when {
                up && currentRole == SlotRole.AtX -> SlotRole.Expr
                !up && currentRole == SlotRole.Expr -> SlotRole.AtX
                else -> null
            }

            is MatrixNode -> {
                if (currentRole !is SlotRole.MatrixCell) return null
                val nextRow = if (up) currentRole.row - 1 else currentRole.row + 1
                if (nextRow in 0 until node.rows) {
                    SlotRole.MatrixCell(nextRow, currentRole.col)
                } else {
                    null
                }
            }

            else -> null
        }
    }

    // -------------------------------------------------------------------------
    // 2. Insertion & Smart Wrapping
    // -------------------------------------------------------------------------

    fun insertDigitOrDecimal(doc: AstDocument, char: Char): AstDocument {
        require(char in '0'..'9' || char == '.') { "Expected digit or '.', got '$char'" }
        val cursor = AstTreeOps.clampCursor(doc.root, doc.cursor)
        val slot = AstTreeOps.findSlot(doc.root, cursor.ownerId, cursor.role) ?: return doc

        // Case A: Cursor is strictly inside an existing NumberNode.
        if (cursor.charOffset > 0 && cursor.index < slot.size) {
            val target = slot.children[cursor.index] as? NumberNode ?: return doc
            if (char == '.' && target.value.contains('.')) return doc
            val updatedValue = target.value.substring(0, cursor.charOffset) +
                char +
                target.value.substring(cursor.charOffset)
            val newRoot = replaceNodeInSlot(doc.root, cursor.ownerId, cursor.role, cursor.index, target.copy(value = updatedValue))
            val newOffset = cursor.charOffset + 1
            val newCursor = if (newOffset < updatedValue.length) {
                cursor.copy(charOffset = newOffset)
            } else {
                cursor.copy(index = cursor.index + 1, charOffset = 0)
            }
            return doc.copy(root = newRoot, cursor = newCursor)
        }

        // Case B: Cursor is immediately after an existing NumberNode -> append.
        if (cursor.charOffset == 0 && cursor.index > 0 && slot.children[cursor.index - 1] is NumberNode) {
            val target = slot.children[cursor.index - 1] as NumberNode
            if (char == '.' && target.value.contains('.')) return doc
            val updatedValue = target.value + char
            val newRoot = replaceNodeInSlot(doc.root, cursor.ownerId, cursor.role, cursor.index - 1, target.copy(value = updatedValue))
            return doc.copy(root = newRoot, cursor = cursor.copy(charOffset = 0))
        }

        // Case C: Cursor is immediately before an existing NumberNode -> prepend.
        if (cursor.charOffset == 0 && cursor.index < slot.size && slot.children[cursor.index] is NumberNode) {
            val target = slot.children[cursor.index] as NumberNode
            if (char == '.' && target.value.contains('.')) return doc
            val updatedValue = char + target.value
            val newRoot = replaceNodeInSlot(doc.root, cursor.ownerId, cursor.role, cursor.index, target.copy(value = updatedValue))
            return doc.copy(root = newRoot, cursor = cursor.copy(charOffset = 1))
        }

        // Case D: Insert a brand new NumberNode.
        val (newId, docWithId) = doc.allocateId()
        val newNode = NumberNode(id = newId, value = char.toString())
        return insertNodeAtBoundary(docWithId, cursor, newNode)
    }

    fun insertNumberLiteral(doc: AstDocument, literal: String): AstDocument {
        var current = doc
        for (ch in literal) {
            if (ch == '-' || ch == '−') {
                current = insertOperator(current, MathOperator.UNARY_MINUS)
            } else {
                current = insertDigitOrDecimal(current, ch)
            }
        }
        return current
    }

    fun insertOperator(doc: AstDocument, operator: MathOperator): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        if (operator == MathOperator.COMMA &&
            boundaryCursor.role == SlotRole.Arg &&
            boundaryCursor.ownerId != ROOT_NODE_ID
        ) {
            val ownerChain = AstTreeOps.findAncestorChain(preparedDoc.root, boundaryCursor.ownerId, boundaryCursor.role)
            val fnNode = ownerChain.firstOrNull()?.childNode as? FunctionNode
            if (fnNode != null && fnNode.secondArg != null && fnNode.secondArg.isEmpty) {
                return preparedDoc.copy(
                    cursor = CursorPosition(
                        ownerId = fnNode.id,
                        role = SlotRole.SecondArg,
                        index = 0,
                        charOffset = 0,
                    )
                )
            }
        }
        val (newId, docWithId) = preparedDoc.allocateId()
        return insertNodeAtBoundary(docWithId, boundaryCursor, OperatorNode(newId, operator))
    }

    fun insertConstant(doc: AstDocument, constant: MathConstant): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        return insertNodeAtBoundary(docWithId, boundaryCursor, ConstantNode(newId, constant))
    }

    fun insertVariable(doc: AstDocument, variable: MathVariable): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        return insertNodeAtBoundary(docWithId, boundaryCursor, VariableNode(newId, variable))
    }

    fun insertParens(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val paren = ParenNode(id = newId)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, paren)
        return inserted.copy(
            cursor = CursorPosition(
                ownerId = newId,
                role = SlotRole.Body,
                index = 0,
                charOffset = 0,
            )
        )
    }

    fun insertAbs(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val abs = AbsNode(id = newId)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, abs)
        return inserted.copy(
            cursor = CursorPosition(
                ownerId = newId,
                role = SlotRole.Body,
                index = 0,
                charOffset = 0,
            )
        )
    }

    /**
     * Inserts a FractionNode. If there is a preceding term before the cursor (e.g., `5` or `2X`),
     * automatically wraps that term into the numerator and moves the cursor into the denominator.
     */
    fun insertFraction(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val slot = AstTreeOps.findSlot(preparedDoc.root, boundaryCursor.ownerId, boundaryCursor.role)
            ?: return doc
        val wrapRange = findFractionWrapStart(slot.children, boundaryCursor.index)
        val (newId, docWithId) = preparedDoc.allocateId()

        if (wrapRange < boundaryCursor.index) {
            val wrappedNodes = slot.children.subList(wrapRange, boundaryCursor.index)
            val fraction = FractionNode(
                id = newId,
                numerator = ExprSlot(wrappedNodes),
                denominator = ExprSlot(),
            )
            val newRoot = AstTreeOps.updateSlot(docWithId.root, boundaryCursor.ownerId, boundaryCursor.role) { s ->
                val updated = buildList {
                    addAll(s.children.subList(0, wrapRange))
                    add(fraction)
                    addAll(s.children.subList(boundaryCursor.index, s.children.size))
                }
                ExprSlot(updated)
            }
            return docWithId.copy(
                root = newRoot,
                cursor = CursorPosition(ownerId = newId, role = SlotRole.Denominator, index = 0, charOffset = 0),
            )
        }

        val fraction = FractionNode(id = newId)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, fraction)
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = SlotRole.Numerator, index = 0, charOffset = 0),
        )
    }

    fun insertMixedFraction(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val mixed = FractionNode(
            id = newId,
            numerator = ExprSlot(),
            denominator = ExprSlot(),
            whole = ExprSlot(),
        )
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, mixed)
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = SlotRole.Whole, index = 0, charOffset = 0),
        )
    }

    /**
     * Inserts a PowerNode (`x^□`). If there is an immediately preceding atom before the cursor
     * (e.g., `5` or `(2+3)`), wraps it into `base` and puts the cursor in `exponent`.
     */
    fun insertPower(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val slot = AstTreeOps.findSlot(preparedDoc.root, boundaryCursor.ownerId, boundaryCursor.role)
            ?: return doc
        val (newId, docWithId) = preparedDoc.allocateId()

        if (boundaryCursor.index > 0 && isWrappableAtom(slot.children[boundaryCursor.index - 1])) {
            val baseAtom = slot.children[boundaryCursor.index - 1]
            val power = PowerNode(
                id = newId,
                base = ExprSlot(listOf(baseAtom)),
                exponent = ExprSlot(),
            )
            val newRoot = AstTreeOps.updateSlot(docWithId.root, boundaryCursor.ownerId, boundaryCursor.role) { s ->
                val updated = buildList {
                    addAll(s.children.subList(0, boundaryCursor.index - 1))
                    add(power)
                    addAll(s.children.subList(boundaryCursor.index, s.children.size))
                }
                ExprSlot(updated)
            }
            return docWithId.copy(
                root = newRoot,
                cursor = CursorPosition(ownerId = newId, role = SlotRole.Exponent, index = 0, charOffset = 0),
            )
        }

        val power = PowerNode(id = newId)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, power)
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = SlotRole.Base, index = 0, charOffset = 0),
        )
    }

    /**
     * Inserts a fixed-exponent PowerNode (e.g., `x²`, `x³`, `x⁻¹`). Wraps the preceding atom into
     * `base` if present and places the cursor immediately after the PowerNode.
     */
    fun insertFixedPower(doc: AstDocument, exponentLiteral: String): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val slot = AstTreeOps.findSlot(preparedDoc.root, boundaryCursor.ownerId, boundaryCursor.role)
            ?: return doc

        var workingDoc = preparedDoc
        val expNodes = buildList {
            if (exponentLiteral.startsWith("-")) {
                val (minusId, nextDoc) = workingDoc.allocateId()
                workingDoc = nextDoc
                add(OperatorNode(minusId, MathOperator.UNARY_MINUS))
                val rest = exponentLiteral.drop(1)
                if (rest.isNotEmpty()) {
                    val (numId, nextDoc2) = workingDoc.allocateId()
                    workingDoc = nextDoc2
                    add(NumberNode(numId, rest))
                }
            } else {
                val (numId, nextDoc) = workingDoc.allocateId()
                workingDoc = nextDoc
                add(NumberNode(numId, exponentLiteral))
            }
        }

        val (powerId, docWithPowerId) = workingDoc.allocateId()
        if (boundaryCursor.index > 0 && isWrappableAtom(slot.children[boundaryCursor.index - 1])) {
            val baseAtom = slot.children[boundaryCursor.index - 1]
            val power = PowerNode(
                id = powerId,
                base = ExprSlot(listOf(baseAtom)),
                exponent = ExprSlot(expNodes),
            )
            val newRoot = AstTreeOps.updateSlot(docWithPowerId.root, boundaryCursor.ownerId, boundaryCursor.role) { s ->
                val updated = buildList {
                    addAll(s.children.subList(0, boundaryCursor.index - 1))
                    add(power)
                    addAll(s.children.subList(boundaryCursor.index, s.children.size))
                }
                ExprSlot(updated)
            }
            return docWithPowerId.copy(
                root = newRoot,
                cursor = boundaryCursor.copy(index = boundaryCursor.index, charOffset = 0),
            )
        }

        val power = PowerNode(id = powerId, base = ExprSlot(), exponent = ExprSlot(expNodes))
        val inserted = insertNodeAtBoundary(docWithPowerId, boundaryCursor, power)
        return inserted.copy(
            cursor = CursorPosition(ownerId = powerId, role = SlotRole.Base, index = 0, charOffset = 0),
        )
    }

    fun insertSqrt(doc: AstDocument, withCustomIndex: Boolean = false, presetIndex: String? = null): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        var workingDoc = preparedDoc
        val indexSlot = when {
            presetIndex != null -> {
                val (idxNumId, nextDoc) = workingDoc.allocateId()
                workingDoc = nextDoc
                ExprSlot(listOf(NumberNode(idxNumId, presetIndex)))
            }
            withCustomIndex -> ExprSlot()
            else -> null
        }
        val (newId, docWithId) = workingDoc.allocateId()
        val sqrt = SqrtNode(id = newId, index = indexSlot, radicand = ExprSlot())
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, sqrt)
        val initialRole = if (withCustomIndex && presetIndex == null) SlotRole.Index else SlotRole.Radicand
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = initialRole, index = 0, charOffset = 0),
        )
    }

    fun insertLog(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val logNode = LogNode(id = newId)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, logNode)
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = SlotRole.Base, index = 0, charOffset = 0),
        )
    }

    fun insertFunction(doc: AstDocument, function: MathFunction): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val fnNode = FunctionNode(
            id = newId,
            function = function,
            arg = ExprSlot(),
            secondArg = if (function.arity == 2) ExprSlot() else null,
        )
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, fnNode)
        return if (function.arity == 0) {
            inserted
        } else {
            inserted.copy(
                cursor = CursorPosition(ownerId = newId, role = SlotRole.Arg, index = 0, charOffset = 0),
            )
        }
    }

    fun insertIntegral(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val integral = IntegralNode(id = newId)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, integral)
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = SlotRole.Expr, index = 0, charOffset = 0),
        )
    }

    fun insertDerivative(doc: AstDocument): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val deriv = DerivativeNode(id = newId)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, deriv)
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = SlotRole.Expr, index = 0, charOffset = 0),
        )
    }

    fun insertSumProd(doc: AstDocument, kind: SumProdKind): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val sumProd = SumProdNode(id = newId, kind = kind)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, sumProd)
        return inserted.copy(
            cursor = CursorPosition(ownerId = newId, role = SlotRole.Lower, index = 0, charOffset = 0),
        )
    }

    fun insertMatrix(doc: AstDocument, rows: Int, cols: Int): AstDocument {
        val (preparedDoc, boundaryCursor) = splitNumberAtCursorIfNeeded(doc)
        val (newId, docWithId) = preparedDoc.allocateId()
        val matrix = MatrixNode(id = newId, rows = rows, cols = cols)
        val inserted = insertNodeAtBoundary(docWithId, boundaryCursor, matrix)
        return inserted.copy(
            cursor = CursorPosition(
                ownerId = newId,
                role = SlotRole.MatrixCell(0, 0),
                index = 0,
                charOffset = 0,
            ),
        )
    }

    // -------------------------------------------------------------------------
    // 3. Deletion (`DEL`) & All Clear (`AC`)
    // -------------------------------------------------------------------------

    fun deleteBeforeCursor(doc: AstDocument): AstDocument {
        val cursor = AstTreeOps.clampCursor(doc.root, doc.cursor)
        val slot = AstTreeOps.findSlot(doc.root, cursor.ownerId, cursor.role) ?: return doc

        // Case 1: Inside a multi-char NumberNode (delete char at charOffset - 1).
        if (cursor.charOffset > 0 && cursor.index < slot.size) {
            val num = slot.children[cursor.index] as? NumberNode ?: return doc
            val newVal = num.value.removeRange(cursor.charOffset - 1, cursor.charOffset)
            val newRoot = if (newVal.isEmpty()) {
                AstTreeOps.updateSlot(doc.root, cursor.ownerId, cursor.role) { s ->
                    ExprSlot(s.children.toMutableList().apply { removeAt(cursor.index) })
                }
            } else {
                replaceNodeInSlot(doc.root, cursor.ownerId, cursor.role, cursor.index, num.copy(value = newVal))
            }
            val newOffset = cursor.charOffset - 1
            return doc.copy(
                root = newRoot,
                cursor = cursor.copy(charOffset = newOffset),
            )
        }

        // Case 2: At boundary with a preceding sibling in the same slot.
        if (cursor.index > 0) {
            val prev = slot.children[cursor.index - 1]
            if (prev is NumberNode && prev.value.length > 1) {
                val trimmed = prev.copy(value = prev.value.dropLast(1))
                val newRoot = replaceNodeInSlot(doc.root, cursor.ownerId, cursor.role, cursor.index - 1, trimmed)
                return doc.copy(root = newRoot, cursor = cursor.copy(charOffset = 0))
            }

            // Delete the node before the cursor and merge adjacent NumberNodes if revealed.
            var resultingCursorIndex = cursor.index - 1
            var resultingOffset = 0
            val newRoot = AstTreeOps.updateSlot(doc.root, cursor.ownerId, cursor.role) { s ->
                val withoutPrev = s.children.toMutableList().apply { removeAt(cursor.index - 1) }
                val (merged, pos) = AstTreeOps.tryMergeAdjacentNumbers(withoutPrev, cursor.index - 1)
                resultingCursorIndex = pos.first
                resultingOffset = pos.second
                ExprSlot(merged)
            }
            return doc.copy(
                root = newRoot,
                cursor = cursor.copy(index = resultingCursorIndex, charOffset = resultingOffset),
            )
        }

        // Case 3: At the very start of a nested slot (index == 0, charOffset == 0).
        // Unwrap the enclosing template (or remove it completely if empty).
        if (cursor.ownerId == ROOT_NODE_ID) return doc
        val chain = AstTreeOps.findAncestorChain(doc.root, cursor.ownerId, cursor.role)
        val step = chain.firstOrNull() ?: return doc
        val ownerNode = step.childNode

        val unwrappedNodes = if (ownerNode.isEmptyTemplate()) {
            emptyList<AstNode>() to 0
        } else {
            unwrapNodesForDelete(ownerNode, cursor.role)
        }

        val (nodesToInsert, junctionOffsetInParent) = unwrappedNodes
        var finalIndex = step.indexInParent + junctionOffsetInParent
        var finalOffset = 0

        val newRoot = AstTreeOps.updateSlot(doc.root, step.parentOwnerId, step.parentRole) { parentSlot ->
            val rebuilt = buildList {
                addAll(parentSlot.children.subList(0, step.indexInParent))
                addAll(nodesToInsert)
                addAll(parentSlot.children.subList(step.indexInParent + 1, parentSlot.children.size))
            }
            // Check merge at left join boundary and right join boundary if needed.
            val leftJoinIdx = step.indexInParent
            val (afterLeftMerge, leftPos) = AstTreeOps.tryMergeAdjacentNumbers(rebuilt, leftJoinIdx)
            if (afterLeftMerge.size < rebuilt.size && junctionOffsetInParent == 0) {
                finalIndex = leftPos.first
                finalOffset = leftPos.second
            } else if (afterLeftMerge.size < rebuilt.size) {
                finalIndex = (finalIndex - 1).coerceAtLeast(0)
            }
            ExprSlot(afterLeftMerge)
        }

        return doc.copy(
            root = newRoot,
            cursor = CursorPosition(
                ownerId = step.parentOwnerId,
                role = step.parentRole,
                index = finalIndex,
                charOffset = finalOffset,
            ),
        )
    }

    fun clearAll(): AstDocument = AstDocument.empty()

    // -------------------------------------------------------------------------
    // Internal Helpers
    // -------------------------------------------------------------------------

    private fun unwrapNodesForDelete(node: AstNode, activeRole: SlotRole): Pair<List<AstNode>, Int> {
        return when (node) {
            is FractionNode -> {
                val combined = buildList {
                    if (node.whole != null) addAll(node.whole.children)
                    addAll(node.numerator.children)
                    addAll(node.denominator.children)
                }
                val cursorIdx = when (activeRole) {
                    SlotRole.Whole -> 0
                    SlotRole.Numerator -> node.whole?.size ?: 0
                    SlotRole.Denominator -> (node.whole?.size ?: 0) + node.numerator.size
                    else -> 0
                }
                combined to cursorIdx
            }

            is PowerNode -> {
                val combined = node.base.children + node.exponent.children
                val cursorIdx = if (activeRole == SlotRole.Exponent) node.base.size else 0
                combined to cursorIdx
            }

            is SqrtNode -> {
                val idxChildren = node.index?.children.orEmpty()
                val combined = idxChildren + node.radicand.children
                val cursorIdx = if (activeRole == SlotRole.Radicand) idxChildren.size else 0
                combined to cursorIdx
            }

            is LogNode -> {
                val combined = node.base.children + node.arg.children
                val cursorIdx = if (activeRole == SlotRole.Arg) node.base.size else 0
                combined to cursorIdx
            }

            is ParenNode -> node.body.children to 0
            is AbsNode -> node.body.children to 0
            is FunctionNode -> {
                val combined = node.arg.children + node.secondArg?.children.orEmpty()
                val cursorIdx = if (activeRole == SlotRole.SecondArg) node.arg.size else 0
                combined to cursorIdx
            }

            is IntegralNode -> {
                val combined = node.lower.children + node.upper.children + node.expr.children
                val cursorIdx = when (activeRole) {
                    SlotRole.Lower -> 0
                    SlotRole.Upper -> node.lower.size
                    SlotRole.Expr -> node.lower.size + node.upper.size
                    else -> 0
                }
                combined to cursorIdx
            }

            is DerivativeNode -> {
                val combined = node.expr.children + node.atX.children
                val cursorIdx = if (activeRole == SlotRole.AtX) node.expr.size else 0
                combined to cursorIdx
            }

            is SumProdNode -> {
                val combined = node.lower.children + node.upper.children + node.expr.children
                val cursorIdx = when (activeRole) {
                    SlotRole.Lower -> 0
                    SlotRole.Upper -> node.lower.size
                    SlotRole.Expr -> node.lower.size + node.upper.size
                    else -> 0
                }
                combined to cursorIdx
            }

            is MatrixNode -> emptyList<AstNode>() to 0
            else -> emptyList<AstNode>() to 0
        }
    }

    private fun splitNumberAtCursorIfNeeded(doc: AstDocument): Pair<AstDocument, CursorPosition> {
        val cursor = AstTreeOps.clampCursor(doc.root, doc.cursor)
        if (cursor.charOffset == 0) return doc to cursor
        val slot = AstTreeOps.findSlot(doc.root, cursor.ownerId, cursor.role) ?: return doc to cursor
        val num = slot.children.getOrNull(cursor.index) as? NumberNode ?: return doc to cursor

        val leftVal = num.value.substring(0, cursor.charOffset)
        val rightVal = num.value.substring(cursor.charOffset)
        val (rightId, docWithId) = doc.allocateId()
        val leftNode = NumberNode(id = num.id, value = leftVal)
        val rightNode = NumberNode(id = rightId, value = rightVal)

        val newRoot = AstTreeOps.updateSlot(docWithId.root, cursor.ownerId, cursor.role) { s ->
            val updated = buildList(s.size + 1) {
                addAll(s.children.subList(0, cursor.index))
                add(leftNode)
                add(rightNode)
                addAll(s.children.subList(cursor.index + 1, s.size))
            }
            ExprSlot(updated)
        }
        val boundaryCursor = cursor.copy(index = cursor.index + 1, charOffset = 0)
        return docWithId.copy(root = newRoot, cursor = boundaryCursor) to boundaryCursor
    }

    private fun insertNodeAtBoundary(
        doc: AstDocument,
        cursor: CursorPosition,
        node: AstNode,
    ): AstDocument {
        val newRoot = AstTreeOps.updateSlot(doc.root, cursor.ownerId, cursor.role) { slot ->
            val updated = slot.children.toMutableList().apply {
                add(cursor.index.coerceIn(0, size), node)
            }
            ExprSlot(updated)
        }
        return doc.copy(
            root = newRoot,
            cursor = cursor.copy(index = cursor.index + 1, charOffset = 0),
        )
    }

    private fun replaceNodeInSlot(
        root: ExprSlot,
        ownerId: Long,
        role: SlotRole,
        index: Int,
        replacement: AstNode,
    ): ExprSlot = AstTreeOps.updateSlot(root, ownerId, role) { slot ->
        val updated = slot.children.toMutableList().apply {
            set(index, replacement)
        }
        ExprSlot(updated)
    }

    private fun isWrappableAtom(node: AstNode): Boolean = when (node) {
        is NumberNode,
        is ConstantNode,
        is VariableNode,
        is ParenNode,
        is AbsNode,
        is SqrtNode,
        is PowerNode,
        is FractionNode,
        is LogNode,
        is FunctionNode,
        is MatrixNode -> true
        is OperatorNode -> node.operator == MathOperator.FACTORIAL || node.operator == MathOperator.PERCENT
        is IntegralNode, is DerivativeNode, is SumProdNode -> true
    }

    /**
     * Scans backward from [endIndex] to find the start index of the contiguous implicit-product
     * term (e.g. `2X` or `3sin(30)` or `5`) to wrap into a fraction numerator.
     */
    private fun findFractionWrapStart(children: List<AstNode>, endIndex: Int): Int {
        var start = endIndex
        while (start > 0 && isWrappableAtom(children[start - 1])) {
            start--
        }
        return start
    }
}
