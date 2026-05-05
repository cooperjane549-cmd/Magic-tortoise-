package co.ke.magictortoise

data class AdminRequest(
    var id: String = "",
    var userId: String = "",
    var type: String = "",
    var nodeSource: String = "",
    var mpesaCode: String = "",
    var socialLink: String = "",
    var screenshotBase64: String = "",
    var status: String = "pending",
    
    // NEW FIELDS for Data & Deposits
    var mb: Int = 0,            // To see if they bought 300MB or 1GB
    var price: Double = 0.0,    // To see how much they paid
    var timestamp: Long = 0     // To know when they requested it
)
