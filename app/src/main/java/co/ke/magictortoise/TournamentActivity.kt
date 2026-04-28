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
        
        // Forced Fullscreen Immersive Mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()

        setContentView(R.layout.layout_tournament_overlay)

        // Hide navigation/setup buttons
        findViewById<View>(R.id.btnMinimize)?.visibility = View.GONE
        findViewById<View>(R.id.btnCloseTournament)?.visibility = View.GONE

        loadTriviaFromAssets()
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
                
                triviaList.add(Question(obj.getInt("id"), obj.getString("question"), optionsList, obj.getString("answer")))
            }
            triviaList.shuffle()
            displayQuestion() // Start the game if questions loaded
        } catch (e: Exception) {
            Toast.makeText(this, "ERROR: questions.json missing in assets folder!", Toast.LENGTH_LONG).show()
            finish() // Close activity if no questions
        }
    }

    private fun displayQuestion() {
        if (isGameOver || currentIndex >= 20 || currentIndex >= triviaList.size) {
            submitScore()
            return
        }

        val question = triviaList[currentIndex]
        val tvLabel = findViewById<TextView>(R.id.tvLiveQuestion)
        val tvQuestion = findViewById<TextView>(R.id.tvTournamentJackpot)
        val btnAction = findViewById<Button>(R.id.btnJoinTournamentFinal)

        tvQuestion.text = question.question
        btnAction.text = "SUBMIT ANSWER"
        btnAction.visibility = View.VISIBLE

        // ANTI-CHEAT TIMER: 10 seconds per question
        questionTimer?.cancel()
        questionTimer = object : CountDownTimer(10000, 100) {
            override fun onTick(ms: Long) {
                val secondsLeft = ms / 1000
                tvLabel.text = "QUESTION ${currentIndex + 1}/20 | TIME: $secondsLeft SEC"
                if (secondsLeft <= 3) tvLabel.setTextColor(Color.RED) else tvLabel.setTextColor(Color.GRAY)
            }

            override fun onFinish() {
                // Time's up! Move to next question with 0 points
                Toast.makeText(this@TournamentActivity, "Time up!", Toast.LENGTH_SHORT).show()
                nextQuestion()
            }
        }.start()

        btnAction.setOnClickListener {
            // For now, this adds points. In a real duel, you'd check an EditText or RadioButtons
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
                Toast.makeText(this, "Tournament Finished! Final Score: $score", Toast.LENGTH_LONG).show()
                finish()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        questionTimer?.cancel()
    }
}
