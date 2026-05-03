package co.ke.magictortoise.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import co.ke.magictortoise.R
import com.google.firebase.auth.FirebaseAuth

class OffersFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_offers, container, false)

        val userId = FirebaseAuth.getInstance().currentUser?.uid

        // 1. Referral Logic
        view.findViewById<Button>(R.id.btn_share_referral).setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            val shareBody = "Join Magic Tortoise and convert your Airtime/Bonga to Cash instantly! Use my code: $userId. Download here: [YOUR_LINK]"
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Magic Tortoise")
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody)
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        // 2. TikTok Task
        view.findViewById<Button>(R.id.btn_tiktok).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/@your_handle"))
            startActivity(intent)
            // Note: In a real app, you'd add a "Verify" button that checks a DB flag
            Toast.makeText(context, "Follow us to earn KES 2.00", Toast.LENGTH_SHORT).show()
        }

        // 3. Telegram Task
        view.findViewById<Button>(R.id.btn_telegram).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/your_group"))
            startActivity(intent)
            Toast.makeText(context, "Join the community to earn KES 3.00", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
