package co.ke.magictortoise.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import co.ke.magictortoise.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.ByteArrayOutputStream
import java.io.InputStream

class OffersFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private val PICK_IMAGE_REQUEST = 71
    private var filePath: Uri? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_offers, container, false)
        database = FirebaseDatabase.getInstance().reference

        // 1. Create Task Button (Advertiser / KES 450) - FEATURE PRESERVED
        view.findViewById<View>(R.id.btn_create_task)?.setOnClickListener {
            showCreateTaskDialog()
        }

        // 2. TikTok GO Button - FEATURE PRESERVED
        view.findViewById<View>(R.id.btn_tiktok_go)?.setOnClickListener {
            val tiktokUrl = "https://tiktok.com/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tiktokUrl))
            startActivity(intent)
        }

        // 3. Submit Proof Button (User earns KES 2.0) - FEATURE PRESERVED
        view.findViewById<View>(R.id.btn_tiktok_submit)?.setOnClickListener {
            launchGallery()
        }

        return view
    }

    private fun showCreateTaskDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_create_task, null)
        
        val etLink = dialogView.findViewById<EditText>(R.id.et_social_link)
        val etPayment = dialogView.findViewById<EditText>(R.id.et_payment_ref)
        
        builder.setView(dialogView)
        builder.setTitle("Promote Your Account")
        
        builder.setPositiveButton("Submit") { _, _ ->
            val link = etLink.text.toString().trim()
            val ref = etPayment.text.toString().trim().uppercase()
            
            if (link.isNotEmpty() && ref.isNotEmpty()) {
                savePendingTask(link, ref)
            } else {
                Toast.makeText(context, "Please fill in all details", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun launchGallery() {
        val intent = Intent().apply {
            type = "image/*"
            action = Intent.ACTION_GET_CONTENT
        }
        startActivityForResult(Intent.createChooser(intent, "Select Proof Screenshot"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            filePath = data.data
            // Instead of Storage, we use our new Database-only method
            uploadProofToDatabaseAsText()
        }
    }

    // NEW METHOD: Replaces Storage with Realtime Database String
    private fun uploadProofToDatabaseAsText() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        filePath?.let { uri ->
            try {
                Toast.makeText(context, "Processing proof...", Toast.LENGTH_SHORT).show()

                // Convert URI to Bitmap
                val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                // Compress and Convert to Base64 String
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 40, baos) // 40% quality is enough for proof
                val byteArray = baos.toByteArray()
                val imageString = Base64.encodeToString(byteArray, Base64.DEFAULT)

                val submission = mapOf(
                    "userId" to userId,
                    "screenshotBase64" to imageString, // The image is now text!
                    "status" to "pending",
                    "type" to "TikTok Follow",
                    "timestamp" to ServerValue.TIMESTAMP
                )

                database.child("mini_task_submissions").push().setValue(submission)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Proof submitted! KES 2.0 pending approval.", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Error: Access Denied. Check Rules.", Toast.LENGTH_SHORT).show()
                    }

            } catch (e: Exception) {
                Toast.makeText(context, "Failed to process image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePendingTask(link: String, ref: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        val taskRequest = mapOf(
            "userId" to userId,
            "socialLink" to link,
            "mpesaCode" to ref,
            "status" to "pending",
            "type" to "Promote Account",
            "timestamp" to ServerValue.TIMESTAMP
        )
        
        database.child("advertiser_requests").push().setValue(taskRequest)
            .addOnSuccessListener {
                Toast.makeText(context, "KES 450 Task Submitted!", Toast.LENGTH_LONG).show()
            }
    }
}
