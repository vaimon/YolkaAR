package ru.mmcs.arplaygroud.rendering

import kotlin.math.pow
import kotlin.math.sqrt

class Vector3(
    val x: Float, val y: Float, val z: Float
){
    val length: Float
        get() = sqrt(x.pow(2)+ y.pow(2) + z.pow(2))
    val normalized: Vector3
        get() = Vector3(x/length, y/length, z/length)

    operator fun minus(other: Vector3): Vector3{
        return Vector3(x - other.x, y - other.y, z - other.z)
    }

    operator fun plus(other: Vector3): Vector3{
        return Vector3(x + other.x, y + other.y, z + other.z)
    }

    operator fun times(k: Float): Vector3{
        return Vector3(k*x, k*y, k*z)
    }

    fun dot (other: Vector3): Float{
        return x * other.x + y * other.y + z * other.z
    }
}

fun FloatArray.toVector3(): Vector3{
    return Vector3(this[0], this[1], this[2])
}