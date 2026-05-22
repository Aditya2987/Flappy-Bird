package com.example.firebase

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.HighScore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private var isInitialized = false

    private val _onlineScores = MutableStateFlow<List<HighScore>>(emptyList())
    val onlineScores: StateFlow<List<HighScore>> = _onlineScores

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _connectionStatusMessage = MutableStateFlow("")
    val connectionStatusMessage: StateFlow<String> = _connectionStatusMessage

    fun initialize(context: Context) {
        if (isInitialized) return

        var apiKey = try { BuildConfig.FIREBASE_API_KEY } catch (e: Exception) { "" }
        var projectId = try { BuildConfig.FIREBASE_PROJECT_ID } catch (e: Exception) { "" }
        var appId = try { BuildConfig.FIREBASE_APP_ID } catch (e: Exception) { "" }

        // Fallback to the user's provided safe Cloud Firebase configurations
        if (apiKey.isEmpty() || apiKey == "YOUR_FIREBASE_API_KEY") {
            apiKey = "AIzaSyCMNc4mwALlEuHmNqSNIkpeSujRFWK8h-g"
        }
        if (projectId.isEmpty() || projectId == "YOUR_FIREBASE_PROJECT_ID") {
            projectId = "flappy-bird-7ba9e"
        }
        if (appId.isEmpty() || appId == "YOUR_FIREBASE_APP_ID") {
            appId = "1:747742335472:android:d7c974d961a876b0544d35"
        }

        Log.d(TAG, "Initializing Firebase manually. Project: $projectId, AppId: $appId")

        if (apiKey.isEmpty() || projectId.isEmpty() || appId.isEmpty()) {
            _connectionStatusMessage.value = "Offline Mode (Keys not configured)"
            Log.d(TAG, "Firebase credentials not provided. Running in offline/local-only mode.")
            return
        }

        try {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setApplicationId(appId)
                .build()

            // Safe double init guard just in case GMS or another system initialized it
            val apps = FirebaseApp.getApps(context)
            if (apps.isEmpty()) {
                FirebaseApp.initializeApp(context, options)
            }
            isInitialized = true
            _connectionStatusMessage.value = "Connected to Firebase datastore"
            Log.d(TAG, "Firebase successfully initialized manually!")
            
            // Initial fetch of leaderboard
            fetchLeaderboard()
        } catch (e: Exception) {
            _connectionStatusMessage.value = "Connection error: ${e.localizedMessage}"
            Log.e(TAG, "Failed programmatic Firebase initialize", e)
        }
    }

    fun isConnected(): Boolean {
        return isInitialized
    }

    fun uploadHighScore(playerName: String, score: Int, gameMode: String) {
        if (!isInitialized) {
            Log.d(TAG, "Cannot upload high score: Firebase not initialized.")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val scoreData = hashMapOf(
            "playerName" to playerName,
            "score" to score,
            "gameMode" to gameMode,
            "date" to System.currentTimeMillis()
        )

        db.collection("leaderboard")
            .add(scoreData)
            .addOnSuccessListener { documentReference ->
                Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                // Refresh top scores after a successful save
                fetchLeaderboard()
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document to Firebase datastore", e)
            }
    }

    fun fetchLeaderboard() {
        if (!isInitialized) return

        _isSyncing.value = true
        val db = FirebaseFirestore.getInstance()
        db.collection("leaderboard")
            .orderBy("score", Query.Direction.DESCENDING)
            .orderBy("date", Query.Direction.DESCENDING)
            .limit(30)
            .get()
            .addOnSuccessListener { result ->
                val list = mutableListOf<HighScore>()
                for ((index, document) in result.withIndex()) {
                    val playerName = document.getString("playerName") ?: "PILOT"
                    val score = document.getLong("score")?.toInt() ?: 0
                    val gameMode = document.getString("gameMode") ?: "Normal"
                    val date = document.getLong("date") ?: System.currentTimeMillis()
                    
                    // We generate an incremental id for UI use
                    list.add(
                        HighScore(
                            id = index,
                            playerName = playerName,
                            score = score,
                            gameMode = gameMode,
                            date = date
                        )
                    )
                }
                _onlineScores.value = list
                _isSyncing.value = false
                Log.d(TAG, "Successfully fetched ${list.size} live global leaderboards")
            }
            .addOnFailureListener { e ->
                _isSyncing.value = false
                Log.e(TAG, "Error fetching live global leaderboard", e)
            }
    }
}
