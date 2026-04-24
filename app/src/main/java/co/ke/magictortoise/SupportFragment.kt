package co.ke.magictortoise

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.util.concurrent.TimeUnit

class SupportFragment : Fragment() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_support, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etUsername = view.findViewById<EditText>(R.id.etUsername)
        val etPhone = view.findViewById<EditText>(R.id.etPhoneNumber)
        val ivProfile = view.findViewById<ImageView>(R.id.ivUserProfilePic)
        val btnSave = view.findViewById<Button>(R.id.btnSaveProfile)
        val btnWA = view.findViewById<Button>(R.id.btnWhatsApp)
        val btnTG = view.findViewById<Button>(R.id.btnTelegram)
        val tvNote = view.findViewById<TextView>(R.id.tvRestrictionNote)

        val user = auth.currentUser
        val uid = user?.uid ?: return

        // Load Google Profile Picture
        user.photoUrl?.let {
            Glide.with(this).load(it).into(ivProfile)
        }

        // 1. Fetch User Data and Check 2-Year Lock
        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val username = snapshot.child("username").value?.toString() ?: ""
                val phone = snapshot.child("phone").value?.toString() ?: ""
                val lastUpdated = snapshot.child("profileLastUpdated").getValue(Long::class.java) ?: 0L

                etUsername.setText(username)
                etPhone.setText(phone)

                // Logic for 2-year restriction (730 days)
                val twoYearsInMillis = 730L * 24 * 60 * 60 * 1000
                val currentTime = System.currentTimeMillis()

                if (currentTime - lastUpdated < twoYearsInMillis && lastUpdated != 0L) {
                    btnSave.isEnabled = false
                    btnSave.text = "LOCKED (2-YEAR RULE)"
                    etUsername.isEnabled = false
                    etPhone.isEnabled = false
                    tvNote.text = "Profile Locked. Available again in 2028."
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // 2. Save Logic
        btnSave.setOnClickListener {
            val name = etUsername.text.toString().trim()
            val phone = etPhone.text.toString().trim()

            if (name.length < 3 || phone.length < 10) {
                Toast.makeText(context, "Username too short or invalid phone!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val updates = mapOf(
                "username" to name,
                "phone" to phone,
                "profileLastUpdated" to System.currentTimeMillis()
            )

            db.child("users").child(uid).updateChildren(updates).addOnSuccessListener {
                Toast.makeText(context, "Profile Secured! See you in 2 years.", Toast.LENGTH_LONG).show()
                btnSave.isEnabled = false // Lock immediately after save
            }
        }

        // 3. Social Links
        btnWA.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/254789574046")))
        }

        btnTG.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/grindganghustle")))
        }
    }
}
