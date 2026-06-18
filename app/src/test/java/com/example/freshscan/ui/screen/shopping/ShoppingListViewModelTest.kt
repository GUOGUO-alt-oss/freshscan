package com.example.freshscan.ui.screen.shopping

import com.example.freshscan.data.history.ShoppingItemEntity
import com.example.freshscan.data.history.ShoppingListDao
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {

    private lateinit var shoppingListDao: ShoppingListDao
    private lateinit var itemsFlow: MutableStateFlow<List<ShoppingItemEntity>>
    private lateinit var viewModel: ShoppingListViewModel

    private val testItems = listOf(
        ShoppingItemEntity(1, "番茄", "2个", false, 1000),
        ShoppingItemEntity(2, "鸡蛋", "3个", true, 1001),
        ShoppingItemEntity(3, "盐", "适量", false, 1002)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        shoppingListDao = mockk(relaxed = true)
        itemsFlow = MutableStateFlow(testItems)
        every { shoppingListDao.getAll() } returns itemsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── Initial State ───

    @Test
    fun `when initialized then exposes items from DAO`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)
        // Keep a subscriber alive so WhileSubscribed collects from upstream
        backgroundScope.launch { viewModel.items.collect {} }
        advanceUntilIdle()

        val items = viewModel.items.value
        assertEquals(3, items.size)
        assertEquals("番茄", items[0].name)
    }

    // ─── Toggle Item ───

    @Test
    fun `when toggle unchecked item then updates to checked`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)

        viewModel.toggleItem(testItems[0])
        advanceUntilIdle()

        coVerify { shoppingListDao.update(match { it.id == 1L && it.isChecked }) }
    }

    @Test
    fun `when toggle checked item then updates to unchecked`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)

        viewModel.toggleItem(testItems[1])  // 鸡蛋 is already checked
        advanceUntilIdle()

        coVerify { shoppingListDao.update(match { it.id == 2L && !it.isChecked }) }
    }

    // ─── Add Item ───

    @Test
    fun `when add new item then inserts into DAO`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)

        viewModel.addItem("西兰花", "1颗")
        advanceUntilIdle()

        coVerify { shoppingListDao.insert(match { it.name == "西兰花" && it.amount == "1颗" }) }
    }

    @Test
    fun `when add duplicate name and amount then skips insertion`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)
        // Keep a subscriber alive so StateFlow collects from upstream
        backgroundScope.launch { viewModel.items.collect {} }
        advanceUntilIdle()

        viewModel.addItem("番茄", "2个")  // Already exists in testItems
        advanceUntilIdle()

        coVerify(exactly = 0) { shoppingListDao.insert(any()) }
    }

    @Test
    fun `when add item with same name but different amount then inserts`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)

        viewModel.addItem("番茄", "5个")  // Same name, different amount
        advanceUntilIdle()

        coVerify(exactly = 1) { shoppingListDao.insert(any()) }
    }

    // ─── Delete Item ───

    @Test
    fun `when deleteItem then calls DAO deleteById`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)

        viewModel.deleteItem(1L)
        advanceUntilIdle()

        coVerify { shoppingListDao.deleteById(1L) }
    }

    // ─── Clear Checked ───

    @Test
    fun `when clearChecked then calls DAO clearChecked`() = runTest {
        viewModel = ShoppingListViewModel(shoppingListDao)

        viewModel.clearChecked()
        advanceUntilIdle()

        coVerify(exactly = 1) { shoppingListDao.clearChecked() }
    }

    // ─── Empty State ───

    @Test
    fun `when DAO returns empty list then items is empty`() = runTest {
        itemsFlow.value = emptyList()
        viewModel = ShoppingListViewModel(shoppingListDao)

        val items = viewModel.items.first()
        assertTrue("Items should be empty", items.isEmpty())
    }
}
