package com.example.freshscan.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.freshscan.R
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.freshscan.ui.screen.analysis.AnalysisScreen
import com.example.freshscan.ui.screen.personalize.MealQueryScreen
import com.example.freshscan.ui.screen.personalize.PersonalizeScreen
import com.example.freshscan.ui.screen.detail.DetailScreen
import com.example.freshscan.ui.screen.favorites.FavoritesScreen
import com.example.freshscan.ui.screen.fridge.FridgeRecipesScreen
import com.example.freshscan.ui.screen.fridge.FridgeScreen
import com.example.freshscan.ui.screen.history.HistoryScreen
import com.example.freshscan.ui.screen.home.HomeScreen
import com.example.freshscan.ui.screen.recipe.RecipeDetailScreen
import com.example.freshscan.ui.screen.settings.SettingsScreen
import com.example.freshscan.ui.screen.collection.CollectionScreen
import com.example.freshscan.ui.screen.report.WeeklyReportScreen
import com.example.freshscan.ui.screen.shopping.ShoppingListScreen

/**
 * Application route constants.
 *
 * v2.0 routes:
 * - Bottom navigation tabs: HOME, HISTORY, SETTINGS
 * - Detail screens (pushed onto nav stack): ANALYSIS, DETAIL, RECIPE_DETAIL,
 *   TASTE_PROFILE, SHOPPING_LIST
 */
object Routes {
    // ── v4.1 Bottom navigation tabs ──
    const val HOME = "home"
    const val MEAL_QUERY = "meal-query"          // Promoted to tab (was full-screen in v3)
    const val FRIDGE_RECIPES = "fridge-recipes"  // New merged tab (v4.1)
    const val SETTINGS = "settings"

    // ── v4.1 Full-screen routes (hide bottom nav) ──
    const val ANALYSIS = "analysis"
    const val DETAIL = "detail/{resultId}"
    const val RECIPE_DETAIL = "recipe/{recipeId}"
    const val SHOPPING_LIST = "shopping-list"
    const val PERSONALIZE = "personalize"
    const val HISTORY = "history"                // Demoted to full-screen (was tab in v4.0)
    const val FAVORITES = "favorites"            // New in v4.1

    // ── Deprecated routes (redirect) ──
    @Deprecated("Replaced by FRIDGE_RECIPES")
    const val FRIDGE = "fridge"
    @Deprecated("Replaced by MEAL_QUERY")
    const val DIET_PLAN = "diet-plan"
    @Deprecated("Replaced by PERSONALIZE")
    const val TASTE_PROFILE = "profile/taste"

    // ── v4.2 ──
    const val WEEKLY_REPORT = "weekly-report"
    const val COLLECTION = "collection"

    // Helper functions for parameterized routes
    fun detail(resultId: String) = "detail/$resultId"
    fun recipeDetail(recipeId: String) = "recipe/$recipeId"
}

/**
 * Top-level destinations that show the bottom navigation bar.
 */
val TOP_LEVEL_ROUTES = setOf(
    Routes.HOME,
    Routes.MEAL_QUERY,
    Routes.FRIDGE_RECIPES,
    Routes.SETTINGS
)

/**
 * Bottom navigation tab definition.
 */
