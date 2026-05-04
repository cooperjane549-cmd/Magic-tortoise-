package co.ke.magictortoise

data class AdminRequest(
    val id: String = "",
    val userId: String = "",
    val socialLink: String = "",
    val mpesaCode: String = "",
    val status: String = "pending",
    val type: String = ""
)
