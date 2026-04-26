package co.ke.magictortoise

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONArray
import kotlin.random.Random

// Data class to hold our 500 questions
data class Question(
    val id: Int,
    val question: String,
    val options: List<String>,
    val answer: String
)

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

    // Trivia Variables
    private var triviaList = mutableListOf<Question>()
    private var currentQuestionIndex = 0

    // UI Elements
    private lateinit var tvTimer: TextView
    private lateinit var pbMyProgress: ProgressBar
    private lateinit var pbOpponentProgress: ProgressBar
    private lateinit var gameStage: FrameLayout
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. STICKY FULL SCREEN MODE
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
        
        pbMyProgress.max = 100 
        pbOpponentProgress.max = 100
    }

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
                val triviaView = inflater.inflate(R.layout.game_trivia_duel, gameStage, false)
                gameStage.addView(triviaView)
                loadTriviaFromAssets()
                showNextTriviaQuestion(triviaView)
            }
        }
    }

    // --- GAME LOGIC: TAP TORTOISE ---
    private fun setupTapLogic(view: View) {
        val ivTortoise = view.findViewById<ImageView>(R.id.ivTapTortoise)
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.05f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.05f)
        ObjectAnimator.ofPropertyValuesHolder(ivTortoise, scaleX, scaleY).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        ivTortoise.setOnClickListener {
            if (!isGameOver) {
                it.animate().scaleX(1.1f).scaleY(1.1f).setDuration(50).withEndAction {
                    it.animate().scaleX(1.0f).scaleY(1.0f).setDuration(50).start()
                }.start()
                it.alpha = 0.6f
                it.postDelayed({ it.alpha = 1.0f }, 50)
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
            val a = Random.nextInt(5, 30)
            val b = Random.nextInt(5, 30)
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
                Toast.makeText(this, "Try again!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // --- GAME LOGIC: TRIVIA DUEL ---
    private fun loadTriviaFromAssets() {
        try {
            val jsonString = assets.open("questions.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val opts = obj.getJSONArray("options")
                val optionsList = mutableListOf<String>()
                for (j in 0 until opts.length()) optionsList.add(opts.getString(j))
                triviaList.add(Question(obj.getInt("id"), obj.getString("question"), optionsList, obj.getString("answer")))
            }
            triviaList.shuffle()
        } catch (e: Exception) {
            Toast.makeText(this, "Error loading questions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNextTriviaQuestion(view: View) {
        if (isGameOver || triviaList.isEmpty()) return
        if (currentQuestionIndex >= triviaList.size) currentQuestionIndex = 0
        
        val currentQ = triviaList[currentQuestionIndex]
        view.findViewById<TextView>(R.id.tvTriviaQuestion).text = currentQ.question
        
        val buttons = listOf<Button>(
            view.findViewById(R.id.btnOpt1), view.findViewById(R.id.btnOpt2),
            view.findViewById(R.id.btnOpt3), view.findViewById(R.id.btnOpt4)
        )

        buttons.forEachIndexed { index, button ->
            button.text = currentQ.options[index]
            button.setOnClickListener {
                if (button.text == currentQ.answer) {
                    onPointScored()
                    currentQuestionIndex++
                    showNextTriviaQuestion(view)
                } else {
                    Toast.makeText(this, "Wrong!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onPointScored() {
        if (isGameOver) return
        myScore++
        pbMyProgress.progress = myScore
        findViewById<TextView>(R.id.tvMyName).text = "YOU: $myScore"
        val myKey = if (isCreator) "player1_score" else "player2_score"
        roomId?.let { id -> db.child("p2p_lobby").child(id).child(myKey).setValue(myScore) }
    }

    private fun endGame() {
        isGameOver = true
        gameStage.visibility = View.GONE
        tvStatus.visibility = View.VISIBLE
        when {
            myScore > opponentScore -> {
                tvStatus.text = "VICTORY! 🏆\nYOU WON THE STAKE"
                tvStatus.setTextColor(Color.GREEN)
            }
            myScore < opponentScore -> {
                tvStatus.text = "DEFEAT! 🐢\nOPPONENT WAS FASTER"
                tvStatus.setTextColor(Color.RED)
            }
            else -> {
                tvStatus.text = "DRAW! 🤝\nSTAKES RETURNED"
                tvStatus.setTextColor(Color.YELLOW)
            }
        }
    }
}
