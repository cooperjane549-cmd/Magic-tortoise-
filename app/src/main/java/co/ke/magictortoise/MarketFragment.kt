package co.ke.magictortoise

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
        val uid = auth.currentUser?.uid ?: return

        db.child("users").child(uid).get().addOnSuccessListener {
            myUsername = it.child("username").value?.toString()
        }

        val tvJackpot = view.findViewById<TextView>(R.id.tvTournamentJackpot)
        val tvPlayers = view.findViewById<TextView>(R.id.tvTournamentPlayers)
        val btnSync = view.findViewById<Button>(R.id.btnSyncNow)
        val btnTournament = view.findViewById<Button>(R.id.btnEnterTournament)
        val btnCreateBattle = view.findViewById<Button>(R.id.btnCreateBattle)
        val p2pContainer = view.findViewById<LinearLayout>(R.id.p2pContainer)
        val syncContainer = view.findViewById<LinearLayout>(R.id.syncContainer)

        btnSync.setOnClickListener { if (checkProfile()) showSyncStakeDialog(uid) }
        btnTournament.setOnClickListener { if (checkProfile()) showTournamentGateway() }
        btnCreateBattle.setOnClickListener { if (checkProfile()) showCreateBattleDialog(uid) }

        // Live Tournament Tracker
        db.child("tournaments").child("active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val count = snapshot.child("players").childrenCount.toInt()
                val calculatedJackpot = (count * 10.0) * 0.75
                currentJackpotDisplay = String.format("%.2f", calculatedJackpot)
                tvPlayers?.text = "Participants: $count"
                tvJackpot?.text = "WIN KES $currentJackpotDisplay"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Live P2P Lobby
        db.child("p2p_lobby").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                p2pContainer.removeAllViews()
                snapshot.children.forEach { battle ->
                    val creator = battle.child("name").value.toString()
                    val stake = battle.child("stake").value.toString()
                    val game = battle.child("game").value?.toString() ?: "Trivia Duel"
                    val bUid = battle.key ?: ""
                    if (bUid != uid) {
                        val bView = layoutInflater.inflate(R.layout.item_p2p_battle, null)
                        bView.findViewById<TextView>(R.id.tvBattleTitle).text = "$creator: $game"
                        bView.findViewById<TextView>(R.id.tvBattleStake).text = "Stake: KES $stake"
                        bView.findViewById<Button>(R.id.btnJoinBattle).setOnClickListener {
                             showJoinBattleGateway(bUid, stake.toDouble(), game)
                        }
                        p2pContainer.addView(bView)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // LIVE SYNC ARENA LOBBY
        db.child("sync_lobby").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                syncContainer?.removeAllViews()
                snapshot.children.forEach { sync ->
                    val sId = sync.key ?: ""
                    val cName = sync.child("creatorName").value.toString()
                    val cUid = sync.child("creatorUid").value.toString()
                    val stake = sync.child("stake").value.toString()
                    val status = sync.child("status").value.toString()

                    if (status == "waiting") {
                        val sView = layoutInflater.inflate(R.layout.item_p2p_battle, null)
                        val titleTv = sView.findViewById<TextView>(R.id.tvBattleTitle)
                        val stakeTv = sView.findViewById<TextView>(R.id.tvBattleStake)
                        val actionBtn = sView.findViewById<Button>(R.id.btnJoinBattle)

                        titleTv.text = "$cName: Sync Battle"
                        stakeTv.text = "Stake: KES $stake"

                        if (cUid == uid) {
                            actionBtn.text = "CANCEL"
                            actionBtn.setBackgroundColor(Color.RED)
                            actionBtn.setOnClickListener { db.child("sync_lobby").child(sId).removeValue() }
                        } else {
                            actionBtn.text = "JOIN"
                            actionBtn.setOnClickListener { showJoinSyncGateway(sId, stake.toDouble()) }
                        }
                        syncContainer?.addView(sView)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun checkProfile(): Boolean {
        if (myUsername.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Set Username in Support first", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showSyncStakeDialog(uid: String) {
        val view = layoutInflater.inflate(R.layout.dialog_create_battle, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        val et = view.findViewById<EditText>(R.id.etStakeAmount)
        val sp = view.findViewById<Spinner>(R.id.spinnerGameType)
        sp.visibility = View.GONE

        view.findViewById<Button>(R.id.btnPostBattle).setOnClickListener {
            val amt = et.text.toString().toDoubleOrNull() ?: 0.0
            if (amt >= 10.0) {
                val lobbyId = db.child("sync_lobby").push().key ?: ""
                db.child("sync_lobby").child(lobbyId).setValue(mapOf(
                    "creatorUid" to uid,
                    "creatorName" to myUsername,
                    "stake" to amt,
                    "status" to "waiting"
                )).addOnSuccessListener {
                    dialog.dismiss()
                    // Listener for the Creator to enter the activity once joined
                    db.child("sync_lobby").child(lobbyId).child("status").addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.value == "active") {
                                val intent = Intent(context, SyncBattleActivity::class.java)
                                intent.putExtra("ROOM_ID", lobbyId)
                                intent.putExtra("IS_CREATOR", true)
                                intent.putExtra("ROOM_TYPE", "sync")
                                startActivity(intent)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
        }
        dialog.show()
    }

    private fun showJoinSyncGateway(sId: String, stake: Double) {
        val view = layoutInflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        view.findViewById<TextView>(R.id.tvTournamentJackpot)?.text = "STAKE: KES $stake"
        view.findViewById<Button>(R.id.btnJoinTournamentFinal).setOnClickListener {
            handleTransaction(auth.uid!!, stake, "sync_join", sId)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showTournamentGateway() {
        val view = layoutInflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        val uid = auth.currentUser?.uid ?: return
        val btnAction = view.findViewById<Button>(R.id.btnJoinTournamentFinal)
        
        db.child("tournaments").child("active").child("players").child(uid).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                btnAction.text = "ENTER ARENA"
                btnAction.setOnClickListener { 
                    startActivity(Intent(context, BattlefieldActivity::class.java).apply {
                        putExtra("ROOM_TYPE", "tournament")
                        putExtra("GAME_TYPE", "Tournament")
                    })
                    dialog.dismiss() 
                }
            } else {
                btnAction.text = "JOIN (10/-)"
                btnAction.setOnClickListener { handleTransaction(uid, 10.0, "tournament") }
            }
        }
        dialog.show()
    }

    private fun showJoinBattleGateway(cUid: String, stake: Double, game: String) {
        val view = layoutInflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        view.findViewById<Button>(R.id.btnJoinTournamentFinal).setOnClickListener { 
            handleTransaction(auth.uid!!, stake, "p2p_join", "$cUid|$game")
            dialog.dismiss() 
        }
        dialog.show()
    }

    private fun showCreateBattleDialog(uid: String) {
        val view = layoutInflater.inflate(R.layout.dialog_create_battle, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        val et = view.findViewById<EditText>(R.id.etStakeAmount)
        val sp = view.findViewById<Spinner>(R.id.spinnerGameType)
        sp.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, arrayOf("Math Blitz", "Tap Tortoise", "Trivia Duel"))
        
        view.findViewById<Button>(R.id.btnPostBattle).setOnClickListener {
            val amt = et.text.toString().toDoubleOrNull() ?: 0.0
            if (amt >= 20.0) { handleTransaction(uid, amt, "p2p_create", sp.selectedItem.toString()); dialog.dismiss() }
        }
        dialog.show()
    }

    private fun handleTransaction(uid: String, amount: Double, type: String, extra: String = "") {
        db.child("users").child(uid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val bal = data.getValue(Double::class.java) ?: 0.0
                if (bal < amount) return Transaction.abort()
                data.value = bal - amount
                return Transaction.success(data)
            }
            override fun onComplete(err: DatabaseError?, comm: Boolean, snap: DataSnapshot?) {
                if (comm) {
                    when (type) {
                        "tournament" -> {
                            db.child("tournaments").child("active").child("players").child(uid).setValue(true)
                            startActivity(Intent(context, BattlefieldActivity::class.java).apply {
                                putExtra("ROOM_TYPE", "tournament")
                                putExtra("GAME_TYPE", "Tournament")
                            })
                        }
                        "sync_join" -> {
                            // Initialize sync_active node so both bars work
                            db.child("sync_active").child(extra).setValue(mapOf(
                                "creatorScore" to 0, "joinerScore" to 0, "timeLeft" to 60
                            ))
                            db.child("sync_lobby").child(extra).updateChildren(mapOf("status" to "active", "joinerUid" to uid))
                            
                            startActivity(Intent(context, SyncBattleActivity::class.java).apply {
                                putExtra("ROOM_ID", extra)
                                putExtra("IS_CREATOR", false)
                                putExtra("ROOM_TYPE", "sync")
                            })
                        }
                        "p2p_create" -> {
                            db.child("p2p_lobby").child(uid).setValue(mapOf("name" to myUsername, "stake" to amount, "game" to extra, "player1_score" to 0, "player2_score" to 0))
                            startActivity(Intent(context, BattlefieldActivity::class.java).apply { 
                                putExtra("ROOM_ID", uid); putExtra("GAME_TYPE", extra); putExtra("IS_CREATOR", true); putExtra("ROOM_TYPE", "p2p") 
                            })
                        }
                        "p2p_join" -> {
                            val p = extra.split("|")
                            startActivity(Intent(context, BattlefieldActivity::class.java).apply { 
                                putExtra("ROOM_ID", p[0]); putExtra("GAME_TYPE", p[1]); putExtra("IS_CREATOR", false); putExtra("ROOM_TYPE", "p2p") 
                            })
                        }
                    }
                }
            }
        })
    }
}
