package com.example.freshscan.ui.screen.personalize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.diet.DietPlanEngine
import com.example.freshscan.data.history.ShoppingItemEntity
import com.example.freshscan.data.history.ShoppingListDao
import com.example.freshscan.data.history.UserProfileDao
import com.example.freshscan.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DietPlanUiState {
    data object Idle : DietPlanUiState()
    data object Generating : DietPlanUiState()
    data class Success(val plan: DietPlan, val selectedDayIndex: Int = 0) : DietPlanUiState()
    data class Error(val message: String) : DietPlanUiState()
}

@HiltViewModel
class DietPlanViewModel @Inject constructor(
    private val dietPlanEngine: DietPlanEngine,
    private val userProfileDao: UserProfileDao,
    private val shoppingListDao: ShoppingListDao
) : ViewModel() {

    private val _uiState = MutableStateFlow<DietPlanUiState>(DietPlanUiState.Idle)
    val uiState: StateFlow<DietPlanUiState> = _uiState.asStateFlow()

    init {
        generatePlan()
    }

    fun generatePlan() {
        viewModelScope.launch {
            _uiState.value = DietPlanUiState.Generating
            try {
                val entity = userProfileDao.get().first()
                    ?: throw IllegalStateException("请先完善个性化档案")
                val profile = entityToProfile(entity)
                dietPlanEngine.generateWeekPlan(profile).collect { plan ->
                    _uiState.value = DietPlanUiState.Success(plan)
                }
            } catch (e: Exception) {
                _uiState.value = DietPlanUiState.Error(
                    e.message ?: "生成失败，请重试"
                )
            }
        }
    }

    fun selectDay(index: Int) {
        val state = _uiState.value
        if (state is DietPlanUiState.Success) {
            _uiState.value = state.copy(selectedDayIndex = index)
        }
    }

    fun addMealToShoppingList(meal: Meal) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            meal.recipe.ingredients.forEach { ingredient ->
                shoppingListDao.insert(
                    ShoppingItemEntity(
                        name = ingredient.name,
                        amount = ingredient.amount,
                        addedAt = now
                    )
                )
            }
        }
    }

    fun addAllToShoppingList() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state is DietPlanUiState.Success) {
                val now = System.currentTimeMillis()
                val seen = mutableSetOf<String>()
                state.plan.dailyPlans.flatMap { it.meals }
                    .flatMap { it.recipe.ingredients }
                    .forEach { ingredient ->
                        if (ingredient.name !in seen) {
                            seen.add(ingredient.name)
                            shoppingListDao.insert(
                                ShoppingItemEntity(
                                    name = ingredient.name,
                                    amount = ingredient.amount,
                                    addedAt = now
                                )
                            )
                        }
                    }
            }
        }
    }

    private fun entityToProfile(entity: com.example.freshscan.data.history.UserProfileEntity): UserProfile {
        return UserProfile(
            spiceLevel = entity.spiceLevel,
            saltLevel = entity.saltLevel,
            oilLevel = entity.oilLevel,
            excludedIngredients = parseSet(entity.excludedIngredients),
            preferredCategories = parseSet(entity.preferredCategories).mapNotNull {
                try {
                    RecipeCategory.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }.toSet(),
            maxCookingTimeMin = entity.maxCookingTimeMin,
            age = entity.age,
            heightCm = entity.heightCm,
            weightKg = entity.weightKg,
            gender = try {
                Gender.valueOf(entity.gender)
            } catch (_: Exception) {
                Gender.UNSPECIFIED
            },
            activityLevel = try {
                ActivityLevel.valueOf(entity.activityLevel)
            } catch (_: Exception) {
                ActivityLevel.MODERATE
            },
            goal = try {
                HealthGoal.valueOf(entity.goal)
            } catch (_: Exception) {
                HealthGoal.EAT_HEALTHY
            },
            mealsPerDay = entity.mealsPerDay,
            calorieTarget = entity.calorieTarget,
            allergies = parseSet(entity.allergies)
        )
    }

    private fun parseSet(json: String): Set<String> = try {
        val arr = org.json.JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
