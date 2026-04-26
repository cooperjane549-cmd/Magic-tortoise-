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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_market, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI References
        val btnTournament = view.findViewById<Button>(R.id.btnEnterTournament)
        val btnSync = view.findViewById<Button>(R.id.btnSyncNow)
        val btnCreate = view.findViewById<Button>(R.id.btnCreateBattle)

        // 1. Get Username for profile verification
        db.child("users").child(auth.currentUser!!.uid).child("username").get().addOnSuccessListener {
            myUsername = it.value?.toString()
        }

        // 2. TRIGGER: Full-Screen Tournament Pop-Up
        btnTournament.setOnClickListener {
            showTournamentFullScreen()
        }

        // 3. TRIGGER: Sync Multi-Selection
        btnSync.setOnClickListener {
            showSyncStakeSelection()
        }

        // 4. TRIGGER: P2P Create (For Math/Tap/Trivia)
        btnCreate.setOnClickListener {
            // This triggers the existing P2P popup files you mentioned
            showCreateBattleDialog() 
        }
    }

    // --- FULL SCREEN TOURNAMENT POP-UP ---
    private fun showTournamentFullScreen() {
        val dialogView = layoutInflater.inflate(R.layout.layout_tournament_gateway, null)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(dialogView)
            .create()

        val btnClose = dialogView.findViewById<ImageButton>(R.id.btnCloseTournament)
        val btnJoin = dialogView.findViewById<Button>(R.id.btnJoinTournamentFinal)

        btnClose.setOnClickListener { dialog.dismiss() }
        btnJoin.setOnClickListener {
            // Logic to deduct 10/- and add to tournament node
            handleTransaction(10.0, "tournament")
            dialog.dismiss()
        }

        dialog.show()
    }

    // --- SYNC MULTI-PRICE LOGIC ---
    private fun showSyncStakeSelection() {
        val stakes = arrayOf("20/-", "50/-", "100/-", "250/-")
        val values = doubleArrayOf(20.0, 50.0, 100.0, 250.0)
        
        AlertDialog.Builder(context).setTitle("Choose Stake Amount")
            .setItems(stakes) { _, i ->
                showSyncParticipantSelection(values[i])
            }.show()
    }

    private fun showSyncParticipantSelection(stake: Double) {
        val options = arrayOf("2 Players", "3 Players", "4 Players", "6 Players", "10 Players")
        val counts = intArrayOf(2, 3, 4, 6, 10)
        
        AlertDialog.Builder(context).setTitle("How many participants?")
            .setItems(options) { _, i ->
                val room = "${counts[i]}_players_at_${stake.toInt()}_stake"
                handleTransaction(stake, "sync", room)
            }.show()
    }

    private fun handleTransaction(amount: Double, type: String, extra: String = "") {
        // Shared logic to check balance, deduct, and route to the battlefield
        // (Similar to the logic we've refined previously)
    }
}
