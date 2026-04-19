package co.ke.magictortoise

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.*
import java.util.concurrent.TimeUnit

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    
    // UI Elements
    private lateinit var etPhone: EditText
    private lateinit var etOTP: EditText
    private lateinit var btnSend: Button
    private lateinit var btnVerify: Button
    private lateinit var otpContainer: LinearLayout
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // If user is already logged in, skip to MainActivity
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        etPhone = findViewById(R.id.etPhoneNumber)
        etOTP = findViewById(R.id.etOTP)
        btnSend = findViewById(R.id.btnSendCode)
        btnVerify = findViewById(R.id.btnVerifyCode)
        otpContainer = findViewById(R.id.otpContainer)
        progressBar = findViewById(R.id.loginProgress)

        btnSend.setOnClickListener {
            val phone = etPhone.text.toString().trim()
            if (phone.isNotEmpty() && phone.length > 9) {
                sendVerificationCode(phone)
            } else {
                Toast.makeText(this, "Enter valid number (e.g. +254...)", Toast.LENGTH_SHORT).show()
            }
        }

        btnVerify.setOnClickListener {
            val code = etOTP.text.toString().trim()
            if (code.length == 6 && verificationId != null) {
                verifyCode(code)
            }
        }
    }

    private fun sendVerificationCode(phone: String) {
        progressBar.visibility = View.VISIBLE
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, e.message, Toast.LENGTH_LONG).show()
                }

                override fun onCodeSent(vId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    progressBar.visibility = View.GONE
                    verificationId = vId
                    otpContainer.visibility = View.VISIBLE
                    btnSend.visibility = View.GONE
                    Toast.makeText(this@LoginActivity, "Code Sent", Toast.LENGTH_SHORT).show()
                }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        signInWithCredential(credential)
    }

    private fun signInWithCredential(credential: PhoneAuthCredential) {
        progressBar.visibility = View.VISIBLE
        auth.signInWithCredential(credential).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Login Failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
