package demo.spirvreflect

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.stream.Collectors

class Reflect(pBinary: ByteBuffer) {

    val ids: Array<ID>
    val header: Header
    var entryPoint: EntryPoint = EntryPoint()
        private set

    val inputs: List<Input>
    val outputs: List<Output>
    val uniforms: List<Uniform>
    val uniformBlocks: List<UniformBlock>

    init {
        val magicNumber = pBinary.int

        when (magicNumber) {
            0x07230203 -> {}
            0x03022307 -> {
                val order = when (pBinary.order()) {
                    ByteOrder.BIG_ENDIAN -> ByteOrder.LITTLE_ENDIAN
                    ByteOrder.LITTLE_ENDIAN -> ByteOrder.BIG_ENDIAN
                    else -> throw UnsupportedOperationException("Only BIG_ENDIAN and LITTLE_ENDIAN ByteOrders are supported!")
                }

                pBinary.order(order)
            }
        }

        this.header = Header(magicNumber, pBinary.int, pBinary.int, pBinary.int, pBinary.int)
        this.ids = Array(header.bound) { ID() }

        while (pBinary.hasRemaining()) {
            val instruction = pBinary.slice().order(pBinary.order())

            instruction.limit(pBinary.getShort(pBinary.position() + 2) * Integer.BYTES)
            pBinary.position(pBinary.position() + instruction.remaining())

            val opcode = instruction.short
            val wordCount = instruction.short

            when (opcode) {
                OP_ENTRYPOINT -> {
                    val execModel = decodeExecutionModel(instruction)
                    val entryPoint = instruction.int
                    val name = decodeString(instruction)
                    val interfaceIDs = decodeLiterals(instruction)

                    this.entryPoint = EntryPoint(execModel, entryPoint, name, interfaceIDs)
                }
                OP_NAME -> {
                    val target = instruction.int
                    val name = decodeString(instruction)

                    this.ids[target].name = name
                }
                OP_DECORATE -> {
                    val target = instruction.int
                    val type = decodeDecorationType(instruction)
                    val literals = decodeLiterals(instruction)

                    this.ids[target].decorations.add(Decoration(type, literals))
                }
                OP_MEMBER_DECORATE -> {
                    val structureId = instruction.int
                    val memberId = instruction.int
                    val type = decodeDecorationType(instruction)
                    val literals = decodeLiterals(instruction)

                    while (this.ids[structureId].members.size <= memberId) {
                        this.ids[structureId].members.add(Member())
                    }

                    this.ids[structureId].members[memberId].decorations.add(Decoration(type, literals))
                }
                OP_TYPE_VOID -> {
                    val target = instruction.int

                    this.ids[target].type = TypeVoid()
                }
                OP_TYPE_BOOL -> {
                    val target = instruction.int

                    this.ids[target].type = TypeBool()
                }
                OP_TYPE_INT -> {
                    val target = instruction.int
                    val width = instruction.int
                    val signedness = instruction.int == 1

                    this.ids[target].type = TypeInt(width, signedness)
                }
                OP_TYPE_FLOAT -> {
                    val target = instruction.int
                    val width = instruction.int

                    this.ids[target].type = TypeFloat(width)
                }
                OP_TYPE_VECTOR -> {
                    val target = instruction.int
                    val componentType = instruction.int
                    val componentCount = instruction.int

                    this.ids[target].type = TypeVector(componentType, componentCount)
                }
                OP_TYPE_MATRIX -> {
                    val target = instruction.int
                    val columnType = instruction.int
                    val columnCount = instruction.int

                    this.ids[target].type = TypeMatrix(columnType, columnCount)
                }
                OP_TYPE_IMAGE -> {
                    val target = instruction.int
                    val sampledType = instruction.int
                    val dim = decodeDimensionality(instruction)
                    val depth = instruction.int == 1
                    val arrayed = instruction.int == 1
                    val ms = instruction.int == 1
                    val sampled = instruction.int == 1
                    val format = decodeImageFormat(instruction)
                    val accessQualifier = if (instruction.hasRemaining()) decodeAccessQualifier(instruction) else null

                    this.ids[target].type = TypeImage(sampledType, dim, depth, arrayed, ms, sampled, format, accessQualifier)
                }
                OP_TYPE_SAMPLED_IMAGE -> {
                    val target = instruction.int
                    val imageType = instruction.int

                    this.ids[target].type = TypeSampledImage(imageType)
                }
                OP_TYPE_ARRAY -> {
                    val target = instruction.int
                    val elementType = instruction.int
                    val length = instruction.int

                    this.ids[target].type = TypeArray(elementType, length)
                }
                OP_TYPE_STRUCT -> {
                    val target = instruction.int
                    val members = decodeLiterals(instruction)

                    this.ids[target].type = TypeStruct(members)
                }
                OP_TYPE_POINTER -> {
                    val target = instruction.int
                    val storageClass = decodeStorageClass(instruction)
                    val type = instruction.int

                    this.ids[target].type = TypePointer(storageClass, type)
                }
                OP_VARIABLE -> {
                    val type = instruction.int
                    val target = instruction.int
                    val storageClass = decodeStorageClass(instruction)
                    val initializer = decodeLiterals(instruction)

                    this.ids[target].variable = Variable(type, storageClass, initializer)
                }
                OP_MEMBER_NAME -> {
                    val type = instruction.int
                    val memberId = instruction.int
                    val name = decodeString(instruction)

                    while (this.ids[type].members.size <= memberId) {
                        this.ids[type].members.add(Member())
                    }

                    this.ids[type].members[memberId].name = name
                }
                OP_CONSTANT, OP_CONSTANT_COMPOSITE -> {
                    val type = instruction.int
                    val target = instruction.int
                    val values = decodeLiterals(instruction)

                    this.ids[target].constantValue = Constant(type, values)
                }
            }
        }

        this.inputs = findInputs(this.entryPoint.executionModel, this.ids)
        this.outputs = findOutputs(this.entryPoint.executionModel, this.ids)
        this.uniforms = findUniforms(this.entryPoint.executionModel, this.ids)
        this.uniformBlocks = findUniformBlocks(this.entryPoint.executionModel, this.ids)
    }

