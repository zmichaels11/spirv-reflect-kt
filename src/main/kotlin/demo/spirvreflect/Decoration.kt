package demo.spirvreflect

data class Decoration(
        val type: DecorationType,
        val literals: List<Int> = emptyList())