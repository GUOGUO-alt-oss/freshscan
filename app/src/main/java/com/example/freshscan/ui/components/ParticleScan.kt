package com.example.freshscan.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Scanning particle animation Canvas.
 *
 * @deprecated Replaced by [WaveScanOverlay] per docs/uiv2.md §5.4.
 *             Retained for backward compatibility with v1 classic mode.
 *
 * Physics model (from 06-详细设计文档-v2.md §7.1):
 * - N = 80 particles (configurable, reduced to 60 in dark mode)
 * - Initial position: randomly distributed on the 4 screen edges
 * - Target: accelerate toward the screen center
 * - Motion: uniformly accelerated linear (a = 400 px/s²)
 * - Lifecycle: alpha decays from 1 → 0 over 2 seconds
 * - Batch spawn: 80 on start, then 10 every 50ms for the first 1.5s
 * - Colors: white + warm gold palette
 *
 * Replaces [androidx.compose.material3.CircularProgressIndicator] in the
 * Animating analysis state.
 */
@Deprecated(
    message = "Use WaveScanOverlay instead",
    replaceWith = ReplaceWith("WaveScanOverlay(isActive, modifier = modifier)")
)
@Composable
fun ParticleScan(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    particleCount: Int = 50,  // Reduced from 80 for stable 60fps on mid-range devices
    onAnimationEnd: () -> Unit = {}
) {
    val particles = remember { mutableStateListOf<Particle>() }
    var centerX by remember { mutableStateOf(0f) }
    var centerY by remember { mutableStateOf(0f) }
    var lastSpawnTime by remember { mutableStateOf(0L) }
    var elapsedStart by remember { mutableStateOf(0L) }
    var hasCalledEnd by remember { mutableStateOf(false) }

    // Track when all particles have faded (animation naturally complete)
    val animationComplete = particles.isEmpty() && elapsedStart > 0L

    LaunchedEffect(animationComplete) {
        if (animationComplete && isActive && !hasCalledEnd) {
            hasCalledEnd = true
            onAnimationEnd()
        }
    }

    // Reset state when isActive changes
    LaunchedEffect(isActive) {
        if (isActive) {
            particles.clear()
            lastSpawnTime = 0L
            elapsedStart = 0L
            hasCalledEnd = false
        }
    }

    // Animation loop — drives particle spawn + physics update on each frame
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect

        var prevFrameTimeMs: Long = 0L

        while (isActive) {
            val frameTimeMs: Long = withFrameMillis { it }

            // Record the first frame as animation start
            if (elapsedStart == 0L) {
                elapsedStart = frameTimeMs
                prevFrameTimeMs = frameTimeMs
            }

            // Compute actual delta time from consecutive frames (in seconds).
            // On 120Hz displays this yields ~0.0083s; on 60Hz ~0.0167s.
            val dt: Float = if (prevFrameTimeMs > 0L) {
                ((frameTimeMs - prevFrameTimeMs) / 1000f).coerceIn(0.004f, 0.050f)
            } else {
                0.01667f  // fallback for first frame
            }
            prevFrameTimeMs = frameTimeMs

            val relativeMs: Long = frameTimeMs - elapsedStart

            // Initial batch spawn (first frame)
            if (particles.isEmpty()) {
                spawnParticles(particles, centerX, centerY, particleCount)
                lastSpawnTime = relativeMs
            }

            // Batch spawn every 50ms for the first 1.5 seconds
            if (relativeMs - lastSpawnTime > 50L && relativeMs < 1500L) {
                spawnParticles(particles, centerX, centerY, 6)  // Proportional to 50 particles (was 10 for 80)
                lastSpawnTime = relativeMs
            }

            // Update all particles (physics step at display refresh rate)
            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()

                // Acceleration toward center
                val dx: Float = p.targetX - p.x
                val dy: Float = p.targetY - p.y
                val dist: Float = sqrt(dx * dx + dy * dy)
                if (dist > 1f) {
                    val ax: Float = (dx / dist) * ACCELERATION
                    val ay: Float = (dy / dist) * ACCELERATION
                    p.vx += ax * dt
                    p.vy += ay * dt
                }

                p.x += p.vx * dt
                p.y += p.vy * dt
                p.life -= dt / LIFE_DURATION_SECONDS

                if (p.life <= 0f) {
                    iterator.remove()
                }
            }
        }
    }

    // Canvas rendering — draws particles each frame
    Canvas(modifier = modifier.fillMaxSize()) {
        centerX = size.width / 2f
        centerY = size.height / 2f

        particles.forEach { p ->
            drawCircle(
                color = p.color.copy(alpha = p.life.coerceIn(0f, 1f)),
                radius = p.size,
                center = Offset(p.x, p.y)
            )
        }
    }
}

// ─── Particle data class ────────────────────────────────────────────────────

private data class Particle(
    var x: Float,
    var y: Float,
    val targetX: Float,
    val targetY: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var life: Float = 1f,
    val size: Float = 3f + Random.nextFloat() * 5f,
    val color: Color = PARTICLE_COLORS[Random.nextInt(PARTICLE_COLORS.size)]
)

// ─── Spawn helpers ──────────────────────────────────────────────────────────

private fun spawnParticles(
    list: MutableList<Particle>,
    cx: Float,
    cy: Float,
    count: Int
) {
    repeat(count) {
        val (x, y) = randomEdgePosition(cx, cy)
        list.add(
            Particle(
                x = x,
                y = y,
                targetX = cx + (Random.nextFloat() - 0.5f) * 60f,
                targetY = cy + (Random.nextFloat() - 0.5f) * 60f,
                vx = (Random.nextFloat() - 0.5f) * 100f,
                vy = (Random.nextFloat() - 0.5f) * 100f
            )
        )
    }
}

/**
 * Generate a random position on one of the 4 screen edges.
 */
private fun randomEdgePosition(cx: Float, cy: Float): Pair<Float, Float> {
    val w: Float = cx * 2f
    val h: Float = cy * 2f
    return when (Random.nextInt(4)) {
        0 -> Random.nextFloat() * w to 0f          // top
        1 -> Random.nextFloat() * w to h           // bottom
        2 -> 0f to Random.nextFloat() * h          // left
        else -> w to Random.nextFloat() * h        // right
    }
}

// ─── Constants ──────────────────────────────────────────────────────────────

/** Acceleration in px/s² toward center. */
private const val ACCELERATION: Float = 400f

/** Particle lifetime in seconds. */
private const val LIFE_DURATION_SECONDS: Float = 2f

/**
 * Color palette: white + warm gold tones.
 * From UI spec: #FFFFFF + #FFD54F.
 */
private val PARTICLE_COLORS: List<Color> = listOf(
    Color.White.copy(alpha = 0.8f),
    Color(0xFFFFF8E1).copy(alpha = 0.7f),  // warm white
    Color(0xFFFFD54F).copy(alpha = 0.4f),  // gold
    Color(0xFFFFECB3).copy(alpha = 0.5f),  // light gold
    Color.White.copy(alpha = 0.6f)
)
