package com.example.domain.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Native ONNX Runtime Execution Engine for PP-OCRv5 Mobile Detection (PPLCNetV3 + DBNet).
 *
 * Runs actual neural network tensors on CPU/NNAPI via Microsoft ONNX Runtime Android SDK.
 */
object OnnxDetRunner {

    private const val TAG = "OnnxDetRunner"
    private const val DEFAULT_ASSET_MODEL = "models/ppocrv5_det.onnx"

    @Volatile
    private var ortEnv: OrtEnvironment? = null

    @Volatile
    private var ortSession: OrtSession? = null

    @Volatile
    private var isModelLoaded: Boolean = false

    @Volatile
    private var activeModelPath: String = "Internal PP-OCRv5 Engine"

    // Explicit override set by the user via the Model Manager UI. When set,
    // this exact file is loaded instead of the auto-discovery heuristic below.
    // Persisted to SharedPreferences so the chosen model survives app restart.
    @Volatile
    private var overridePath: String? = null

    private const val PREFS_NAME = "folia_model_prefs"
    private const val PREF_KEY_DET_MODEL_PATH = "active_det_model_path"

    data class EngineStatus(
        val isOrtAvailable: Boolean,
        val isModelLoaded: Boolean,
        val modelSource: String,
        val runtimeVersion: String = "ONNX Runtime 1.18.0",
        val executionProvider: String = "CPU / NNAPI"
    )

    fun getStatus(context: Context): EngineStatus {
        checkOrInitSession(context)
        return EngineStatus(
            isOrtAvailable = true,
            isModelLoaded = isModelLoaded,
            modelSource = activeModelPath
        )
    }

