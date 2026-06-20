package com.example.freshscan.ui.screen.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import com.example.freshscan.data.history.HistoryDao
import com.example.freshscan.data.history.MealHistoryDao
import com.example.freshscan.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var historyDao: HistoryDao
    private lateinit var mealHistoryDao: MealHistoryDao

    private val preferencesFlow = MutableStateFlow<Preferences>(emptyPreferences())

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(Logger)

        dataStore = mockk(relaxed = true)
        every { dataStore.data } returns preferencesFlow
        coEvery { dataStore.updateData(any()) } answers {
            preferencesFlow.value
        }

        historyDao = mockk(relaxed = true)
        mealHistoryDao = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            dataStore = dataStore,
            historyDao = historyDao,
            mealHistoryDao = mealHistoryDao
        )
    }

    // ── Initial State ──

    @Test
    fun `given no saved preferences when ViewModel created then isClassicMode defaults to false`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        assertFalse(vm.isClassicMode.value)
    }

    @Test
    fun `given classic_mode is true in DataStore when ViewModel created then isClassicMode is true`() = runTest {
        val key = booleanPreferencesKey("classic_mode")
        preferencesFlow.value = preferencesOf(key to true)

        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.isClassicMode.value)
    }

    // ── toggleClassicMode ──

    @Test
    fun `given ViewModel created when toggleClassicMode true then isClassicMode updates immediately`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleClassicMode(true)
        assertTrue(vm.isClassicMode.value)
    }

    @Test
    fun `given classicMode is true when toggleClassicMode false then isClassicMode becomes false`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()

        vm.toggleClassicMode(true)
        advanceUntilIdle()
        vm.toggleClassicMode(false)

        assertFalse(vm.isClassicMode.value)
    }

    // ── clearHistory ──

    @Test
    fun `given both DAOs succeed when clearHistory called then both deleteAll invoked`() = runTest {
        coEvery { historyDao.deleteAll() } returns 0
        coEvery { mealHistoryDao.deleteAll() } returns Unit

        val vm = createViewModel()
        advanceUntilIdle()

        vm.clearHistory()
        advanceUntilIdle()

        coVerify(exactly = 1) { historyDao.deleteAll() }
        coVerify(exactly = 1) { mealHistoryDao.deleteAll() }
    }

    @Test
    fun `given historyDao throws when clearHistory called then no crash`() = runTest {
        coEvery { historyDao.deleteAll() } throws RuntimeException("DB locked")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.clearHistory()
        advanceUntilIdle()

        // clearHistory catches the exception internally and emits an error message
        // We verify the DAO was called; the error handling is internal
        coVerify(exactly = 1) { historyDao.deleteAll() }
    }

    @Test
    fun `given mealHistoryDao throws when clearHistory called then no crash`() = runTest {
        coEvery { historyDao.deleteAll() } returns 0
        coEvery { mealHistoryDao.deleteAll() } throws RuntimeException("IO error")

        val vm = createViewModel()
        advanceUntilIdle()

        vm.clearHistory()
        advanceUntilIdle()

        coVerify(exactly = 1) { mealHistoryDao.deleteAll() }
    }

    // ── DataStore read error ──

    @Test
    fun `given DataStore throws on load when ViewModel created then isClassicMode stays false`() = runTest {
        val errorDataStore = mockk<DataStore<Preferences>>(relaxed = true)
        every { errorDataStore.data } throws RuntimeException("DataStore corrupt")

        val vm = SettingsViewModel(
            dataStore = errorDataStore,
            historyDao = historyDao,
            mealHistoryDao = mealHistoryDao
        )
        advanceUntilIdle()

        // Falls back to default false
        assertFalse(vm.isClassicMode.value)
    }
}
