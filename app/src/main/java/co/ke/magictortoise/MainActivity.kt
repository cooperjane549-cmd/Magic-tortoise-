package co.ke.magictortoise

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.appcompat.app.AppCompatActivity
import co.ke.magictortoise.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var adCount = 0
    private var shellBalance = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupAdLogic()
        setupPurchaseLogic()
    }

    private fun setupAdLogic() {
        binding.btnWatchAd.setOnClickListener {
            // This is where AdMob call will go
            processAdCompletion()
        }
    }

    private fun processAdCompletion() {
        adCount++
        binding.adProgressBar.progress = adCount

        when (adCount) {
            15 -> {
                rewardUser(1.00)
                binding.tvStageStatus.text = "Stage 2: Next 10 ads for KES 0.50"
            }
            25 -> {
                rewardUser(0.50)
                binding.tvStageStatus.text = "Stage 3: Last 10 ads for KES 0.50 bonus!"
            }
            35 -> {
                rewardUser(0.50)
                adCount = 0 // Reset cycle
                binding.adProgressBar.progress = 0
                binding.tvStageStatus.text = "Magic cycle complete! Start again?"
            }
        }
    }

    private fun rewardUser(amount: Double) {
        shellBalance += amount
        binding.tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
    }

    private fun setupPurchaseLogic() {
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toDoubleOrNull() ?: 0.0
                val discountedPrice = input * 0.98 // 2% Discount
                binding.tvPayAmount.text = "You pay: KES ${String.format("%.2f", discountedPrice)}"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}
