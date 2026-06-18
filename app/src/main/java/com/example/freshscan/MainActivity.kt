package com.example.freshscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.freshscan.navigation.AppNavGraph
import com.example.freshscan.navigation.BottomNavigationBar
import com.example.freshscan.navigation.TOP_LEVEL_ROUTES
import com.example.freshscan.ui.theme.FreshScanTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry point for FreshScan v2.0.
 *
 * Uses Compose Navigation with a bottom navigation bar for top-level
 * destinations (Home, History, Settings). Detail screens (Analysis,
 * Recipe, TasteProfile, ShoppingList, Detail) push onto the nav stack
 * and hide the bottom bar.
 *
 * Camera lifecycle is bound to this Activity's lifecycle.
 * Edge-to-edge display is enabled for immersive full-screen pages.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FreshScanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    Scaffold(
                        bottomBar = {
                            if (currentRoute in TOP_LEVEL_ROUTES) {
                                BottomNavigationBar(
                                    navController = navController,
                                    currentRoute = currentRoute
                                )
                            }
                        }
                    ) { innerPadding ->
                        // Apply scaffold padding only when bottom bar is visible.
                        // Full-screen pages (analysis, detail, etc.) use the full canvas.
                        val modifier = if (currentRoute in TOP_LEVEL_ROUTES) {
                            Modifier.padding(innerPadding)
                        } else {
                            Modifier
                        }
                        Surface(modifier = modifier) {
                            AppNavGraph(navController = navController)
                        }
                    }
                }
            }
        }
    }
}
