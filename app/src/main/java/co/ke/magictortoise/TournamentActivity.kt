package co.ke.magictortoise

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONArray

class TournamentActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private val triviaList = mutableListOf<Question>()
    
    private var currentIndex = 0
    private var score = 0
    private var isGameOver = false
    private var questionTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen Arena Mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()

        setContentView(R.layout.layout_tournament_overlay)

        findViewById<View>(R.id.btnMinimize)?.visibility = View.GONE
        findViewById<View>(R.id.btnCloseTournament)?.visibility = View.GONE

        // Arena Logic: Wait for participants
        checkLobbyAndStart()
    }

    private fun checkLobbyAndStart() {
        val tvStatus = findViewById<TextView>(R.id.tvLiveQuestion)
        val tvMain = findViewById<TextView>(R.id.tvTournamentJackpot)
        val btnAction = findViewById<Button>(R.id.btnJoinTournamentFinal)

        tvMain.text = "SYNCING WITH ARENA..."
        btnAction.visibility = View.GONE

        db.child("tournaments").child("active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (isGameOver) return
                
                val playerCount = snapshot.child("players").childrenCount.toInt()
                // Game starts only when 2 or more users are in
                if (playerCount >= 2) {
                    tvStatus.text = "CHALLENGERS FOUND!"
                    loadTriviaFromAssets()
                    db.child("tournaments").child("active").removeEventListener(this)
                } else {
                    tvStatus.text = "WAITING FOR CHALLENGERS..."
                    tvMain.text = "Participants: $playerCount/2\n\nArena starts when others join."
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadTriviaFromAssets() {
        try {
            val jsonString = assets.open("questions.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val opts = obj.getJSONArray("options")
                val optionsList = mutableListOf<String>()
                for (j in 0 until opts.length()) optionsList.add(opts.getString(j))
                
                triviaList.add(Question(
                    obj.getInt("id"), 
                    obj.getString("question"), 
                    optionsList, 
                    obj.getString("answer")
                ))
            }
            triviaList.shuffle()
            displayQuestion() 
        } catch (e: Exception) {
            Toast.makeText(this, "Arena Error: Asset Path Mismatch", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun displayQuestion() {
        if (isGameOver || currentIndex >= 20 || currentIndex >= triviaList.size) {
            submitScore()
            return
        }

        val question = triviaList[currentIndex]
        val tvStatus = findViewById<TextView>(R.id.tvLiveQuestion)
        val tvQuestion = findViewById<TextView>(R.id.tvTournamentJackpot)
        val btnAction = findViewById<Button>(R.id.btnJoinTournamentFinal)

        tvQuestion.text = question.question
        btnAction.text = "SUBMIT ANSWER"
        btnAction.visibility = View.VISIBLE

        // 10s Anti-Cheat Timer
        questionTimer?.cancel()
        questionTimer = object : CountDownTimer(10000, 100) {
            override fun onTick(ms: Long) {
                val secondsLeft = ms / 1000
                // TYPO FIXED HERE: secondsLeft instead of secondsLefts
                tvStatus.text = "ARENA PROGRESS: ${currentIndex + 1}/20 | TIME: $secondsLeft"
                
                if (secondsLeft <= 3) {
                    tvStatus.setTextColor(Color.RED) 
                } else {
                    tvStatus.setTextColor(Color.YELLOW)
                }
            }

            override fun onFinish() {
                nextQuestion()
            }
        }.start()

        btnAction.setOnClickListener {
            score += 10 
            nextQuestion()
        }
    }

    private fun nextQuestion() {
        currentIndex++
        displayQuestion()
    }

    private fun submitScore() {
        questionTimer?.cancel()
        isGameOver = true
        val uid = auth.currentUser?.uid ?: return
        
        db.child("tournaments").child("active").child("scores").child(uid).setValue(score)
            .addOnSuccessListener {
                Toast.makeText(this, "Arena Finished! Final Score: $score", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        questionTimer?.cancel()
    }
}
