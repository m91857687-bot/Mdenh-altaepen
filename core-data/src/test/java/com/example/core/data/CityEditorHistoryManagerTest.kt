package com.example.core.data

import com.example.core.data.history.CityEditorCommand
import com.example.core.data.history.CityEditorHistoryManager
import com.example.core.data.local.LocalCityAdminRepository
import com.example.core.data.model.AdminOperationResult
import com.example.core.data.model.CityDataState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CityEditorHistoryManagerTest {

    @Test
    fun `history manager supports undo and redo and resets redo stack on new command`() = runBlocking {
        val repo = LocalCityAdminRepository()
        val snap = (repo.observeAdminSnapshot().first { it is CityDataState.Success } as CityDataState.Success).data
        val manager = CityEditorHistoryManager()

        var value = 0
        manager.push(
            CityEditorCommand(
                id = "c1",
                labelAr = "تعديل تجريبي",
                labelEn = "Sample Edit",
                undoAction = {
                    value = 0
                    AdminOperationResult.Success("تم التراجع")
                },
                redoAction = {
                    value = 1
                    AdminOperationResult.Success("تمت الإعادة")
                }
            )
        )
        value = 1
        assertTrue(manager.state.value.canUndo)
        assertFalse(manager.state.value.canRedo)

        val undoRes = manager.undo(snap)
        assertTrue(undoRes is AdminOperationResult.Success)
        assertEquals(0, value)
        assertFalse(manager.state.value.canUndo)
        assertTrue(manager.state.value.canRedo)

        val redoRes = manager.redo(snap)
        assertTrue(redoRes is AdminOperationResult.Success)
        assertEquals(1, value)
        assertTrue(manager.state.value.canUndo)
        assertFalse(manager.state.value.canRedo)
    }

    @Test
    fun `history manager blocks undo when remote layoutVersion drift is detected`() = runBlocking {
        val repo = LocalCityAdminRepository()
        val snapV1 = (repo.observeAdminSnapshot().first { it is CityDataState.Success } as CityDataState.Success).data
        val snapV2 = snapV1.copy(layoutVersion = snapV1.layoutVersion + 1)
        val manager = CityEditorHistoryManager()

        var undoExecuted = false
        manager.push(
            CityEditorCommand(
                id = "c_conflict",
                labelAr = "رسم طريق",
                labelEn = "Paint Road",
                canUndoPredicate = { cur ->
                    if (cur.layoutVersion != snapV1.layoutVersion) {
                        "تعارض: تم تعديل التخطيط عن بُعد."
                    } else null
                },
                undoAction = {
                    undoExecuted = true
                    AdminOperationResult.Success("تم")
                },
                redoAction = { AdminOperationResult.Success("تم") }
            )
        )

        val res = manager.undo(snapV2)
        assertTrue(res is AdminOperationResult.Failure)
        assertFalse("Conflicting undo must never execute remote mutation", undoExecuted)
        assertFalse(manager.state.value.canUndo)
    }
}
