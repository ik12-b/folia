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
import kotlin.math.exp
import kotlin.math.max

/**
 * ONNX Runtime execution engine for CTC-based line recognition (HTR/OCR),
 * bundled by default with the Muharaf Kraken model trained on historical
 * Arabic/Pegon manuscripts, but designed to support hot-swapping to other
 * CTC recognition models via the Model Manager UI.
 *
 * Input/output shape is NOT assumed fixed: it is read from each model's own
 * ONNX metadata when loaded (see readInputSpec), since different models
 * declare different conventions -- e.g. the bundled model is
 * [Batch, 1 (grayscale), Height=120, Width] with 174 output classes, while
 * other CTC recognition models (such as the kraken PP-OCRv6 family) may
 * declare [Batch, 3 (RGB), Height=96 or 128, Width] with a much larger
 * class count. A model whose input isn't a 4D NCHW tensor with a fixed
 * height and 1 or 3 channels is rejected at load time rather than guessed
 * at.
 *
 * Decoding: CTC Greedy Decoder against the alphabet loaded from a sibling
 * "<model>_keys.txt" (or "keys.txt") file next to the active model, where
 * index 0 is the CTC blank.
 */
object OnnxRecRunner {

    private const val TAG = "OnnxRecRunner"
    private const val DEFAULT_ASSET_MODEL = "models/muharaf_rec_best.onnx"
    private const val DEFAULT_ASSET_KEYS = "models/muharaf_keys.txt"

    // Fallback used only if shape introspection below fails outright (should
    // not normally happen for a valid ONNX model). Real preprocessing always
    // prefers the values read from the currently loaded session so that
    // switching to a model with a different expected input size/channel
    // count (e.g. a grayscale 120px model vs. an RGB 96px or 128px model)
    // actually works instead of silently feeding it the wrong-shaped input.
    private const val FALLBACK_HEIGHT = 120
    private const val FALLBACK_CHANNELS = 1

    @Volatile
    private var ortEnv: OrtEnvironment? = null

    @Volatile
    private var ortSession: OrtSession? = null

    @Volatile
    private var isModelLoaded: Boolean = false

    @Volatile
    private var alphabetList: List<String> = emptyList()

    @Volatile
    private var activeModelPath: String = "Internal Muharaf Engine"

    // Input spec read from the active session's own metadata (see
    // readInputSpec below), not assumed from a hardcoded constant. This is
    // what makes switching between models with different expected input
    // shapes (grayscale 1-channel @ 120px vs. RGB 3-channel @ 96px/128px,
    // etc.) actually work correctly instead of silently mis-shaping the
    // tensor for whichever model happens to be active.
    @Volatile
    private var inputHeight: Int = FALLBACK_HEIGHT

    @Volatile
    private var inputChannels: Int = FALLBACK_CHANNELS

    // Whether the active model's CTC time axis needs to be reversed before
    // decoding. This was verified true for the bundled 174-class grayscale
    // Muharaf model (reversing fixed a real symptom: correct characters but
    // reversed word/letter order). It is NOT known to hold for other models
    // with a different architecture/training -- applying it unconditionally
    // to every future model would silently corrupt output for any model
    // that doesn't share this quirk. Defaults to true only for the known
    // bundled model; custom-imported models default to false (no reversal)
    // since that's the more common CTC convention, and can be overridden
    // once a specific model's correct orientation is confirmed.
    @Volatile
    private var reverseTimeAxis: Boolean = true

    // Explicit override chosen by the user via the Model Manager UI, persisted
    // across restarts. Takes priority over the auto-discovery heuristic below.
    @Volatile
    private var overridePath: String? = null

    private const val PREFS_NAME = "folia_model_prefs"
    private const val PREF_KEY_REC_MODEL_PATH = "active_rec_model_path"

    data class RecStatus(
        val isOrtAvailable: Boolean,
        val isModelLoaded: Boolean,
        val modelSource: String,
        val alphabetSize: Int,
        val runtimeVersion: String = "ONNX Runtime 1.18.0",
        val executionProvider: String = "CPU / NNAPI"
    )

