package co.ke.magictortoise

import android.content.Intent
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

        btnSync.setOnClickListener { if (checkProfile()) showSyncTierSelection(uid) }
        btnTournament.setOnClickListener { if (checkProfile()) showTournamentFullScreen() }
        btnCreateBattle.setOnClickListener { if (checkProfile()) showCreateBattleDialog(uid) }

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
    }

    private fun checkProfile(): Boolean {
        if (myUsername.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "Set Username in Support first", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showTournamentFullScreen() {
        val view = layoutInflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), android.R.style.Theme_NoTitleBar_Fullscreen)
            .setView(view).create()
        
        view.findViewById<TextView>(R.id.tvLiveQuestion)?.text = "CURRENT JACKPOT\nKES $currentJackpotDisplay"

        view.findViewById<ImageButton>(R.id.btnCloseTournament).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnJoinTournamentFinal).setOnClickListener {
            handleTransaction(auth.uid!!, 10.0, "tournament")
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showSyncTierSelection(uid: String) {
        val stakes = arrayOf("20/-", "50/-", "100/-", "250/-")
        val values = doubleArrayOf(20.0, 50.0, 100.0, 250.0)
        MaterialAlertDialogBuilder(requireContext()).setTitle("Select Stake").setItems(stakes) { _, i ->
            showSyncParticipantDialog(uid, values[i])
        }.show()
    }

    private fun showSyncParticipantDialog(uid: String, stake: Double) {
        val options = arrayOf("2 Players", "4 Players", "6 Players", "10 Players")
        val counts = intArrayOf(2, 4, 6, 10)
        MaterialAlertDialogBuilder(requireContext()).setTitle("Choose Pool").setItems(options) { _, i ->
            handleTransaction(uid, stake, "sync", "${counts[i]}_players_at_${stake.toInt()}_stake")
        }.show()
    }

    private fun showJoinBattleGateway(cUid: String, stake: Double, game: String) {
        val view = layoutInflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = MaterialAlertDialogBuilder(requireContext(), android.R.style.Theme_NoTitleBar_Fullscreen)
            .setView(view).create()
        view.findViewById<Button>(R.id.btnJoinTournamentFinal).text = "JOIN FOR $stake/-"
        view.findViewById<Button>(R.id.btnJoinTournamentFinal).setOnClickListener {
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
                            // Register the player for the upcoming timed event
                            db.child("tournaments").child("active").child("players").child(uid).setValue(true)
                            
                            activity?.runOnUiThread {
                                (activity as? MainActivity)?.showTournamentOverlay(currentJackpotDisplay)
                                Toast.makeText(context, "Registered for next Arena event!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        "sync" -> {
                            db.child("sync_active").child(extra).child("participants").child(uid).child("name").setValue(myUsername)
                            startActivity(Intent(context, SyncBattleActivity::class.java).putExtra("ROOM_ID", extra))
                        }
                        "p2p_create" -> {
                            db.child("p2p_lobby").child(uid).setValue(mapOf("name" to myUsername, "stake" to amount, "game" to extra))
                            startActivity(Intent(context, BattlefieldActivity::class.java).apply {
                                putExtra("ROOM_ID", uid); putExtra("GAME_TYPE", extra); putExtra("IS_CREATOR", true)
                            })
                        }
                        "p2p_join" -> {
                            val p = extra.split("|")
                            startActivity(Intent(context, BattlefieldActivity::class.java).apply {
                                putExtra("ROOM_ID", p[0]); putExtra("GAME_TYPE", p[1]); putExtra("IS_CREATOR", false)
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
