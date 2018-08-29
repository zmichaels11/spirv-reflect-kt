package demo.spirvreflect

data class Output(
        val model: ExecutionModel,
        val location: Int,
        val name: String?,
        val type: GLSLType)