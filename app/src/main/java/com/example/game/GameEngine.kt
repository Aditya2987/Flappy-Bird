package com.example.game

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioSynthesizer
import com.example.data.HighScore
import com.example.data.HighScoreDatabase
import com.example.data.HighScoreRepository
import com.example.firebase.FirebaseSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

class GameEngine(application: Application) : AndroidViewModel(application) {

    // Room Database components
    private val repository: HighScoreRepository
    val topScores: StateFlow<List<HighScore>>
    val personalBest: StateFlow<Int?>

    // Audio SFX synthesis
    val audio = AudioSynthesizer(application)

    // Game Loop Job
    private var gameLoopJob: Job? = null

    // Constants for 1000x1000 Virtual Coordinate Canvas
    val canvasWidth = 1000f
    val canvasHeight = 1000f
    val groundY = 850f
    private val gravity = 0.36f
    private val jumpStrength = -8.5f
    private val terminalVelocity = 14f

    // Bird locked X position
    val birdX = 260f

    // Core Game States (Observable by Compose UI)
    private val _gameState = MutableStateFlow(GameState.MENU)
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private val _difficulty = MutableStateFlow(GameDifficulty.NORMAL)
    val difficulty: StateFlow<GameDifficulty> = _difficulty.asStateFlow()

    private val _activeTheme = MutableStateFlow(SkinTheme.ARTISTIC)
    val activeTheme: StateFlow<SkinTheme> = _activeTheme.asStateFlow()

    private val _bird = MutableStateFlow(BirdState())
    val bird: StateFlow<BirdState> = _bird.asStateFlow()

    private val _pipes = MutableStateFlow<List<Pipe>>(emptyList())
    val pipes: StateFlow<List<Pipe>> = _pipes.asStateFlow()

    private val _particles = MutableStateFlow<List<GameParticle>>(emptyList())
    val particles: StateFlow<List<GameParticle>> = _particles.asStateFlow()

    private val _backgroundElements = MutableStateFlow<List<StarOrCloud>>(emptyList())
    val backgroundElements: StateFlow<List<StarOrCloud>> = _backgroundElements.asStateFlow()

    private val _currentScore = MutableStateFlow(0)
    val currentScore: StateFlow<Int> = _currentScore.asStateFlow()

    // Sound status
    private val _soundMuted = MutableStateFlow(false)
    val soundMuted: StateFlow<Boolean> = _soundMuted.asStateFlow()

    // Temp inputs
    var lastPlayerName = "PILOT"

    // Engine Frame ticker statistics
    private var tickCount = 0L
    private var pipeIdCounter = 0L
    private var bgElementIdCounter = 0L

    init {
        val database = HighScoreDatabase.getDatabase(application)
        repository = HighScoreRepository(database.highScoreDao())
        
        // Expose database hot flows as read-only states in ViewModel
        val scoreState = MutableStateFlow<List<HighScore>>(emptyList())
        topScores = scoreState
        val bestState = MutableStateFlow<Int?>(null)
        personalBest = bestState

        viewModelScope.launch {
            repository.topScores.collect { scoreState.value = it }
        }
        viewModelScope.launch {
            repository.personalBest.collect { bestState.value = it }
        }

        // Setup ambient background stars/clouds
        initBackgroundElements()

        // Start active render clock
        startGameLoop()
    }

    private fun initBackgroundElements() {
        val list = mutableListOf<StarOrCloud>()
        // Pre-populate background clouds & stars
        for (i in 1..8) {
            list.add(
                StarOrCloud(
                    id = bgElementIdCounter++,
                    x = Random.nextFloat() * canvasWidth,
                    y = Random.nextFloat() * 400f + 40f,
                    speed = Random.nextFloat() * 1.5f + 0.3f,
                    size = Random.nextFloat() * 90f + 30f,
                    isCloud = Random.nextBoolean()
                )
            )
        }
        _backgroundElements.value = list
    }

