package co.ke.magictortoise

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AdminAdapter(
    private val requests: List<AdminRequest>,
    private val onApprove: (AdminRequest) -> Unit
) : RecyclerView.Adapter<AdminAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvType: TextView = view.findViewById(R.id.tv_request_type)
        val tvDetail: TextView = view.findViewById(R.id.tv_request_detail)
        val btnApprove: Button = view.findViewById(R.id.btn_approve)
        val btnReject: Button = view.findViewById(R.id.btn_reject)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_request, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val request = requests[position]
        
        // Use default values if data is missing to prevent blank text
        holder.tvType.text = request.type.ifEmpty { "Unknown Task" }
        
        // Format the detail view clearly
        val details = "Code: ${request.mpesaCode}\nLink: ${request.socialLink}"
        holder.tvDetail.text = details

        holder.btnApprove.setOnClickListener { 
            onApprove(request) 
        }
        
        holder.btnReject.setOnClickListener { 
            // Optional: You could add a 'delete' call here later
            holder.itemView.alpha = 0.5f // Gray it out to show it's ignored
        }
    }

    override fun getItemCount() = requests.size
}
