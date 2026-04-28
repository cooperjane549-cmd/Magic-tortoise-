package co.ke.magictortoise

import android.os.Bundle
import android.os.CountDownTimer
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
        // Ensure this layout exists in your res/layout folder
        setContentView(R.layout.activity_tournament_arena)

        loadTriviaFromAssets()
        startTournamentTimer()
        displayQuestion()
    }

    private fun loadTriviaFromAssets() {
        try {
            // Accessing your specific questions.json file
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
            triviaList.shuffle() // Keeps the arena unpredictable
        } catch (e: Exception) {
            Toast.makeText(this, "Asset Error: questions.json not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun displayQuestion() {
        if (isGameOver || triviaList.isEmpty()) return

        // Loop back to start if they finish all 500 questions before time ends
        val question = triviaList[currentIndex % triviaList.size]
        
        findViewById<TextView>(R.id.tvTournamentQuestion).text = question.question

        val buttons = listOf<Button>(
            findViewById(R.id.btnTournOpt1),
            findViewById(R.id.btnTournOpt2),
            findViewById(R.id.btnTournOpt3),
            findViewById(R.id.btnTournOpt4)
        )

        buttons.forEachIndexed { index, button ->
            button.text = question.options[index]
            button.setOnClickListener {
                if (button.text == question.answer) {
                    score += 10
                    Toast.makeText(this, "Correct! +10", Toast.LENGTH_SHORT).show()
                } else {
                    score -= 5
                    Toast.makeText(this, "Wrong! -5", Toast.LENGTH_SHORT).show()
                }
                currentIndex++
                displayQuestion()
            }
        }
    }

    private fun startTournamentTimer() {
        // 5 Minute Tournament Duration
        object : CountDownTimer(300000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                findViewById<TextView>(R.id.tvTournamentTimer).text = 
                    String.format("Time Left: %02d:%02d", mins, secs)
            }

            override fun onFinish() {
                isGameOver = true
                submitScore()
            }
        }.start()
    }

    private fun submitScore() {
        val uid = auth.currentUser?.uid ?: return
        // Save score to the active tournament node
        db.child("tournaments").child("active").child("scores").child(uid).setValue(score)
            .addOnSuccessListener {
                Toast.makeText(this, "Tournament Over! Score: $score", Toast.LENGTH_LONG).show()
                finish()
            }
    }
}
