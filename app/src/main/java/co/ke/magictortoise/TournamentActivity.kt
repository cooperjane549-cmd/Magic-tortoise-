package co.ke.magictortoise

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen setup
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()

        setContentView(R.layout.layout_tournament_overlay)

        // Adjust UI: Hide unnecessary buttons for Arena mode
        findViewById<View>(R.id.btnMinimize)?.visibility = View.GONE
        findViewById<View>(R.id.btnCloseTournament)?.visibility = View.GONE

        loadTriviaFromAssets()
        startTournamentTimer()
        displayQuestion()
    }

    private fun loadTriviaFromAssets() {
        try {
            val jsonString = assets.open("questions.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val opts = obj.getJSONArray("options")
                val optionsList = mutableListOf<String>()
                for (j in 0 until opts.length()) {
                    optionsList.add(opts.getString(j))
                }
                
                triviaList.add(Question(
                    obj.getInt("id"),
                    obj.getString("question"),
                    optionsList,
                    obj.getString("answer")
                ))
            }
            triviaList.shuffle()
        } catch (e: Exception) {
            Toast.makeText(this, "Questions not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun displayQuestion() {
        if (isGameOver || triviaList.isEmpty()) return

        val question = triviaList[currentIndex % triviaList.size]
        
        // FIXED: Using the IDs from your actual XML
        // Small grey label shows the status
        findViewById<TextView>(R.id.tvLiveQuestion).text = "TOURNAMENT QUESTION:"
        
        // Large white text shows the actual question
        findViewById<TextView>(R.id.tvTournamentJackpot).text = question.question

        val btnAction = findViewById<Button>(R.id.btnJoinTournamentFinal)
        btnAction.visibility = View.VISIBLE
        btnAction.text = "NEXT QUESTION"
        
        btnAction.setOnClickListener {
            // In Arena mode, we increment score for each answered and move on
            score += 10 
            currentIndex++
            if (currentIndex < triviaList.size) {
                displayQuestion()
            } else {
                submitScore()
            }
        }
    }

    private fun startTournamentTimer() {
        // 5 Minute Arena
        object : CountDownTimer(300000, 1000) {
            override fun onTick(ms: Long) {
                val m = (ms / 60000) % 60
                val s = (ms / 1000) % 60
                // Optionally show timer in the small label
                findViewById<TextView>(R.id.tvLiveQuestion).text = 
                    String.format("TIME LEFT: %02d:%02d | SCORE: %d", m, s, score)
            }

            override fun onFinish() {
                isGameOver = true
                submitScore()
            }
        }.start()
    }

    private fun submitScore() {
        val uid = auth.currentUser?.uid ?: return
        // Saving score to tournaments/active/scores/uid
        db.child("tournaments").child("active").child("scores").child(uid).setValue(score)
            .addOnSuccessListener {
                Toast.makeText(this, "Tournament Ended! Final Score: $score", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                finish()
            }
    }
}
