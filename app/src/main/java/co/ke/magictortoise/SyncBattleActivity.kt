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
        
        // Fullscreen Mode
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()

        setContentView(R.layout.activity_sync_battle)

        roomId = intent.getStringExtra("ROOM_ID") ?: ""
        
        startSettlementProcess()
    }

    private fun startSettlementProcess() {
        val tvTimer = findViewById<TextView>(R.id.tvSyncTimer)
        val tvStatus = findViewById<TextView>(R.id.tvSyncStatus)

        // The 2-minute Binance-style countdown
        syncTimer = object : CountDownTimer(120000, 1000) {
            override fun onTick(ms: Long) {
                val min = (ms / 1000) / 60
                val sec = (ms / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", min, sec)
                
                if (ms <= 10000) tvTimer.setTextColor(Color.RED)
            }

            override fun onFinish() {
                tvTimer.text = "00:00"
                tvStatus.text = "SYNC COMPLETE"
                pickWinner()
            }
        }.start()
    }

    private fun pickWinner() {
        if (isSettled) return
        isSettled = true

        // Room listener to get creator/joiner IDs
        db.child("sync_active").child(roomId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val creator = snapshot.child("creator").value.toString()
                val joiner = snapshot.child("joiner").value.toString()
                val stake = snapshot.child("stake").getValue(Double::class.java) ?: 0.0
                
                // Only creator's app runs the math to save resources
                if (auth.currentUser?.uid == creator) {
                    val winnerId = if (Random.nextBoolean()) creator else joiner
                    db.child("sync_active").child(roomId).child("winner").setValue(winnerId)
                    
                    // Pay out the winner (stake * 2)
                    transferWinnings(winnerId, stake * 2)
                }
                
                observeWinnerUpdate()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun transferWinnings(uid: String, totalPot: Double) {
        db.child("users").child(uid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val bal = data.getValue(Double::class.java) ?: 0.0
                data.value = bal + totalPot
                return Transaction.success(data)
            }
            override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {}
        })
    }

    private fun observeWinnerUpdate() {
        db.child("sync_active").child(roomId).child("winner").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val winner = snapshot.value.toString()
                    val tvStatus = findViewById<TextView>(R.id.tvSyncStatus)
                    
                    if (auth.currentUser?.uid == winner) {
                        tvStatus.text = "YOU WON!"
                        tvStatus.setTextColor(Color.GREEN)
                    } else {
                        tvStatus.text = "LOST"
                        tvStatus.setTextColor(Color.RED)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    override fun onBackPressed() {
        if (isSettled) super.onBackPressed() else Toast.makeText(this, "STAY ON SCREEN FOR SETTLEMENT!", Toast.LENGTH_SHORT).show()
    }
}
