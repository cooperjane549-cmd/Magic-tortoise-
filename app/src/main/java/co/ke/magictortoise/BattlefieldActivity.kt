package co.ke.magictortoise

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class BattlefieldActivity : AppCompatActivity() {

    private lateinit var db: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var roomId: String? = null
    private var gameType: String? = null
    private var isCreator: Boolean = false

    private var myScore = 0
    private var opponentScore = 0
    private var isGameOver = false

    // UI Elements
    private lateinit var tvTimer: TextView
    private lateinit var pbMyProgress: ProgressBar
    private lateinit var pbOpponentProgress: ProgressBar
    private lateinit var gameStage: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battlefield)

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance().reference

        // Get match details passed from MarketFragment
        roomId = intent.getStringExtra("ROOM_ID")
        gameType = intent.getStringExtra("GAME_TYPE")
        isCreator = intent.getBooleanExtra("IS_CREATOR", false)

        initUI()
        setupSync()
        startCountdown()
        loadGameLayout()
    }

    private fun initUI() {
        tvTimer = findViewById(R.id.tvTimer)
        pbMyProgress = findViewById(R.id.pbMyProgress)
        pbOpponentProgress = findViewById(R.id.pbOpponentProgress)
        gameStage = findViewById(R.id.gameStage)
    }

    // 1. SYNC LOGIC: Listen for Opponent's score in real-time
    private fun setupSync() {
        val uid = auth.currentUser?.uid ?: return
        val opponentKey = if (isCreator) "player2_score" else "player1_score"
        val myKey = if (isCreator) "player1_score" else "player2_score"

        roomId?.let { id ->
            db.child("p2p_lobby").child(id).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isGameOver) return

                    // Update Opponent's Progress Bar
                    opponentScore = snapshot.child(opponentKey).getValue(Int::class.java) ?: 0
                    pbOpponentProgress.progress = opponentScore
                    findViewById<TextView>(R.id.tvOpponentName).text = "OPPONENT: $opponentScore"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // 2. TIMER LOGIC: The 60-second limit
    private fun startCountdown() {
        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = String.format("00:%02d", seconds)
                if (seconds <= 10) tvTimer.setTextColor(android.graphics.Color.RED)
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                endGame()
            }
        }.start()
    }

    // 3. GAME LOADING: Decide which furniture to put in the house
    private fun loadGameLayout() {
        val inflater = layoutInflater
        when (gameType) {
            "Math Blitz" -> {
                val mathView = inflater.inflate(R.layout.game_math_blitz, gameStage, false)
                gameStage.addView(mathView)
                setupMathLogic(mathView)
            }
            "Tap Tortoise" -> {
                val tapView = inflater.inflate(R.layout.game_tap_tortoise, gameStage, false)
                gameStage.addView(mathView) // Use tapView here
                setupTapLogic(tapView)
            }
            // Add Trivia logic similarly later
        }
    }

    // Example Math Increment
    private fun onPointScored() {
        if (isGameOver) return
        myScore++
        pbMyProgress.progress = myScore
        findViewById<TextView>(R.id.tvMyName).text = "YOU: $myScore"

        // Update Firebase so opponent sees your progress
        val myKey = if (isCreator) "player1_score" else "player2_score"
        roomId?.let { id ->
            db.child("p2p_lobby").child(id).child(myKey).setValue(myScore)
        }
    }

    private fun endGame() {
        isGameOver = true
        gameStage.visibility = View.GONE
        Toast.makeText(this, "Time Up! Final Score: $myScore", Toast.LENGTH_LONG).show()
        // Logic for checking winner and awarding stake goes here
    }
}
