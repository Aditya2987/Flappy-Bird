package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import com.example.data.HighScore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.firebase.FirebaseSync
import com.example.game.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GameScreen(
    viewModel: GameEngine,
    modifier: Modifier = Modifier
) {
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val currentScore by viewModel.currentScore.collectAsStateWithLifecycle()
    val difficulty by viewModel.difficulty.collectAsStateWithLifecycle()
    val activeTheme by viewModel.activeTheme.collectAsStateWithLifecycle()
    val bird by viewModel.bird.collectAsStateWithLifecycle()
    val pipes by viewModel.pipes.collectAsStateWithLifecycle()
    val particles by viewModel.particles.collectAsStateWithLifecycle()
    val bgElements by viewModel.backgroundElements.collectAsStateWithLifecycle()
    val topScores by viewModel.topScores.collectAsStateWithLifecycle()
    val personalBest by viewModel.personalBest.collectAsStateWithLifecycle()
    val soundMuted by viewModel.soundMuted.collectAsStateWithLifecycle()

    var showLeaderboardSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // Core Physics Game Engine Canvas Drawing Block
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("game_canvas")
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            viewModel.onScreenTap()
                        }
                    )
                }
        ) {
            val scaleX = size.width / viewModel.canvasWidth
            val scaleY = size.height / viewModel.canvasHeight

            // 1. Draw Skyscape colorful gradient background
            drawSkyBackground(activeTheme)

            // 2. Draw Stars and Clouds (Parallax drifting decoration assets)
            drawBackgroundElements(bgElements, activeTheme, scaleX, scaleY)

            // 3. Draw Scrolling Pipe obstacles with customized gradients, vertical glossy highlights, and lip caps
            drawPipes(pipes, activeTheme, scaleX, scaleY)

            // 4. Draw Interactive dynamic environment ground texture with cartoon scrolling indicator stripes
            drawGround(activeTheme, scaleX, scaleY, viewModel.groundY)

            // 5. Draw animated bird character with rotational tilting and active wings flap offsets
            drawBirdCharacter(bird, activeTheme, scaleX, scaleY)

            // 6. Draw glowing retro particle FX objects
            drawGameParticles(particles, scaleX, scaleY)
        }

        // --- ACTIVE GAME OVERLAY HUD ---

        // Top Header Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Highscore/Score Mini Indicator
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Peak Score",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "BEST: ${personalBest ?: 0}",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Audio mute & leaderboard utilities
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Leaderboard action button
                IconButton(
                    onClick = { showLeaderboardSheet = true },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Leaderboard,
                        contentDescription = "Show Highscores",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Volume Action Button
                IconButton(
                    onClick = { viewModel.toggleMute() },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .size(40.dp)
                        .testTag("sound_toggle")
                ) {
                    Icon(
                        imageVector = if (soundMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Mute status",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Live large center Score HUD (visible during game play & fall slide)
        if (gameState == GameState.PLAYING || gameState == GameState.COLLIDED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (-140).dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$currentScore",
                    color = Color.White,
                    fontSize = 82.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    style = MaterialTheme.typography.displayLarge.copy(
                        drawStyle = Stroke(
                            miter = 2f,
                            width = 6f,
                            join = StrokeJoin.Round
                        )
                    ),
                    modifier = Modifier.testTag("live_score_display")
                )
                // Solid font face inside the stroke
                Text(
                    text = "$currentScore",
                    color = if (activeTheme == SkinTheme.CYBERPUNK) Color(0xFF00FFFF) else Color.White,
                    fontSize = 82.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    modifier = Modifier.testTag("live_score_solid")
                )
            }

            // TAP TO FLAP Breathing Tutorial prompt
            if (currentScore == 0 && gameState == GameState.PLAYING) {
                val infiniteTransition = rememberInfiniteTransition(label = "Prompt Pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = twinKeyframes(),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "Pulse Alpha"
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.offset(y = 120.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Tap gesture tip",
                            tint = Color.White.copy(alpha = alpha),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "TAB ON SCREEN TO FLAP",
                            color = Color.White.copy(alpha = alpha),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- OVERLAY SCREENS ---

        // 1. MAIN MENU SCREEN OVERLAY
        AnimatedVisibility(
            visible = gameState == GameState.MENU,
            enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessLow)) + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                MainMenuCard(
                    difficulty = difficulty,
                    currentTheme = activeTheme,
                    bestScore = personalBest ?: 0,
                    onSelectDifficulty = { viewModel.setDifficulty(it) },
                    onSelectTheme = { viewModel.setTheme(it) },
                    onPlayClick = { viewModel.onScreenTap() },
                    onShowLeaderboard = { showLeaderboardSheet = true }
                )
            }
        }

        // 2. GAME OVER / RESULT CARD OVERLAY
        AnimatedVisibility(
            visible = gameState == GameState.GAME_OVER,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
            ) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                GameOverCard(
                    score = currentScore,
                    bestScore = personalBest ?: 0,
                    difficultyLabel = difficulty.label.split(" ").firstOrNull() ?: "Normal",
                    defaultName = viewModel.lastPlayerName,
                    currentTheme = activeTheme,
                    onSaveScore = { name -> viewModel.saveHighScore(name, currentScore) },
                    onRestart = { viewModel.onScreenTap() },
                    onReturnToMenu = { viewModel.returnToMainMenu() }
                )
            }
        }

        // --- BOTTOM SHEET LEADERBOARDS ---
        if (showLeaderboardSheet) {
            LeaderboardOverlay(
                scores = topScores,
                currentTheme = activeTheme,
                onClose = { showLeaderboardSheet = false },
                onClearAll = { viewModel.clearScores() }
            )
        }
    }
}

