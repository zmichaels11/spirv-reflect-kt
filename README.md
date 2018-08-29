# SpirV Reflect - Kotlin
SpirV-Reflect is a Kotlin module that can reflect information on Inputs, Uniforms,
and Outputs from a SpirV binary.

This works by interpreting a subset of SpirV instructions and then evaluating the
Variables that are exported as Input, Output, Uniform, and UniformConstant.

## How to use
Construct an instance of Reflect and handle the lists: 
* inputs
* outputs
* uniforms
* uniformBlocks

The Array _ids_ is publicly accessible if more complex reflection is needed.

## Example
``` kotlin
FileChannel.open(Paths.get("myshader.spv")).use { file ->
    val pBinary = file.map(FileChannel.MapMode.READ_ONLY, 0, file.size())
    val reflect = Reflect(pBinary)

    System.out.println("Inputs: ${reflect.inputs}")
    System.out.println("Outputs: ${reflect.outputs}")
    System.out.println("Uniforms: ${reflect.uniforms}")
    System.out.println("Uniform Blocks: ${reflect.uniformBlocks}")
}
```
## Other Languages/Dependencies
SpirV-Reflect depends on JVM/Kotlin only. This should work in a pure Java project
if kotlin-stdlib-jdk8 is included. This will however __not__ work in other Kotlin
targets due to requiring ByteBuffer.
