package co.ke.magictortoise

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class AdminRequest(
    var id: String = "",
    
    @get:PropertyName("socialLink")
    @set:PropertyName("socialLink")
    var socialLink: String = "",
    
    @get:PropertyName("mpesaCode")
    @set:PropertyName("mpesaCode")
    var mpesaCode: String = "",
    
    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = "",
    
    @get:PropertyName("type")
    @set:PropertyName("type")
    var type: String = "",
    
    @get:PropertyName("userId")
    @set:PropertyName("userId")
    var userId: String = ""
)
