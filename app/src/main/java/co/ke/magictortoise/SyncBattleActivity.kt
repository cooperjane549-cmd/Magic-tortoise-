package co.ke.magictortoise

import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlin.random.Random

class SyncBattleActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var syncTimer: CountDownTimer? = null
    private var isSettled = false
    
    private lateinit var roomId: String
    private var stakeAmount: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Fullscreen Immersive Mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()

        setContentView(R.layout.activity_sync_battle)

        roomId = intent.getStringExtra("ROOM_ID") ?: ""
        
        // Safety Step: Double check the room exists before taking money
        verifyAndStart()
    }

    private fun verifyAndStart() {
        db.child("sync_active").child(roomId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    stakeAmount = snapshot.child("stake").getValue(Double::class.java) ?: 0.0
                    startSyncTimer()
                } else {
                    Toast.makeText(this@SyncBattleActivity, "Sync Expired or Cancelled", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startSyncTimer() {
        val tvTimer = findViewById<TextView>(R.id.tvSyncTimer)
        
        // 2-Minute Settlement Timer
        syncTimer = object : CountDownTimer(120000, 1000) {
            override fun onTick(ms: Long) {
                val min = (ms / 1000) / 60
                val sec = (ms / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", min, sec)
                
                // Red Alert for last 10 seconds
                if (ms <= 10000) tvTimer.setTextColor(Color.RED)
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                findViewById<TextView>(R.id.tvSyncStatus).text = "FINALIZING SETTLEMENT..."
                performAutoSettlement()
            }
        }.start()
    }

    private fun performAutoSettlement() {
        if (isSettled) return
        isSettled = true

        db.child("sync_active").child(roomId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val creator = snapshot.child("creator").value.toString()
                val joiner = snapshot.child("joiner").value.toString()
                
                // The App (Creator's side) picks the winner automatically
                if (auth.currentUser?.uid == creator) {
                    val winnerId = if (Random.nextBoolean()) creator else joiner
                    val totalPot = stakeAmount * 2
                    
                    // Update Winner in Firebase
                    db.child("sync_active").child(roomId).child("winner").setValue(winnerId)
                    
                    // Credit Winner
                    creditWinner(winnerId, totalPot)
                }
                
                showFinalResult()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun creditWinner(uid: String, amount: Double) {
        db.child("users").child(uid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val current = data.getValue(Double::class.java) ?: 0.0
                data.value = current + amount
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })
    }

    private fun showFinalResult() {
        db.child("sync_active").child(roomId).child("winner").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val winner = snapshot.value.toString()
                    val tvStatus = findViewById<TextView>(R.id.tvSyncStatus)
                    
                    if (auth.currentUser?.uid == winner) {
                        tvStatus.text = "SYNC SUCCESS: YOU WON!"
                        tvStatus.setTextColor(Color.GREEN)
                    } else {
                        tvStatus.text = "SYNC COMPLETE: LOST"
                        tvStatus.setTextColor(Color.GRAY)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onBackPressed() {
        if (isSettled) {
            super.onBackPressed()
        } else {
            Toast.makeText(this, "SETTLEMENT IN PROGRESS - DO NOT EXIT", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        syncTimer?.cancel()
    }
}