// Draw methods running on Canvas DrawScope safely

private fun DrawScope.drawSkyBackground(theme: SkinTheme) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(theme.skyTop, theme.skyBottom),
            startY = 0f,
            endY = size.height
        )
    )
}

private fun DrawScope.drawBackgroundElements(
    elements: List<StarOrCloud>,
    theme: SkinTheme,
    scaleX: Float,
    scaleY: Float
) {
    for (el in elements) {
        val px = el.x * scaleX
        val py = el.y * scaleY
        val pSize = el.size * scaleX

        if (theme == SkinTheme.COSMIC) {
            // Space twinkling star clusters instead of clouds
            val starAlpha = if (el.id % 2L == 0L) 0.8f else 0.4f
            drawCircle(
                color = Color.White.copy(alpha = starAlpha),
                radius = (el.size / 15f).coerceAtLeast(2f) * scaleX,
                center = Offset(px, py)
            )
            // Plus shapes for star flares
            val flareLen = pSize / 2f
            if (el.id % 3L == 0L) {
                drawLine(
                    color = Color.White.copy(alpha = starAlpha * 0.5f),
                    start = Offset(px - flareLen, py),
                    end = Offset(px + flareLen, py),
                    strokeWidth = 1.5f * scaleX
                )
                drawLine(
                    color = Color.White.copy(alpha = starAlpha * 0.5f),
                    start = Offset(px, py - flareLen),
                    end = Offset(px, py + flareLen),
                    strokeWidth = 1.5f * scaleX
                )
            }
        } else if (theme == SkinTheme.CYBERPUNK) {
            // Futuristic structural skylines shadow
            val skylineColor = Color(0xFF1E0A34).copy(alpha = 0.55f)
            drawRect(
                color = skylineColor,
                topLeft = Offset(px, py),
                size = Size(pSize * 0.9f, size.height - py)
            )
            // Little neon yellow window squares
            if (el.id % 2L == 0L) {
                val winSize = 4f * scaleX
                val rows = 5
                val cols = 3
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        drawRect(
                            color = Color(0xFFFFFF00).copy(alpha = 0.4f),
                            topLeft = Offset(px + 10f * scaleX + c * 8f * scaleX, py + 12f * scaleY + r * 15f * scaleY),
                            size = Size(winSize, winSize)
                        )
                    }
                }
            }
        } else {
            // Soft fluffy light clouds
            val cloudColor = Color.White.copy(alpha = if (theme == SkinTheme.CORAL_REEF) 0.22f else 0.55f)
            translate(left = px, top = py) {
                // Clouds drawn as cute retro rounded curves
                val r = pSize * 0.3f
                drawCircle(color = cloudColor, radius = r, center = Offset(0f, 0f))
                drawCircle(color = cloudColor, radius = r * 1.5f, center = Offset(r * 0.8f, -r * 0.2f))
                drawCircle(color = cloudColor, radius = r * 1.2f, center = Offset(r * 1.7f, 0f))
                drawRoundRect(
                    color = cloudColor,
                    topLeft = Offset(-r * 0.6f, -r * 0.1f),
                    size = Size(r * 3f, r * 1.1f),
                    cornerRadius = CornerRadius(r, r)
                )
            }
        }
    }
}

private fun DrawScope.drawPipes(pList: List<Pipe>, theme: SkinTheme, scaleX: Float, scaleY: Float) {
    for (p in pList) {
        val px = p.x * scaleX
        val pWidth = p.width * scaleX
        val ptHeight = p.topHeight * scaleY
        val pbHeight = p.bottomHeight * scaleY

        // Use custom linear gradient brushes for retro pipe lighting/highlights
        val pipeBrush = Brush.horizontalGradient(
            colors = listOf(theme.pipeColor, theme.pipeColor.copy(alpha = 0.8f), theme.pipeLipColor),
            startX = px,
            endX = px + pWidth
        )

        val lipBrush = Brush.horizontalGradient(
            colors = listOf(theme.pipeColor.copy(alpha = 0.9f), Color.White.copy(alpha = 0.4f), theme.pipeLipColor),
            startX = px - 8f * scaleX,
            endX = px + pWidth + 8f * scaleX
        )

        val outlineColor = if (theme == SkinTheme.ARTISTIC) Color(0xFF21005D) else Color(0xFF1E1E1E)
        val shaftStrokeWidth = if (theme == SkinTheme.ARTISTIC) 4.2f * scaleX else 2.8f * scaleX
        val lipStrokeWidth = if (theme == SkinTheme.ARTISTIC) 4.8f * scaleX else 3.0f * scaleX

        // 1. DRAW TOP PIPE
        // Body shaft
        drawRoundRect(
            brush = pipeBrush,
            topLeft = Offset(px, 0f),
            size = Size(pWidth, ptHeight),
            cornerRadius = CornerRadius(4f * scaleX, 4f * scaleY)
        )
        // Outline
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(px, 0f),
            size = Size(pWidth, ptHeight),
            style = Stroke(width = shaftStrokeWidth),
            cornerRadius = CornerRadius(4f * scaleX, 4f * scaleY)
        )
        
        // Rim edge / Lip cap
        val lipHeight = 44f * scaleY
        drawRoundRect(
            brush = lipBrush,
            topLeft = Offset(px - 6f * scaleX, ptHeight - lipHeight),
            size = Size(pWidth + 12f * scaleX, lipHeight),
            cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(px - 6f * scaleX, ptHeight - lipHeight),
            size = Size(pWidth + 12f * scaleX, lipHeight),
            style = Stroke(width = lipStrokeWidth),
            cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
        )

        // 2. DRAW BOTTOM PIPE
        // Body shaft
        val bPipeHeight = size.height - pbHeight
        drawRoundRect(
            brush = pipeBrush,
            topLeft = Offset(px, pbHeight),
            size = Size(pWidth, bPipeHeight),
            cornerRadius = CornerRadius(4f * scaleX, 4f * scaleY)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(px, pbHeight),
            size = Size(pWidth, bPipeHeight),
            style = Stroke(width = shaftStrokeWidth),
            cornerRadius = CornerRadius(4f * scaleX, 4f * scaleY)
        )

        // Rim edge / Lip cap
        drawRoundRect(
            brush = lipBrush,
            topLeft = Offset(px - 6f * scaleX, pbHeight),
            size = Size(pWidth + 12f * scaleX, lipHeight),
            cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
        )
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(px - 6f * scaleX, pbHeight),
            size = Size(pWidth + 12f * scaleX, lipHeight),
            style = Stroke(width = lipStrokeWidth),
            cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
        )
    }
}