    data class RecognitionResult(
        val text: String,
        val confidence: Float,
        val rawTimeSteps: Int = 0,
        val isFromOnnx: Boolean = true
    )

    fun getStatus(context: Context): RecStatus {
        checkOrInitSession(context)
        return RecStatus(
            isOrtAvailable = true,
            isModelLoaded = isModelLoaded,
            modelSource = activeModelPath,
            alphabetSize = alphabetList.size
        )
    }

    /**
     * Reads the active session's declared input shape and derives
     * (channels, height) from it. ONNX Runtime reports dynamic dimensions
     * (batch, width) as -1 or as a named/unset dimension depending on the
     * exporter; only the two dimensions that matter for preprocessing
     * (channels at index 1, height at index 2 of an NCHW tensor) are
     * required to be concrete, fixed values -- which is the normal case
     * for every HTR/OCR recognition model, since the network architecture
     * itself fixes them.
     *
     * Returns null if the input isn't a 4D NCHW tensor with a sane
     * (1 or 3) channel count and a positive fixed height -- i.e. this
     * model doesn't match any input convention this runner knows how to
     * feed, and should be rejected rather than guessed at.
     */
    private fun readInputSpec(session: OrtSession): Pair<Int, Int>? {
        return try {
            val inputName = session.inputNames.firstOrNull() ?: return null
            val info = session.inputInfo[inputName]?.info as? ai.onnxruntime.TensorInfo ?: return null
            val shape = info.shape ?: return null
            if (shape.size != 4) {
                Log.e(TAG, "readInputSpec: expected 4D NCHW input, got shape=${shape.joinToString()}")
                return null
            }
            val channels = shape[1].toInt()
            val height = shape[2].toInt()
            if (channels != 1 && channels != 3) {
                Log.e(TAG, "readInputSpec: unsupported channel count $channels (expected 1 or 3)")
                return null
            }
            if (height <= 0) {
                Log.e(TAG, "readInputSpec: model does not declare a fixed input height (got $height); cannot determine preprocessing target size")
                return null
            }
            Pair(channels, height)
        } catch (e: Exception) {
            Log.e(TAG, "readInputSpec failed: ${e.message}", e)
            null
        }
    }

    /**
     * Switches the active recognition model to the given absolute file path
     * and reloads the ONNX session immediately. Returns true on success; on
     * failure the previous session is left in place. If a sibling
     * "<name-without-ext>_keys.txt" or a plain "keys.txt" file exists next to
     * the model, it's used as the alphabet instead of the bundled one — a
     * different HTR model will almost always need its own alphabet mapping.
     */
    @Synchronized
    fun setActiveModelPath(context: Context, path: String?): Boolean {
        if (path == null) {
            overridePath = null
            persistOverridePath(context, null)
            ortSession?.close()
            ortSession = null
            isModelLoaded = false
            alphabetList = emptyList()
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
            if (newSession == null) {
                Log.e(TAG, "setActiveModelPath: createSession returned null for $path")
                return false
            }

            // Validate the new session's input shape BEFORE touching the
            // currently active session at all. If this model's input isn't
            // something recognizeLine() knows how to feed (wrong rank,
            // unsupported channel count, no fixed height), the old session
            // must be left completely untouched -- switching to an
            // incompatible model should never leave the app with no
            // working recognizer at all.
            val spec = readInputSpec(newSession)
            if (spec == null) {
                newSession.close()
                Log.e(TAG, "setActiveModelPath: rejected $path -- unsupported input shape, keeping previous model active")
                return false
            }
            val (channels, height) = spec

            // Only now is it safe to swap: the new session is confirmed
            // loadable AND has a shape this runner can preprocess for.
            ortSession?.close()
            ortSession = newSession
            isModelLoaded = true
            inputChannels = channels
            inputHeight = height
            activeModelPath = file.name
            overridePath = path
            persistOverridePath(context, path)

            // Try to load a sibling alphabet file matching this model; fall
            // back to whatever was already loaded (usually the bundled
            // muharaf_keys.txt) if none is found. A different model almost
            // always has a different-sized alphabet (verified: a 174-class
            // grayscale model vs. a 1623-class multi-script RGB model both
            // exist in practice) -- silently keeping the old alphabet would
            // make CTC decoding index into the wrong character set entirely.
            val siblingKeys = File(file.parentFile, file.nameWithoutExtension + "_keys.txt")
            val genericKeys = File(file.parentFile, "keys.txt")
            when {
                siblingKeys.exists() -> {
                    alphabetList = siblingKeys.readLines()
                    Log.i(TAG, "Loaded ${alphabetList.size} alphabet keys from ${siblingKeys.name}")
                }
                genericKeys.exists() -> {
                    alphabetList = genericKeys.readLines()
                    Log.i(TAG, "Loaded ${alphabetList.size} alphabet keys from ${genericKeys.name}")
                }
                else -> {
                    alphabetList = emptyList()
                    Log.e(
                        TAG,
                        "No sibling alphabet file found for $path (expected " +
                            "\"${file.nameWithoutExtension}_keys.txt\" or \"keys.txt\" next to it). " +
                            "Recognition will be disabled until a matching alphabet file is provided -- " +
                            "reusing the previous model's alphabet would silently decode this model's " +
                            "output through the wrong character mapping."
                    )
                }
            }

            Log.i(TAG, "Switched active recognition model to: $path (channels=$inputChannels, height=$inputHeight, classes=${alphabetList.size})")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch recognition model to $path: ${e.message}", e)
            false
        }
    }

