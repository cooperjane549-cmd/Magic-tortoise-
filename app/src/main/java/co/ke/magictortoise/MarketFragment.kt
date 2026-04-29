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

        // Fetch user profile data
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

                    val bView = layoutInflater.inflate(R.layout.item_p2p_battle, null)
                    bView.findViewById<TextView>(R.id.tvBattleTitle).text = "$creator: $game"
                    bView.findViewById<TextView>(R.id.tvBattleStake).text = "Stake: KES $stake"
                    bView.findViewById<Button>(R.id.btnJoinBattle).setOnClickListener {
                        if (bUid != uid && checkProfile()) showJoinBattleGateway(bUid, stake.toDouble(), game)
                    }
                    p2pContainer.addView(bView)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // LIVE SYNC ARENA LOBBY - This handles showing the cards in the Market
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
                            // Creator sees a Cancel button
                            actionBtn.text = "CANCEL"
                            actionBtn.setBackgroundColor(Color.RED)
                            actionBtn.setOnClickListener {
                                db.child("sync_lobby").child(sId).removeValue()
                            }
                        } else {
                            // Others see a Join button that opens the Pop-up gateway
                            actionBtn.text = "JOIN"
                            actionBtn.setOnClickListener {
                                if (checkProfile()) showJoinSyncGateway(sId, stake.toDouble())
                            }
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
        et.hint = "Enter Sync Stake (Min 10/-)"

        view.findViewById<Button>(R.id.btnPostBattle).setOnClickListener {
            val amt = et.text.toString().toDoubleOrNull() ?: 0.0
            if (amt >= 10.0) {
                val lobbyId = db.child("sync_lobby").push().key ?: ""
                val winnings = (amt * 2) * 0.90 // 10% House Cut

                db.child("sync_lobby").child(lobbyId).setValue(mapOf(
                    "creatorUid" to uid,
                    "creatorName" to myUsername,
                    "stake" to amt,
                    "winnings" to winnings,
                    "status" to "waiting"
                )).addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(context, "Challenge Posted!", Toast.LENGTH_SHORT).show()
                    
                    // Wait for a partner to join
                    db.child("sync_lobby").child(lobbyId).child("status").addValueEventListener(object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            if (snapshot.value == "active") {
                                val intent = Intent(context, SyncBattleActivity::class.java)
                                intent.putExtra("ROOM_ID", lobbyId)
                                startActivity(intent)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            } else {
                Toast.makeText(context, "Minimum 10/-", Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    // THE POP-UP GATEWAY FOR SYNC
    private fun showJoinSyncGateway(sId: String, stake: Double) {
        val view = layoutInflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        
        // Customizing the existing overlay layout for the Sync message
        view.findViewById<TextView>(R.id.tvLiveQuestion)?.text = "SECURE P2P SETTLEMENT ARENA"
        view.findViewById<TextView>(R.id.tvTournamentJackpot)?.text = "STAKE: KES $stake"
        
        val actionBtn = view.findViewById<Button>(R.id.btnJoinTournamentFinal)
        actionBtn.text = "CONFIRM & JOIN"
        actionBtn.setOnClickListener {
            handleTransaction(auth.uid!!, stake, "sync_join", sId)
            dialog.dismiss()
        }
        
        view.findViewById<ImageButton>(R.id.btnCloseTournament).setOnClickListener { dialog.dismiss() }
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
                    startActivity(Intent(context, TournamentActivity::class.java))
                    dialog.dismiss() 
                }
            } else {
                btnAction.text = "JOIN (10/-)"
                btnAction.setOnClickListener { 
                    handleTransaction(uid, 10.0, "tournament")
                    dialog.dismiss() 
                }
            }
        }
        view.findViewById<ImageButton>(R.id.btnCloseTournament).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showJoinBattleGateway(cUid: String, stake: Double, game: String) {
        val view = layoutInflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(view).create()
        val actionBtn = view.findViewById<Button>(R.id.btnJoinTournamentFinal)
        
        view.findViewById<TextView>(R.id.tvLiveQuestion)?.text = "BATTLE ARENA: $game"
        view.findViewById<TextView>(R.id.tvTournamentJackpot)?.text = "STAKE: KES $stake"
        
        actionBtn.text = "ACCEPT CHALLENGE"
        actionBtn.setOnClickListener { 
            handleTransaction(auth.uid!!, stake, "p2p_join", "$cUid|$game")
            dialog.dismiss() 
        }
        
        view.findViewById<ImageButton>(R.id.btnCloseTournament).setOnClickListener { dialog.dismiss() }
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
            if (amt >= 20.0) { 
                handleTransaction(uid, amt, "p2p_create", sp.selectedItem.toString())
                dialog.dismiss() 
            }
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
                            startActivity(Intent(context, TournamentActivity::class.java))
                        }
                        "sync_join" -> {
                            // Transition the lobby from 'waiting' to 'active'
                            db.child("sync_lobby").child(extra).child("status").setValue("active")
                            db.child("sync_lobby").child(extra).child("joinerUid").setValue(uid)
                            db.child("sync_lobby").child(extra).child("joinerName").setValue(myUsername)
                            
                            // Launch the settlement arena
                            val intent = Intent(context, SyncBattleActivity::class.java)
                            intent.putExtra("ROOM_ID", extra)
                            startActivity(intent)
                        }
                        "p2p_create" -> {
                            db.child("p2p_lobby").child(uid).setValue(mapOf("name" to myUsername, "stake" to amount, "game" to extra))
                            startActivity(Intent(context, BattlefieldActivity::class.java).apply { 
                                putExtra("ROOM_ID", uid)
                                putExtra("GAME_TYPE", extra)
                                putExtra("IS_CREATOR", true) 
                            })
                        }
                        "p2p_join" -> {
                            val p = extra.split("|")
                            db.child("p2p_lobby").child(p[0]).removeValue()
                            startActivity(Intent(context, BattlefieldActivity::class.java).apply { 
                                putExtra("ROOM_ID", p[0])
                                putExtra("GAME_TYPE", p[1])
                                putExtra("IS_CREATOR", false) 
                            })
                        }
                    }
                } else {
                    Toast.makeText(context, "Insufficient Balance", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}