    /**
     * Switches the active detection model to the given absolute file path and
     * reloads the ONNX session immediately. Returns true if the new model
     * loaded successfully; on failure the previous session is left in place
     * so a bad model file can't leave the app without any working session.
     */
    @Synchronized
    fun setActiveModelPath(context: Context, path: String?): Boolean {
        if (path == null) {
            overridePath = null
            persistOverridePath(context, null)
            ortSession?.close()
            ortSession = null
            isModelLoaded = false
            checkOrInitSession(context)
            return isModelLoaded
        }

        val file = File(path)
        if (!file.exists() || file.length() <= 1024) {
            Log.e(TAG, "setActiveModelPath: file does not exist or is too small: $path")
            return false
        }

        val sessionOptions = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(4)
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
        }
        return try {
            if (ortEnv == null) ortEnv = OrtEnvironment.getEnvironment()
            val newSession = ortEnv?.createSession(file.absolutePath, sessionOptions)
            // Only swap in the new session once creation succeeded, so a
            // failed load never leaves the runner without any session.
            ortSession?.close()
            ortSession = newSession
            isModelLoaded = true
            activeModelPath = file.name
            overridePath = path
            persistOverridePath(context, path)
            Log.i(TAG, "Switched active detection model to: $path")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch detection model to $path: ${e.message}", e)
            false
        }
    }

    private fun persistOverridePath(context: Context, path: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (path == null) remove(PREF_KEY_DET_MODEL_PATH) else putString(PREF_KEY_DET_MODEL_PATH, path)
        }.apply()
    }

    @Synchronized
    private fun checkOrInitSession(context: Context) {
        if (ortSession != null && isModelLoaded) return

        try {
            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment()
            }

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            // 0. Explicit override chosen by the user (in-memory, or restored
            // from SharedPreferences on cold start) takes priority over
            // everything else.
            val restoredPath = overridePath ?: context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_KEY_DET_MODEL_PATH, null)
            if (restoredPath != null) {
                val overrideFile = File(restoredPath)
                if (overrideFile.exists() && overrideFile.length() > 1024) {
                    try {
                        ortSession = ortEnv?.createSession(overrideFile.absolutePath, sessionOptions)
                        isModelLoaded = true
                        activeModelPath = overrideFile.name
                        overridePath = restoredPath
                        Log.i(TAG, "Loaded user-selected ONNX model from: $restoredPath")
                        return
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed loading user-selected model $restoredPath: ${e.message}")
                    }
                } else {
                    Log.w(TAG, "Previously selected model no longer exists: $restoredPath")
                }
            }

            // Check if model exists in custom app storage
            val customModelsDir = File(context.filesDir, "models")
            val customCandidates = listOf("ppocrv5_det_int8.onnx", "ppocrv5_det.onnx")
            for (candidate in customCandidates) {
                val customModelFile = File(customModelsDir, candidate)
                if (customModelFile.exists() && customModelFile.length() > 1024) {
                    try {
                        ortSession = ortEnv?.createSession(customModelFile.absolutePath, sessionOptions)
                        isModelLoaded = true
                        activeModelPath = customModelFile.name
                        Log.i(TAG, "Loaded custom ONNX model from: ${customModelFile.absolutePath}")
                        return
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed loading custom model $candidate: ${e.message}")
                    }
                }
            }

            // Check assets
            val assetList = context.assets.list("models")?.toList() ?: emptyList()
            val assetCandidates = listOf("ppocrv5_det_int8.onnx", "ppocrv5_det.onnx") + assetList.filter { it.endsWith(".onnx") && (it.contains("det") || it.contains("ppocr")) }
            for (assetName in assetCandidates.distinct()) {
                if (assetList.contains(assetName)) {
                    try {
                        val assetPath = "models/$assetName"
                        val tempFile = File(context.cacheDir, "ppocrv5_det_temp.onnx")
                        context.assets.open(assetPath).use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 1024) {
                            ortSession = ortEnv?.createSession(tempFile.absolutePath, sessionOptions)
                            isModelLoaded = true
                            activeModelPath = "Asset: $assetPath"
                            Log.i(TAG, "Loaded ONNX model from assets: $assetPath")
                            return
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed loading asset model $assetName: ${e.message}")
                    }
                }
            }

            isModelLoaded = false
            activeModelPath = "CV DBNet Pre/Postprocessor (ONNX ready)"
        } catch (e: Exception) {
            Log.w(TAG, "ONNX session initialization notice: ${e.message}")
            isModelLoaded = false
            activeModelPath = "Mathematical DBNet Engine"
        }
    }

    /**
     * Imports a user-selected ONNX model file into the application models
     * directory under its OWN unique filename (derived from content hash +
     * original name) so multiple imported models can coexist without
     * overwriting each other. Does NOT automatically activate the model —
     * call setActiveModelPath with the returned path to switch to it.
     * Returns the absolute path of the imported file, or null on failure.
     */
    fun importCustomModel(context: Context, inputStream: java.io.InputStream, fileName: String): String? {
        return try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val safeBaseName = fileName.substringAfterLast('/').substringAfterLast('\\')
                .ifBlank { "model.onnx" }
                .let { if (it.endsWith(".onnx")) it else "$it.onnx" }

            // Avoid clobbering an existing import with the same display name:
            // suffix with a counter if needed.
            var targetFile = File(modelsDir, safeBaseName)
            var counter = 1
            val nameWithoutExt = safeBaseName.removeSuffix(".onnx")
            while (targetFile.exists()) {
                targetFile = File(modelsDir, "${nameWithoutExt}_$counter.onnx")
                counter++
            }

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (!targetFile.exists() || targetFile.length() <= 1024) {
                Log.e(TAG, "Imported model file is missing or too small: ${targetFile.absolutePath}")
                targetFile.delete()
                return null
            }

            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import custom ONNX model", e)
            null
        }
    }

    /**
     * Runs true ONNX tensor inference or fallback DBNet pipeline
     */
    fun runInference(
        context: Context,
        bitmap: Bitmap,
        config: PpOcrV5SegmentationEngine.DetConfig
    ): Pair<Array<FloatArray>, Boolean> {
        checkOrInitSession(context)

        val session = ortSession
        val env = ortEnv

        if (session != null && env != null && isModelLoaded) {
            try {
                // 1. Preprocessing: DetResizeForTest & ImageNet Normalization
                val origW = bitmap.width
                val origH = bitmap.height

                val maxSide = config.resizeLong
                val scale = if (max(origW, origH) > 0) maxSide.toFloat() / max(origW, origH).toFloat() else 1.0f

                var targetW = (origW * scale).toInt()
                var targetH = (origH * scale).toInt()

                // Align dimensions to multiple of 32
                targetW = max(32, ((targetW + 31) / 32) * 32)
                targetH = max(32, ((targetH + 31) / 32) * 32)

                val scaledBmp = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
                val pixels = IntArray(targetW * targetH)
                scaledBmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

                // ImageNet mean & std
                val mean = floatArrayOf(0.485f, 0.456f, 0.406f)
                val std = floatArrayOf(0.229f, 0.224f, 0.225f)

                // CHW layout FloatBuffer
                val floatBuffer = FloatBuffer.allocate(1 * 3 * targetH * targetW)

                // Channel R
                for (i in 0 until targetH * targetW) {
                    val r = (Color.red(pixels[i]) / 255.0f - mean[0]) / std[0]
                    floatBuffer.put(r)
                }
                // Channel G
                for (i in 0 until targetH * targetW) {
                    val g = (Color.green(pixels[i]) / 255.0f - mean[1]) / std[1]
                    floatBuffer.put(g)
                }
                // Channel B
                for (i in 0 until targetH * targetW) {
                    val b = (Color.blue(pixels[i]) / 255.0f - mean[2]) / std[2]
                    floatBuffer.put(b)
                }
                floatBuffer.rewind()

                // 2. Create Input Tensor
                val shape = longArrayOf(1, 3, targetH.toLong(), targetW.toLong())
                val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

                val inputName = session.inputNames.firstOrNull() ?: "x"
                val outputMap = session.run(Collections.singletonMap(inputName, inputTensor))

                // Log actual output tensor shape/type so mismatches are visible instead of silent
                val outputTensorInfo = outputMap.get(0)
                val rawShape = (outputTensorInfo.info as? ai.onnxruntime.TensorInfo)?.shape
                Log.i(
                    TAG,
                    "Det output[0] name=${session.outputNames.firstOrNull()} shape=${rawShape?.joinToString(prefix = "[", postfix = "]")} " +
                        "javaType=${outputTensorInfo.value?.javaClass?.name}"
                )

                val outputValue = outputTensorInfo.value
                inputTensor.close()
                outputMap.close()

                // Extract a 2D plane from whatever rank the model actually returned, then
                // resize it to [targetH, targetW] if needed (DBNet heads sometimes emit a
                // downsampled map, e.g. H/4 x W/4, when exported without a final upsample).
                val rawPlane: Array<FloatArray>? = extractPlane(outputValue)

                if (rawPlane == null || rawPlane.isEmpty() || rawPlane[0].isEmpty()
                    || rawPlane.size < 4 || rawPlane[0].size < 4
                ) {
                    Log.e(
                        TAG,
                        "Det tensor parsing FAILED: could not extract a usable 2D plane from shape=" +
                            "${rawShape?.joinToString(prefix = "[", postfix = "]")}. Falling back to CV DBNet."
                    )
                    return Pair(emptyArray(), false)
                }

                val probMap = if (rawPlane.size == targetH && rawPlane[0].size == targetW) {
                    rawPlane
                } else {
                    Log.w(
                        TAG,
                        "Det output plane is ${rawPlane.size}x${rawPlane[0].size}, resizing to " +
                            "${targetH}x$targetW to match preprocessed input."
                    )
                    resizePlane(rawPlane, targetH, targetW)
                }

                return Pair(probMap, true)
            } catch (e: Exception) {
                Log.e(TAG, "ONNX tensor execution failed, falling back to CV DBNet: ${e.message}")
            }
        }

        // Return empty with false indicator to let the Sauvola CV DBNet pipeline handle it
        return Pair(emptyArray(), false)
    }

    /**
     * Extracts a single 2D [H, W] probability plane from an ONNX output value of
     * unknown rank. Handles the shapes actually seen in practice:
     *  - 4D [N, C, H, W]: if C == 1 take that channel; if C > 1 (e.g. softmax
     *    bg/fg) take the LAST channel, which is the foreground/ink class by
     *    PaddleOCR export convention.
     *  - 3D [N, H, W]: take the first batch slice.
     *  - 2D [H, W]: used directly.
     * Returns null if the value doesn't match any of these shapes.
     */
    private fun extractPlane(outputValue: Any?): Array<FloatArray>? {
        if (outputValue !is Array<*>) return null

        // Try 4D: Array<Array<Array<FloatArray>>> = [N][C][H][W]
        @Suppress("UNCHECKED_CAST")
        val raw4D = outputValue as? Array<Array<Array<FloatArray>>>
        if (raw4D != null && raw4D.isNotEmpty() && raw4D[0].isNotEmpty()) {
            val channels = raw4D[0]
            val channelIdx = if (channels.size == 1) 0 else channels.size - 1
            return channels[channelIdx]
        }

        // Try 3D: Array<Array<FloatArray>> = [N][H][W]
        @Suppress("UNCHECKED_CAST")
        val raw3D = outputValue as? Array<Array<FloatArray>>
        if (raw3D != null && raw3D.isNotEmpty()) {
            return raw3D[0]
        }

        // Try 2D: Array<FloatArray> = [H][W]
        @Suppress("UNCHECKED_CAST")
        val raw2D = outputValue as? Array<FloatArray>
        if (raw2D != null) {
            return raw2D
        }

        return null
    }

    /**
     * Nearest-neighbor resize of a 2D float plane to [targetH, targetW].
     * Used when the model's output resolution doesn't match the preprocessed
     * input resolution (e.g. a DBNet head exported without its final upsample).
     */
    private fun resizePlane(src: Array<FloatArray>, targetH: Int, targetW: Int): Array<FloatArray> {
        val srcH = src.size
        val srcW = src[0].size
        if (srcH == targetH && srcW == targetW) return src

        val out = Array(targetH) { FloatArray(targetW) }
        for (y in 0 until targetH) {
            val srcY = (y * srcH / targetH).coerceIn(0, srcH - 1)
            for (x in 0 until targetW) {
                val srcX = (x * srcW / targetW).coerceIn(0, srcW - 1)
                out[y][x] = src[srcY][srcX]
            }
        }
        return out
    }
}
