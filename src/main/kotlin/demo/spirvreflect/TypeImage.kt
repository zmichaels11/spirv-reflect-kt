package demo.spirvreflect

data class TypeImage(
        val sampledType: Int,
        val dim: Dimensionality,
        val depth: Boolean,
        val arrayed: Boolean,
        val multisampled: Boolean,
        val sampled: Boolean,
        val imageFormat: ImageFormat,
        val accessQualifier: AccessQualifier? = null) : Type