private fun DrawScope.drawGround(theme: SkinTheme, scaleX: Float, scaleY: Float, groundY: Float) {
    val gy = groundY * scaleY
    val gHeight = size.height - gy

    // Main soil surface filling
    drawRect(
        color = theme.groundColor,
        topLeft = Offset(0f, gy),
        size = Size(size.width, gHeight)
    )

    // Top grassy edge lip line
    val lipSize = 15f * scaleY
    drawRect(
        color = theme.groundLipColor,
        topLeft = Offset(0f, gy),
        size = Size(size.width, lipSize)
    )

    // Divider line separating sky and soil
    val outlineColor = if (theme == SkinTheme.ARTISTIC) Color(0xFF21005D) else Color(0xFF1E1E1E)
    val topDivideWidth = if (theme == SkinTheme.ARTISTIC) 5f * scaleY else 4f * scaleY
    val bottomDivideWidth = if (theme == SkinTheme.ARTISTIC) 3.5f * scaleY else 2.5f * scaleY

    drawLine(
        color = outlineColor,
        start = Offset(0f, gy),
        end = Offset(size.width, gy),
        strokeWidth = topDivideWidth
    )
    drawLine(
        color = outlineColor,
        start = Offset(0f, gy + lipSize),
        end = Offset(size.width, gy + lipSize),
        strokeWidth = bottomDivideWidth
    )

    // Draw little details inside dirt/soil (diagonal lines) to simulate retro scroll speed
    val stripeWidth = 14f * scaleX
    val spacing = (70f * scaleX).coerceAtLeast(10f)
    var xOffset = 0f
    
    while (xOffset < size.width + spacing) {
        drawLine(
            color = theme.groundColor.copy(red = (theme.groundColor.red * 0.8f).coerceIn(0f, 1f)),
            start = Offset(xOffset, gy + lipSize + 8f * scaleY),
            end = Offset(xOffset - 25f * scaleX, gy + gHeight),
            strokeWidth = stripeWidth
        )
        xOffset += spacing
    }
}

