package com.example.ast

data class CursorPosition(
    val ownerId: Long = ROOT_NODE_ID,
    val role: SlotRole = SlotRole.Root,
    val index: Int = 0,
    val charOffset: Int = 0,
) {
    init {
        require(index >= 0) { "Cursor index must be non-negative" }
        require(charOffset >= 0) { "Cursor charOffset must be non-negative" }
    }
}

data class AstDocument(
    val root: ExprSlot = ExprSlot(),
    val cursor: CursorPosition = CursorPosition(),
    val nextId: Long = 1L,
) {
    fun allocateId(): Pair<Long, AstDocument> = nextId to copy(nextId = nextId + 1L)

    companion object {
        fun empty(): AstDocument = AstDocument()
    }
}

data class SlotLocator(
    val ownerId: Long,
    val role: SlotRole,
    val slot: ExprSlot,
)

data class AncestorStep(
    val parentOwnerId: Long,
    val parentRole: SlotRole,
    val childNode: AstNode,
    val indexInParent: Int,
    val roleInChild: SlotRole,
)
