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
        holder.tvType.text = request.type
        holder.tvDetail.text = "Code: ${request.mpesaCode}\nLink: ${request.socialLink}"

        holder.btnApprove.setOnClickListener { onApprove(request) }
        holder.btnReject.setOnClickListener { /* Add delete logic if needed */ }
    }

    override fun getItemCount() = requests.size
}
