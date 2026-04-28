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

// Standard Data class for Trivia
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
    private var stakeAmount = 0.0

    private var triviaList = mutableListOf<Question>()
    private var currentQuestionIndex = 0

    private lateinit var tvTimer: TextView
    private lateinit var pbMyProgress: ProgressBar
    private lateinit var pbOpponentProgress: ProgressBar
    private lateinit var gameStage: FrameLayout
    private lateinit var tvStatus: TextView
    private lateinit var tvMyScoreLabel: TextView
    private lateinit var tvOpponentScoreLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen immersive mode
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
        fetchStakeAndSync()
        startCountdown()
        loadGameLayout()
    }

    private fun initUI() {
        tvTimer = findViewById(R.id.tvTimer)
        pbMyProgress = findViewById(R.id.pbMyProgress)
        pbOpponentProgress = findViewById(R.id.pbOpponentProgress)
        gameStage = findViewById(R.id.gameStage)
        tvStatus = findViewById(R.id.tvWaitingMessage)
        tvMyScoreLabel = findViewById(R.id.tvMyName)
        tvOpponentScoreLabel = findViewById(R.id.tvOpponentName)
        
        // Progress bars represent the 100-point target for a quick match
        pbMyProgress.max = 100 
        pbOpponentProgress.max = 100
    }

    private fun fetchStakeAndSync() {
        roomId?.let { id ->
            // Use 'active_battles' or similar if you move them out of 'p2p_lobby' after start
            val lobbyRef = db.child("p2p_lobby").child(id)
            
            lobbyRef.child("stake").get().addOnSuccessListener {
                stakeAmount = it.getValue(Double::class.java) ?: 0.0
            }

            val opponentKey = if (isCreator) "player2_score" else "player1_score"
            lobbyRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (isGameOver || !snapshot.exists()) return
                    opponentScore = snapshot.child(opponentKey).getValue(Int::class.java) ?: 0
                    pbOpponentProgress.progress = opponentScore
                    tvOpponentScoreLabel.text = "OPPONENT: $opponentScore"
                    
                    // Instant win if opponent hits 100
                    if (opponentScore >= 100) endGame()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun startCountdown() {
        object : CountDownTimer(60000, 1000) { // 60 Second Blitz
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
        gameStage.removeAllViews()
        when (gameType) {
            "Math Blitz" -> {
                val v = inflater.inflate(R.layout.game_math_blitz, gameStage, false)
                gameStage.addView(v)
                setupMathLogic(v)
            }
            "Tap Tortoise" -> {
                val v = inflater.inflate(R.layout.game_tap_tortoise, gameStage, false)
                gameStage.addView(v) 
                setupTapLogic(v)
            }
            "Trivia Duel" -> {
                val v = inflater.inflate(R.layout.game_trivia_duel, gameStage, false)
                gameStage.addView(v)
                loadTriviaFromAssets()
                showNextTriviaQuestion(v)
            }
        }
    }

    // UPDATED: Added Teleportation to Tap Tortoise
    private fun setupTapLogic(view: View) {
        val ivTortoise = view.findViewById<ImageView>(R.id.ivTapTortoise)
        
        ivTortoise.setOnClickListener {
            if (!isGameOver) {
                onPointScored()
                
                // Teleport logic: Move the tortoise to a random spot in the frame
                val maxX = gameStage.width - ivTortoise.width
                val maxY = gameStage.height - ivTortoise.height
                
                if (maxX > 0 && maxY > 0) {
                    ivTortoise.x = Random.nextInt(maxX).toFloat()
                    ivTortoise.y = Random.nextInt(maxY).toFloat()
                }

                // Pop animation
                it.scaleX = 0.8f
                it.scaleY = 0.8f
                it.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
                    it.scaleX = 1.0f
                    it.scaleY = 1.0f
                }.start()
            }
        }
    }

    // UPDATED: Multi-operator Math Logic
    private fun setupMathLogic(view: View) {
        val tvQuestion = view.findViewById<TextView>(R.id.tvMathProblem)
        val etAnswer = view.findViewById<EditText>(R.id.etMathAnswer)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitAnswer)
        
        fun generateProblem() {
            val a = Random.nextInt(1, 20)
            val b = Random.nextInt(1, 10)
            val operator = listOf("+", "-", "*").random()
            
            mathAnswer = when (operator) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                else -> a + b
            }
            
            tvQuestion.text = "$a $operator $b = ?"
            etAnswer.text.clear()
        }
        
        generateProblem()
        btnSubmit.setOnClickListener {
            val input = etAnswer.text.toString().toIntOrNull()
            if (input == mathAnswer) {
                onPointScored()
                generateProblem()
            } else {
                Toast.makeText(this, "Wrong!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTriviaFromAssets() {
        try {
            val json = assets.open("questions.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val optsArr = obj.getJSONArray("options")
                val optsList = mutableListOf<String>()
                for (j in 0 until optsArr.length()) optsList.add(optsArr.getString(j))
                triviaList.add(Question(obj.getInt("id"), obj.getString("question"), optsList, obj.getString("answer")))
            }
            triviaList.shuffle()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showNextTriviaQuestion(view: View) {
        if (isGameOver || triviaList.isEmpty()) return
        val q = triviaList[currentQuestionIndex % triviaList.size]
        view.findViewById<TextView>(R.id.tvTriviaQuestion).text = q.question
        
        val ids = listOf(R.id.btnOpt1, R.id.btnOpt2, R.id.btnOpt3, R.id.btnOpt4)
        ids.forEachIndexed { i, id ->
            val btn = view.findViewById<Button>(id)
            btn.text = q.options[i]
            btn.setOnClickListener {
                if (btn.text == q.answer) {
                    onPointScored()
                    currentQuestionIndex++
                    showNextTriviaQuestion(view)
                } else {
                    Toast.makeText(this, "Incorrect", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun onPointScored() {
        if (isGameOver) return
        myScore++
        pbMyProgress.progress = myScore
        tvMyScoreLabel.text = "YOU: $myScore"
        
        val key = if (isCreator) "player1_score" else "player2_score"
        roomId?.let { db.child("p2p_lobby").child(it).child(key).setValue(myScore) }

        if (myScore >= 100) endGame()
    }

    private fun endGame() {
        if (isGameOver) return
        isGameOver = true
        gameStage.visibility = View.GONE
        tvStatus.visibility = View.VISIBLE
        
        when {
            myScore > opponentScore -> {
                tvStatus.text = "VICTORY! 🏆\nPRIZE ADDED"
                tvStatus.setTextColor(Color.GREEN)
                payWinner(auth.currentUser!!.uid)
            }
            myScore < opponentScore -> {
                tvStatus.text = "DEFEAT! 🐢\nTRY AGAIN"
                tvStatus.setTextColor(Color.RED)
                // Opponent will clean up
            }
            else -> {
                tvStatus.text = "DRAW! 🤝\nREFUNDED"
                tvStatus.setTextColor(Color.YELLOW)
                payWinner(auth.currentUser!!.uid, isDraw = true)
            }
        }
    }

    private fun payWinner(uid: String, isDraw: Boolean = false) {
        // Prize: (Stake * 2) - 25% Admin Fee. If draw, just refund stake.
        val prize = if (isDraw) stakeAmount else (stakeAmount * 2) * 0.75
        
        db.child("users").child(uid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val current = data.getValue(Double::class.java) ?: 0.0
                data.value = current + prize
                return Transaction.success(data)
            }
            override fun onComplete(err: DatabaseError?, comm: Boolean, snap: DataSnapshot?) {
                if (comm && isCreator) cleanupRoom()
            }
        })
    }

    private fun cleanupRoom() {
        roomId?.let { 
            // Give a 5 second delay so opponent can see their result screen before node vanishes
            tvStatus.postDelayed({
                db.child("p2p_lobby").child(it).removeValue() 
            }, 5000)
        }
    }
}
