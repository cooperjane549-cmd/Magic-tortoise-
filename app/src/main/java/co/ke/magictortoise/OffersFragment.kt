package co.ke.magictortoise.fragments

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import co.ke.magictortoise.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class OffersFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private val PICK_IMAGE_REQUEST = 71
    private var filePath: Uri? = null
    private var currentTaskId: String = "tiktok_follow_task"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_offers, container, false)
        database = FirebaseDatabase.getInstance().reference

        // 1. Create Task Button (Advertiser / KES 450)
        view.findViewById<View>(R.id.btn_create_task)?.setOnClickListener {
            showCreateTaskDialog()
        }

        // 2. TikTok GO Button
        view.findViewById<View>(R.id.btn_tiktok_go)?.setOnClickListener {
            // Updated to use a safer intent for external links
            val tiktokUrl = "https://tiktok.com/"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tiktokUrl))
            startActivity(intent)
        }

        // 3. Submit Proof Button (User earns KES 2.0)
        view.findViewById<View>(R.id.btn_tiktok_submit)?.setOnClickListener {
            launchGallery()
        }

        return view
    }

    private fun showCreateTaskDialog() {
        // Using a standard Alert Dialog
        val builder = AlertDialog.Builder(requireContext())
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_create_task, null)
        
        val etLink = dialogView.findViewById<EditText>(R.id.et_social_link)
        val etPayment = dialogView.findViewById<EditText>(R.id.et_payment_ref)
        
        builder.setView(dialogView)
        builder.setTitle("Promote Your Account")
        
        builder.setPositiveButton("Submit") { _, _ ->
            val link = etLink.text.toString().trim()
            val ref = etPayment.text.toString().trim().uppercase() // Ensure M-Pesa code is CAPS
            
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
            uploadProofToFirebase()
        }
    }

    private fun uploadProofToFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        // Unique name for each screenshot using timestamp
        val fileName = "proof_${System.currentTimeMillis()}.jpg"
        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
            .child("task_proofs/$userId/$fileName")

        filePath?.let { uri ->
            Toast.makeText(context, "Uploading proof...", Toast.LENGTH_SHORT).show()
            
            storageRef.putFile(uri).addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val submission = mapOf(
                        "userId" to userId,
                        "screenshotUrl" to downloadUri.toString(),
                        "status" to "pending", // Fixed: Matches Admin Panel filter
                        "type" to "TikTok Follow",
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                    database.child("mini_task_submissions").push().setValue(submission)
                    Toast.makeText(context, "Proof submitted! KES 2.0 pending approval.", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "Upload failed. Check internet.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePendingTask(link: String, ref: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        
        val taskRequest = mapOf(
            "userId" to userId,
            "socialLink" to link,
            "mpesaCode" to ref,
            "status" to "pending", // Fixed: Matches Admin Panel filter
            "type" to "Promote Account",
            "timestamp" to ServerValue.TIMESTAMP
        )
        
        // This goes to the advertiser_requests node
        database.child("advertiser_requests").push().setValue(taskRequest)
            .addOnSuccessListener {
                Toast.makeText(context, "KES 450 Task Submitted! Checking payment...", Toast.LENGTH_LONG).show()
            }
    }
}
