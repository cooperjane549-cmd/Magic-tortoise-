package co.ke.magictortoise

import android.app.AlertDialog
import android.content.Intent
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
        val btnSync = view.findViewById<Button>(R.id.btnSyncNow)
        val btnTournament = view.findViewById<Button>(R.id.btnEnterTournament)
        val btnCreateBattle = view.findViewById<Button>(R.id.btnCreateBattle)
        val p2pContainer = view.findViewById<LinearLayout>(R.id.p2pContainer)

        val uid = auth.currentUser?.uid ?: return

        // Fetch User Profile
        db.child("users").child(uid).get().addOnSuccessListener {
            myUsername = it.child("username").value?.toString()
        }

        // Live Tournament Updates
        db.child("tournaments").child("active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.child("players").childrenCount.toInt()
                val netJackpot = (count * 10.0) * 0.75
                currentJackpotDisplay = String.format("%.2f", netJackpot)
                tvPlayers?.text = "Participants: $count"
                tvJackpot?.text = "WIN KES $currentJackpotDisplay"
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // P2P Lobby Updates
        db.child("p2p_lobby").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                p2pContainer.removeAllViews()
                snapshot.children.forEach { battle ->
                    val creator = battle.child("name").value.toString()
                    val stake = battle.child("stake").value.toString()
                    val gameType = battle.child("game").value?.toString() ?: "Trivia Duel"
                    val battleUid = battle.key ?: ""

                    val battleView = layoutInflater.inflate(R.layout.item_p2p_battle, null)
                    battleView.findViewById<TextView>(R.id.tvBattleTitle).text = "$creator: $gameType"
                    battleView.findViewById<TextView>(R.id.tvBattleStake).text = "Stake: KES $stake"
                    
                    battleView.findViewById<Button>(R.id.btnJoinBattle).setOnClickListener {
                        if (battleUid != uid && checkProfile()) {
                            showJoinBattleGateway(battleUid, stake.toDouble(), gameType)
                        }
                    }
                    p2pContainer.addView(battleView)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        btnSync.setOnClickListener { if (checkProfile()) showSyncTierSelection(uid) }
        btnTournament.setOnClickListener { if (checkProfile()) showTournamentFullScreen() }
        btnCreateBattle.setOnClickListener { if (checkProfile()) showCreateBattleDialog(uid) }
    }

    private fun checkProfile(): Boolean {
        if (myUsername.isNullOrEmpty()) {
            Toast.makeText(context, "Set Username in Support tab first!", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun showTournamentFullScreen() {
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView) 
            .create()
        
        dialogView.findViewById<ImageButton>(R.id.btnCloseTournament).setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<Button>(R.id.btnJoinTournamentFinal).setOnClickListener {
            handleTransaction(auth.uid!!, 10.0, "tournament")
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showSyncTierSelection(uid: String) {
        val stakes = arrayOf("20/-", "50/-", "100/-", "250/-")
        val values = doubleArrayOf(20.0, 50.0, 100.0, 250.0)
        AlertDialog.Builder(context).setTitle("Select Stake").setItems(stakes) { _, i ->
            showSyncParticipantDialog(uid, values[i])
        }.show()
    }

    private fun showSyncParticipantDialog(uid: String, stake: Double) {
        val options = arrayOf("2 Players", "4 Players", "6 Players", "10 Players")
        val counts = intArrayOf(2, 4, 6, 10)
        AlertDialog.Builder(context).setTitle("Choose Pool Size").setItems(options) { _, i ->
            val room = "${counts[i]}_players_at_${stake.toInt()}_stake"
            handleTransaction(uid, stake, "sync", room)
        }.show()
    }

    private fun showJoinBattleGateway(creatorUid: String, stake: Double, game: String) {
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.layout_tournament_overlay, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnJoinTournamentFinal).text = "JOIN FOR $stake/-"
        dialogView.findViewById<Button>(R.id.btnJoinTournamentFinal).setOnClickListener {
            handleTransaction(auth.uid!!, stake, "p2p_join", "$creatorUid|$game")
            dialog.dismiss()
        }
        dialogView.findViewById<ImageButton>(R.id.btnCloseTournament).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showCreateBattleDialog(uid: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_battle, null)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()
        val etStake = dialogView.findViewById<EditText>(R.id.etStakeAmount)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerGameType)
        val games = arrayOf("Math Blitz", "Tap Tortoise", "Trivia Duel")
        spinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, games)

        dialogView.findViewById<Button>(R.id.btnPostBattle).setOnClickListener {
            val amount = etStake.text.toString().toDoubleOrNull() ?: 0.0
            if (amount >= 20.0) {
                handleTransaction(uid, amount, "p2p_create", spinner.selectedItem.toString())
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun handleTransaction(uid: String, amount: Double, type: String, extra: String = "") {
        db.child("users").child(uid).child("balance").runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val bal = mutableData.getValue(Double::class.java) ?: 0.0
                if (bal < amount) return Transaction.abort()
                mutableData.value = bal - amount
                return Transaction.success(mutableData)
            }
            override fun onComplete(err: DatabaseError?, comm: Boolean, snap: DataSnapshot?) {
                if (comm) {
                    when (type) {
                        "tournament" -> {
                            db.child("tournaments").child("active").child("players").child(uid).setValue(true)
                            (activity as? MainActivity)?.showTournamentOverlay(currentJackpotDisplay)
                        }
                        "sync" -> {
                            db.child("sync_active").child(extra).child("participants").child(uid).child("name").setValue(myUsername)
                            startActivity(Intent(context, SyncBattleActivity::class.java).putExtra("ROOM_ID", extra))
                        }
                        "p2p_create" -> {
                            val battle = mapOf("name" to myUsername, "stake" to amount, "game" to extra, "player1_score" to 0, "player2_score" to 0)
                            db.child("p2p_lobby").child(uid).setValue(battle)
                            val intent = Intent(context, BattlefieldActivity::class.java)
                            intent.putExtra("ROOM_ID", uid)
                            intent.putExtra("GAME_TYPE", extra)
                            intent.putExtra("IS_CREATOR", true)
                            startActivity(intent)
                        }
                        "p2p_join" -> {
                            val parts = extra.split("|")
                            val intent = Intent(context, BattlefieldActivity::class.java)
                            intent.putExtra("ROOM_ID", parts[0])
                            intent.putExtra("GAME_TYPE", parts[1])
                            intent.putExtra("IS_CREATOR", false)
                            startActivity(intent)
                        }
                    }
                }
            }
        })
    }
}
