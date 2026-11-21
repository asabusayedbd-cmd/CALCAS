package com.example.ast

internal object AstTreeOps {

    fun findSlot(root: ExprSlot, ownerId: Long, role: SlotRole): ExprSlot? {
        if (ownerId == ROOT_NODE_ID) {
            return if (role == SlotRole.Root) root else null
        }
        return findSlotInList(root.children, ownerId, role)
    }

    private fun findSlotInList(nodes: List<AstNode>, ownerId: Long, role: SlotRole): ExprSlot? {
        for (node in nodes) {
            if (node.id == ownerId) {
                return node.slotFor(role)
            }
            for ((_, childSlot) in node.orderedSlots()) {
                val found = findSlotInList(childSlot.children, ownerId, role)
                if (found != null) return found
            }
        }
        return null
    }

    fun findNode(root: ExprSlot, nodeId: Long): AstNode? {
        for (node in root.children) {
            if (node.id == nodeId) return node
            for ((_, slot) in node.orderedSlots()) {
                val found = findNode(slot, nodeId)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Returns the path of ancestors from the immediate enclosing node up to the root slot.
     * Index 0 in the returned list is the immediate parent step of `(ownerId, role)`.
     */
    fun findAncestorChain(root: ExprSlot, ownerId: Long, role: SlotRole): List<AncestorStep> {
        if (ownerId == ROOT_NODE_ID) return emptyList()
        val chain = mutableListOf<AncestorStep>()
        val found = collectAncestors(
            currentOwnerId = ROOT_NODE_ID,
            currentRole = SlotRole.Root,
            currentSlot = root,
            targetOwnerId = ownerId,
            targetRole = role,
            out = chain,
        )
        return if (found) chain else emptyList()
    }

    private fun collectAncestors(
        currentOwnerId: Long,
        currentRole: SlotRole,
        currentSlot: ExprSlot,
        targetOwnerId: Long,
        targetRole: SlotRole,
        out: MutableList<AncestorStep>,
    ): Boolean {
        currentSlot.children.forEachIndexed { idx, node ->
            if (node.id == targetOwnerId && node.slotFor(targetRole) != null) {
                out.add(
                    AncestorStep(
                        parentOwnerId = currentOwnerId,
                        parentRole = currentRole,
                        childNode = node,
                        indexInParent = idx,
                        roleInChild = targetRole,
                    )
                )
                return true
            }
            for ((childRole, childSlot) in node.orderedSlots()) {
                if (collectAncestors(node.id, childRole, childSlot, targetOwnerId, targetRole, out)) {
                    out.add(
                        AncestorStep(
                            parentOwnerId = currentOwnerId,
                            parentRole = currentRole,
                            childNode = node,
                            indexInParent = idx,
                            roleInChild = childRole,
                        )
                    )
                    return true
                }
            }
        }
        return false
    }

    fun updateSlot(
        root: ExprSlot,
        ownerId: Long,
        role: SlotRole,
        transform: (ExprSlot) -> ExprSlot,
    ): ExprSlot {
        if (ownerId == ROOT_NODE_ID && role == SlotRole.Root) {
            return transform(root)
        }
        return ExprSlot(updateSlotInChildren(root.children, ownerId, role, transform))
    }

    private fun updateSlotInChildren(
        children: List<AstNode>,
        ownerId: Long,
        role: SlotRole,
        transform: (ExprSlot) -> ExprSlot,
    ): List<AstNode> {
        var changed = false
        val updated = children.map { node ->
            if (node.id == ownerId) {
                val target = node.slotFor(role)
                if (target != null) {
                    changed = true
                    node.withSlotUpdated(role, transform(target))
                } else {
                    node
                }
            } else {
                var childChanged = false
                var currentNode = node
                for ((childRole, childSlot) in node.orderedSlots()) {
                    val newChildren = updateSlotInChildren(childSlot.children, ownerId, role, transform)
                    if (newChildren !== childSlot.children) {
                        childChanged = true
                        currentNode = currentNode.withSlotUpdated(childRole, ExprSlot(newChildren))
                    }
                }
                if (childChanged) {
                    changed = true
                    currentNode
                } else {
                    node
                }
            }
        }
        return if (changed) updated else children
    }

    fun clampCursor(root: ExprSlot, cursor: CursorPosition): CursorPosition {
        val slot = findSlot(root, cursor.ownerId, cursor.role)
            ?: return CursorPosition(ROOT_NODE_ID, SlotRole.Root, root.size.coerceAtLeast(0), 0)
        val clampedIndex = cursor.index.coerceIn(0, slot.size)
        val clampedOffset = if (clampedIndex < slot.size) {
            val node = slot.children[clampedIndex]
            if (node is NumberNode && cursor.charOffset in 1 until node.value.length) {
                cursor.charOffset
            } else {
                0
            }
        } else {
            0
        }
        return cursor.copy(index = clampedIndex, charOffset = clampedOffset)
    }

    fun firstLeafSlot(node: AstNode): Pair<SlotRole, ExprSlot>? = node.orderedSlots().firstOrNull()

    fun lastLeafSlot(node: AstNode): Pair<SlotRole, ExprSlot>? = node.orderedSlots().lastOrNull()

    /**
     * Merges adjacent NumberNodes at [joinIndex - 1] and [joinIndex] if doing so yields a valid
     * numeric literal (at most one decimal point). Returns the updated children list and the
     * resulting cursor (index, charOffset) at the merge boundary.
     */
    fun tryMergeAdjacentNumbers(
        children: List<AstNode>,
        joinIndex: Int,
    ): Pair<List<AstNode>, Pair<Int, Int>> {
        if (joinIndex <= 0 || joinIndex >= children.size) {
            return children to (joinIndex to 0)
        }
        val left = children[joinIndex - 1]
        val right = children[joinIndex]
        if (left is NumberNode && right is NumberNode) {
            val combined = left.value + right.value
            if (combined.count { it == '.' } <= 1) {
                val merged = NumberNode(id = left.id, value = combined)
                val newList = buildList(children.size - 1) {
                    addAll(children.subList(0, joinIndex - 1))
                    add(merged)
                    addAll(children.subList(joinIndex + 1, children.size))
                }
                return newList to ((joinIndex - 1) to left.value.length)
            }
        }
        return children to (joinIndex to 0)
    }
}
