package com.example.core.data.history

import com.example.core.data.model.AdminCitySnapshot
import com.example.core.data.model.AdminOperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Conflict-safe Undo/Redo manager for Admin City Builder operations.
 * Every command captures expected layoutVersion / entity pre-state checks so Undo or Redo
 * never silently overwrites newer remote changes from another session.
 */
data class CityEditorCommand(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val canUndoPredicate: (AdminCitySnapshot) -> String? = { null },
    val canRedoPredicate: (AdminCitySnapshot) -> String? = { null },
    val undoAction: suspend () -> AdminOperationResult,
    val redoAction: suspend () -> AdminOperationResult
)

data class CityEditorHistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabelAr: String? = null,
    val undoLabelEn: String? = null,
    val redoLabelAr: String? = null,
    val redoLabelEn: String? = null,
    val undoDepth: Int = 0,
    val redoDepth: Int = 0
)

class CityEditorHistoryManager(
    private val maxEntries: Int = 40
) {
    private val undoStack = ArrayDeque<CityEditorCommand>()
    private val redoStack = ArrayDeque<CityEditorCommand>()

    private val _state = MutableStateFlow(CityEditorHistoryState())
    val state: StateFlow<CityEditorHistoryState> = _state.asStateFlow()

    fun push(command: CityEditorCommand) {
        undoStack.addLast(command)
        while (undoStack.size > maxEntries) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        emitState()
    }

    suspend fun undo(currentSnapshot: AdminCitySnapshot?): AdminOperationResult {
        val snap = currentSnapshot ?: return AdminOperationResult.Failure("لا توجد بيانات مدينة صالحة للتراجع.")
        val cmd = undoStack.lastOrNull() ?: return AdminOperationResult.Failure("لا توجد عملية سابقة للتراجع عنها.")
        val conflictReason = cmd.canUndoPredicate(snap)
        if (conflictReason != null) {
            // Drop stale conflicting entry so the queue is not wedged while protecting remote state
            undoStack.removeLast()
            redoStack.clear()
            emitState()
            return AdminOperationResult.Failure(conflictReason)
        }
        val result = cmd.undoAction()
        if (result is AdminOperationResult.Success) {
            undoStack.removeLast()
            redoStack.addLast(cmd)
            emitState()
        }
        return result
    }

    suspend fun redo(currentSnapshot: AdminCitySnapshot?): AdminOperationResult {
        val snap = currentSnapshot ?: return AdminOperationResult.Failure("لا توجد بيانات مدينة صالحة للإعادة.")
        val cmd = redoStack.lastOrNull() ?: return AdminOperationResult.Failure("لا توجد عملية متاحة للإعادة.")
        val conflictReason = cmd.canRedoPredicate(snap)
        if (conflictReason != null) {
            redoStack.removeLast()
            emitState()
            return AdminOperationResult.Failure(conflictReason)
        }
        val result = cmd.redoAction()
        if (result is AdminOperationResult.Success) {
            redoStack.removeLast()
            undoStack.addLast(cmd)
            emitState()
        }
        return result
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        emitState()
    }

    private fun emitState() {
        val topUndo = undoStack.lastOrNull()
        val topRedo = redoStack.lastOrNull()
        _state.value = CityEditorHistoryState(
            canUndo = topUndo != null,
            canRedo = topRedo != null,
            undoLabelAr = topUndo?.labelAr,
            undoLabelEn = topUndo?.labelEn,
            redoLabelAr = topRedo?.labelAr,
            redoLabelEn = topRedo?.labelEn,
            undoDepth = undoStack.size,
            redoDepth = redoStack.size
        )
    }
}
