package co.ke.magictortoise

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminAdapter(
    private var requests: List<AdminRequest>, // Changed to var so we can update it
    private val onApprove: (AdminRequest) -> Unit
) : RecyclerView.Adapter<AdminAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_request_type)
        val tvDetail: TextView = view.findViewById(R.id.tv_request_detail)
        val ivScreenshot: ImageView = view.findViewById(R.id.iv_screenshot)
        val btnApprove: Button = view.findViewById(R.id.btn_approve)
        val btnReject: Button = view.findViewById(R.id.btn_reject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        
        // Safe check for Type
        holder.tvType.text = if (request.type.isNullOrEmpty()) "Unknown Task" else request.type
        
        // Safe check for Details to prevent null pointer crashes
        val code = request.mpesaCode ?: ""
        val link = request.socialLink ?: ""
        
        val details = if (code.isNotEmpty()) {
            "M-Pesa: $code"
        } else if (link.isNotEmpty()) {
            "Link: $link"
        } else {
            "User ID: ${request.userId}"
        }
        holder.tvDetail.text = details

        // --- SAFE IMAGE DECODING ---
        val base64String = request.screenshotBase64
        if (!base64String.isNullOrEmpty()) {
            try {
                // Remove potential whitespace that causes decoding crashes
                val cleanString = base64String.trim()
                val decodedBytes = Base64.decode(cleanString, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                
                if (bitmap != null) {
                    holder.ivScreenshot.visibility = View.VISIBLE
                    holder.ivScreenshot.setImageBitmap(bitmap)
                } else {
                    holder.ivScreenshot.visibility = View.GONE
                }
            } catch (e: Exception) {
                holder.ivScreenshot.visibility = View.GONE
            }
        } else {
            holder.ivScreenshot.visibility = View.GONE
        }

        holder.btnApprove.setOnClickListener { onApprove(request) }
        holder.btnReject.setOnClickListener { holder.itemView.alpha = 0.5f }
    }

    override fun getItemCount() = requests.size

    // Add this helper function to update data safely
    fun updateData(newList: List<AdminRequest>) {
        this.requests = newList
        notifyDataSetChanged()
    }
}