data class BottomNavTab(
    val route: String,
    val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

val BOTTOM_NAV_TABS = listOf(
    BottomNavTab(
        route = Routes.HOME,
        labelResId = R.string.bottom_nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavTab(
        route = Routes.MEAL_QUERY,
        labelResId = R.string.bottom_nav_meal_query,
        selectedIcon = Icons.Filled.Restaurant,
        unselectedIcon = Icons.Outlined.Restaurant
    ),
    BottomNavTab(
        route = Routes.FRIDGE_RECIPES,
        labelResId = R.string.bottom_nav_fridge_recipes,
        selectedIcon = Icons.Filled.Kitchen,
        unselectedIcon = Icons.Outlined.Kitchen
    ),
    BottomNavTab(
        route = Routes.SETTINGS,
        labelResId = R.string.bottom_nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

/**
 * v2.0 application navigation graph with bottom navigation.
 *
 * Structure:
 * - 3 bottom tabs: Home, History, Settings
 * - 5 full-screen detail pages: Analysis, Detail, RecipeDetail,
 *   TasteProfile, ShoppingList
 *
 * Bottom navigation is hidden for detail screens and shown for top-level tabs.
 */
@Composable
fun AppNavGraph(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in TOP_LEVEL_ROUTES

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        // ═══ Bottom Navigation Tabs ═══

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToAnalysis = {
                    navController.navigate(Routes.ANALYSIS)
                },
                onNavigateToRecipe = { recipeId ->
                    navController.navigate(Routes.recipeDetail(recipeId))
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToFridge = {
                    navController.navigate(Routes.FRIDGE_RECIPES) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToCollection = {
                    navController.navigate(Routes.COLLECTION)
                }
            )
        }

        composable(Routes.MEAL_QUERY) {
            MealQueryScreen(
                onNavigateBack = { /* Tab page — no back nav needed */ }
            )
        }

        composable(Routes.FRIDGE_RECIPES) {
            FridgeRecipesScreen(
                onNavigateToRecipe = { recipeId ->
                    navController.navigate(Routes.recipeDetail(recipeId))
                },
                onNavigateToFridgeManage = {
                    // Open fridge management — use existing FridgeScreen as sub-page
                    navController.navigate(Routes.FRIDGE)
                },
                onNavigateToCamera = {
                    navController.navigate(Routes.ANALYSIS)
                }
            )
        }

        // Legacy: redirect FRIDGE to FRIDGE_RECIPES
        composable(Routes.FRIDGE) {
            FridgeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = {
                    navController.navigate(Routes.ANALYSIS)
                }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateToFavorites = {
                    navController.navigate(Routes.FAVORITES)
                },
                onNavigateToShoppingList = {
                    navController.navigate(Routes.SHOPPING_LIST)
                },
                onNavigateToHistory = {
                    navController.navigate(Routes.HISTORY)
                },
                onNavigateToPersonalize = {
                    navController.navigate(Routes.PERSONALIZE)
                },
                onNavigateToWeeklyReport = {
                    navController.navigate(Routes.WEEKLY_REPORT)
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ═══ Full-Screen Detail Pages (hide bottom nav) ═══

        composable(
            route = Routes.ANALYSIS,
            enterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { it / 4 } + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(200)) { it / 4 } + fadeOut(animationSpec = tween(200))
            }
        ) {
            AnalysisScreen(
                onNavigateBack = {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                },
                onNavigateToRecipe = { recipeId ->
                    navController.navigate(Routes.recipeDetail(recipeId))
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("resultId") { type = NavType.StringType }
            ),
            enterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { it / 4 } + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(200)) { it / 4 } + fadeOut(animationSpec = tween(200))
            }
        ) {
            DetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.RECIPE_DETAIL,
            arguments = listOf(
                navArgument("recipeId") { type = NavType.StringType }
            ),
            enterTransition = {
                slideInHorizontally(animationSpec = tween(300)) { it / 4 } + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(200)) { it / 4 } + fadeOut(animationSpec = tween(200))
            }
        ) {
            RecipeDetailScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToShoppingList = {
                    navController.navigate(Routes.SHOPPING_LIST)
                }
            )
        }

        // ── v3: Personalize (replaces TasteProfile) ──
        composable(Routes.PERSONALIZE) {
            PersonalizeScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDietPlan = {
                    navController.navigate(Routes.MEAL_QUERY)
                }
            )
        }

        // ── v4.1: Full-screen routes ──
        composable(Routes.HISTORY) {
            HistoryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCamera = {
                    navController.navigate(Routes.ANALYSIS)
                },
                onNavigateToDetail = { resultId ->
                    navController.navigate(Routes.detail(resultId))
                }
            )
        }

        composable(Routes.FAVORITES) {
            FavoritesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToRecipe = { recipeId ->
                    navController.navigate(Routes.recipeDetail(recipeId))
                },
                onNavigateToBrowse = {
                    navController.navigate(Routes.FRIDGE_RECIPES) {
                        popUpTo(Routes.FAVORITES) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.SHOPPING_LIST) {
            ShoppingListScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── v4.2: Weekly report ──
        composable(Routes.WEEKLY_REPORT) {
            WeeklyReportScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── v4.2: Collection view ──
        composable(Routes.COLLECTION) {
            CollectionScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Legacy: redirect TASTE_PROFILE to PERSONALIZE (no visible frame)
        composable(Routes.TASTE_PROFILE) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.PERSONALIZE) {
                    popUpTo(Routes.TASTE_PROFILE) { inclusive = true }
                }
            }
        }

        // Legacy: redirect DIET_PLAN to MEAL_QUERY (no visible frame)
        composable(Routes.DIET_PLAN) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.MEAL_QUERY) {
                    popUpTo(Routes.DIET_PLAN) { inclusive = true }
                }
            }
        }

    }
}

/**
 * Bottom navigation bar composable.
 *
 * Only rendered when [showBottomBar] is true (i.e., for top-level destinations).
 * Uses [currentRoute] to highlight the selected tab.
 */
@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    currentRoute: String?
) {
    NavigationBar {
        BOTTOM_NAV_TABS.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelResId)
                    )
                },
                label = { Text(stringResource(tab.labelResId)) }
            )
        }
    }
}
