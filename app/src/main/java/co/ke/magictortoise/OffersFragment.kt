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
import com.google.firebase.storage.FirebaseStorage // Added missing import
import com.google.firebase.storage.StorageReference

class OffersFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private val PICK_IMAGE_REQUEST = 71
    private var filePath: Uri? = null
    private var currentTaskId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_offers, container, false)
        database = FirebaseDatabase.getInstance().reference

        // 1. Create Task Button (Advertiser)
        view.findViewById<View>(R.id.btn_create_task)?.setOnClickListener {
            showCreateTaskDialog()
        }

        // 2. TikTok GO Button
        view.findViewById<View>(R.id.btn_tiktok_go)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tiktok.com/"))
            startActivity(intent)
        }

        // 3. Submit Proof Button
        view.findViewById<View>(R.id.btn_tiktok_submit)?.setOnClickListener {
            currentTaskId = "tiktok_follow_task"
            launchGallery()
        }

        return view
    }

    private fun showCreateTaskDialog() {
        val builder = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_Dialog_Alert)
        val inflater = LayoutInflater.from(requireContext())
        val dialogView = inflater.inflate(R.layout.dialog_create_task, null)
        
        val etLink = dialogView.findViewById<EditText>(R.id.et_social_link)
        val etPayment = dialogView.findViewById<EditText>(R.id.et_payment_ref)
        
        builder.setView(dialogView)
        builder.setTitle("Promote Your Account")
        builder.setMessage("Pay KES 450 to Till: 3043489. Enter details below.")
        
        builder.setPositiveButton("Submit") { _, _ ->
            val link = etLink.text.toString().trim()
            val ref = etPayment.text.toString().trim()
            if (link.isNotEmpty() && ref.isNotEmpty()) {
                savePendingTask(link, ref)
            } else {
                Toast.makeText(context, "All fields required", Toast.LENGTH_SHORT).show()
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
        val storageRef: StorageReference = FirebaseStorage.getInstance().reference
            .child("task_proofs/$currentTaskId/$userId.jpg")

        filePath?.let { uri ->
            storageRef.putFile(uri).addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val submission = mapOf(
                        "userId" to userId,
                        "screenshotUrl" to downloadUri.toString(),
                        "status" to "pending",
                        "timestamp" to ServerValue.TIMESTAMP
                    )
                    database.child("mini_task_submissions").push().setValue(submission)
                    Toast.makeText(context, "Proof uploaded! Rewards after verification.", Toast.LENGTH_LONG).show()
                }
            }.addOnFailureListener {
                Toast.makeText(context, "Upload failed. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePendingTask(link: String, ref: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val taskRequest = mapOf(
            "userId" to userId,
            "link" to link,
            "mpesaRef" to ref,
            "status" to "awaiting_payment_verification",
            "timestamp" to ServerValue.TIMESTAMP
        )
        database.child("advertiser_requests").push().setValue(taskRequest)
        Toast.makeText(context, "Submitted! Task goes live after we verify payment.", Toast.LENGTH_LONG).show()
    }
}
