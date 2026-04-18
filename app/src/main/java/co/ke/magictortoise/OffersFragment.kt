package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class OffersFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_offers, container, false)

        // Firebase Sync: Check for new "Job Done" notifications or earnings
        val uid = auth.currentUser?.uid ?: return root

        db.child("users").child(uid).child("pending_rewards").get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                // Logic to claim pending offer rewards would go here
                Toast.makeText(context, "Checking for completed jobs...", Toast.LENGTH_SHORT).show()
            }
        }

        return root
    }
    
    // Function to trigger an external offer wall webview or SDK
    private fun openOfferWall() {
        // This is where you would link your specific Offer Wall URL or SDK trigger
    }
}
