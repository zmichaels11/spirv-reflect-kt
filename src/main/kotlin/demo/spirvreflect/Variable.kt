package demo.spirvreflect

data class Variable(
        val type: Int,
        val storageClass: StorageClass,
        val initializer: List<Int> = emptyList())