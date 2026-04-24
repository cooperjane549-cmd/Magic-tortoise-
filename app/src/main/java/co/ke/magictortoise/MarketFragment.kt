package co.ke.magictortoise

import android.os.Bundle
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
    private var myUsername: String? = null
    private var currentJackpotDisplay: String = "0.00"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_market, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvJackpot = view.findViewById<TextView>(R.id.tvTournamentJackpot)
        val tvPlayers = view.findViewById<TextView>(R.id.tvTournamentPlayers)
        val tvSyncList = view.findViewById<TextView>(R.id.tvSyncUserList)
        val syncProgress = view.findViewById<ProgressBar>(R.id.syncProgressBar)
        val btnSync = view.findViewById<Button>(R.id.btnSyncNow)
        val btnTournament = view.findViewById<Button>(R.id.btnEnterTournament)
        val btnCreateBattle = view.findViewById<Button>(R.id.btnCreateBattle)
        val p2pContainer = view.findViewById<LinearLayout>(R.id.p2pContainer)

        val uid = auth.currentUser?.uid ?: return

        // 1. Fetch Local Profile to ensure Username exists
        db.child("users").child(uid).get().addOnSuccessListener {
            myUsername = it.child("username").value?.toString()
        }

        // 2. Tournament Logic: Calculate 35% Cut Live
        db.child("tournaments").child("active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.child("players").childrenCount.toInt()
                val grossAmount = count * 10.0
                val netJackpot = grossAmount * 0.65 // Your 35% cut is removed here
                
                currentJackpotDisplay = String.format("%.2f", netJackpot)
                tvPlayers.text = "Participants: $count"
                tvJackpot.text = "WIN KES $currentJackpotDisplay"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. Quick Sync Logic: Display Names & Progress
        db.child("sync_active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val names = mutableListOf<String>()
                snapshot.child("participants").children.forEach { 
                    names.add(it.child("name").value.toString()) 
                }
                
                syncProgress.progress = names.size
                tvSyncList.text = if (names.isEmpty()) "Be the first to Sync!" 
                                   else "Joined: " + names.joinToString(", ")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 4. P2P Lobby Logic: Show custom stakes
        db.child("p2p_lobby").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                p2pContainer.removeAllViews()
                snapshot.children.forEach { battle ->
                    val creator = battle.child("name").value.toString()
                    val stake = battle.child("stake").value.toString()
                    
                    val battleView = layoutInflater.inflate(R.layout.item_p2p_battle, null)
                    battleView.findViewById<TextView>(R.id.tvBattleTitle).text = "$creator's Battle"
                    battleView.findViewById<TextView>(R.id.tvBattleStake).text = "Stake: KES $stake"
                    
                    battleView.findViewById<Button>(R.id.btnJoinBattle).setOnClickListener {
                        // Logic for joining will go here
                    }
                    p2pContainer.addView(battleView)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // --- Click Handlers ---
        btnSync.setOnClickListener {
            if (myUsername.isNullOrEmpty()) {
                Toast.makeText(context, "Complete Profile in Support tab first!", Toast.LENGTH_SHORT).show()
            } else {
                handleTransaction(uid, 20.0, "sync")
            }
        }

        btnTournament.setOnClickListener {
            if (myUsername.isNullOrEmpty()) {
                Toast.makeText(context, "Complete Profile in Support tab first!", Toast.LENGTH_SHORT).show()
            } else {
                // Deduct money first
                handleTransaction(uid, 10.0, "tournament")
                
                // Show the Pop-up via MainActivity with the REAL dynamic jackpot
                (activity as? MainActivity)?.showTournamentOverlay(currentJackpotDisplay)
            }
        }
        
        btnCreateBattle.setOnClickListener {
             if (myUsername.isNullOrEmpty()) {
                Toast.makeText(context, "Complete Profile in Support tab first!", Toast.LENGTH_SHORT).show()
            } else {
                // Next step: Show P2P Pop-up
            }
        }
    }

    private fun handleTransaction(uid: String, amount: Double, type: String) {
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                if (balance < amount) return Transaction.abort()
                
                mutableData.child("balance").value = balance - amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    if (type == "sync") {
                        db.child("sync_active").child("participants").child(uid).child("name").setValue(myUsername)
                    } else if (type == "tournament") {
                        db.child("tournaments").child("active").child("players").child(uid).setValue(true)
                    }
                } else {
                    Toast.makeText(context, "Insufficient Funds!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
