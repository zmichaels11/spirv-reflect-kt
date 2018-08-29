package demo.spirvreflect

data class Input(
        val model: ExecutionModel,
        val location: Int,
        val name: String?,
        val type: GLSLType)