private fun DrawScope.drawBirdCharacter(bird: BirdState, theme: SkinTheme, scaleX: Float, scaleY: Float) {
    val bx = 260f * scaleX
    val by = bird.y * scaleY
    val radius = bird.radius * scaleX

    // Isolate transformation context
    translate(left = bx, top = by) {
        rotate(degrees = bird.rotation, pivot = Offset(0f, 0f)) {
            if (theme == SkinTheme.ARTISTIC) {
                // 1. Draw Rounded Boxy Body
                val sizeVal = radius * 1.8f
                drawRoundRect(
                    color = theme.birdMainColor,
                    topLeft = Offset(-sizeVal / 2f, -sizeVal / 2f),
                    size = Size(sizeVal, sizeVal),
                    cornerRadius = CornerRadius(14f * scaleX, 14f * scaleY)
                )
                // Chunky deep outline
                drawRoundRect(
                    color = Color(0xFF21005D),
                    topLeft = Offset(-sizeVal / 2f, -sizeVal / 2f),
                    size = Size(sizeVal, sizeVal),
                    cornerRadius = CornerRadius(14f * scaleX, 14f * scaleY),
                    style = Stroke(width = 4.2f * scaleX)
                )

                // 2. Eye
                val eyeRadius = sizeVal * 0.16f
                val eyeX = sizeVal * 0.22f
                val eyeY = -sizeVal * 0.22f
                drawCircle(
                    color = Color(0xFF21005D),
                    radius = eyeRadius,
                    center = Offset(eyeX, eyeY)
                )

                // 3. Wing (on left/back)
                val wingWidth = sizeVal * 0.45f
                val wingHeight = sizeVal * 0.38f
                val wingX = -sizeVal * 0.65f
                val wingY = -sizeVal * 0.08f
                drawRoundRect(
                    color = Color(0xFF6750A4),
                    topLeft = Offset(wingX, wingY),
                    size = Size(wingWidth, wingHeight),
                    cornerRadius = CornerRadius(8f * scaleX, 8f * scaleY)
                )
                drawRoundRect(
                    color = Color(0xFF21005D),
                    topLeft = Offset(wingX, wingY),
                    size = Size(wingWidth, wingHeight),
                    cornerRadius = CornerRadius(8f * scaleX, 8f * scaleY),
                    style = Stroke(width = 2.5f * scaleX)
                )

                // 4. Beak (on right-front)
                val beakWidth = sizeVal * 0.38f
                val beakHeight = sizeVal * 0.28f
                val beakX = sizeVal * 0.45f
                val beakY = sizeVal * 0.08f
                drawRoundRect(
                    color = Color(0xFFFBBC04),
                    topLeft = Offset(beakX, beakY),
                    size = Size(beakWidth, beakHeight),
                    cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY)
                )
                drawRoundRect(
                    color = Color(0xFF21005D),
                    topLeft = Offset(beakX, beakY),
                    size = Size(beakWidth, beakHeight),
                    cornerRadius = CornerRadius(6f * scaleX, 6f * scaleY),
                    style = Stroke(width = 2.5f * scaleX)
                )
            } else {
                // 1. Draw Body
                drawCircle(
                    color = theme.birdMainColor,
                    radius = radius,
                    center = Offset(0f, 0f)
                )
                drawCircle(
                    color = Color(0xFF1A1A1A),
                    radius = radius,
                    center = Offset(0f, 0f),
                    style = Stroke(width = 3.5f * scaleX)
                )

                // 2. Draw Eye Highlight Belly
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f),
                    radius = radius * 0.75f,
                    center = Offset(-radius * 0.2f, radius * 0.1f)
                )

                // 3. Draw Big Cartoon Eyes
                val eyeRadius = radius * 0.38f
                val eyeX = radius * 0.40f
                val eyeY = -radius * 0.32f
                
                drawCircle(
                    color = theme.birdEyeColor,
                    radius = eyeRadius,
                    center = Offset(eyeX, eyeY)
                )
                drawCircle(
                    color = Color(0xFF1A1A1A),
                    radius = eyeRadius,
                    center = Offset(eyeX, eyeY),
                    style = Stroke(width = 2.5f * scaleX)
                )

                // 4. Draw Pupil
                val pupilRadius = eyeRadius * 0.5f
                val pupilOffset = Offset(eyeX + 3f * scaleX, eyeY)
                drawCircle(
                    color = Color(0xFF1A1A1A),
                    radius = pupilRadius,
                    center = pupilOffset
                )
                // Tiny white light shine inside pupil
                drawCircle(
                    color = Color.White,
                    radius = pupilRadius * 0.35f,
                    center = Offset(pupilOffset.x + 1f * scaleX, pupilOffset.y - 1.5f * scaleY)
                )

                // 5. Draw Lip Beak
                val beakTop = Path().apply {
                    moveTo(radius * 0.55f, -radius * 0.08f)
                    lineTo(radius * 1.30f, radius * 0.08f)
                    lineTo(radius * 0.80f, radius * 0.32f)
                    lineTo(radius * 0.45f, radius * 0.32f)
                    close()
                }
                drawPath(path = beakTop, color = theme.birdBeakColor)
                drawPath(path = beakTop, color = Color(0xFF1A1A1A), style = Stroke(width = 2.6f * scaleX))

                val beakBottom = Path().apply {
                    moveTo(radius * 0.45f, radius * 0.32f)
                    lineTo(radius * 0.95f, radius * 0.32f)
                    lineTo(radius * 0.65f, radius * 0.55f)
                    lineTo(radius * 0.40f, radius * 0.42f)
                    close()
                }
                drawPath(path = beakBottom, color = theme.birdBeakColor.copy(red = (theme.birdBeakColor.red * 0.85f).coerceIn(0f,1f)))
                drawPath(path = beakBottom, color = Color(0xFF1A1A1A), style = Stroke(width = 2.6f * scaleX))

                // 6. Draw wing (Animate flapping position based on bird's vertical speed)
                val wingFlappingUp = bird.velocity < 1.0f
                val wingPath = Path().apply {
                    if (wingFlappingUp) {
                        moveTo(-radius * 0.1f, radius * 0.12f)
                        cubicTo(
                            -radius * 0.9f, -radius * 0.45f,
                            -radius * 0.8f, radius * 0.45f,
                            -radius * 0.1f, radius * 0.42f
                        )
                    } else {
                        moveTo(-radius * 0.1f, radius * 0.12f)
                        cubicTo(
                            -radius * 0.75f, radius * 0.72f,
                            -radius * 0.9f, radius * 0.22f,
                            -radius * 0.10f, radius * 0.32f
                        )
                    }
                    close()
                }
                
                val wingThemeColor = if (theme == SkinTheme.CYBERPUNK) Color(0xFF00FFCC) else Color(0xFFFFF176)
                drawPath(path = wingPath, color = wingThemeColor)
                drawPath(path = wingPath, color = Color(0xFF1A1A1A), style = Stroke(width = 2.6f * scaleX))
            }
        }
    }
}

