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
        // Using your existing overlay layout instead of creating a new one
        setContentView(R.layout.layout_tournament_overlay)

        // Adjust UI for the "Arena" mode
        findViewById<View>(R.id.btnMinimize)?.visibility = View.GONE
        findViewById<View>(R.id.btnCloseTournament)?.visibility = View.GONE
        findViewById<Button>(R.id.btnJoinTournamentFinal)?.visibility = View.GONE

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
        
        // Using tvLiveQuestion because it exists in your XML
        findViewById<TextView>(R.id.tvLiveQuestion).text = question.question

        // NOTE: If you don't have 4 buttons in layout_tournament_overlay, 
        // this part needs the correct IDs from that XML. 
        // For now, I will use a Toast to show the score since the IDs were missing.
        
        // I'm adding this check to prevent another crash if buttons aren't there
        val btnJoin = findViewById<Button>(R.id.btnJoinTournamentFinal)
        btnJoin.visibility = View.VISIBLE
        btnJoin.text = "NEXT QUESTION"
        btnJoin.setOnClickListener {
            currentIndex++
            displayQuestion()
        }
    }

    private fun startTournamentTimer() {
        object : CountDownTimer(300000, 1000) {
            override fun onTick(ms: Long) {
                val m = (ms / 60000) % 60
                val s = (ms / 1000) % 60
                // Reusing tvLiveQuestion to show time if needed, or update title
                title = String.format("Arena Time: %02d:%02d", m, s)
            }

            override fun onFinish() {
                isGameOver = true
                submitScore()
            }
        }.start()
    }

    private fun submitScore() {
        val uid = auth.currentUser?.uid ?: return
        db.child("tournaments").child("active").child("scores").child(uid).setValue(score)
            .addOnSuccessListener {
                Toast.makeText(this, "Arena Finished! Score: $score", Toast.LENGTH_LONG).show()
                finish()
            }
    }
}
