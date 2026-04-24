package co.ke.magictortoise

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MarketFragment : Fragment() {

    private val db = FirebaseDatabase.getInstance().reference
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_market, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnSync = view.findViewById<Button>(R.id.btnSyncNow)
        val progressBar = view.findViewById<ProgressBar>(R.id.syncProgressBar)
        val tvStatus = view.findViewById<TextView>(R.id.tvSyncStatus)
        val btnTournament = view.findViewById<Button>(R.id.btnEnterTournament)

        // 1. Listen for Sync Pool filling up
        db.child("sync_active").child("current_pool").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.child("participants").childrenCount.toInt()
                progressBar.progress = count
                tvStatus.text = "Participants: $count/6"

                // If pool is full, trigger selection (This usually runs on a backend, 
                // but for now, we'll let the 6th person trigger the win)
                if (count >= 6) {
                    processSyncWinner(snapshot)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        btnSync.setOnClickListener {
            handleJoinSync()
        }
        
        btnTournament.setOnClickListener {
            handleTournamentEntry()
        }
    }

    private fun handleJoinSync() {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.child("users").child(uid)

        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                if (balance < 20.0) return Transaction.abort() // Insufficient funds

                mutableData.child("balance").value = balance - 20.0
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    // Add user to the pool
                    db.child("sync_active").child("current_pool").child("participants").child(uid).setValue(true)
                    Toast.makeText(context, "Synced successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Insufficient Balance!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun processSyncWinner(poolSnapshot: DataSnapshot) {
        val participants = mutableListOf<String>()
        poolSnapshot.child("participants").children.forEach { participants.add(it.key!!) }

        if (participants.size == 6) {
            val winnerUid = participants.random()
            
            // Credit winner 100/-
            db.child("users").child(winnerUid).child("balance").runTransaction(object : Transaction.Handler {
                override fun doTransaction(mutableData: MutableData): Transaction.Result {
                    val bal = mutableData.getValue(Double::class.java) ?: 0.0
                    mutableData.value = bal + 100.0
                    return Transaction.success(mutableData)
                }
                override fun onComplete(p0: DatabaseError?, p1: Boolean, p2: DataSnapshot?) {
                    // Reset Pool
                    db.child("sync_active").child("current_pool").removeValue()
                    Toast.makeText(context, "Winner Picked! Pool Reset.", Toast.LENGTH_LONG).show()
                }
            })
        }
    }
    
    private fun handleTournamentEntry() {
        // Logic similar to Sync: Deduct 10/- and add to tournament node
    }
}
