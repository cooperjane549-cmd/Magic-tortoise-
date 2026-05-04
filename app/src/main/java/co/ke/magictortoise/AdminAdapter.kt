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
    private val requests: List<AdminRequest>,
    private val onApprove: (AdminRequest) -> Unit
) : RecyclerView.Adapter<AdminAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_request_type)
        val tvDetail: TextView = view.findViewById(R.id.tv_request_detail)
        val ivScreenshot: ImageView = view.findViewById(R.id.iv_screenshot) // Add this to your XML
        val btnApprove: Button = view.findViewById(R.id.btn_approve)
        val btnReject: Button = view.findViewById(R.id.btn_reject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        
        holder.tvType.text = request.type.ifEmpty { "Unknown Task" }
        
        // Show M-Pesa code or Social Link based on what is available
        val details = if (request.mpesaCode.isNotEmpty()) {
            "M-Pesa: ${request.mpesaCode}"
        } else {
            "Link: ${request.socialLink}"
        }
        holder.tvDetail.text = details

        // --- NEW IMAGE DECODING LOGIC ---
        if (!request.screenshotBase64.isNullOrEmpty()) {
            try {
                val decodedBytes = Base64.decode(request.screenshotBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                holder.ivScreenshot.visibility = View.VISIBLE
                holder.ivScreenshot.setImageBitmap(bitmap)
            } catch (e: Exception) {
                holder.ivScreenshot.visibility = View.GONE
            }
        } else {
            holder.ivScreenshot.visibility = View.GONE
        }
        // --------------------------------

        holder.btnApprove.setOnClickListener { 
            onApprove(request) 
        }
        
        holder.btnReject.setOnClickListener { 
            holder.itemView.alpha = 0.5f 
        }
    }

    override fun getItemCount() = requests.size
}
