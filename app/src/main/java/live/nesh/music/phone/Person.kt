package live.nesh.music.phone

import java.io.Serializable

data class Person(
    val name:String = "",
    val gmail:String = "",
    val age:Int = 0
) : Serializable