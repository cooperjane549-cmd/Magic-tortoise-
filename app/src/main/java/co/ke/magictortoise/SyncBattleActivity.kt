package co.ke.magictortoise

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SyncBattleActivity : AppCompatActivity() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()
    private var syncTimer: CountDownTimer? = null
    
    private lateinit var roomId: String
    private var isCreator: Boolean = false
    private var isTransitioning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        supportActionBar?.hide()

        setContentView(R.layout.activity_sync_battle)

        roomId = intent.getStringExtra("ROOM_ID") ?: ""
        isCreator = intent.getBooleanExtra("IS_CREATOR", false)
        
        verifyAndStart()
    }

    private fun verifyAndStart() {
        // Look in sync_active to see if both players are ready
        db.child("sync_active").child(roomId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    startSyncTimer()
                } else {
                    Toast.makeText(this@SyncBattleActivity, "Sync Expired", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startSyncTimer() {
        val tvTimer = findViewById<TextView>(R.id.tvSyncTimer)
        val tvStatus = findViewById<TextView>(R.id.tvSyncStatus)
        
        tvStatus.text = "SYNCING DEVICES..."

        // We use a shorter 5-second timer just to ensure both phones are 
        // on this screen before the game starts
        syncTimer = object : CountDownTimer(5000, 1000) {
            override fun onTick(ms: Long) {
                tvTimer.text = "Starting in: ${ms / 1000}"
            }

            override fun onFinish() {
                launchBattlefield()
            }
        }.start()
    }

    private fun launchBattlefield() {
        if (isTransitioning) return
        isTransitioning = true

        // Launch the Universal Engine we built earlier
        val intent = Intent(this, BattlefieldActivity::class.java).apply {
            putExtra("ROOM_ID", roomId)
            putExtra("IS_CREATOR", isCreator)
            putExtra("ROOM_TYPE", "sync")
            putExtra("GAME_TYPE", "Math Blitz") // You can change this to any game
        }
        startActivity(intent)
        finish() // Close this activity so they can't go back to the timer
    }

    override fun onBackPressed() {
        Toast.makeText(this, "PREPARING BATTLE...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        syncTimer?.cancel()
    }
}