    fun setDifficulty(diff: GameDifficulty) {
        if (_gameState.value == GameState.MENU) {
            _difficulty.value = diff
        }
    }

    fun setTheme(theme: SkinTheme) {
        _activeTheme.value = theme
    }

    fun toggleMute() {
        audio.toggleMute()
        _soundMuted.value = audio.isMuted()
    }

    // High score entry submission
    fun saveHighScore(name: String, score: Int) {
        val trimmedName = name.trim().uppercase()
        if (trimmedName.isNotEmpty() && score > 0) {
            lastPlayerName = trimmedName
            val modeLabel = _difficulty.value.label.replace(" Mode", "").replace("Classic ", "").replace("Extreme ", "")
            viewModelScope.launch {
                repository.insert(
                    HighScore(
                        playerName = trimmedName,
                        score = score,
                        gameMode = modeLabel
                    )
                )
                
                // Programmatically upload high score to Firebase Cloud Firestore if connected
                if (FirebaseSync.isConnected()) {
                    FirebaseSync.uploadHighScore(trimmedName, score, modeLabel)
                }
            }
        }
    }

    // Flap wings button! Triggered from screen tapping
    fun onScreenTap() {
        when (_gameState.value) {
            GameState.MENU -> {
                resetGameToActive()
                _gameState.value = GameState.PLAYING
                flapBirdWings()
            }
            GameState.PLAYING -> {
                flapBirdWings()
            }
            GameState.COLLIDED -> {
                // Ignore keypresses during active crash slide down
            }
            GameState.GAME_OVER -> {
                // Restart back to playing
                resetGameToActive()
                _gameState.value = GameState.PLAYING
            }
        }
    }

    private fun flapBirdWings() {
        val b = _bird.value.copy()
        b.velocity = jumpStrength
        _bird.value = b
        
        audio.playJump()
        spawnFlapParticles(birdX, b.y)
    }

    private fun resetGameToActive() {
        _bird.value = BirdState(y = 480f, velocity = 0f, rotation = 0f)
        _pipes.value = emptyList()
        _particles.value = emptyList()
        _currentScore.value = 0
        tickCount = 0L
    }

    fun returnToMainMenu() {
        _gameState.value = GameState.MENU
        _bird.value = BirdState(y = 500f) // Ambient hover
        _pipes.value = emptyList()
    }

