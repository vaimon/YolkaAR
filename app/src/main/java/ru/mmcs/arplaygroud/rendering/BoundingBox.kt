package ru.mmcs.arplaygroud.rendering

import android.opengl.Matrix

data class BoundingBox(
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

    fun getModelBoundingBox(modelMatrix: FloatArray) : BoundingBox{
        val resultBox = BoundingBox()
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

    fun intersectsWith(other: BoundingBox) : Boolean{
        return Segment(minX, maxX).intersectsWith(Segment(other.minX, other.maxX)) &&
                Segment(minY, maxY).intersectsWith(Segment(other.minY, other.maxY)) &&
                Segment(minZ, maxZ).intersectsWith(Segment(other.minZ, other.maxZ))
    }
}