package demo.spirvreflect

enum class StorageClass {
    UNIFORM_CONSTANT,
    INPUT,
    UNIFORM,
    OUTPUT,
    WORKGROUP,
    CROSS_WORKGROUP,
    PRIVATE,
    FUNCTION,
    GENERIC,
    PUSH_CONSTANT,
    ATOMIC_COUNTER,
    IMAGE,
    STORAGE_BUFFER,
    UNKNOWN
}