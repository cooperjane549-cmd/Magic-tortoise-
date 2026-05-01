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

// Universal Question Model
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
    private var triviaList = mutableListOf<Question>()
    private var currentQuestionIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battlefield)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        db = FirebaseDatabase.getInstance().reference

        // Retrieve the "Flags" sent from Market or Dashboard
        roomId = intent.getStringExtra("ROOM_ID")
        gameType = intent.getStringExtra("GAME_TYPE") ?: "Math Blitz"
        isCreator = intent.getBooleanExtra("IS_CREATOR", false)
        roomType = intent.getStringExtra("ROOM_TYPE") ?: "p2p"

        setupGame()
        syncScores()
    }

    private fun setupGame() {
        val container = findViewById<FrameLayout>(R.id.gameStage)
        container.removeAllViews()

        when (gameType) {
            "Tournament", "Trivia Duel" -> {
                layoutInflater.inflate(R.layout.game_trivia_duel, container, true)
                loadQuestions()
                showQuestion()
            }
            "Math Blitz" -> {
                layoutInflater.inflate(R.layout.game_math_blitz, container, true)
                setupMath()
            }
            "Tap Tortoise" -> {
                layoutInflater.inflate(R.layout.game_tap_tortoise, container, true)
                setupTap()
            }
        }
        
        startTimer()
    }

    private fun loadQuestions() {
        try {
            val json = assets.open("questions.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val opts = obj.getJSONArray("options")
                val optList = mutableListOf<String>()
                for (j in 0 until opts.length()) optList.add(opts.getString(j))
                
                triviaList.add(Question(i, obj.getString("question"), optList, obj.getString("answer")))
            }
            triviaList.shuffle()
        } catch (e: Exception) {
            // Fallback so user never sees "Option A"
            triviaList.add(Question(0, "What is the capital of Kenya?", listOf("Nairobi", "Mombasa", "Kisumu", "Nakuru"), "Nairobi"))
        }
    }

    private fun showQuestion() {
        if (isGameOver || triviaList.isEmpty()) return
        val q = triviaList[currentQuestionIndex % triviaList.size]
        
        findViewById<TextView>(R.id.tvTournamentJackpot).text = q.question
        
        val buttons = listOf(R.id.btnOpt1, R.id.btnOpt2, R.id.btnOpt3, R.id.btnOpt4)
        buttons.forEachIndexed { i, id ->
            val btn = findViewById<Button>(id)
            btn.text = q.options[i]
            btn.setOnClickListener {
                if (btn.text == q.answer) {
                    updateScore()
                    currentQuestionIndex++
                    showQuestion()
                } else {
                    Toast.makeText(this, "Wrong!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateScore() {
        myScore += 5
        findViewById<ProgressBar>(R.id.pbMyProgress).progress = myScore
        findViewById<TextView>(R.id.tvMyName).text = "YOU: $myScore"

        val uid = auth.currentUser?.uid ?: return
        val scoreRef = when(roomType) {
            "tournament" -> db.child("tournaments").child("active").child("scores").child(uid)
            "sync" -> db.child("sync_active").child(roomId!!).child(if(isCreator) "creatorScore" else "joinerScore")
            else -> db.child("p2p_lobby").child(roomId!!).child(if(isCreator) "player1_score" else "player2_score")
        }
        scoreRef.setValue(myScore)
    }

    private fun syncScores() {
        if (roomType == "tournament") {
            // In tournament, "Opponent" bar shows the current highest score in the room
            db.child("tournaments").child("active").child("scores").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var topScore = 0
                    snapshot.children.forEach { 
                        val s = it.getValue(Int::class.java) ?: 0
                        if (s > topScore) topScore = s
                    }
                    findViewById<ProgressBar>(R.id.pbOpponentProgress).progress = topScore
                    findViewById<TextView>(R.id.tvOpponentName).text = "TOP: $topScore"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } else {
            // In P2P/Sync, "Opponent" bar shows the other person
            val oppKey = if(isCreator) "joinerScore" else "creatorScore"
            db.child("sync_active").child(roomId!!).child(oppKey).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val s = snapshot.getValue(Int::class.java) ?: 0
                    findViewById<ProgressBar>(R.id.pbOpponentProgress).progress = s
                    findViewById<TextView>(R.id.tvOpponentName).text = "OPPONENT: $s"
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }

    private fun startTimer() {
        object : CountDownTimer(60000, 1000) {
            override fun onTick(ms: Long) {
                findViewById<TextView>(R.id.tvTimer).text = "00:${ms/1000}"
            }
            override fun onFinish() {
                isGameOver = true
                Toast.makeText(this@BattlefieldActivity, "Time Up!", Toast.LENGTH_LONG).show()
                finish()
            }
        }.start()
    }
    
    // Stub methods for Math/Tap to prevent crashes - you can add logic later
    private fun setupMath() {}
    private fun setupTap() {}
}
