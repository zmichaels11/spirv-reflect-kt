package demo.spirvreflect

data class Uniform(
        val model: ExecutionModel,
        val binding: Int,
        val descriptorSet: Int,
        val name: String?,
        val type: GLSLType)