    // Local DB management
    fun clearScores() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (isActive) {
                val start = System.currentTimeMillis()
                tickGameFrame()
                val elapsed = System.currentTimeMillis() - start
                val sleep = 16L - elapsed // Seek 60 FPS target
                if (sleep > 0) {
                    delay(sleep)
                } else {
                    delay(2L) // Safety buffer to keep responsiveness
                }
            }
        }
    }

    private fun tickGameFrame() {
        tickCount++
        val state = _gameState.value

        // 1. Hover/Ambient physics in idle modes
        if (state == GameState.MENU || state == GameState.GAME_OVER) {
            val b = _bird.value.copy()
            // Gentle hovering float using sine wave
            b.y = 480f + kotlin.math.sin(tickCount * 0.08f) * 16f
            b.velocity = 0f
            b.rotation = kotlin.math.sin(tickCount * 0.03f) * 10f
            _bird.value = b
        }

        // 2. Active playing physics
        if (state == GameState.PLAYING) {
            val b = _bird.value.copy()
            b.velocity += gravity
            if (b.velocity > terminalVelocity) {
                b.velocity = terminalVelocity
            }
            b.y += b.velocity
            
            // Lock rotation relative to speed
            b.rotation = (b.velocity * 5.2f).coerceIn(-32f, 75f)
            
            // Limit upper ceiling
            if (b.y - b.radius < 0) {
                b.y = b.radius
                b.velocity = 0f
            }
            _bird.value = b

            // Ground crash condition
            if (b.y + b.radius >= groundY) {
                triggerBirdCollision(b.y)
            }

            // Pipe Spawning
            tickPipeSpawnAnScroll()
        }

        // 3. Falling after colliding physics
        if (state == GameState.COLLIDED) {
            val b = _bird.value.copy()
            b.velocity += gravity * 1.5f // Fall faster on death
            if (b.velocity > terminalVelocity) {
                b.velocity = terminalVelocity
            }
            b.y += b.velocity
            // Spin bird descending quickly
            b.rotation = (b.rotation + 18f).coerceIn(-30f, 90f)

            if (b.y + b.radius >= groundY) {
                b.y = groundY - b.radius
                _gameState.value = GameState.GAME_OVER
                audio.playGameOver()
            }
            _bird.value = b
        }

        // 4. Update background drifting aesthetics (Parallax star/cloud travel)
        tickBackgroundElements()

        // 5. Physics tick for floating FX particle objects
        tickParticles()
    }

    private fun tickPipeSpawnAnScroll() {
        val difficultyConfig = _difficulty.value
        val list = _pipes.value.toMutableList()
        val b = _bird.value

        // Spawn pipes if gap distance matches
        var rightmostX = 0f
        if (list.isNotEmpty()) {
            rightmostX = list.maxOf { it.x }
        }

        if (list.isEmpty() || rightmostX < (canvasWidth - difficultyConfig.spawnInterval)) {
            // Randomize pipe vertical placement
            val minHeight = 100f
            val maxHeight = groundY - difficultyConfig.gap - 100f
            val topH = minHeight + Random.nextFloat() * (maxHeight - minHeight)
            val botH = topH + difficultyConfig.gap
            
            // 20% moving pipe chance in Hard mode to add insane difficulty!
            val dynamicPipe = difficultyConfig == GameDifficulty.HARD && Random.nextFloat() < 0.35f

            list.add(
                Pipe(
                    id = pipeIdCounter++,
                    x = canvasWidth,
                    topHeight = topH,
                    bottomHeight = botH,
                    passed = false,
                    isMoving = dynamicPipe,
                    direction = if (Random.nextBoolean()) 1f else -1f,
                    speed = Random.nextFloat() * 1.5f + 1.0f,
                    minTopHeight = 60f,
                    maxTopHeight = groundY - difficultyConfig.gap - 60f
                )
            )
        }

        // Scroll columns and handle dynamic oscillating pipes
        val iterator = list.iterator()
        val updatedList = mutableListOf<Pipe>()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x -= difficultyConfig.speed

            // Perform vertical column movement (only for dynamic oscillating pipes)
            if (p.isMoving) {
                p.topHeight += p.direction * p.speed
                if (p.topHeight < p.minTopHeight) {
                    p.topHeight = p.minTopHeight
                    p.direction = 1f
                } else if (p.topHeight > p.maxTopHeight) {
                    p.topHeight = p.maxTopHeight
                    p.direction = -1f
                }
                p.bottomHeight = p.topHeight + difficultyConfig.gap
            }

            // Check if scoring
            if (!p.passed && (birdX > p.x + p.width)) {
                p.passed = true
                _currentScore.value += 1
                audio.playScore()
                spawnScoreParticles(p.x + p.width / 2, b.y)
            }

            // Screen deletion check
            if (p.x + p.width > -50f) {
                updatedList.add(p)
            }

            // Hit detection inside columns
            val birdLeft = birdX - b.radius
            val birdRight = birdX + b.radius
            val birdTop = b.y - b.radius
            val birdBottom = b.y + b.radius

            val pipeLeft = p.x
            val pipeRight = p.x + p.width

            // Check overlapping AABB / Circle bounding box overlap with pipes
            if (birdRight >= pipeLeft && birdLeft <= pipeRight) {
                // Inside pipe horizontal width, check top and bottom pipe hit state
                if (birdTop <= p.topHeight || birdBottom >= p.bottomHeight) {
                    triggerBirdCollision(b.y)
                }
            }
        }
        _pipes.value = updatedList
    }

    private fun triggerBirdCollision(crashY: Float) {
        _gameState.value = GameState.COLLIDED
        audio.playHit()
        spawnCrashParticles(birdX, crashY)
    }

    private fun tickBackgroundElements() {
        val list = _backgroundElements.value.toMutableList()
        val scrollSpeedFactor = when (_gameState.value) {
            GameState.PLAYING -> 1.0f
            GameState.COLLIDED, GameState.GAME_OVER -> 0.05f
            GameState.MENU -> 0.25f
        }

        for (el in list) {
            el.x -= el.speed * scrollSpeedFactor
            if (el.x + el.size < -100f) {
                el.x = canvasWidth + 50f
                el.y = Random.nextFloat() * 450f + 30f
            }
        }
        _backgroundElements.value = list
    }

    private fun tickParticles() {
        val list = _particles.value.toMutableList()
        if (list.isEmpty()) return

        val iterator = list.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx
            p.y += p.vy
            
            // Soft drift decay
            if (p.shape == ParticleShape.BUBBLE) {
                p.vy -= 0.05f // Bubbles float up gently!
                p.vx += kotlin.math.sin(tickCount * 0.2f) * 0.05f
            } else {
                p.vy += 0.08f // Soft falling gravity for feathers & sparks
            }

            p.life++
            p.alpha = (1.0f - p.life.toFloat() / p.maxLife).coerceIn(0f, 1f)

            if (p.life >= p.maxLife) {
                iterator.remove()
            }
        }
        _particles.value = list
    }

    // --- Juice Particle Spawners ---

    private fun spawnFlapParticles(x: Float, y: Float) {
        val list = _particles.value.toMutableList()
        val theme = _activeTheme.value
        val shapeType = if (theme == SkinTheme.CORAL_REEF) ParticleShape.BUBBLE else ParticleShape.FEATHER
        
        repeat(4) {
            list.add(
                GameParticle(
                    x = x - 5f,
                    y = y + 10f,
                    vx = -Random.nextFloat() * 3.5f - 1.2f, // Shoot backwards
                    vy = Random.nextFloat() * 2.2f + 0.8f,  // Soft downwards flutter
                    color = theme.particleColors.random(),
                    size = Random.nextFloat() * 10f + 6f,
                    shape = shapeType,
                    maxLife = 12 + Random.nextInt(10)
                )
            )
        }
        
        // Cap max active graphics to verify high frame fidelity on older devices
        if (list.size > 140) {
            _particles.value = list.takeLast(100)
        } else {
            _particles.value = list
        }
    }

    private fun spawnScoreParticles(x: Float, y: Float) {
        val list = _particles.value.toMutableList()
        val theme = _activeTheme.value
        
        repeat(16) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 5.5f + 2.5f
            list.add(
                GameParticle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed - 1.5f, // rising float
                    color = theme.particleColors.random(),
                    size = Random.nextFloat() * 11f + 7f,
                    shape = ParticleShape.STAR,
                    maxLife = 20 + Random.nextInt(12)
                )
            )
        }
        _particles.value = list.takeLast(140)
    }

    private fun spawnCrashParticles(x: Float, y: Float) {
        val list = _particles.value.toMutableList()
        val colors = listOf(Color(0xFFFF5252), Color(0xFFFFEB3B), Color(0xFFFF9800), Color(0xFFFFFFFF))
        
        repeat(35) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 8f + 2.2f
            list.add(
                GameParticle(
                    x = x,
                    y = y,
                    vx = kotlin.math.cos(angle) * speed,
                    vy = kotlin.math.sin(angle) * speed,
                    color = colors.random(),
                    size = Random.nextFloat() * 12f + 5f,
                    shape = ParticleShape.CIRCLE,
                    maxLife = 30 + Random.nextInt(18)
                )
            )
        }
        _particles.value = list.takeLast(140)
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
    }
}
