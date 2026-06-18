package com.example.freshscan.ui.screen.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TasteProfileViewModelTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var viewModel: TasteProfileViewModel

    private val emptyPrefs: Preferences = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        dataStore = mockk(relaxed = true)
        // Return a flow emitting a mock empty Preferences so loadPreferences() gets defaults
        every { dataStore.data } returns flowOf(emptyPrefs)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Default Values ───

    @Test
    fun `when initialized then loads default values`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        val state = viewModel.uiState.value
        assertEquals(0, state.spiceLevel)
        assertEquals(1, state.saltLevel)
        assertEquals(1, state.oilLevel)
        assertTrue(state.excludedIngredients.isEmpty())
        assertTrue(state.preferredCategories.isEmpty())
        assertFalse(state.isDirty)
    }

    // ─── Spice Level ───

    @Test
    fun `when updateSpiceLevel then state reflects change`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.updateSpiceLevel(2)

        assertEquals(2, viewModel.uiState.value.spiceLevel)
        assertTrue("isDirty should be true after update", viewModel.uiState.value.isDirty)
    }

    // ─── Salt Level ───

    @Test
    fun `when updateSaltLevel then state reflects change`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.updateSaltLevel(2)

        assertEquals(2, viewModel.uiState.value.saltLevel)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    // ─── Oil Level ───

    @Test
    fun `when updateOilLevel then state reflects change`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.updateOilLevel(0)

        assertEquals(0, viewModel.uiState.value.oilLevel)
        assertTrue(viewModel.uiState.value.isDirty)
    }

    // ─── Excluded Ingredients ───

    @Test
    fun `when toggle excluded ingredient then adds or removes from set`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.toggleExcludedIngredient("香菜")
        assertTrue(viewModel.uiState.value.excludedIngredients.contains("香菜"))

        viewModel.toggleExcludedIngredient("香菜")
        assertFalse(viewModel.uiState.value.excludedIngredients.contains("香菜"))
    }

    // ─── Preferred Categories ───

    @Test
    fun `when toggle preferred category then adds or removes from set`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.togglePreferredCategory(com.example.freshscan.domain.model.RecipeCategory.HOME)
        assertTrue(viewModel.uiState.value.preferredCategories.contains(
            com.example.freshscan.domain.model.RecipeCategory.HOME
        ))

        viewModel.togglePreferredCategory(com.example.freshscan.domain.model.RecipeCategory.HOME)
        assertFalse(viewModel.uiState.value.preferredCategories.contains(
            com.example.freshscan.domain.model.RecipeCategory.HOME
        ))
    }

    // ─── Save ───

    @Test
    fun `when save then writes to DataStore and clears isDirty`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.updateSpiceLevel(3)
        viewModel.updateSaltLevel(0)
        assertTrue(viewModel.uiState.value.isDirty)

        viewModel.save()
        advanceUntilIdle()

        // Verify edit was called (mockk relaxed handles the suspend generic method)
        coVerify(atLeast = 1) { dataStore.data }
        assertFalse("isDirty should be false after save", viewModel.uiState.value.isDirty)
        assertTrue(viewModel.uiState.value.savedSuccessfully)
    }

    // ─── Boundary Values ───

    @Test
    fun `when spice level at max (3) then update is accepted`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.updateSpiceLevel(3)
        assertEquals(3, viewModel.uiState.value.spiceLevel)
    }

    @Test
    fun `when salt level at min (0) then update is accepted`() = runTest {
        viewModel = TasteProfileViewModel(dataStore)

        viewModel.updateSaltLevel(0)
        assertEquals(0, viewModel.uiState.value.saltLevel)
    }
}
