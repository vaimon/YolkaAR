/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Modifications Copyright (c) 2020 Razeware LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * Notwithstanding the foregoing, you may not use, copy, modify, merge, publish,
 * distribute, sublicense, create a derivative work, and/or sell copies of the
 * Software in any work that is designed, intended, or marketed for pedagogical or
 * instructional purposes related to programming, coding, application development,
 * or information technology.  Permission for such use, copying, modification,
 * merger, publication, distribution, sublicensing, creation of derivative works,
 * or sale is expressly withheld.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ru.mmcs.arplaygroud.rendering

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import ru.mmcs.arplaygroud.R
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class Material(
    var ambient: Float = 0.3f,
    var diffuse: Float = 1.0f,
    var specular: Float = 1.0f,
    var specularPower: Float = 6.0f
)

/**
 * Renders an object loaded from an OBJ file in OpenGL.
 */
open class ObjectRenderer(
    var planeAttachment: PlaneAttachment,
    private val objAssetName: String,
    private val diffuseTextureAssetName: String,
    private val scaleFactor: Float = 1f,
    val material: Material = Material(),
) {

    private val viewLightDirection = FloatArray(4)

    // Object vertex buffer variables.
    private var vertexBufferId = 0
    private var verticesBaseAddress = 0
    private var texCoordsBaseAddress = 0
    private var normalsBaseAddress = 0
    private var indexBufferId = 0
    private var indexCount = 0
    private var program = 0
    private val textures = IntArray(1)

    // Shader location: model view projection matrix.
    private var modelViewUniform = 0
    private var modelViewProjectionUniform = 0

    // Shader location: object attributes.
    private var positionAttribute = 0
    private var normalAttribute = 0
    private var texCoordAttribute = 0

    // Shader location: texture sampler.
    private var textureUniform = 0

    // Shader location: environment properties.
    private var lightingParametersUniform = 0

    // Shader location: material properties.
    private var materialParametersUniform = 0

    // Shader location: color correction property
    private var colorCorrectionParameterUniform = 0

    // Shader location: object color property (to change the primary color of the object).
    private var colorUniform = 0

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)

    private val _boundingBox = BoundingBox()

    val boundingBox: BoundingBox
        get(){
            updateModelMatrix()
            return _boundingBox.getModelBoundingBox(modelMatrix)
        }

    /**
     * Creates and initializes OpenGL resources needed for rendering the model.
     *
     * @param context                 Context for loading the shader and below-named model and texture assets.
     */
    @Throws(IOException::class)
    fun createOnGlThread(
        context: Context
    ) {
        val vertexShader = ShaderUtil.loadGLShader(
            TAG,
            context,
            GLES20.GL_VERTEX_SHADER,
            VERTEX_SHADER_NAME
        )

        val fragmentShader = ShaderUtil.loadGLShader(
            TAG,
            context,
            GLES20.GL_FRAGMENT_SHADER,
            FRAGMENT_SHADER_NAME
        )

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        GLES20.glUseProgram(program)
        ShaderUtil.checkGLError(TAG, "Program creation")

        modelViewUniform = GLES20.glGetUniformLocation(program, "u_ModelView")
        modelViewProjectionUniform = GLES20.glGetUniformLocation(program, "u_ModelViewProjection")
        positionAttribute = GLES20.glGetAttribLocation(program, "a_Position")
        normalAttribute = GLES20.glGetAttribLocation(program, "a_Normal")
        texCoordAttribute = GLES20.glGetAttribLocation(program, "a_TexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
        lightingParametersUniform = GLES20.glGetUniformLocation(program, "u_LightingParameters")
        materialParametersUniform = GLES20.glGetUniformLocation(program, "u_MaterialParameters")
        colorCorrectionParameterUniform =
            GLES20.glGetUniformLocation(program, "u_ColorCorrectionParameters")
        colorUniform = GLES20.glGetUniformLocation(program, "u_ObjColor")
        ShaderUtil.checkGLError(TAG, "Program parameters")

        // Read the texture.
        val textureBitmap =
            BitmapFactory.decodeStream(context.assets.open(diffuseTextureAssetName))
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGenTextures(textures.size, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        textureBitmap.recycle()
        ShaderUtil.checkGLError(TAG, "Texture loading")

        // Read the obj file.
        val objInputStream = context.assets.open(objAssetName)
        var obj = ObjReader.read(objInputStream)
        // Prepare the Obj so that its structure is suitable for rendering with OpenGL
        obj = ObjUtils.convertToRenderable(obj)

        // Obtain the data from the OBJ, as direct buffers:
        val wideIndices = ObjData.getFaceVertexIndices(obj, 3)
        val vertices = ObjData.getVertices(obj)
        val texCoords = ObjData.getTexCoords(obj, 2)
        val normals = ObjData.getNormals(obj)

        ObjData.getVerticesArray(obj).forEachIndexed { index, coordinate ->
            when(index % 3){
                0 -> {
                    _boundingBox.minX = minOf(_boundingBox.minX, coordinate)
                    _boundingBox.maxX = maxOf(_boundingBox.maxX, coordinate)
                }
                1 -> {
                    _boundingBox.minY = minOf(_boundingBox.minY, coordinate)
                    _boundingBox.maxY = maxOf(_boundingBox.maxY, coordinate)
                }
                2 -> {
                    _boundingBox.minZ = minOf(_boundingBox.minZ, coordinate)
                    _boundingBox.maxZ = maxOf(_boundingBox.maxZ, coordinate)
                }
            }
        }
        // Convert int indices to shorts for GL ES 2.0 compatibility
        val indices =
            ByteBuffer.allocateDirect(2 * wideIndices.limit())
                .order(ByteOrder.nativeOrder())
                .asShortBuffer()

        while (wideIndices.hasRemaining()) {
            indices.put(wideIndices.get().toShort())
        }
        indices.rewind()

        val buffers = IntArray(2)
        GLES20.glGenBuffers(2, buffers, 0)
        vertexBufferId = buffers[0]
        indexBufferId = buffers[1]

        // Load vertex buffer
        verticesBaseAddress = 0
        texCoordsBaseAddress = verticesBaseAddress + 4 * vertices.limit()
        normalsBaseAddress = texCoordsBaseAddress + 4 * texCoords.limit()

        val totalBytes = normalsBaseAddress + 4 * normals.limit()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW)
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, verticesBaseAddress, 4 * vertices.limit(), vertices
        )
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, texCoordsBaseAddress, 4 * texCoords.limit(), texCoords
        )
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, normalsBaseAddress, 4 * normals.limit(), normals
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Load index buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        indexCount = indices.limit()
        GLES20.glBufferData(
            GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * indexCount, indices, GLES20.GL_STATIC_DRAW
        )
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
        ShaderUtil.checkGLError(TAG, "OBJ buffer load")

        Matrix.setIdentityM(modelMatrix, 0)
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the `modelMatrix`.
     * @see Matrix
     */
    fun updateModelMatrix() {
        val anchorMatrix = FloatArray(16)
        planeAttachment.pose.toMatrix(anchorMatrix, 0)
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        scaleMatrix[0] = scaleFactor
        scaleMatrix[5] = scaleFactor
        scaleMatrix[10] = scaleFactor
        Matrix.multiplyMM(this.modelMatrix, 0, anchorMatrix, 0, scaleMatrix, 0)
    }

    /**
     * Draws the model.
     *
     * @param cameraView        A 4x4 view matrix, in column-major order.
     * @param cameraPerspective A 4x4 projection matrix, in column-major order.
     * @param lightIntensity    Illumination intensity. Combined with diffuse and specular material
     * properties.
     * @see .setBlendMode
     * @see .updateModelMatrix
     * @see .setMaterialProperties
     * @see Matrix
     */
    @JvmOverloads
    fun draw(
        cameraView: FloatArray?,
        cameraPerspective: FloatArray?,
        colorCorrectionRgba: FloatArray?,
        objColor: FloatArray? = DEFAULT_COLOR
    ) {
        ShaderUtil.checkGLError(TAG, "Before draw")
        // Build the ModelView and ModelViewProjection matrices for calculating object position and light.
        Matrix.multiplyMM(modelViewMatrix, 0, cameraView, 0, modelMatrix, 0)
        Matrix.multiplyMM(
            modelViewProjectionMatrix,
            0,
            cameraPerspective,
            0,
            modelViewMatrix,
            0
        )
        GLES20.glUseProgram(program)

        // Set the lighting environment properties.
        Matrix.multiplyMV(
            viewLightDirection,
            0,
            modelViewMatrix,
            0,
            LIGHT_DIRECTION,
            0
        )
        normalizeVec3(viewLightDirection)
        GLES20.glUniform4f(
            lightingParametersUniform,
            viewLightDirection[0],
            viewLightDirection[1],
            viewLightDirection[2],
            1f
        )
        GLES20.glUniform4fv(colorCorrectionParameterUniform, 1, colorCorrectionRgba, 0)

        // Set the object color property.
        GLES20.glUniform4fv(colorUniform, 1, objColor, 0)

        // Set the object material properties.
        GLES20.glUniform4f(
            materialParametersUniform,
            material.ambient,
            material.diffuse,
            material.specular,
            material.specularPower
        )

        // Attach the object texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        GLES20.glUniform1i(textureUniform, 0)

        // Set the vertex attributes.
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glVertexAttribPointer(
            positionAttribute,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            verticesBaseAddress
        )
        GLES20.glVertexAttribPointer(
            normalAttribute,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            normalsBaseAddress
        )
        GLES20.glVertexAttribPointer(
            texCoordAttribute, 2, GLES20.GL_FLOAT, false, 0, texCoordsBaseAddress
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0)
        GLES20.glUniformMatrix4fv(
            modelViewProjectionUniform,
            1,
            false,
            modelViewProjectionMatrix,
            0
        )

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(positionAttribute)
        GLES20.glEnableVertexAttribArray(normalAttribute)
        GLES20.glEnableVertexAttribArray(texCoordAttribute)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(positionAttribute)
        GLES20.glDisableVertexAttribArray(normalAttribute)
        GLES20.glDisableVertexAttribArray(texCoordAttribute)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        ShaderUtil.checkGLError(TAG, "After draw")
    }

    companion object {
        private val TAG = ObjectRenderer::class.java.simpleName

        // Shader names.
        private const val VERTEX_SHADER_NAME = "shaders/object.vert"
        private const val FRAGMENT_SHADER_NAME = "shaders/object.frag"
        private const val COORDS_PER_VERTEX = 3
        val DEFAULT_COLOR = floatArrayOf(0f, 0f, 0f, 0f)
        val HIGHLIGHT_COLOR = floatArrayOf(1f, 1f, 0f, 0.5f)

        // Note: the last component must be zero to avoid applying the translational part of the matrix.
        private val LIGHT_DIRECTION = floatArrayOf(0.250f, 0.866f, 0.433f, 0.0f)

        private fun normalizeVec3(v: FloatArray) {
            val reciprocalLength = 1.0f / Math.sqrt(
                v[0] * v[0] + v[1] * v[1] + (v[2] * v[2]).toDouble()
            ).toFloat()
            v[0] *= reciprocalLength
            v[1] *= reciprocalLength
            v[2] *= reciprocalLength
        }
    }

}

class VikingObject(context: Context, planeAttachment: PlaneAttachment) : ObjectRenderer(
    planeAttachment,
    context.getString(R.string.model_viking_obj),
    context.getString(R.string.model_viking_png),
    material = Material(specular = .25f, specularPower = 16f)
)

class CannonObject(context: Context, planeAttachment: PlaneAttachment) : ObjectRenderer(
    planeAttachment,
    context.getString(R.string.model_cannon_obj),
    context.getString(R.string.model_cannon_png),
    scaleFactor = .2f
)

class TargetObject(context: Context, planeAttachment: PlaneAttachment) : ObjectRenderer(
    planeAttachment,
    context.getString(R.string.model_target_obj),
    context.getString(R.string.model_target_png),
    scaleFactor = .5f
)