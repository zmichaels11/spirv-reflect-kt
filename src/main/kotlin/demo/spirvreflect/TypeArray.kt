package demo.spirvreflect

data class TypeArray(
        val elementType: Int,
        val lengthID: Int) : Type