package com.example.core.data

import com.example.core.data.local.LocalCityExplorerRepository
import com.example.core.data.model.CityDataState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalCityExplorerRepositoryTest {

    @Test
    fun `repository emits 12 starter test houses with zero private contact leaks`() = runTest {
        val repository = LocalCityExplorerRepository()
        val state = repository.observeCitySnapshot().first()

        assertTrue(state is CityDataState.Success)
        val snapshot = (state as CityDataState.Success).data

        assertTrue(snapshot.isTestData)
        assertEquals(12, snapshot.scene.houses.size)
        assertEquals(12, snapshot.houseDetailsById.size)

        snapshot.houseDetailsById.values.forEach { details ->
            assertTrue(details.isTestData)
            assertNull("Phone must not leak for ${details.houseId}", details.approvedPhone)
            assertNull("Email must not leak for ${details.houseId}", details.approvedEmail)
        }

        // Verify optional missing-data cases exist and are represented cleanly as null
        val house08 = snapshot.houseDetailsById["house_08"]
        assertNotNull(house08)
        assertNull(house08?.publicAbout)
        assertNull(house08?.publicRole)

        val house10 = snapshot.houseDetailsById["house_10"]
        assertNotNull(house10)
        assertNull(house10?.familyMembersCount)
    }
}
