package co.ke.magictortoise

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
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

        tvStatus = findViewById(R.id.tvSyncStatus)
        tvTimer = findViewById(R.id.tvSyncTimer)
        tvRoomDetails = findViewById(R.id.tvRoomDetails)

        // FIXED: Match key with MarketFragment
        roomName = intent.getStringExtra("ROOM_ID") ?: ""

        // Extracting data from room string: "2_players_at_20_stake"
        if (roomName.isNotEmpty()) {
            try {
                val parts = roomName.split("_")
                playerCount = parts[0].toInt()
                stake = parts[3].toDouble()
            } catch (e: Exception) {
                playerCount = 2 // Fallback
                stake = 20.0
            }
        }

        tvRoomDetails.text = "Pool: $playerCount Players | Stake: KES $stake"

        if (roomName.isNotEmpty()) {
            monitorRoom()
        } else {
            Toast.makeText(this, "Room Error", Toast.LENGTH_SHORT).show()
            finish()
        }
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
        
        object : CountDownTimer(600000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = (millisUntilFinished / 1000) / 60
                val secs = (millisUntilFinished / 1000) % 60
                tvTimer.text = String.format("%02d:%02d", mins, secs)
            }

            override fun onFinish() {
                tvTimer.text = "GO!"
                launchTriviaGame()
            }
        }.start()
    }

    private fun launchTriviaGame() {
        Toast.makeText(this, "Game Starting Now!", Toast.LENGTH_SHORT).show()
    }

    fun declareWinner(winnerUid: String) {
        val totalPool = stake * playerCount
        val winnerPrize = totalPool * 0.75

        db.child("users").child(winnerUid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentBal = mutableData.getValue(Double::class.java) ?: 0.0
                mutableData.value = currentBal + winnerPrize
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    db.child("sync_active").child(roomName).removeValue()
                    finish()
                }
            }
        })
    }
}
