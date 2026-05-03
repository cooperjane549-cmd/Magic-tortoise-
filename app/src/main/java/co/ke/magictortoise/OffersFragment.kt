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

class OffersFragment : Fragment() {

    private lateinit var database: DatabaseReference
    private val PICK_IMAGE_REQUEST = 71
    private var filePath: Uri? = null
    private var currentTaskId: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_offers, container, false)
        database = FirebaseDatabase.getInstance().reference

        // 1. Create Task Button (For Advertisers)
        view.findViewById<View>(R.id.btn_create_task).setOnClickListener {
            showCreateTaskDialog()
        }

        // 2. TikTok "GO" Button
        view.findViewById<View>(R.id.btn_tiktok_go).setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://tiktok.com/@your_account")) // Update this later
            startActivity(intent)
        }

        // 3. Submit Proof Button (Triggers Gallery)
        view.findViewById<View>(R.id.btn_tiktok_submit).setOnClickListener {
            currentTaskId = "tiktok_task_001" // Unique ID for this task
            launchGallery()
        }

        return view
    }

    private fun showCreateTaskDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Promote Your Account")
        
        val layout = LayoutInflater.from(context).inflate(R.layout.dialog_create_task, null)
        val etLink = layout.findViewById<EditText>(R.id.et_social_link)
        val etPayment = layout.findViewById<EditText>(R.id.et_payment_ref)
        
        builder.setView(layout)
        builder.setMessage("Pay KES 450 to Till: 3043489. Enter your link and M-Pesa code below.")
        
        builder.setPositiveButton("Submit") { _, _ ->
            val link = etLink.text.toString()
            val ref = etPayment.text.toString()
            if (link.isNotEmpty() && ref.isNotEmpty()) {
                savePendingTask(link, ref)
            }
        }
        builder.show()
    }

    private fun launchGallery() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Screenshot"), PICK_IMAGE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null && data.data != null) {
            filePath = data.data
            uploadProofToFirebase()
        }
    }

    private fun uploadProofToFirebase() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference.child("proofs/$currentTaskId/$userId.jpg")

        filePath?.let { uri ->
            storageRef.putFile(uri).addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    val submission = mapOf(
                        "userId" to userId,
                        "screenshot" to downloadUri.toString(),
                        "status" to "pending"
                    )
                    database.child("mini_task_submissions").push().setValue(submission)
                    Toast.makeText(context, "Proof uploaded! Pending approval.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun savePendingTask(link: String, ref: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val taskRequest = mapOf(
            "userId" to userId,
            "link" to link,
            "mpesaRef" to ref,
            "status" to "unpaid"
        )
        database.child("task_requests").push().setValue(taskRequest)
        Toast.makeText(context, "Request sent. Task will go live after payment verify.", Toast.LENGTH_LONG).show()
    }
}
