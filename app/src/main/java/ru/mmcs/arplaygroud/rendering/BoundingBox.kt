package ru.mmcs.arplaygroud.rendering

import android.graphics.PointF
import android.opengl.Matrix
import android.util.Log
import ru.mmcs.arplaygroud.format
import java.util.Vector
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

open class BoundingBox(
    var minX: Float = Float.POSITIVE_INFINITY,
    var maxX: Float = Float.NEGATIVE_INFINITY,
    var minY: Float = Float.POSITIVE_INFINITY,
    var maxY: Float = Float.NEGATIVE_INFINITY,
    var minZ: Float = Float.POSITIVE_INFINITY,
    var maxZ: Float = Float.NEGATIVE_INFINITY,
) {
    private data class Segment(var start: Float, var end: Float){
        fun intersectsWith(other: Segment): Boolean{
            if(start > other.start){
                start = other.start.also { other.start = start }
                end = other.end.also { other.end = end }
            }
            return end > other.start
        }
    }
    fun getModelBoxCornersCoordinates(): List<FloatArray> {
        return listOf(
            floatArrayOf(minX, minY, minZ, 1f),
            floatArrayOf(minX, minY, maxZ, 1f),
            floatArrayOf(minX, maxY, minZ, 1f),
            floatArrayOf(minX, maxY, maxZ, 1f),
            floatArrayOf(maxX, minY, minZ, 1f),
            floatArrayOf(maxX, minY, maxZ, 1f),
            floatArrayOf(maxX, maxY, minZ, 1f),
            floatArrayOf(maxX, maxY, maxZ, 1f),
            )
    }

    fun getModelBoundingBox(modelMatrix: FloatArray) : ModelBoundingBox{
        val resultBox = ModelBoundingBox()
        for(corner in getModelBoxCornersCoordinates()){
            val projectedPoint = FloatArray(4)
            Matrix.multiplyMV(projectedPoint,0, modelMatrix,0, corner, 0)
            resultBox.minX = minOf(projectedPoint[0], resultBox.minX)
            resultBox.minY = minOf(projectedPoint[1], resultBox.minY)
            resultBox.minZ = minOf(projectedPoint[2], resultBox.minZ)

            resultBox.maxX = maxOf(projectedPoint[0], resultBox.maxX)
            resultBox.maxY = maxOf(projectedPoint[1], resultBox.maxY)
            resultBox.maxZ = maxOf(projectedPoint[2], resultBox.maxZ)
        }
        return resultBox
    }

    fun getConeModelBoundingBox(modelMatrix: FloatArray) : ConeModelBoundingBox{
        val resultBox = ConeModelBoundingBox()
        for(corner in getModelBoxCornersCoordinates()){
            val projectedPoint = FloatArray(4)
            Matrix.multiplyMV(projectedPoint,0, modelMatrix,0, corner, 0)
            resultBox.minX = minOf(projectedPoint[0], resultBox.minX)
            resultBox.minY = minOf(projectedPoint[1], resultBox.minY)
            resultBox.minZ = minOf(projectedPoint[2], resultBox.minZ)

            resultBox.maxX = maxOf(projectedPoint[0], resultBox.maxX)
            resultBox.maxY = maxOf(projectedPoint[1], resultBox.maxY)
            resultBox.maxZ = maxOf(projectedPoint[2], resultBox.maxZ)
        }

        val cX = (resultBox.maxX + resultBox.minX) / 2
        val cZ = (resultBox.maxZ + resultBox.minZ) / 2
        resultBox.topCenter = floatArrayOf(cX, resultBox.maxY, cZ, 1f)
        resultBox.bottomCenter = floatArrayOf(cX, resultBox.minY, cZ, 1f)
        resultBox.radius = abs(resultBox.maxX - resultBox.minX) / 2
        return resultBox
    }

    fun intersectsWith(other: BoundingBox) : Boolean{
        return Segment(minX, maxX).intersectsWith(Segment(other.minX, other.maxX)) &&
                Segment(minY, maxY).intersectsWith(Segment(other.minY, other.maxY)) &&
                Segment(minZ, maxZ).intersectsWith(Segment(other.minZ, other.maxZ))
    }

    fun projectPoint(point: FloatArray, modelMatrix: FloatArray) : FloatArray{
        val projectedPoint = FloatArray(4)
        Matrix.multiplyMV(projectedPoint,0, modelMatrix,0, point, 0)
        return projectedPoint
    }

    override fun toString(): String {
        return "${minX.format(2)}|${maxX.format(2)} ${minY.format(2)}|${maxY.format(2)} ${minZ.format(2)}|${maxZ.format(2)}"
    }
}

open class ModelBoundingBox: BoundingBox() {
    open fun intersectWithRay(rayDirection: FloatArray, raySource: FloatArray) : FloatArray? {
        return floatArrayOf() // TODO
    }
}

class ConeModelBoundingBox(
    var topCenter: FloatArray = floatArrayOf(),
    var bottomCenter: FloatArray = floatArrayOf(),
    var radius: Float = 0f
) : ModelBoundingBox(){
    override fun intersectWithRay(rayDirection: FloatArray, raySource: FloatArray): FloatArray? {
        Log.d("Debug", topCenter.joinToString(" ", prefix = "top: "))
        Log.d("Debug", bottomCenter.joinToString(" ", prefix = "bottom: "))
        Log.d("Debug", "radius: $radius")

//        val thetaCos = (topCenter.toVector3() - bottomCenter.toVector3()).length / (Vector3(bottomCenter[0] - radius, bottomCenter[1], bottomCenter[2]) - topCenter.toVector3()).length
        val thetaCos = sqrt(3f) / 2f
        val directionVector = rayDirection.toVector3().normalized
        val axisVector = (bottomCenter.toVector3() - topCenter.toVector3()).normalized
        val topOriginVector = raySource.toVector3() - topCenter.toVector3()

        val a = directionVector.dot(axisVector).pow(2) - thetaCos.pow(2)
        val b = 2 * ((directionVector.dot(axisVector) * topOriginVector.dot(axisVector)) - (directionVector.dot(topOriginVector) * thetaCos.pow(2)))
        val c = topOriginVector.dot(axisVector).pow(2) - topOriginVector.dot(topOriginVector) * thetaCos.pow(2)
        val d = b.pow(2) - 4 * a * c

        if(d < 0)
            return null
        val res =  (raySource.toVector3() + (directionVector * ((-b - sqrt(d)) / (2 * a))))
        return floatArrayOf(res.x, res.y, res.z, 1f)
    }
}