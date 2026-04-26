package co.ke.magictortoise

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SyncBattleActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    
    private lateinit var tvStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvRoomDetails: TextView

    private var roomName: String = ""
    private var stake: Double = 0.0
    private var playerCount: Int = 0
    private var isTimerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_battle)

        // Initialize Views
        tvStatus = findViewById(R.id.tvSyncStatus)
        tvTimer = findViewById(R.id.tvSyncTimer)
        tvRoomDetails = findViewById(R.id.tvRoomDetails)

        // Get Data from Intent
        roomName = intent.getStringExtra("ROOM_NAME") ?: ""
        stake = intent.getDoubleExtra("STAKE", 0.0)
        playerCount = intent.getIntExtra("PLAYER_COUNT", 0)

        tvRoomDetails.text = "Pool: $playerCount Players | Stake: KES $stake"

        monitorRoom()
    }

    private fun monitorRoom() {
        db.child("sync_active").child(roomName).child("participants")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentJoined = snapshot.childrenCount.toInt()
                    
                    if (currentJoined < playerCount) {
                        tvStatus.text = "Waiting for players... ($currentJoined/$playerCount)"
                        tvTimer.text = "Syncing..."
                    } else {
                        if (!isTimerRunning) {
                            startTenMinuteCountdown()
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun startTenMinuteCountdown() {
        isTimerRunning = true
        tvStatus.text = "POOL FULL! Preparing Game..."
        
        // 10 Minutes = 600,000ms
        object : CountDownTimer(600000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", mins, secs)
                
                // Alert at 1 minute remaining
                if (mins == 1L && secs == 0L) {
                    Toast.makeText(this@SyncBattleActivity, "Get Ready! 1 Minute left.", Toast.LENGTH_LONG).show()
                }
            }

            override fun onFinish() {
                tvTimer.text = "GO!"
                launchTriviaGame()
            }
        }.start()
    }

    private fun launchTriviaGame() {
        // Logic to start the 500 questions quiz
        // After the quiz ends, you would call declareWinner(auth.uid!!)
        Toast.makeText(this, "Game Starting Now!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Call this function when the game ends.
     * It calculates the 25% house cut and pays the winner.
     */
    fun declareWinner(winnerUid: String) {
        val totalPool = stake * playerCount
        val houseCut = totalPool * 0.25
        val winnerPrize = totalPool - houseCut

        db.child("users").child(winnerUid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentBal = mutableData.getValue(Double::class.java) ?: 0.0
                mutableData.value = currentBal + winnerPrize
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    // 1. Notify Winner
                    val winnerMsg = "🏆 Victory! You won KES $winnerPrize from the $playerCount-player Sync!"
                    db.child("users").child(winnerUid).child("notifications").push().setValue(winnerMsg)
                    
                    // 2. Clean up the room for new players
                    db.child("sync_active").child(roomName).removeValue()
                    
                    Toast.makeText(this@SyncBattleActivity, "Prize Awarded!", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        })
    }
}
