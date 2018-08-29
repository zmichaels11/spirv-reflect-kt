import demo.spirvreflect.Reflect
import org.junit.Test
import java.nio.channels.FileChannel
import java.nio.file.Paths

class TestReflect {
    @Test
    fun runTest() {
        FileChannel.open(Paths.get("shaders", "image_colortransform.comp.spv")).use { file ->
            val pBinary = file.map(FileChannel.MapMode.READ_ONLY, 0, file.size())
            val reflect = Reflect(pBinary)

            System.out.println("Inputs")
            System.out.println(reflect.inputs)
            System.out.println("Outputs")
            System.out.println(reflect.outputs)
            System.out.println("Uniforms")
            System.out.println(reflect.uniforms)
            System.out.println("Uniform Blocks")
            System.out.println(reflect.uniformBlocks)

        }
    }
}