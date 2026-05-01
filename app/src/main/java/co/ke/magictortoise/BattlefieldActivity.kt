package co.ke.magictortoise

import android.animation.ObjectAnimator
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

// 1. DATA CLASS MUST BE HERE (At the top, outside the Activity class)
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
    private var roomType: String = "p2p" 
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
        gameType = intent.getStringExtra("GAME_TYPE") ?: "Math Blitz"
        isCreator = intent.getBooleanExtra("IS_CREATOR", false)
        roomType = intent.getStringExtra("ROOM_TYPE") ?: "p2p"

        initUI()
        fetchDataAndSync()
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
        
        pbMyProgress.max = 100 
        pbOpponentProgress.max = 100
        
        if (roomType == "tournament") {
            tvOpponentScoreLabel.text = "TOP SCORE: 0"
        }
    }

    private fun fetchDataAndSync() {
        val id = roomId ?: return
        val nodePath = when(roomType) {
            "sync" -> "sync_active/$id"
            "tournament" -> "tournaments/active"
            else -> "p2p_lobby/$id"
        }
        
        val roomRef = db.child(nodePath)

        roomRef.child("stake").get().addOnSuccessListener {
            stakeAmount = it.getValue(Double::class.java) ?: 0.0
        }

        val opponentKey = if (roomType == "tournament") "topScore" 
                         else if (isCreator) "joinerScore" else "creatorScore"
        
        roomRef.child(opponentKey).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isGameOver) return
                opponentScore = snapshot.getValue(Int::class.java) ?: 0
                pbOpponentProgress.progress = opponentScore
                tvOpponentScoreLabel.text = if(roomType == "tournament") "TOP SCORE: $opponentScore" else "OPPONENT: $opponentScore"
                
                if (roomType != "tournament" && opponentScore >= 100) endGame()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun onPointScored() {
        if (isGameOver) return
        myScore++
        pbMyProgress.progress = myScore
        tvMyScoreLabel.text = "YOU: $myScore"
        
        val id = roomId ?: return
        val myKey = if (isCreator) "creatorScore" else "joinerScore"
        
        when(roomType) {
            "sync" -> db.child("sync_active").child(id).child(myKey).setValue(myScore)
            "tournament" -> db.child("tournaments").child("active").child("scores").child(auth.uid!!).setValue(myScore)
            else -> db.child("p2p_lobby").child(id).child(if(isCreator) "player1_score" else "player2_score").setValue(myScore)
        }

        if (myScore >= 100) endGame()
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
        gameStage.removeAllViews()
        val inflater = layoutInflater
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
            "Trivia Duel", "Tournament" -> {
                val v = inflater.inflate(R.layout.game_trivia_duel, gameStage, false)
                gameStage.addView(v)
                loadTriviaFromAssets()
                showNextTriviaQuestion(v)
            }
        }
    }

    // 2. RESTORED MISSING FUNCTION: setupTapLogic
    private fun setupTapLogic(view: View) {
        val ivTortoise = view.findViewById<ImageView>(R.id.ivTapTortoise)
        ivTortoise.setOnClickListener {
            if (!isGameOver) {
                onPointScored()
                val maxX = gameStage.width - ivTortoise.width
                val maxY = gameStage.height - ivTortoise.height
                if (maxX > 0 && maxY > 0) {
                    ivTortoise.x = Random.nextInt(maxX).toFloat()
                    ivTortoise.y = Random.nextInt(maxY).toFloat()
                }
                it.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
                    it.scaleX = 1.0f
                    it.scaleY = 1.0f
                }.start()
            }
        }
    }

    private fun setupMathLogic(view: View) {
        val tvQuestion = view.findViewById<TextView>(R.id.tvMathProblem)
        val etAnswer = view.findViewById<EditText>(R.id.etMathAnswer)
        val btnSubmit = view.findViewById<Button>(R.id.btnSubmitAnswer)
        
        etAnswer.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && etAnswer.text.toString() == "?") etAnswer.setText("")
        }

        fun generateProblem() {
            val a = Random.nextInt(1, 20)
            val b = Random.nextInt(1, 10)
            val op = listOf("+", "-").random()
            mathAnswer = if (op == "+") a + b else a - b
            tvQuestion.text = "$a $op $b = ?"
            etAnswer.text.clear()
        }
        
        generateProblem()
        btnSubmit.setOnClickListener {
            if (etAnswer.text.toString().toIntOrNull() == mathAnswer) {
                onPointScored()
                generateProblem()
            } else {
                Toast.makeText(this, "Incorrect!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadTriviaFromAssets() {
        try {
            val json = assets.open("questions.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            triviaList.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val opts = obj.getJSONArray("options")
                val optList = mutableListOf<String>()
                for (j in 0 until opts.length()) optList.add(opts.getString(j))
                triviaList.add(Question(obj.getInt("id"), obj.getString("question"), optList, obj.getString("answer")))
            }
            triviaList.shuffle()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun showNextTriviaQuestion(view: View) {
        if (isGameOver || triviaList.isEmpty()) return
        val q = triviaList[currentQuestionIndex % triviaList.size]
        
        view.findViewById<TextView>(R.id.tvTournamentJackpot).text = q.question
        
        val btnIds = listOf(R.id.btnOpt1, R.id.btnOpt2, R.id.btnOpt3, R.id.btnOpt4)
        btnIds.forEachIndexed { i, id ->
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

    private fun endGame() {
        if (isGameOver) return
        isGameOver = true
        gameStage.visibility = View.GONE
        tvStatus.visibility = View.VISIBLE
        
        if (roomType == "tournament") {
            tvStatus.text = "TOURNAMENT OVER!\nSCORE: $myScore"
        } else {
            when {
                myScore > opponentScore -> {
                    tvStatus.text = "VICTORY! 🏆"
                    payWinner(auth.uid!!)
                }
                myScore < opponentScore -> tvStatus.text = "DEFEAT! 🐢"
                else -> {
                    tvStatus.text = "DRAW! 🤝"
                    payWinner(auth.uid!!, true)
                }
            }
        }
    }

    private fun payWinner(uid: String, isDraw: Boolean = false) {
        val prize = if (isDraw) stakeAmount else (stakeAmount * 2) * 0.90
        db.child("users").child(uid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                data.value = (data.getValue(Double::class.java) ?: 0.0) + prize
                return Transaction.success(data)
            }
            override fun onComplete(e: DatabaseError?, c: Boolean, s: DataSnapshot?) {
                if (c && isCreator) cleanup()
            }
        })
    }

    private fun cleanup() {
        roomId?.let { 
            tvStatus.postDelayed({ db.child("sync_active").child(it).removeValue() }, 5000)
        }
    }
}
