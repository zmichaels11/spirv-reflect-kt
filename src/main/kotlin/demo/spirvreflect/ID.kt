package demo.spirvreflect

data class ID(
        var name: String? = null,
        val decorations: MutableList<Decoration> = ArrayList(),
        val members: MutableList<Member> = ArrayList(),
        var type: Type? = null,
        var variable: Variable? = null,
        var constantValue: Constant? = null)