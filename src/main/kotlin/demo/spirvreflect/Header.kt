package demo.spirvreflect

data class Header(
        val magicNumber: Int,
        val versionNumber: Int,
        val generator: Int,
        val bound: Int,
        val schema: Int)