private fun DrawScope.drawGameParticles(particles: List<GameParticle>, scaleX: Float, scaleY: Float) {
    for (p in particles) {
        val px = p.x * scaleX
        val py = p.y * scaleY
        val size = p.size * scaleX
        val alphaColor = p.color.copy(alpha = p.alpha)

        when (p.shape) {
            ParticleShape.CIRCLE -> {
                drawCircle(color = alphaColor, radius = size / 2, center = Offset(px, py))
            }
            ParticleShape.STAR -> {
                val starPath = Path().apply {
                    moveTo(px, py - size) // Top
                    lineTo(px + size * 0.25f, py - size * 0.25f)
                    lineTo(px + size, py) // Right
                    lineTo(px + size * 0.25f, py + size * 0.25f)
                    lineTo(px, py + size) // Bottom
                    lineTo(px - size * 0.25f, py + size * 0.25f)
                    lineTo(px - size, py) // Left
                    lineTo(px - size * 0.25f, py - size * 0.25f)
                    close()
                }
                drawPath(path = starPath, color = alphaColor)
            }
            ParticleShape.FEATHER -> {
                val capsule = Rect(px - size / 1.5f, py - size / 3, px + size / 1.5f, py + size / 3)
                rotate(degrees = p.life * 10f, pivot = Offset(px, py)) {
                    drawOval(color = alphaColor, topLeft = Offset(capsule.left, capsule.top), size = Size(capsule.width, capsule.height))
                }
            }
            ParticleShape.BUBBLE -> {
                drawCircle(
                    color = alphaColor,
                    radius = size / 2,
                    center = Offset(px, py),
                    style = Stroke(width = 1.6f * scaleX)
                )
                // highlight spark in bubbles
                drawCircle(
                    color = Color.White.copy(alpha = p.alpha * 0.6f),
                    radius = size * 0.12f,
                    center = Offset(px - size * 0.18f, py - size * 0.18f)
                )
            }
        }
    }
}

// Helpers for prompts
private fun twinKeyframes(): KeyframesSpec<Float> = keyframes {
    durationMillis = 800
    0.3f at 0
    1.0f at 400
    0.3f at 800
}


// --- SCREEN OVERLAYS COMPOSABLES ---

