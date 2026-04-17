package co.ke.magictortoise

import android.net.Uri
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

        setupVideoBackground()
        setupAdLogic()
        setupPurchaseLogic()
    }

    private fun setupVideoBackground() {
        val videoPath = "android.resource://" + packageName + "/" + R.raw.magic_bg
        val uri = Uri.parse(videoPath)
        binding.backgroundVideo.setVideoURI(uri)
        
        // Loop the video
        binding.backgroundVideo.setOnPreparedListener { mp ->
            mp.isLooping = true
            // Mute the video so it's just a background effect
            mp.setVolume(0f, 0f) 
            binding.backgroundVideo.start()
        }
    }

    private fun setupAdLogic() {
        binding.btnWatchAd.setOnClickListener {
            adCount++
            binding.adProgressBar.progress = adCount
            
            when (adCount) {
                15 -> {
                    shellBalance += 1.0
                    binding.tvStageStatus.text = "Stage 2: 10 ads for KES 0.50"
                }
                25 -> {
                    shellBalance += 0.5
                    binding.tvStageStatus.text = "Stage 3: 10 ads for KES 0.50 bonus"
                }
                35 -> {
                    shellBalance += 0.5
                    adCount = 0
                    binding.adProgressBar.progress = 0
                    binding.tvStageStatus.text = "Cycle Complete! KES 2.0 earned."
                }
            }
            binding.tvBalance.text = "KES ${String.format("%.2f", shellBalance)}"
        }
    }

    private fun setupPurchaseLogic() {
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString().toDoubleOrNull() ?: 0.0
                val pay = input * 0.98
                binding.tvPayAmount.text = "You pay: KES ${String.format("%.2f", pay)}"
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    // Ensure video restarts when user comes back to the app
    override fun onResume() {
        super.onResume()
        binding.backgroundVideo.start()
    }
}
