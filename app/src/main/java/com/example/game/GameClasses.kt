package com.example.game

import androidx.compose.ui.graphics.Color

enum class GameState {
    MENU,
    PLAYING,
    COLLIDED,
    GAME_OVER
}

enum class GameDifficulty(val label: String, val speed: Float, val gap: Float, val spawnInterval: Float) {
    EASY("Easy Mode", 3.2f, 235f, 530f),
    NORMAL("Classic Normal", 4.2f, 205f, 490f),
    HARD("Extreme Hard", 5.5f, 170f, 440f)
}

enum class SkinTheme(
    val title: String,
    val skyTop: Color,
    val skyBottom: Color,
    val pipeColor: Color,
    val pipeLipColor: Color,
    val groundColor: Color,
    val groundLipColor: Color,
    val birdMainColor: Color,
    val birdEyeColor: Color,
    val birdBeakColor: Color,
    val particleColors: List<Color>
) {
    ARTISTIC(
        title = "Artistic Flair",
        skyTop = Color(0xFFD0BCFF),      // Lavender pastel
        skyBottom = Color(0xFFEADDFF),   // Light pastel purple
        pipeColor = Color(0xFF6750A4),    // Medium purple
        pipeLipColor = Color(0xFF21005D), // Deep purple border/lip
        groundColor = Color(0xFF21005D),   // Deep purple base ground
        groundLipColor = Color(0xFF6750A4),// Medium purple grass lip
        birdMainColor = Color(0xFFFFD8E4), // Light pink bird
        birdEyeColor = Color(0xFFFFFFFF),
        birdBeakColor = Color(0xFFFBBC04), // Bright Yellow
        particleColors = listOf(Color(0xFFFFD8E4), Color(0xFFD0BCFF), Color(0xFF6750A4))
    ),
    CLASSIC(
        title = "Sunny Field",
        skyTop = Color(0xFF29B6F6),      // Vivid Sky Blue
        skyBottom = Color(0xFFE1F5FE),   // Soft Light Blue
        pipeColor = Color(0xFF4CAF50),    // Mario Green
        pipeLipColor = Color(0xFF2E7D32), // Darker Green
        groundColor = Color(0xFF8D6E63),   // Soil Brown
        groundLipColor = Color(0xFF4CAF50),// Green Grass
        birdMainColor = Color(0xFFFFEB3B), // Yellow Bird
        birdEyeColor = Color(0xFFFFFFFF),
        birdBeakColor = Color(0xFFFF5722), // Orange Red
        particleColors = listOf(Color(0xFFFFEB3B), Color(0xFFFFF9C4), Color(0xFFFFFFFF))
    ),
    CYBERPUNK(
        title = "Neon Night",
        skyTop = Color(0xFF120338),      // Deep Midnight Purple
        skyBottom = Color(0xFF32004F),   // Electric Magenta
        pipeColor = Color(0xFF00E5FF),    // Cyan Neon
        pipeLipColor = Color(0xFF006064), // Deep Cyan
        groundColor = Color(0xFF111111),   // Dark Asphalt
        groundLipColor = Color(0xFFFF007F),// Hot Pink Neon Lip
        birdMainColor = Color(0xFFFF007F), // Neon Pink Bird
        birdEyeColor = Color(0xFF00FFCC),  // Cyan Eyes
        birdBeakColor = Color(0xFFFFFF00), // Yellow Neon Beak
        particleColors = listOf(Color(0xFFFF007F), Color(0xFF00E5FF), Color(0xFFFFFF00))
    ),
    COSMIC(
        title = "Orion Nebula",
        skyTop = Color(0xFF0B001A),      // Black Deep Space
        skyBottom = Color(0xFF1D0A35),   // Starry Nebula Violet
        pipeColor = Color(0xFFBB86FC),    // Cosmic Lavender
        pipeLipColor = Color(0xFF3700B3), // Dark Purple
        groundColor = Color(0xFF1C2833),   // Charcoal Rock Space Dust
        groundLipColor = Color(0xFF03DAC6),// Cyan Force Field Ground
        birdMainColor = Color(0xFF03DAC6), // Cyan Space Bird
        birdEyeColor = Color(0xFFFFFFFF),
        birdBeakColor = Color(0xFFCF6679), // Rose Red Beak
        particleColors = listOf(Color(0xFFBA68C8), Color(0xFF00E5FF), Color(0xFFFFFFFF))
    ),
    CORAL_REEF(
        title = "Coral Deep Blue",
        skyTop = Color(0xFF0D47A1),      // Ocean Deep Blue
        skyBottom = Color(0xFF00ACC1),   // Turquoise Water
        pipeColor = Color(0xFFFF7043),    // Sea Sponge Orange
        pipeLipColor = Color(0xFFBF360C), // Dark Terracotta
        groundColor = Color(0xFFF0F4C3),   // Golden Reef Sands
        groundLipColor = Color(0xFF26A69A),// Teal Kelp
        birdMainColor = Color(0xFF29B6F6), // Aqua Blue Bird
        birdEyeColor = Color(0xFFFFFFFF),
        birdBeakColor = Color(0xFFFFEE58), // Sea Lemon Yellow
        particleColors = listOf(Color(0xFF29B6F6), Color(0xFFE1F5FE), Color(0xFFFFFFFF), Color(0xFFFFAB91))
    )
}

data class BirdState(
    var y: Float = 500f,
    var velocity: Float = 0f,
    var rotation: Float = 0f,
    val radius: Float = 28f
)

data class Pipe(
    val id: Long,
    var x: Float,
    var topHeight: Float,
    var bottomHeight: Float,
    val width: Float = 115f,
    var passed: Boolean = false,
    val isMoving: Boolean = false,
    var direction: Float = 1f,
    val speed: Float = 2.0f,
    val minTopHeight: Float = 80f,
    val maxTopHeight: Float = 620f
)

enum class ParticleShape {
    CIRCLE,
    STAR,
    FEATHER,
    BUBBLE
}

data class GameParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var size: Float,
    var alpha: Float = 1.0f,
    val shape: ParticleShape = ParticleShape.CIRCLE,
    var life: Int = 0,
    val maxLife: Int = 25
)

data class StarOrCloud(
    val id: Long,
    var x: Float,
    var y: Float,
    val speed: Float,
    val size: Float,
    val isCloud: Boolean
)