@Composable
fun MainMenuCard(
    difficulty: GameDifficulty,
    currentTheme: SkinTheme,
    bestScore: Int,
    onSelectDifficulty: (GameDifficulty) -> Unit,
    onSelectTheme: (SkinTheme) -> Unit,
    onPlayClick: () -> Unit,
    onShowLeaderboard: () -> Unit
) {
    val isArtistic = currentTheme == SkinTheme.ARTISTIC
    val containerBg = if (isArtistic) Color(0xFFEADDFF) else Color.Black.copy(alpha = 0.82f)
    val borderStrokeColor = if (isArtistic) Color(0xFF21005D) else Color.White.copy(alpha = 0.22f)
    val borderStrokeWidth = if (isArtistic) 4.dp else 1.5.dp
    val textTitleColor = if (isArtistic) Color(0xFF21005D) else Color(0xFFFFEB3B)
    val textSubColor = if (isArtistic) Color(0xFF21005D).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f)
    val boxBg = if (isArtistic) Color(0xFF6750A4).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.1f)
    val pbTextColor = if (isArtistic) Color(0xFF21005D) else Color(0xFFFFD700)

    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 24.dp)
            .testTag("main_menu_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isArtistic) 12.dp else 8.dp),
        border = BorderStroke(borderStrokeWidth, borderStrokeColor)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Retro Title Header
            Text(
                text = "FLAPPY BIRD",
                color = textTitleColor,
                fontSize = 38.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp,
                fontFamily = FontFamily.SansSerif,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            
            Text(
                text = "Jetpack Compose Edition",
                color = textSubColor,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 18.dp)
            )

            // Personal Best Record Label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(boxBg)
                    .then(if (isArtistic) Modifier.border(2.dp, Color(0xFF21005D), RoundedCornerShape(12.dp)) else Modifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "🏆 RECORD PEAK: $bestScore",
                    color = pbTextColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Difficulty Tab Picker
            Text(
                text = "CHOOSE DIFFICULTY",
                color = textSubColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isArtistic) Color(0xFF21005D).copy(alpha = 0.08f) else Color.White.copy(alpha = 0.08f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                GameDifficulty.values().forEach { mode ->
                    val selected = mode == difficulty
                    val tabBg = when {
                        selected && isArtistic -> Color(0xFF6750A4)
                        selected -> Color(0xFFFFD700)
                        else -> Color.Transparent
                    }
                    val tabText = when {
                        selected && isArtistic -> Color.White
                        selected -> Color.Black
                        isArtistic -> Color(0xFF21005D)
                        else -> Color.White
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tabBg)
                            .clickable { onSelectDifficulty(mode) }
                            .then(
                                if (selected && isArtistic) Modifier.border(2.dp, Color(0xFF21005D), RoundedCornerShape(8.dp)) else Modifier
                            )
                            .padding(vertical = 10.dp)
                            .testTag("difficulty_tab_${mode.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.name,
                            color = tabText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Visual Theme / Skins Horizontal row choosing
            Text(
                text = "SELECT WORLD SKIN",
                color = textSubColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SkinTheme.values().forEach { theme ->
                    val isSelected = theme == currentTheme
                    val borderAccent = if (isSelected) {
                        if (isArtistic) Color(0xFF21005D) else Color(0xFFFFD700)
                    } else {
                        if (isArtistic) Color(0xFF21005D).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.15f)
                    }
                    val cardBg = if (isArtistic) Color(0xFF21005D).copy(alpha = 0.04f) else Color.White.copy(alpha = 0.05f)
                    val cardText = if (isSelected) {
                        if (isArtistic) Color(0xFF21005D) else Color(0xFFFFD700)
                    } else {
                        if (isArtistic) Color(0xFF21005D).copy(alpha = 0.6f) else Color.White
                    }
                    
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg)
                            .border(if (isSelected && isArtistic) 3.dp else 1.8.dp, borderAccent, RoundedCornerShape(14.dp))
                            .clickable { onSelectTheme(theme) }
                            .width(105.dp)
                            .padding(10.dp)
                            .testTag("theme_card_${theme.name.lowercase()}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Color palette bubble previews
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(modifier = Modifier.size(12.dp).background(theme.skyTop, CircleShape))
                            Box(modifier = Modifier.size(12.dp).background(theme.pipeColor, CircleShape))
                            Box(modifier = Modifier.size(12.dp).background(theme.birdMainColor, CircleShape))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = theme.title,
                            color = cardText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Main Play Action
            val buttonBg = if (isArtistic) Color(0xFF6750A4) else Color(0xFFFFD700)
            val buttonText = if (isArtistic) Color.White else Color.Black
            
            Button(
                onClick = onPlayClick,
                colors = ButtonDefaults.buttonColors(containerColor = buttonBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .then(
                        if (isArtistic) Modifier.border(3.dp, Color(0xFF21005D), RoundedCornerShape(16.dp)) else Modifier
                    )
                    .testTag("play_button"),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = if (isArtistic) 8.dp else 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Tap start",
                    tint = buttonText,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TAP TO FLAP",
                    color = buttonText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Leaderboard toggle secondary button
            val textBtnColor = if (isArtistic) Color(0xFF21005D) else Color.White.copy(alpha = 0.8f)
            TextButton(
                onClick = onShowLeaderboard,
                modifier = Modifier.testTag("leaderboards_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Leaderboard,
                    contentDescription = "score button",
                    tint = textBtnColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "VIEW SCORES & STATS",
                    color = textBtnColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GameOverCard(
    score: Int,
    bestScore: Int,
    difficultyLabel: String,
    defaultName: String,
    currentTheme: SkinTheme = SkinTheme.CLASSIC,
    onSaveScore: (String) -> Unit,
    onRestart: () -> Unit,
    onReturnToMenu: () -> Unit
) {
    var nameState by remember { mutableStateOf(defaultName) }
    var hasSaved by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val kbController = LocalSoftwareKeyboardController.current

    val isArtistic = currentTheme == SkinTheme.ARTISTIC
    val containerBg = if (isArtistic) Color(0xFFEADDFF) else Color.Black.copy(alpha = 0.85f)
    val borderStrokeColor = if (isArtistic) Color(0xFF21005D) else Color.White.copy(alpha = 0.22f)
    val borderStrokeWidth = if (isArtistic) 4.dp else 1.5.dp
    val textTitleColor = if (isArtistic) Color(0xFF21005D) else Color(0xFFF44336)
    val textSubColor = if (isArtistic) Color(0xFF21005D).copy(alpha = 0.7f) else Color.White.copy(alpha = 0.5f)
    val cellBg = if (isArtistic) Color(0xFFDECAFF) else Color.White.copy(alpha = 0.08f)
    val scoreTextColor = if (isArtistic) Color(0xFF21005D) else Color.White
    val bestTextColor = if (isArtistic) Color(0xFF6750A4) else Color(0xFFFFD700)
    val formBg = if (isArtistic) Color(0xFFFFD8E4).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f)

    Card(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(16.dp)
            .testTag("game_over_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isArtistic) 12.dp else 8.dp),
        border = BorderStroke(borderStrokeWidth, borderStrokeColor)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GAME OVER",
                color = textTitleColor,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 2.sp
            )
            
            Text(
                text = "DIFFICULTY: $difficultyLabel",
                color = textSubColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Grid displaying current score vs best peak score side-by-side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Score Box
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = cellBg),
                    shape = RoundedCornerShape(16.dp),
                    border = if (isArtistic) BorderStroke(2.dp, Color(0xFF21005D)) else null
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "SCORE", color = textSubColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$score", color = scoreTextColor, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    }
                }

                // High score Box
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = cellBg),
                    shape = RoundedCornerShape(16.dp),
                    border = if (isArtistic) BorderStroke(2.dp, Color(0xFF21005D)) else null
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "BEST", color = bestTextColor.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "$bestScore", color = bestTextColor, fontSize = 36.sp, fontWeight = FontWeight.Black)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Save high scores room form if qualified!
            if (score > 0 && !hasSaved) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(formBg)
                        .then(if (isArtistic) Modifier.border(2.dp, Color(0xFF21005D), RoundedCornerShape(16.dp)) else Modifier)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "NEW HIGHSCORE! ENTER NICKNAME:",
                        color = if (isArtistic) Color(0xFF21005D) else Color(0xFFFFEB3B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedTextField(
                        value = nameState,
                        onValueChange = { if (it.length <= 8) nameState = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("score_name_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isArtistic) Color(0xFF21005D) else Color.White,
                            unfocusedTextColor = if (isArtistic) Color(0xFF21005D) else Color.White,
                            focusedBorderColor = if (isArtistic) Color(0xFF21005D) else Color(0xFFFFD700),
                            unfocusedBorderColor = if (isArtistic) Color(0xFF21005D).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.3f),
                            focusedContainerColor = if (isArtistic) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                            unfocusedContainerColor = if (isArtistic) Color.White.copy(alpha = 0.3f) else Color.Transparent
                        ),
                        placeholder = { Text("PILOT", color = if (isArtistic) Color(0xFF21005D).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.3f)) },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (nameState.isNotEmpty()) {
                                    onSaveScore(nameState)
                                    hasSaved = true
                                    focusManager.clearFocus()
                                    kbController?.hide()
                                }
                            }
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val saveBtnBg = if (isArtistic) Color(0xFF6750A4) else Color(0xFFFFB300)
                    val saveBtnText = if (isArtistic) Color.White else Color.Black

                    Button(
                        onClick = {
                            if (nameState.isNotEmpty()) {
                                onSaveScore(nameState)
                                hasSaved = true
                                focusManager.clearFocus()
                                kbController?.hide()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = saveBtnBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .then(if (isArtistic) Modifier.border(2.dp, Color(0xFF21005D), RoundedCornerShape(10.dp)) else Modifier)
                            .testTag("save_score_button"),
                        enabled = nameState.trim().isNotEmpty()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = "store highscore", tint = saveBtnText)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SAVE TO HALL OF FAME", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = saveBtnText)
                    }
                }
            } else if (hasSaved) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isArtistic) Color(0xFF6750A4).copy(alpha = 0.15f) else Color(0xFF2E7D32).copy(alpha = 0.25f))
                        .border(1.2.dp, if (isArtistic) Color(0xFF21005D) else Color(0xFF4CAF50).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Score saved successfully! Check the podium.",
                        color = if (isArtistic) Color(0xFF21005D) else Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Action Buttons
            val restartBg = if (isArtistic) Color(0xFF6750A4) else Color(0xFFFFD700)
            val restartText = if (isArtistic) Color.White else Color.Black

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = restartBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .then(if (isArtistic) Modifier.border(3.dp, Color(0xFF21005D), RoundedCornerShape(16.dp)) else Modifier)
                    .testTag("restart_button"),
                elevation = ButtonDefaults.buttonElevation(if (isArtistic) 8.dp else 4.dp)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry code", tint = restartText)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "PLAY AGAIN", color = restartText, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(10.dp))

            val menuBtnColor = if (isArtistic) Color(0xFF21005D) else Color.White

            OutlinedButton(
                onClick = onReturnToMenu,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = menuBtnColor),
                border = BorderStroke(if (isArtistic) 3.dp else 1.5.dp, if (isArtistic) Color(0xFF21005D) else Color.White.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("main_menu_button")
            ) {
                Icon(imageVector = Icons.Default.Menu, contentDescription = "Quit logic", tint = menuBtnColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "MAIN MENU", color = menuBtnColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LeaderboardOverlay(
    scores: List<HighScore>,
    currentTheme: SkinTheme = SkinTheme.CLASSIC,
    onClose: () -> Unit,
    onClearAll: () -> Unit
) {
    var confirmClearDialog by remember { mutableStateOf(false) }

    val isArtistic = currentTheme == SkinTheme.ARTISTIC
    val containerBg = if (isArtistic) Color(0xFFEADDFF) else Color(0xFF15191C)
    val borderStrokeColor = if (isArtistic) Color(0xFF21005D) else Color.White.copy(alpha = 0.15f)
    val borderStrokeWidth = if (isArtistic) 4.dp else 1.5.dp
    val textTitleColor = if (isArtistic) Color(0xFF21005D) else Color.White
    val textSubColor = if (isArtistic) Color(0xFF21005D).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f)
    val cellBg = if (isArtistic) Color(0xFFDECAFF) else Color.White.copy(alpha = 0.05f)
    val trashColor = if (isArtistic) Color(0xFF21005D) else Color.Red.copy(alpha = 0.8f)
    val closeColor = if (isArtistic) Color(0xFF21005D) else Color.White
    val textDefaultColor = if (isArtistic) Color(0xFF21005D) else Color.White

    // Firebase Integration States
    val isFirebaseConfigured = FirebaseSync.isConnected()
    var isGlobalSelected by remember { mutableStateOf(isFirebaseConfigured) }

    val onlineScores by FirebaseSync.onlineScores.collectAsStateWithLifecycle()
    val isSyncing by FirebaseSync.isSyncing.collectAsStateWithLifecycle()
    val connectionMessage by FirebaseSync.connectionStatusMessage.collectAsStateWithLifecycle()

    // Trigger score fetch on load
    LaunchedEffect(isGlobalSelected) {
        if (isGlobalSelected && isFirebaseConfigured) {
            FirebaseSync.fetchLeaderboard()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clickable { /* Block parent clicks */ }
                .border(if (isArtistic) 3.dp else 0.dp, borderStrokeColor, RoundedCornerShape(26.dp))
                .testTag("leaderboards_sheet_card"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = containerBg),
            border = BorderStroke(borderStrokeWidth, borderStrokeColor)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxSize()
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = "🏆",
                            tint = if (isArtistic) Color(0xFF6750A4) else Color(0xFFFFD700),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "HALL OF FAME",
                            color = textTitleColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Row {
                        IconButton(onClick = { confirmClearDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Erase Data",
                                tint = trashColor
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Close overlay", tint = closeColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Modern Tab selector (Local Offline vs Global Firebase)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isArtistic) Color(0xFFE8DDFF) else Color.White.copy(alpha = 0.04f))
                        .then(if (isArtistic) Modifier.border(2.dp, Color(0xFF21005D), RoundedCornerShape(12.dp)) else Modifier)
                        .padding(4.dp)
                ) {
                    // Local Tab Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (!isGlobalSelected) {
                                    if (isArtistic) Color(0xFF6750A4) else Color.White.copy(alpha = 0.12f)
                                } else Color.Transparent
                            )
                            .clickable { isGlobalSelected = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "LOCAL LEADERBOARD",
                            color = if (!isGlobalSelected) Color.White else textSubColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Global Tab Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isGlobalSelected) {
                                    if (isArtistic) Color(0xFF6750A4) else Color.White.copy(alpha = 0.12f)
                                } else Color.Transparent
                            )
                            .clickable { isGlobalSelected = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = if (isGlobalSelected) Color(0xFFFFD700) else textSubColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "GLOBAL SYNC",
                                color = if (isGlobalSelected) Color.White else textSubColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Determine active list & status view
                val activeScores = if (isGlobalSelected) onlineScores else scores

                if (isGlobalSelected && isFirebaseConfigured) {
                    // Show firestore dynamic synchronization connection header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = connectionMessage,
                                color = if (isArtistic) Color(0xFF21005D).copy(alpha = 0.8f) else Color(0xFF4CAF50),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        IconButton(
                            onClick = { FirebaseSync.fetchLeaderboard() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = if (isArtistic) Color(0xFF6750A4) else Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = textSubColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (isGlobalSelected && !isFirebaseConfigured) {
                    // GORGEOUS educational instruction board on how to configure Cloud Storage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isArtistic) Color(0xFFDECAFF).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.03f))
                            .border(1.dp, if (isArtistic) Color(0xFF21005D).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = "Cloud Leaderboard",
                                tint = if (isArtistic) Color(0xFF6750A4) else Color(0xFFFFD700),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "GLOBAL LEADERBOARD READY",
                                color = textTitleColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Compete with pilots worldwide! To connect this client to your Firebase Firestore datastore on Google Cloud, securely add these parameters using your AI Studio Secrets panel:",
                                color = textSubColor,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Key Badges
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val badges = listOf("FIREBASE_API_KEY", "FIREBASE_PROJECT_ID", "FIREBASE_APP_ID")
                                badges.forEach { badge ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isArtistic) Color(0xFFC8B4EB) else Color.White.copy(alpha = 0.07f))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = badge,
                                            color = textTitleColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Score columns description header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(cellBg)
                            .then(if (isArtistic) Modifier.border(1.5.dp, Color(0xFF21005D), RoundedCornerShape(8.dp)) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = "R", color = textSubColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.12f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "PLAYER", color = textSubColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.43f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(text = "MODE", color = textSubColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.25f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                        Text(text = "SCORE", color = textSubColor, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.20f), fontSize = 11.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (activeScores.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Leaderboard,
                                    contentDescription = "Empty Stats",
                                    tint = textSubColor.copy(alpha = 0.3f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isGlobalSelected) "NO GLOBAL SCORES YET" else "NO HIGHSCORES RECORDED",
                                    color = textSubColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (isGlobalSelected) "Be the first to upload an online record!" else "Take flight and set your first record!",
                                    color = textSubColor.copy(alpha = 0.7f),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(activeScores) { rank, row ->
                                val colorIndex = when (rank) {
                                    0 -> if (isArtistic) Color(0xFF21005D) else Color(0xFFFFD700) // First rank highlight
                                    1 -> if (isArtistic) Color(0xFF6750A4) else Color(0xFFC0C0C0)
                                    2 -> if (isArtistic) Color(0xFF6750A4).copy(alpha = 0.8f) else Color(0xFFCD7F32)
                                    else -> textDefaultColor
                                }

                                val rowBackground = if (rank % 2 == 0) Color.Transparent else cellBg.copy(alpha = if (isArtistic) 0.5f else 0.02f)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(rowBackground)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("leaderboard_row_$rank"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${rank + 1}",
                                        color = colorIndex,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(0.12f),
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = row.playerName,
                                        color = if (rank <= 2) colorIndex else textDefaultColor,
                                        fontWeight = if (rank <= 2) FontWeight.Black else FontWeight.Medium,
                                        modifier = Modifier.weight(0.43f),
                                        fontSize = 13.sp
                                    )
                                    Box(
                                        modifier = Modifier
                                            .weight(0.25f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isArtistic) Color(0xFF6750A4).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f))
                                            .then(if (isArtistic) Modifier.border(1.dp, Color(0xFF21005D), RoundedCornerShape(6.dp)) else Modifier)
                                            .padding(vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = row.gameMode.uppercase(),
                                            color = when (row.gameMode) {
                                                "Hard" -> if (isArtistic) Color(0xFF21005D) else Color(0xFFFF5252)
                                                "Normal" -> if (isArtistic) Color(0xFF6750A4) else Color(0xFFFFD700)
                                                else -> if (isArtistic) Color(0xFF21005D).copy(alpha = 0.7f) else Color(0xFF4CAF50)
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Text(
                                        text = "${row.score}",
                                        color = colorIndex,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(0.20f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer banner stats
                Text(
                    text = if (isGlobalSelected && isFirebaseConfigured) 
                        "Scores are synchronized remotely with Firebase Datastore."
                    else
                        "Highscores are securely preserved locally via SQLite.",
                    textAlign = TextAlign.Center,
                    fontSize = 10.sp,
                    color = textSubColor,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Erase verification dialog
    if (confirmClearDialog) {
        AlertDialog(
            onDismissRequest = { confirmClearDialog = false },
            title = { Text("Erase Scores?") },
            text = { Text("Are you absolutely sure you want to permanently delete all local high scores? This action is irreversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        confirmClearDialog = false
                    }
                ) {
                    Text("ERASE ALL", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDialog = false }) {
                    Text("CANCEL", color = Color.Gray)
                }
            }
        )
    }
}
