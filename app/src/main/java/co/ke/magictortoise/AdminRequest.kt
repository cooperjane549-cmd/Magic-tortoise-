package co.ke.magictortoise

data class AdminRequest(
    var id: String = "",                // Changed to var so we can set child.key
    var userId: String = "",            // Changed to var for safety
    var type: String = "",              // Changed to var so we can set the node name
    var nodeSource: String = "",        
    var mpesaCode: String = "",         // Changed to var
    var socialLink: String = "",        // Changed to var
    var screenshotBase64: String = "",  // Changed to var
    var status: String = "pending"      // Changed to var
)
