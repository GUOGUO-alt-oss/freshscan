package com.example.freshscan.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Ink-wave (墨韵) ripple animation Canvas.
 *
 * Replaces the old [ParticleScan] white-particle animation with a Japanese
 * minimalist concentric-ripple effect. 3-4 concentric circles expand from
 * the photo center outward, each in moss-green at alpha 12%, fading as
 * they spread.
 *
 * Ref: docs/uiv2.md §5.4
 *
 * @param isActive Whether the animation is running.
 * @param rippleColor Color of the ripple circles (default: primary moss green).
 * @param onSkipClick Callback when user taps the skip button.
 * @param modifier Modifier for the Canvas.
 */
@Composable
fun WaveScanOverlay(
    isActive: Boolean,
    rippleColor: Color = Color(0xFF3D5A35),
    onSkipClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var centerX by remember { mutableStateOf(0f) }
    var centerY by remember { mutableStateOf(0f) }
    var maxRadius by remember { mutableStateOf(0f) }

    // 3 ripple layers, each triggered 450ms apart
    val ripple1Alpha = remember { Animatable(0f) }
    val ripple1Radius = remember { Animatable(0f) }
    val ripple2Alpha = remember { Animatable(0f) }
    val ripple2Radius = remember { Animatable(0f) }
    val ripple3Alpha = remember { Animatable(0f) }
    val ripple3Radius = remember { Animatable(0f) }

    // Overlay dimming
    val overlayAlpha = remember { Animatable(0f) }

    val density = LocalDensity.current
    val initialRadiusPx = with(density) { 60.dp.toPx() }

    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect

        // Dim the background
        overlayAlpha.animateTo(0.45f, animationSpec = tween(500))

        // Ripple 1 — starts immediately
        launch {
            ripple1Radius.snapTo(initialRadiusPx)
            ripple1Alpha.snapTo(0.12f)
            ripple1Alpha.animateTo(0f, tween(1200, easing = LinearEasing))
            while (true) {
                ripple1Radius.animateTo(maxRadius, tween(1800, easing = LinearEasing))
                ripple1Radius.snapTo(initialRadiusPx)
                ripple1Alpha.snapTo(0.12f)
                ripple1Alpha.animateTo(0f, tween(1200, easing = LinearEasing))
            }
        }

        // Ripple 2 — 450ms delay
        launch {
            delay(450)
            ripple2Radius.snapTo(initialRadiusPx)
            ripple2Alpha.snapTo(0.12f)
            ripple2Alpha.animateTo(0f, tween(1200, easing = LinearEasing))
            while (true) {
                ripple2Radius.animateTo(maxRadius, tween(1800, easing = LinearEasing))
                ripple2Radius.snapTo(initialRadiusPx)
                ripple2Alpha.snapTo(0.12f)
                ripple2Alpha.animateTo(0f, tween(1200, easing = LinearEasing))
            }
        }

        // Ripple 3 — 900ms delay
        launch {
            delay(900)
            ripple3Radius.snapTo(initialRadiusPx)
            ripple3Alpha.snapTo(0.12f)
            ripple3Alpha.animateTo(0f, tween(1200, easing = LinearEasing))
            while (true) {
                ripple3Radius.animateTo(maxRadius, tween(1800, easing = LinearEasing))
                ripple3Radius.snapTo(initialRadiusPx)
                ripple3Alpha.snapTo(0.12f)
                ripple3Alpha.animateTo(0f, tween(1200, easing = LinearEasing))
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        centerX = size.width / 2f
        centerY = size.height / 2f
        maxRadius = maxOf(size.width, size.height)

        val center = Offset(centerX, centerY)

        // Draw ripple circles (ordered from largest to smallest for visual layering)
        listOf(
            ripple3Radius.value to ripple3Alpha.value,
            ripple2Radius.value to ripple2Alpha.value,
            ripple1Radius.value to ripple1Alpha.value
        ).forEach { (radius, alpha) ->
            if (alpha > 0.001f && radius > 0f) {
                drawCircle(
                    color = rippleColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = radius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
                )
            }
        }
    }
}
