package co.ke.magictortoise

import android.app.AlertDialog
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

        // 1. Fetch Profile
        db.child("users").child(uid).get().addOnSuccessListener {
            myUsername = it.child("username").value?.toString()
        }

        // 2. Tournament Logic (35% Cut)
        db.child("tournaments").child("active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.child("players").childrenCount.toInt()
                val netJackpot = (count * 10.0) * 0.65
                currentJackpotDisplay = String.format("%.2f", netJackpot)
                tvPlayers.text = "Participants: $count"
                tvJackpot.text = "WIN KES $currentJackpotDisplay"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 3. Quick Sync Logic
        db.child("sync_active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val names = mutableListOf<String>()
                snapshot.child("participants").children.forEach { names.add(it.child("name").value.toString()) }
                syncProgress.progress = names.size
                tvSyncList.text = if (names.isEmpty()) "Be the first to Sync!" else "Joined: " + names.joinToString(", ")
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 4. P2P Lobby Logic
        db.child("p2p_lobby").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                p2pContainer.removeAllViews()
                snapshot.children.forEach { battle ->
                    val creator = battle.child("name").value.toString()
                    val stake = battle.child("stake").value.toString()
                    val battleUid = battle.key ?: ""

                    val battleView = layoutInflater.inflate(R.layout.item_p2p_battle, null)
                    battleView.findViewById<TextView>(R.id.tvBattleTitle).text = "$creator's Battle"
                    battleView.findViewById<TextView>(R.id.tvBattleStake).text = "Stake: KES $stake"
                    
                    battleView.findViewById<Button>(R.id.btnJoinBattle).setOnClickListener {
                        if (battleUid != uid) handleTransaction(uid, stake.toDouble(), "p2p_join", battleUid)
                    }
                    p2pContainer.addView(battleView)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        btnSync.setOnClickListener { if (checkProfile()) handleTransaction(uid, 20.0, "sync") }
        btnTournament.setOnClickListener {
            if (checkProfile()) {
                handleTransaction(uid, 10.0, "tournament")
                (activity as? MainActivity)?.showTournamentOverlay(currentJackpotDisplay)
            }
        }
        btnCreateBattle.setOnClickListener { if (checkProfile()) showCreateBattleDialog(uid) }
    }

    private fun checkProfile(): Boolean {
        if (myUsername.isNullOrEmpty()) {
            Toast.makeText(context, "Set Username in Support tab first!", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showCreateBattleDialog(uid: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_battle, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        val etStake = dialogView.findViewById<EditText>(R.id.etStakeAmount)
        val btnPost = dialogView.findViewById<Button>(R.id.btnPostBattle)

        btnPost.setOnClickListener {
            val amount = etStake.text.toString().toDoubleOrNull() ?: 0.0
            if (amount < 20.0) {
                Toast.makeText(context, "Minimum stake is 20/-", Toast.LENGTH_SHORT).show()
            } else {
                handleTransaction(uid, amount, "p2p_create")
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun handleTransaction(uid: String, amount: Double, type: String, extraId: String = "") {
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val balance = mutableData.child("balance").getValue(Double::class.java) ?: 0.0
                if (balance < amount) return Transaction.abort()
                mutableData.child("balance").value = balance - amount
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    when (type) {
                        "sync" -> db.child("sync_active").child("participants").child(uid).child("name").setValue(myUsername)
                        "tournament" -> db.child("tournaments").child("active").child("players").child(uid).setValue(true)
                        "p2p_create" -> {
                            val battle = mapOf("name" to myUsername, "stake" to amount, "status" to "waiting")
                            db.child("p2p_lobby").child(uid).setValue(battle)
                        }
                    }
                    Toast.makeText(context, "Success!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Insufficient Funds!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
