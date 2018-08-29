package demo.spirvreflect

data class GLSLArrayType(
        val elementType: GLSLType,
        val elements: Int) : GLSLType