package co.ke.magictortoise

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.random.Random

class BattlefieldActivity : AppCompatActivity() {

    private lateinit var db: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private var roomId: String? = null
    private var gameType: String? = null
    private var isCreator: Boolean = false

    private var myScore = 0
    private var opponentScore = 0
    private var isGameOver = false
    private var mathAnswer = 0

    // UI Elements
    private lateinit var tvTimer: TextView
    private lateinit var pbMyProgress: ProgressBar
    private lateinit var pbOpponentProgress: ProgressBar
    private lateinit var gameStage: FrameLayout
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. SET FULL SCREEN MODE
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()

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
        tvStatus = findViewById(R.id.tvWaitingMessage)
        
        // Max progress for 60 seconds of tapping/math
        pbMyProgress.max = 100 
        pbOpponentProgress.max = 100
    }

    // 2. SYNC LOGIC: Real-time Score Tracking
    private fun setupSync() {
        val opponentKey = if (isCreator) "player2_score" else "player1_score"

        roomId?.let { id ->
            db.child("p2p_lobby").child(id).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isGameOver) return
                    opponentScore = snapshot.child(opponentKey).getValue(Int::class.java) ?: 0
                    pbOpponentProgress.progress = opponentScore
                    findViewById<TextView>(R.id.tvOpponentName).text = "OPPONENT: $opponentScore"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    // 3. TIMER LOGIC: 60 Seconds
    private fun startCountdown() {
        object : CountDownTimer(60000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                tvTimer.text = String.format("00:%02d", seconds)
                if (seconds <= 10) tvTimer.setTextColor(Color.RED)
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                endGame()
            }
        }.start()
    }

    // 4. GAME LOADING: Load the sub-layouts
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
                gameStage.addView(tapView) 
                setupTapLogic(tapView)
            }
            "Trivia Duel" -> {
                // Future Trivia Logic
            }
        }
    }

    // --- GAME LOGIC: TAP TORTOISE ---
    private fun setupTapLogic(view: View) {
        val ivTortoise = view.findViewById<ImageView>(R.id.ivTapTortoise)
        ivTortoise.setOnClickListener {
            if (!isGameOver) {
                it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(50).withEndAction {
                    it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(50).start()
                }.start()
                onPointScored()
            }
        }
    }

    // --- GAME LOGIC: MATH BLITZ ---
    private fun setupMathLogic(view: View) {
        val tvQuestion = view.findViewById<TextView>(R.id.tvMathProblem)
        val etAnswer = view.findViewById<EditText>(R.id.etMathAnswer)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitAnswer)

        fun generateProblem() {
            val a = Random.nextInt(1, 20)
            val b = Random.nextInt(1, 20)
            mathAnswer = a + b
            tvQuestion.text = "$a + $b = ?"
            etAnswer.text.clear()
        }

        generateProblem()

        btnSubmit.setOnClickListener {
            val ans = etAnswer.text.toString().toIntOrNull()
            if (ans == mathAnswer) {
                onPointScored()
                generateProblem()
            } else {
                Toast.makeText(this, "Wrong!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun onPointScored() {
        if (isGameOver) return
        myScore++
        pbMyProgress.progress = myScore
        findViewById<TextView>(R.id.tvMyName).text = "YOU: $myScore"

        val myKey = if (isCreator) "player1_score" else "player2_score"
        roomId?.let { id ->
            db.child("p2p_lobby").child(id).child(myKey).setValue(myScore)
        }
    }

    private fun endGame() {
        isGameOver = true
        gameStage.visibility = View.GONE
        tvStatus.visibility = View.VISIBLE

        if (myScore > opponentScore) {
            tvStatus.text = "VICTORY! YOU WON KES"
            tvStatus.setTextColor(Color.GREEN)
        } else if (myScore < opponentScore) {
            tvStatus.text = "DEFEAT! BETTER LUCK NEXT TIME"
            tvStatus.setTextColor(Color.RED)
        } else {
            tvStatus.text = "DRAW! STAKES RETURNED"
            tvStatus.setTextColor(Color.YELLOW)
        }
        
        Toast.makeText(this, "Match Ended. Score: $myScore", Toast.LENGTH_LONG).show()
    }
}
