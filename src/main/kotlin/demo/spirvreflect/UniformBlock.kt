package demo.spirvreflect

data class UniformBlock(
        val model: ExecutionModel,
        val binding: Int,
        val descriptorSet: Int,
        val name: String?,
        val size: Int)