    private fun persistOverridePath(context: Context, path: String?) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (path == null) remove(PREF_KEY_REC_MODEL_PATH) else putString(PREF_KEY_REC_MODEL_PATH, path)
        }.apply()
    }

    @Synchronized
    /**
     * Attempts to create and validate an ONNX session from the given file,
     * and if successful, commits it as the active session along with its
     * matching alphabet file and detected input shape. Centralizing this
     * here (used by all three loading paths below: restored user override,
     * custom-imported storage, and bundled assets) ensures every path gets
     * the same shape validation instead of three independently-maintained
     * copies where it would be easy to fix a bug in one and miss the others.
     *
     * Returns true if this file was successfully loaded and activated.
     */
    private fun tryLoadSession(
        sessionOptions: OrtSession.SessionOptions,
        modelFile: File,
        sourceLabel: String
    ): Boolean {
        return try {
            val candidateSession = ortEnv?.createSession(modelFile.absolutePath, sessionOptions) ?: return false
            val spec = readInputSpec(candidateSession)
            if (spec == null) {
                candidateSession.close()
                Log.e(TAG, "tryLoadSession: rejected $sourceLabel -- unsupported input shape")
                return false
            }
            val (channels, height) = spec

            ortSession?.close()
            ortSession = candidateSession
            isModelLoaded = true
            inputChannels = channels
            inputHeight = height
            activeModelPath = sourceLabel

            // Only the known bundled Muharaf model has been verified to need
            // the time-axis reversal; any other model (custom-imported, or
            // even a same-named file the user swapped in) defaults to no
            // reversal until specifically confirmed otherwise.
            reverseTimeAxis = modelFile.name.contains("muharaf", ignoreCase = true)

            val siblingKeys = File(modelFile.parentFile, modelFile.nameWithoutExtension + "_keys.txt")
            val genericKeys = File(modelFile.parentFile, "keys.txt")
            when {
                siblingKeys.exists() -> alphabetList = siblingKeys.readLines()
                genericKeys.exists() -> alphabetList = genericKeys.readLines()
                else -> {
                    // Bundled default model ships without a sibling keys file
                    // next to it (its alphabet is loaded separately via
                    // loadAlphabetKeys() from DEFAULT_ASSET_KEYS) -- only
                    // clear the alphabet here if one was expected and truly
                    // missing for a non-default (custom) model.
                    if (alphabetList.isEmpty()) {
                        Log.w(TAG, "No sibling alphabet file found next to $sourceLabel; relying on whatever loadAlphabetKeys() already loaded")
                    }
                }
            }

            Log.i(TAG, "Loaded recognition model: $sourceLabel (channels=$inputChannels, height=$inputHeight, classes=${alphabetList.size})")
            true
        } catch (e: Exception) {
            Log.w(TAG, "tryLoadSession failed for $sourceLabel: ${e.message}")
            false
        }
    }

    fun checkOrInitSession(context: Context) {
        if (ortSession != null && isModelLoaded && alphabetList.isNotEmpty()) return

        try {
            // 1. Load Alphabet Keys
            loadAlphabetKeys(context)

            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment()
            }

            val sessionOptions = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(4)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            // 0. Explicit override chosen by the user (in-memory, or restored
            // from SharedPreferences on cold start) takes priority.
            val restoredPath = overridePath ?: context
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_KEY_REC_MODEL_PATH, null)
            if (restoredPath != null) {
                val overrideFile = File(restoredPath)
                if (overrideFile.exists() && overrideFile.length() > 1024) {
                    overridePath = restoredPath
                    if (tryLoadSession(sessionOptions, overrideFile, overrideFile.name)) {
                        Log.i(TAG, "Restored user-selected recognition model from: $restoredPath")
                        return
                    }
                    // Fell through: the previously-selected model no longer
                    // loads validly (corrupted, or shape changed). Clear the
                    // stale override so subsequent launches don't keep
                    // retrying a broken path, and fall through to bundled
                    // defaults below instead of leaving recognition disabled.
                    Log.w(TAG, "Previously-selected recognition model at $restoredPath failed validation; clearing override and falling back to bundled model")
                    overridePath = null
                    persistOverridePath(context, null)
                } else {
                    Log.w(TAG, "Previously selected recognition model no longer exists: $restoredPath")
                }
            }

            // Check custom storage first
            val customModelsDir = File(context.filesDir, "models")
            val customCandidates = listOf("muharaf_rec_best_int8.onnx", "muharaf_rec_best.onnx")
            for (candidate in customCandidates) {
                val customModelFile = File(customModelsDir, candidate)
                if (customModelFile.exists() && customModelFile.length() > 1024) {
                    if (tryLoadSession(sessionOptions, customModelFile, customModelFile.name)) {
                        Log.i(TAG, "Loaded custom Muharaf ONNX model from: ${customModelFile.absolutePath}")
                        return
                    }
                }
            }

            // Check app assets
            val assetList = context.assets.list("models")?.toList() ?: emptyList()
            val assetCandidates = listOf("muharaf_rec_best_int8.onnx", "muharaf_rec_best.onnx") + assetList.filter { it.endsWith(".onnx") && (it.contains("rec") || it.contains("muharaf")) }
            for (assetName in assetCandidates.distinct()) {
                if (assetList.contains(assetName)) {
                    try {
                        val assetPath = "models/$assetName"
                        val tempFile = File(context.cacheDir, "muharaf_rec_temp.onnx")
                        context.assets.open(assetPath).use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 1024) {
                            if (tryLoadSession(sessionOptions, tempFile, "Asset: $assetPath")) {
                                Log.i(TAG, "Loaded Muharaf ONNX model from assets: $assetPath")
                                return
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed loading asset rec model $assetName: ${e.message}")
                    }
                }
            }

            isModelLoaded = false
            activeModelPath = "Fallback Kitab HTR Engine"
        } catch (e: Exception) {
            Log.w(TAG, "Muharaf ONNX session initialization notice: ${e.message}")
            isModelLoaded = false
            activeModelPath = "Fallback Kitab HTR Engine"
        }
    }

    private fun loadAlphabetKeys(context: Context) {
        if (alphabetList.isNotEmpty()) return
        try {
            val assetList = context.assets.list("models") ?: emptyArray()
            if (assetList.contains("muharaf_keys.txt")) {
                context.assets.open(DEFAULT_ASSET_KEYS).bufferedReader().use { reader ->
                    val lines = mutableListOf<String>()
                    reader.forEachLine { line ->
                        lines.add(line)
                    }
                    alphabetList = lines
                    Log.i(TAG, "Loaded ${alphabetList.size} Muharaf alphabet keys")
                }
            } else {
                val customFile = File(context.filesDir, "models/muharaf_keys.txt")
                if (customFile.exists()) {
                    alphabetList = customFile.readLines()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading muharaf_keys.txt", e)
        }
    }

    /**
     * Imports a user-selected ONNX model file into the application models
     * directory under its own unique filename (no overwrite of existing
     * imports). Does NOT auto-activate — call setActiveModelPath with the
     * returned path to switch to it. Returns the absolute path, or null on
     * failure.
     */
    fun importCustomModel(context: Context, inputStream: java.io.InputStream, fileName: String): String? {
        return try {
            val modelsDir = File(context.filesDir, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val safeBaseName = fileName.substringAfterLast('/').substringAfterLast('\\')
                .ifBlank { "model.onnx" }
                .let { if (it.endsWith(".onnx")) it else "$it.onnx" }

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
            Log.e(TAG, "Failed to import custom Muharaf ONNX model", e)
            null
        }
    }

    /**
     * Preprocesses a cropped text line bitmap and runs real ONNX CTC inference.
     *
     * Adapts to whatever (channels, height) was detected from the active
     * session's own declared input shape (see readInputSpec) rather than
     * assuming the bundled Muharaf model's grayscale/120px convention --
     * that assumption previously meant switching to any model with a
     * different input spec (e.g. a 3-channel RGB model, or one expecting a
     * different fixed height) would silently feed it a wrongly-shaped
     * tensor instead of failing loudly or working correctly.
     */
    fun recognizeLine(
        context: Context,
        lineBitmap: Bitmap
    ): RecognitionResult {
        checkOrInitSession(context)

        val session = ortSession
        val env = ortEnv

        if (session != null && env != null && isModelLoaded && alphabetList.isNotEmpty()) {
            try {
                val origW = lineBitmap.width
                val origH = lineBitmap.height

                if (origW <= 0 || origH <= 0) {
                    return RecognitionResult("", 0f, 0, false)
                }

                val targetHeight = inputHeight
                val channels = inputChannels

                // Scale line image to the active model's declared height, proportional width
                val scale = targetHeight.toFloat() / origH.toFloat()
                var targetW = (origW * scale).toInt().coerceAtLeast(32)
                targetW = max(32, ((targetW + 3) / 4) * 4)

                val scaledBmp = Bitmap.createScaledBitmap(lineBitmap, targetW, targetHeight, true)
                val pixels = IntArray(targetW * targetHeight)
                scaledBmp.getPixels(pixels, 0, targetW, 0, 0, targetW, targetHeight)

                val floatBuffer = FloatBuffer.allocate(channels * targetHeight * targetW)
                val shape: LongArray

                if (channels == 1) {
                    // Grayscale path (verified against the bundled 174-class
                    // Muharaf/Kraken model): min-max normalize per-line for
                    // contrast, then invert to match the model's trained
                    // convention of ink=bright/background=dark. Without the
                    // invert, logits collapse to near-constant blank
                    // predictions (verified: only ~4 of 174 classes ever won
                    // argmax on a real manuscript line before this fix).
                    val rawGrays = FloatArray(targetW * targetHeight)
                    var minG = 1.0f
                    var maxG = 0.0f
                    for (y in 0 until targetHeight) {
                        for (x in 0 until targetW) {
                            val color = pixels[y * targetW + x]
                            val r = Color.red(color) / 255.0f
                            val g = Color.green(color) / 255.0f
                            val b = Color.blue(color) / 255.0f
                            val gray = 0.299f * r + 0.587f * g + 0.114f * b
                            rawGrays[y * targetW + x] = gray
                            if (gray < minG) minG = gray
                            if (gray > maxG) maxG = gray
                        }
                    }
                    val range = (maxG - minG).coerceAtLeast(0.15f)
                    for (i in rawGrays.indices) {
                        val normalized = ((rawGrays[i] - minG) / range).coerceIn(0.0f, 1.0f)
                        floatBuffer.put(1.0f - normalized)
                    }
                    shape = longArrayOf(1, 1, targetHeight.toLong(), targetW.toLong())
                } else {
                    // 3-channel (RGB) path, for models such as the kraken
                    // PP-OCRv6 family that declare a 3-channel input.
                    //
                    // IMPORTANT / NOT FULLY VERIFIED: unlike the grayscale
                    // path above, this branch's exact normalization has not
                    // been confirmed against the model's real training
                    // preprocessing. Testing against a kraken medium
                    // (h=96 and h=128 variants) model showed plain 0-255
                    // (unnormalized) raw pixel values produced far more
                    // plausible-looking output than any 0-1 or mean/std
                    // normalization tried, so that's what's used here, but
                    // the decoded text in that testing still mixed in
                    // non-Arabic characters rather than being reliably
                    // correct -- something about this path (possibly
                    // kraken's baseline-relative dewarping, which this
                    // runner does not perform, only a plain rectangular
                    // crop/resize) is still likely not an exact match for
                    // what the model expects. Treat 3-channel-model output
                    // with proportionally more skepticism than the
                    // grayscale path until this is confirmed against an
                    // authoritative reference implementation.
                    val chwPlanes = Array(3) { FloatArray(targetW * targetHeight) }
                    for (y in 0 until targetHeight) {
                        for (x in 0 until targetW) {
                            val color = pixels[y * targetW + x]
                            val idx = y * targetW + x
                            chwPlanes[0][idx] = Color.red(color).toFloat()
                            chwPlanes[1][idx] = Color.green(color).toFloat()
                            chwPlanes[2][idx] = Color.blue(color).toFloat()
                        }
                    }
                    for (c in 0 until 3) {
                        for (v in chwPlanes[c]) floatBuffer.put(v)
                    }
                    shape = longArrayOf(1, 3, targetHeight.toLong(), targetW.toLong())
                }
                floatBuffer.rewind()

                val inputTensor = OnnxTensor.createTensor(env, floatBuffer, shape)

                val inputName = session.inputNames.firstOrNull() ?: "input"
                val outputMap = session.run(Collections.singletonMap(inputName, inputTensor))

                val outputTensor = outputMap.get(0)
                val outputValue = outputTensor.value

                // Log actual output shape so casting mismatches are visible instead of silently
                // producing an empty transcription that still gets reported as "ONNX success".
                val rawShape = (outputTensor.info as? ai.onnxruntime.TensorInfo)?.shape
                Log.i(
                    TAG,
                    "Rec output[0] name=${session.outputNames.firstOrNull()} shape=${rawShape?.joinToString(prefix = "[", postfix = "]")} " +
                        "javaType=${outputValue?.javaClass?.name} inputW=$targetW alphabetSize=${alphabetList.size}"
                )

                inputTensor.close()
                outputMap.close()

                // Decode CTC Logits
                return decodeCtcOutput(outputValue)
            } catch (e: Exception) {
                Log.e(TAG, "Muharaf ONNX inference failed: ${e.message}", e)
            }
        }

        return RecognitionResult(
            text = "",
            confidence = 0f,
            rawTimeSteps = 0,
            isFromOnnx = false
        )
    }

    /**
     * CTC Greedy Decoder:
     * - Takes argmax class index at each timestep T
     * - Collapses consecutive repeated indices
     * - Strips blank index (0)
     * - Maps index to character from alphabetList
     */
    private fun decodeCtcOutput(outputValue: Any?): RecognitionResult {
        if (outputValue == null) return RecognitionResult("", 0f, 0, false)

        // Use the loaded alphabet's actual size -- no artificial minimum.
        // A previous version floored this at 174 (the bundled Muharaf
        // model's class count), which meant any custom model with a
        // smaller alphabet would never match its true output shape here
        // (e.g. a 50-class model's [1,50,1,T] output would be compared
        // against numClasses=174 and silently fail every shape check
        // below).
        val numClasses = alphabetList.size

        // Handle 4D [1, C, 1, T] or 3D [1, C, T] or [1, T, C]
        var timeSteps = 0
        var shapeRecognized = false
        val predIndices = mutableListOf<Int>()
        val confidences = mutableListOf<Float>()

        try {
            when (outputValue) {
                is Array<*> -> {
                    // Check if 4D Array: [Batch][Channel][Height][Width] -> [1][174][1][T]
                    @Suppress("UNCHECKED_CAST")
                    val raw4D = outputValue as? Array<Array<Array<FloatArray>>>
                    if (raw4D != null && raw4D.isNotEmpty() && raw4D[0].isNotEmpty()) {
                        shapeRecognized = true
                        val channels = raw4D[0].size // 174
                        val tSteps = raw4D[0][0][0].size // T
                        timeSteps = tSteps

                        for (t in 0 until tSteps) {
                            var maxVal = -Float.MAX_VALUE
                            var maxIdx = 0
                            var sumExp = 0.0

                            for (c in 0 until channels) {
                                val logit = raw4D[0][c][0][t]
                                if (logit > maxVal) {
                                    maxVal = logit
                                    maxIdx = c
                                }
                            }

                            // Compute Softmax score for confidence
                            for (c in 0 until channels) {
                                sumExp += exp((raw4D[0][c][0][t] - maxVal).toDouble())
                            }
                            val conf = (1.0 / sumExp).toFloat().coerceIn(0.01f, 1.0f)

                            predIndices.add(maxIdx)
                            confidences.add(conf)
                        }
                    } else {
                        // Check if 3D Array: [1][174][T]
                        @Suppress("UNCHECKED_CAST")
                        val raw3D = outputValue as? Array<Array<FloatArray>>
                        if (raw3D != null && raw3D.isNotEmpty()) {
                            shapeRecognized = true
                            if (raw3D[0].size == numClasses) {
                                // [1][174][T]
                                val channels = raw3D[0].size
                                val tSteps = raw3D[0][0].size
                                timeSteps = tSteps
                                for (t in 0 until tSteps) {
                                    var maxVal = -Float.MAX_VALUE
                                    var maxIdx = 0
                                    for (c in 0 until channels) {
                                        val logit = raw3D[0][c][t]
                                        if (logit > maxVal) {
                                            maxVal = logit
                                            maxIdx = c
                                        }
                                    }
                                    predIndices.add(maxIdx)
                                    confidences.add(0.95f)
                                }
                            } else {
                                // [1][T][174]
                                val tSteps = raw3D[0].size
                                timeSteps = tSteps
                                for (t in 0 until tSteps) {
                                    val row = raw3D[0][t]
                                    var maxVal = -Float.MAX_VALUE
                                    var maxIdx = 0
                                    for (c in row.indices) {
                                        if (row[c] > maxVal) {
                                            maxVal = row[c]
                                            maxIdx = c
                                        }
                                    }
                                    predIndices.add(maxIdx)
                                    confidences.add(0.95f)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing CTC logits: ${e.message}", e)
        }

        if (!shapeRecognized) {
            Log.e(TAG, "Rec tensor shape not recognized by any of the 4D/3D casts; check logged shape above against expected [1,174,1,T].")
            return RecognitionResult("", 0f, 0, false)
        }

        // See reverseTimeAxis field docs: this correction is specific to
        // the bundled Muharaf model's known CTC output orientation and is
        // skipped for other models unless confirmed to need it too.
        if (reverseTimeAxis) {
            predIndices.reverse()
            confidences.reverse()
        }

        // Apply CTC rule: Collapse repeats and eliminate blank (index 0)
        val decodedChars = StringBuilder()
        var prevIdx = -1
        var totalConf = 0.0
        var charCount = 0

        for (i in predIndices.indices) {
            val idx = predIndices[i]
            if (idx != prevIdx) {
                if (idx != 0 && idx < alphabetList.size) {
                    val charStr = alphabetList[idx]
                    decodedChars.append(charStr)
                    totalConf += confidences.getOrElse(i) { 0.90f }
                    charCount++
                }
                prevIdx = idx
            }
        }

        val avgConfidence = if (charCount > 0) {
            (totalConf / charCount).toFloat().coerceIn(0.50f, 0.99f)
        } else {
            0.85f
        }

        val resultText = decodedChars.toString().trim()
        return RecognitionResult(
            text = resultText,
            confidence = avgConfidence,
            rawTimeSteps = timeSteps,
            // Only report true ONNX success when the shape was actually parseable AND
            // at least one non-blank character was decoded — an empty result from a
            // recognized-but-blank-only prediction should not masquerade as success.
            isFromOnnx = resultText.isNotBlank()
        )
    }
}
