package demo.spirvreflect

data class EntryPoint(
        val executionModel: ExecutionModel = ExecutionModel.UNKNOWN,
        val entryPoint: Int = 0,
        val name: String = "main",
        val interfaceIDs: List<Int> = emptyList())