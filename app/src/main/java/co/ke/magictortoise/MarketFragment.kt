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

        // Sync main market view with real-time tournament data
        db.child("tournaments").child("active").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val count = snapshot.child("players").childrenCount.toInt()
                // Formula: 75% of the total 10/- entry pool
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
        
        // Sync the Jackpot display inside the overlay
        view.findViewById<TextView>(R.id.tvOverlayJackpot)?.text = "KES $currentJackpotDisplay"

        view.findViewById<ImageButton>(R.id.btnCloseTournament).setOnClickListener { dialog.dismiss() }
        view.findViewById<Button>(R.id.btnJoinTournamentFinal).setOnClickListener {
            handleTransaction(auth.uid!!, 10.0, "tournament")
            dialog.dismiss()
        }
        dialog.show()
    }

    // [Standard Sync and P2P Dialogs Omitted for brevity, use previous version for those]

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
                            // Register player
                            db.child("tournaments").child("active").child("players").child(uid).setValue(true)
                            
                            // CRASH FIX: Run on UI thread and check for null activity
                            activity?.runOnUiThread {
                                try {
                                    (activity as? MainActivity)?.showTournamentOverlay(currentJackpotDisplay)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Joined Tournament!", Toast.LENGTH_SHORT).show()
                                }
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
