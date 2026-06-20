package com.example.freshscan.ui.screen.personalize

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshscan.data.history.UserProfileDao
import com.example.freshscan.data.history.UserProfileEntity
import com.example.freshscan.data.history.UserProfileMapper
import com.example.freshscan.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

@HiltViewModel
class PersonalizeViewModel @Inject constructor(
    private val userProfileDao: UserProfileDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalizeUiState())
    val uiState: StateFlow<PersonalizeUiState> = _uiState.asStateFlow()

    private val _navigateToDietPlan = MutableSharedFlow<Unit>()
    val navigateToDietPlan = _navigateToDietPlan.asSharedFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            val entity = userProfileDao.get().first()
            if (entity != null) {
                _uiState.value = entityToUiState(entity)
            }
        }
    }

    // ── Field updaters ──

    fun updateSpiceLevel(level: Int) {
        _uiState.value = _uiState.value.copy(spiceLevel = level, isDirty = true)
    }

    fun updateSaltLevel(level: Int) {
        _uiState.value = _uiState.value.copy(saltLevel = level, isDirty = true)
    }

    fun updateOilLevel(level: Int) {
        _uiState.value = _uiState.value.copy(oilLevel = level, isDirty = true)
    }

    fun toggleExcludedIngredient(ingredient: String) {
        val current = _uiState.value.excludedIngredients
        _uiState.value = _uiState.value.copy(
            excludedIngredients = if (ingredient in current) current - ingredient else current + ingredient,
            isDirty = true
        )
    }

    fun togglePreferredCategory(category: RecipeCategory) {
        val current = _uiState.value.preferredCategories
        _uiState.value = _uiState.value.copy(
            preferredCategories = if (category in current) current - category else current + category,
            isDirty = true
        )
    }

    fun updateMaxCookingTime(minutes: Int) {
        _uiState.value = _uiState.value.copy(maxCookingTimeMin = minutes, isDirty = true)
    }

    fun updateAge(age: Int) {
        _uiState.value = _uiState.value.copy(
            age = age.coerceIn(1, 150),
            isDirty = true
        )
    }

    fun updateHeightCm(height: Int) {
        _uiState.value = _uiState.value.copy(
            heightCm = height.coerceIn(30, 300),
            isDirty = true
        )
    }

    fun updateWeightKg(weight: Float) {
        _uiState.value = _uiState.value.copy(
            weightKg = weight.coerceIn(1f, 500f),
            isDirty = true
        )
    }

    fun updateGender(gender: Gender) {
        _uiState.value = _uiState.value.copy(gender = gender, isDirty = true)
    }

    fun updateActivityLevel(level: ActivityLevel) {
        _uiState.value = _uiState.value.copy(activityLevel = level, isDirty = true)
    }

    fun updateGoal(goal: HealthGoal) {
        _uiState.value = _uiState.value.copy(goal = goal, isDirty = true)
    }

    fun updateMealsPerDay(meals: Int) {
        _uiState.value = _uiState.value.copy(mealsPerDay = meals, isDirty = true)
    }

    fun updateCalorieTarget(calories: Int?) {
        _uiState.value = _uiState.value.copy(calorieTarget = calories, isDirty = true)
    }

    fun toggleAllergy(allergy: String) {
        val current = _uiState.value.allergies
        _uiState.value = _uiState.value.copy(
            allergies = if (allergy in current) current - allergy else current + allergy,
            isDirty = true
        )
    }

    // ── Save ──

    fun save() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
            try {
                val state = _uiState.value
                val entity = uiStateToEntity(state)
                userProfileDao.upsert(entity)
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isDirty = false,
                    savedSuccessfully = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "保存失败，请重试"
                )
            }
        }
    }

    fun clearSaveFlag() {
        _uiState.value = _uiState.value.copy(savedSuccessfully = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun onStartCustomization() {
        viewModelScope.launch {
            _navigateToDietPlan.emit(Unit)
        }
    }

    // ── Mapping ──

    private fun uiStateToEntity(state: PersonalizeUiState): UserProfileEntity {
        return UserProfileEntity(
            id = 1,
            spiceLevel = state.spiceLevel,
            saltLevel = state.saltLevel,
            oilLevel = state.oilLevel,
            excludedIngredients = JSONArray(state.excludedIngredients.toList()).toString(),
            preferredCategories = JSONArray(state.preferredCategories.map { it.name }).toString(),
            maxCookingTimeMin = state.maxCookingTimeMin,
            age = state.age,
            heightCm = state.heightCm,
            weightKg = state.weightKg,
            gender = state.gender.name,
            activityLevel = state.activityLevel.name,
            goal = state.goal.name,
            mealsPerDay = state.mealsPerDay,
            calorieTarget = state.calorieTarget,
            allergies = JSONArray(state.allergies.toList()).toString()
        )
    }

    private fun entityToUiState(entity: UserProfileEntity): PersonalizeUiState {
        val domain = UserProfileMapper.toDomain(entity)
        return PersonalizeUiState(
            spiceLevel = domain.spiceLevel,
            saltLevel = domain.saltLevel,
            oilLevel = domain.oilLevel,
            excludedIngredients = domain.excludedIngredients,
            preferredCategories = domain.preferredCategories,
            maxCookingTimeMin = domain.maxCookingTimeMin,
            age = domain.age,
            heightCm = domain.heightCm,
            weightKg = domain.weightKg,
            gender = domain.gender,
            activityLevel = domain.activityLevel,
            goal = domain.goal,
            mealsPerDay = domain.mealsPerDay,
            calorieTarget = domain.calorieTarget,
            allergies = domain.allergies
        )
    }
}
