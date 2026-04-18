package co.ke.magictortoise

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class SupportFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.fragment_support, container, false)

        val btnWhatsApp = root.findViewById<Button>(R.id.btnWhatsApp)
        
        btnWhatsApp.setOnClickListener {
            val phoneNumber = "+254789574046"
            val message = "Hi Magic Tortoise Support, I have a question about my airtime delivery."
            val url = "https://api.whatsapp.com/send?phone=$phoneNumber&text=${Uri.encode(message)}"
            
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        }

        return root
    }
}
