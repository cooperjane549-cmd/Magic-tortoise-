package co.ke.magictortoise

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.slider.Slider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class MarketFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Use a try-catch to prevent the whole app from dying if the XML is bad
        return try {
            val root = inflater.inflate(R.layout.fragment_market, container, false)
            
            // Wire up only the slider for now to test
            val swipe = root.findViewById<Slider>(R.id.swipeConfirm)
            swipe?.addOnChangeListener { _, value, _ ->
                if (value >= 95f) {
                    Toast.makeText(context, "Magic Order Sent!", Toast.LENGTH_SHORT).show()
                    swipe.value = 0f
                }
            }
            root
        } catch (e: Exception) {
            // If the XML is broken, show a simple text view instead of crashing
            val tv = TextView(context)
            tv.text = "Market Layout Error: ${e.message}"
            tv
        }
    }
}
