package demo.spirvreflect

data class Member(
        var name: String? = null,
        val decorations: MutableList<Decoration> = ArrayList())