    companion object {
        const val OP_ENTRYPOINT: Short = 15
        const val OP_NAME: Short = 5
        const val OP_DECORATE: Short = 71
        const val OP_TYPE_VOID: Short = 19
        const val OP_TYPE_BOOL: Short = 20
        const val OP_TYPE_INT: Short = 21
        const val OP_TYPE_FLOAT: Short = 22
        const val OP_TYPE_VECTOR: Short = 23
        const val OP_TYPE_MATRIX: Short = 24
        const val OP_TYPE_POINTER: Short = 32
        const val OP_VARIABLE: Short = 59
        const val OP_MEMBER_NAME: Short = 6
        const val OP_MEMBER_DECORATE: Short = 72
        const val OP_TYPE_STRUCT: Short = 30
        const val OP_TYPE_IMAGE: Short = 25
        const val OP_TYPE_SAMPLED_IMAGE: Short = 27
        const val OP_TYPE_ARRAY: Short = 28
        const val OP_CONSTANT: Short = 43
        const val OP_CONSTANT_COMPOSITE: Short = 44

        fun decodeDimensionality(data: ByteBuffer) = when(data.int) {
            0 -> Dimensionality.DIM_1D
            1 -> Dimensionality.DIM_2D
            2 -> Dimensionality.DIM_3D
            3 -> Dimensionality.DIM_CUBE
            4 -> Dimensionality.DIM_RECT
            5 -> Dimensionality.DIM_BUFFER
            6 -> Dimensionality.DIM_SUBPASS_DATA
            else -> throw UnsupportedOperationException("Unsupported Image Dimensionality!")
        }

        fun decodeImageFormat(data: ByteBuffer) = when(data.int) {
            0 -> ImageFormat.UNKNOWN
            1 -> ImageFormat.R32_G32_B32_A32_SFLOAT
            2 -> ImageFormat.R16_G16_B16_A16_SFLOAT
            3 -> ImageFormat.R32_SFLOAT
            4 -> ImageFormat.R8_G8_B8_A8_UNORM
            5 -> ImageFormat.R8_G8_B8_A8_SNORM
            6 -> ImageFormat.R32_G32_SFLOAT
            7 -> ImageFormat.R16_G16_SFLOAT
            8 -> ImageFormat.R11_G11_B10_SFLOAT
            9 -> ImageFormat.R16_SFLOAT
            10 -> ImageFormat.R16_G16_B16_A16_UNORM
            11 -> ImageFormat.R10_G10_B10_A2_UNORM
            12 -> ImageFormat.R16_G16_UNORM
            13 -> ImageFormat.R8_G8_UNORM
            14 -> ImageFormat.R16_UNORM
            15 -> ImageFormat.R8_UNORM
            16 -> ImageFormat.R16_G16_B16_A16_SNORM
            17 -> ImageFormat.R16_G16_SNORM
            18 -> ImageFormat.R8_SNORM
            19 -> ImageFormat.R16_SNORM
            20 -> ImageFormat.R8_SNORM
            21 -> ImageFormat.R32_G32_B32_A32_SINT
            22 -> ImageFormat.R16_G16_B16_A16_SINT
            23 -> ImageFormat.R8_G8_B8_A8_SINT
            24 -> ImageFormat.R32_SINT
            25 -> ImageFormat.R32_G32_SINT
            26 -> ImageFormat.R16_G16_SINT
            27 -> ImageFormat.R8_G8_SINT
            28 -> ImageFormat.R16_SINT
            29 -> ImageFormat.R8_SINT
            30 -> ImageFormat.R32_G32_B32_A32_UINT
            31 -> ImageFormat.R16_G16_B16_A16_UINT
            32 -> ImageFormat.R8_G8_B8_A8_UINT
            33 -> ImageFormat.R32_UINT
            34 -> ImageFormat.R10_G10_B10_A2_UINT
            35 -> ImageFormat.R32_G32_UINT
            36 -> ImageFormat.R16_G16_UINT
            37 -> ImageFormat.R8_G8_UINT
            38 -> ImageFormat.R16_UINT
            39 -> ImageFormat.R8_UINT
            else -> throw UnsupportedOperationException("Unsupported ImageFormat!")
        }

        fun decodeAccessQualifier(data: ByteBuffer) = when (data.int) {
            0 -> AccessQualifier.READ_ONLY
            1 -> AccessQualifier.WRITE_ONLY
            2 -> AccessQualifier.READ_WRITE
            else -> throw UnsupportedOperationException("Unsupported AccessQualifier!")
        }

        fun sizeofType(type: Type, ids: Array<ID>) : Int = when (type) {
                is TypeArray -> {
                    val elementSize = ids[type.lengthID].constantValue!!.values[0]

                    elementSize * sizeofType(ids[type.elementType].type!!, ids)
                }
                is TypePointer -> sizeofType(ids[type.type].type!!, ids)
                is TypeStruct -> if (type.memberTypes.stream()
                                        .filter { memberId -> ids[memberId].type == null }
                                        .findFirst()
                                        .isPresent) -1
                                else type.memberTypes.stream()
                                        .map { memberId -> ids[memberId].type!! }
                                        .mapToInt { member -> sizeofType(member, ids) }
                                        .sum()
                else -> when (type) {
                    is TypeMatrix -> when (type.columnCount) {
                        2 -> 32
                        3 -> 48
                        4 -> 64
                        else -> throw UnsupportedOperationException("Matrix must have either 2, 3, or 4 columns!")
                    }
                    is TypeVector -> when (type.componentCount) {
                        2 -> 8
                        3, 4 -> 16
                        else -> throw UnsupportedOperationException("Matrix must have either 2, 3, or 4 columns!")
                    }
                    else -> throw UnsupportedOperationException("TypeSize inference requires padding to Vec4!")
                }
            }

        fun typeToGLSLType(type: Type, ids: Array<ID>) : GLSLType {
            return when (type) {
                is TypeArray -> {
                    val elementType = typeToGLSLType(ids[type.elementType].type!!, ids)
                    val elementSize = ids[type.lengthID].constantValue!!.values[0]

                    GLSLArrayType(elementType, elementSize)
                }
                is TypePointer -> typeToGLSLType(ids[type.type].type!!, ids)
                is TypeImage -> when (type.dim) {
                    Dimensionality.DIM_1D -> if (type.arrayed) GLSLBaseType.IMAGE_1D_ARRAY else GLSLBaseType.IMAGE_1D
                    Dimensionality.DIM_2D -> when {
                        type.arrayed -> if (type.multisampled) GLSLBaseType.IMAGE_2D_MS_ARRAY else GLSLBaseType.IMAGE_2D_ARRAY
                        type.multisampled -> GLSLBaseType.IMAGE_2D_MS
                        else -> GLSLBaseType.IMAGE_2D
                    }
                    Dimensionality.DIM_3D -> GLSLBaseType.IMAGE_3D
                    Dimensionality.DIM_CUBE -> if (type.arrayed) GLSLBaseType.IMAGE_CUBE_ARRAY else GLSLBaseType.IMAGE_CUBE
                    Dimensionality.DIM_RECT -> GLSLBaseType.IMAGE_2D_RECT
                    Dimensionality.DIM_BUFFER -> GLSLBaseType.IMAGE_BUFFER
                    else -> throw UnsupportedOperationException("Unsupported Type: $type")
                }
                is TypeSampledImage -> {
                    val imageType = ids[type.imageType].type!!

                    when (imageType) {
                        is TypeImage -> if (imageType.sampled) when (imageType.dim) {
                            Dimensionality.DIM_1D -> {
                                if (imageType.arrayed) {
                                    if (imageType.depth) GLSLBaseType.SAMPLER_1D_ARRAY_SHADOW else GLSLBaseType.SAMPLER_1D_ARRAY
                                } else {
                                    if (imageType.depth) GLSLBaseType.SAMPLER_1D_SHADOW else GLSLBaseType.SAMPLER_1D
                                }
                            }
                            Dimensionality.DIM_2D -> {
                                if (imageType.arrayed) {
                                    when {
                                        imageType.depth -> GLSLBaseType.SAMPLER_2D_ARRAY_SHADOW
                                        imageType.multisampled -> GLSLBaseType.SAMPLER_2D_MS_ARRAY
                                        else -> GLSLBaseType.SAMPLER_2D_ARRAY
                                    }
                                } else {
                                    when {
                                        imageType.depth -> GLSLBaseType.SAMPLER_2D_SHADOW
                                        imageType.multisampled -> GLSLBaseType.SAMPLER_2D_MS_ARRAY
                                        else -> GLSLBaseType.SAMPLER_2D
                                    }
                                }
                            }
                            Dimensionality.DIM_3D ->  GLSLBaseType.SAMPLER_3D
                            Dimensionality.DIM_CUBE -> if (imageType.arrayed) {
                                if (imageType.depth) GLSLBaseType.SAMPLER_CUBE_ARRAY_SHADOW else GLSLBaseType.SAMPLER_CUBE_ARRAY
                            } else if (imageType.depth) GLSLBaseType.SAMPLER_CUBE_SHADOW else GLSLBaseType.SAMPLER_CUBE
                            Dimensionality.DIM_RECT -> if (imageType.depth) GLSLBaseType.SAMPLER_2D_RECT_SHADOW else GLSLBaseType.SAMPLER_2D_RECT
                            Dimensionality.DIM_BUFFER -> GLSLBaseType.SAMPLER_BUFFER
                            else -> throw UnsupportedOperationException("Unsupported Type: $imageType")
                        } else {
                            throw UnsupportedOperationException("Unsupported Type: $imageType")
                        }
                        else -> throw UnsupportedOperationException("Unsupported Type: $imageType")
                    }
                }
                is TypeBool -> GLSLBaseType.BOOL
                is TypeFloat -> when (type.width) {
                    16, 32 -> GLSLBaseType.FLOAT
                    64 -> GLSLBaseType.DOUBLE
                    else -> throw UnsupportedOperationException("Unsupported Type: $type")
                }
                is TypeInt -> if (type.signedness) GLSLBaseType.INT else GLSLBaseType.UINT
                is TypeVector -> {
                    val componentType = ids[type.componentType].type!!
                    val componentCount = type.componentCount

                    when (componentType) {
                        is TypeBool -> {
                            when (componentCount) {
                                2 -> GLSLBaseType.BVEC2
                                3 -> GLSLBaseType.BVEC3
                                4 -> GLSLBaseType.BVEC4
                                else -> throw UnsupportedOperationException("Unsupported Vector Type: $componentType")
                            }
                        }
                        is TypeFloat -> {
                            when (componentType.width) {
                                16, 32 -> when (componentCount) {
                                    2 -> GLSLBaseType.VEC2
                                    3 -> GLSLBaseType.VEC3
                                    4 -> GLSLBaseType.VEC4
                                    else -> throw UnsupportedOperationException("Unsupported Vector Type: $componentType")
                                }
                                64 -> when (componentCount) {
                                    2 -> GLSLBaseType.DVEC2
                                    3 -> GLSLBaseType.DVEC3
                                    4 -> GLSLBaseType.DVEC4
                                    else -> throw UnsupportedOperationException("Unsupported Vector Type: $componentType")
                                }
                                else -> throw UnsupportedOperationException("Unsupported Vector Type: $componentType")
                            }
                        }
                        is TypeInt -> {
                            if (componentType.signedness) {
                                when (componentCount) {
                                    2 -> GLSLBaseType.IVEC2
                                    3 -> GLSLBaseType.IVEC3
                                    4 -> GLSLBaseType.IVEC4
                                    else -> throw UnsupportedOperationException("Unsupported Type: $componentType")
                                }
                            } else {
                                when (componentCount) {
                                    2 -> GLSLBaseType.UVEC2
                                    3 -> GLSLBaseType.UVEC3
                                    4 -> GLSLBaseType.UVEC4
                                    else -> throw UnsupportedOperationException("Unsupported Type: $componentType")
                                }
                            }
                        }
                        else -> throw UnsupportedOperationException("Unsupported Type: $componentType")
                    }
                }
                else -> throw UnsupportedOperationException("Unsupported Type: $type")
            }
        }

        fun findInputs(model: ExecutionModel, ids: Array<ID>): List<Input> = Arrays.stream(ids)
                .filter { id -> id.variable?.storageClass == StorageClass.INPUT }
                .map { id ->
                    id.decorations.stream()
                            .filter { decoration -> decoration.type == DecorationType.LOCATION }
                            .findFirst()
                            .map { it -> it.literals[0] }
                            .map {
                                val type = typeToGLSLType(ids[id.variable!!.type].type!!, ids)

                                Input(model, it, id.name, type)
                            }
                }
                .filter { it.isPresent }
                .map { it.get() }
                .collect(Collectors.toList())

        fun findOutputs(model: ExecutionModel, ids: Array<ID>): List<Output> = Arrays.stream(ids)
                .filter { id -> id.variable?.storageClass == StorageClass.OUTPUT }
                .map { id ->
                    id.decorations.stream()
                            .filter { decoration -> decoration.type == DecorationType.LOCATION }
                            .findFirst()
                            .map { it -> it.literals[0] }
                            .map {
                                val type = typeToGLSLType(ids[id.variable!!.type].type!!, ids)
                                Output(model, it, id.name, type)
                            }
                }
                .filter { it.isPresent }
                .map { it.get() }
                .collect(Collectors.toList())

        fun findUniformBlocks(model: ExecutionModel, ids: Array<ID>) : List<UniformBlock> = Arrays.stream(ids)
                .filter { id -> id.variable?.storageClass == StorageClass.UNIFORM }
                .map { id ->
                    val ptrType = ids[id.variable!!.type]
                    val blockType = ids[(ptrType.type as TypePointer).type]

                    val binding = id.decorations.stream()
                            .filter { decoration -> decoration.type == DecorationType.BINDING }
                            .findFirst()
                            .get()
                            .literals[0]

                    val descriptorSet = id.decorations.stream()
                            .filter { decoration -> decoration.type == DecorationType.DESCRIPTOR_SET}
                            .findFirst()
                            .get()
                            .literals[0]

                    val name = blockType.name
                    val structType = blockType.type as TypeStruct
                    val size = sizeofType(structType, ids)

                    UniformBlock(model, binding, descriptorSet, name, size)
                }
                .collect(Collectors.toList())

        fun findUniforms(model: ExecutionModel, ids: Array<ID>) : List<Uniform> = Arrays.stream(ids)
                .filter { id -> id.variable?.storageClass == StorageClass.UNIFORM_CONSTANT }
                .map { id ->
                    val binding = id.decorations.stream()
                            .filter { decoration -> decoration.type == DecorationType.BINDING }
                            .findFirst()
                            .get()
                            .literals[0]

                    val descriptorSet = id.decorations.stream()
                            .filter { decoration -> decoration.type == DecorationType.DESCRIPTOR_SET }
                            .findFirst()
                            .get()
                            .literals[0]

                    val type = typeToGLSLType(ids[id.variable!!.type].type!!, ids)

                    Uniform(model, binding, descriptorSet, id.name, type)
                }
                .collect(Collectors.toList())

        private fun decodeExecutionModel(data: ByteBuffer) = when (data.int) {
            0 -> ExecutionModel.VERTEX
            1 -> ExecutionModel.TESSELLATION_CONTROL
            2 -> ExecutionModel.TESSELLATION_EVALUATION
            3 -> ExecutionModel.GEOMETRY
            4 -> ExecutionModel.FRAGMENT
            5 -> ExecutionModel.COMPUTE
            else -> ExecutionModel.UNKNOWN
        }

        private fun decodeDecorationType(data: ByteBuffer) = when (data.int) {
            0 -> DecorationType.RELAXED_PRECISION
            1 -> DecorationType.SPEC_ID
            2 -> DecorationType.BLOCK
            3 -> DecorationType.BUFFER_BLOCK
            4 -> DecorationType.ROW_MAJOR
            5 -> DecorationType.COL_MAJOR
            6 -> DecorationType.ARRAY_STRIDE
            7 -> DecorationType.MATRIX_STRIDE
            8 -> DecorationType.GLSL_SHARED
            9 -> DecorationType.GLSL_PACKED
            11 -> DecorationType.BUILTIN
            13 -> DecorationType.NO_PERSPECTIVE
            14 -> DecorationType.FLAT
            15 -> DecorationType.PATCH
            16 -> DecorationType.CENTROID
            17 -> DecorationType.SAMPLE
            18 -> DecorationType.INVARIANT
            19 -> DecorationType.RESTRICT
            20 -> DecorationType.ALIASED
            21 -> DecorationType.VOLATILE
            23 -> DecorationType.COHERENT
            24 -> DecorationType.NON_WRITABLE
            25 -> DecorationType.NON_READABLE
            26 -> DecorationType.UNIFORM
            29 -> DecorationType.STREAM
            30 -> DecorationType.LOCATION
            31 -> DecorationType.COMPONENT
            32 -> DecorationType.INDEX
            33 -> DecorationType.BINDING
            34 -> DecorationType.DESCRIPTOR_SET
            35 -> DecorationType.OFFSET
            36 -> DecorationType.XFB_BUFFER
            37 -> DecorationType.XFB_STRIDE
            42 -> DecorationType.NO_CONTRACT
            43 -> DecorationType.INPUT_ATTACHMENT_INDEX
            else -> DecorationType.UNKNOWN
        }

        private fun decodeLiterals(data: ByteBuffer) : List<Int> {
            val result = ArrayList<Int>()

            while (data.hasRemaining()) {
                result.add(data.int)
            }

            return result
        }

        private fun decodeStorageClass(data: ByteBuffer) = when(data.int) {
            0 -> StorageClass.UNIFORM_CONSTANT
            1 -> StorageClass.INPUT
            2 -> StorageClass.UNIFORM
            3 -> StorageClass.OUTPUT
            4 -> StorageClass.WORKGROUP
            5 -> StorageClass.CROSS_WORKGROUP
            6 -> StorageClass.PRIVATE
            7 -> StorageClass.FUNCTION
            8 -> StorageClass.GENERIC
            9 -> StorageClass.PUSH_CONSTANT
            10 -> StorageClass.ATOMIC_COUNTER
            11 -> StorageClass.IMAGE
            12 -> StorageClass.STORAGE_BUFFER
            else -> StorageClass.UNKNOWN
        }

        private fun decodeString(data: ByteBuffer) : String {
            val bos = ByteArrayOutputStream()

            while (data.hasRemaining()) {
                val c0 = data.get().toInt()
                val c1 = data.get().toInt()
                val c2 = data.get().toInt()
                val c3 = data.get().toInt()

                if (c0 == 0) {
                    break
                } else {
                    bos.write(c0)
                }

                if (c1 == 0) {
                    break
                } else {
                    bos.write(c1)
                }

                if (c2 == 0) {
                    break
                } else {
                    bos.write(c2)
                }

                if (c3 == 0) {
                    break
                } else {
                    bos.write(c3)
                }
            }

            return String(bos.toByteArray(), Charsets.UTF_8)
        }
    }
}