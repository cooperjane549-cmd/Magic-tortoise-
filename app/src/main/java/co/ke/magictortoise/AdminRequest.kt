package co.ke.magictortoise

// These names MUST match your Firebase screenshot exactly
data class AdminRequest(
    val id: String = "",
    val userId: String = "",
    val socialLink: String = "", // Matches 'socialLink' in screenshot
    val mpesaCode: String = "",  // Matches 'mpesaCode' in screenshot
    val status: String = "pending",
    val type: String = ""
)
