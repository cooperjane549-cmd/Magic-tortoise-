package co.ke.magictortoise

data class AdminRequest(
    var id: String = "",
    val userId: String = "",
    var type: String = "",
    var nodeSource: String = "",        // This fixes the AdminActivity errors
    val mpesaCode: String = "",
    val socialLink: String = "",
    val screenshotBase64: String = "",  // This fixes the AdminAdapter errors
    val status: String = "pending"
)
