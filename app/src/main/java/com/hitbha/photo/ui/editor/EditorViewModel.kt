package com.hitbha.photo.ui.editor

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hitbha.photo.utils.ImageProcessor
import com.hitbha.photo.utils.StatePersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class EditorState(
    val originalBitmap: Bitmap? = null,
    val sourceBitmap: Bitmap? = null,
    val currentBitmap: Bitmap? = null,
    val preFillBitmap: Bitmap? = null,
    val imageUri: Uri? = null,
    val brightness: Int = 0,
    val contrast: Float = 0f,
    val temperature: Float = 0f,
    val sharpen: Float = 0f,
    val rotation: Float = 0f,
    val isMirroredH: Boolean = false,
    val isMirroredV: Boolean = false,
    val compressQuality: Int = -1
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val _editorState = MutableLiveData(EditorState())
    val editorState: LiveData<EditorState> = _editorState

    private val _processing = MutableLiveData(false)
    val processing: LiveData<Boolean> = _processing

    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    private val _canRedo = MutableLiveData(false)
    val canRedo: LiveData<Boolean> = _canRedo

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    fun clearMessage() {
        _message.value = null
    }

    fun setProcessing(processing: Boolean) {
        _processing.value = processing
    }

    private var pendingAdjustJob: Job? = null

    private val undoStack = mutableListOf<EditorState>()
    private val redoStack = mutableListOf<EditorState>()
    private val transformMutex = Mutex()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private companion object {
        private const val MAX_UNDO = 15
    }

    /**
     * Recycles a bitmap only once the UI has had a chance to switch away from it.
     * Bitmaps must never be recycled on a background thread while the ImageView may
     * still be drawing them - doing so triggers a hard crash.
     */
    private fun recycleSafe(b: Bitmap?) {
        if (b == null || b.isRecycled) return
        val pending = mainHandler.post { if (!b.isRecycled) b.recycle() }
        if (!pending) {
            if (!b.isRecycled) b.recycle()
        }
    }

    private fun recycleIfDifferent(previous: Bitmap?, current: Bitmap?, excluded: Set<Bitmap> = emptySet()) {
        if (previous == null || previous === current) return
        if (previous in excluded) return
        if (!previous.isRecycled) previous.recycle()
    }

    private fun adjustmentsFromState(s: EditorState) = AdjustParams(
        s.brightness, s.contrast, s.temperature
    )

    private data class AdjustParams(
        val brightness: Int, val contrast: Float, val temperature: Float
    ) {
        fun isActive() = brightness != 0 || contrast != 0f || temperature != 0f
    }

    private fun applyAll(bitmap: Bitmap, p: AdjustParams): Bitmap {
        return ImageProcessor.applyAdjustments(
            bitmap, p.brightness, p.contrast, p.temperature
        )
    }

    private fun fullRender(source: Bitmap, state: EditorState): Bitmap {
        val p = adjustmentsFromState(state)
        val colorAdjusted = if (p.isActive()) applyAll(source, p) else source
        return if (state.sharpen > 0f) ImageProcessor.applySharpen(colorAdjusted, state.sharpen) else colorAdjusted
    }

    private fun applyAdjustments(update: (EditorState) -> EditorState) {
        pendingAdjustJob?.cancel()
        pendingAdjustJob = viewModelScope.launch(Dispatchers.Default) {
            val s = _editorState.value ?: return@launch
            val updated = update(s)
            val src = updated.sourceBitmap ?: return@launch
            val rendered = fullRender(src, updated)
            if (!isActive) return@launch
            val prev = _editorState.value
            _editorState.postValue(updated.copy(currentBitmap = rendered))
            val prevCurrent = prev?.currentBitmap
            if (prev != null && prevCurrent !== rendered &&
                prevCurrent !== prev.sourceBitmap && prevCurrent !== prev.originalBitmap) {
                recycleSafe(prevCurrent)
            }
        }
    }

    fun setOriginalImage(bitmap: Bitmap) {
        val prev = _editorState.value
        undoStack.clear()
        redoStack.clear()
        _editorState.value = prev?.copy(
            originalBitmap = bitmap,
            sourceBitmap = bitmap,
            currentBitmap = bitmap,
            preFillBitmap = null
        )
        prev?.let { p ->
            listOf(p.originalBitmap, p.sourceBitmap, p.currentBitmap, p.preFillBitmap)
                .filterNotNull().distinct()
                .filter { it !== bitmap && !it.isRecycled }
                .forEach { it.recycle() }
        }
        updateUndoRedoState()
    }

    private fun saveState() {
        val state = _editorState.value ?: return
        undoStack.add(state)
        if (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        redoStack.forEach { rs ->
            listOf(rs.originalBitmap, rs.sourceBitmap, rs.currentBitmap, rs.preFillBitmap)
                .filterNotNull().distinct()
                .forEach { recycleSafe(it) }
        }
        redoStack.clear()
        updateUndoRedoState()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val currentState = _editorState.value ?: return
        redoStack.add(currentState)
        val prevState = undoStack.removeAt(undoStack.lastIndex)
        _editorState.value = prevState
        updateUndoRedoState()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val currentState = _editorState.value ?: return
        undoStack.add(currentState)
        val nextState = redoStack.removeAt(redoStack.lastIndex)
        _editorState.value = nextState
        updateUndoRedoState()
    }

    private fun updateUndoRedoState() {
        _canUndo.postValue(undoStack.isNotEmpty())
        _canRedo.postValue(redoStack.isNotEmpty())
    }

    fun applyResize(width: Int, height: Int, keepAspectRatio: Boolean) {
        saveState()
        val current = _editorState.value?.sourceBitmap ?: return
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val resized = ImageProcessor.resizeBitmap(current, width, height, keepAspectRatio)
                val s = _editorState.value
                val rendered = if (s != null) fullRender(resized, s) else resized
                _editorState.postValue(_editorState.value?.copy(sourceBitmap = resized, currentBitmap = rendered, preFillBitmap = null))
            }
            _processing.postValue(false)
        }
    }

    fun applyBrightness(value: Int) {
        applyAdjustments { it.copy(brightness = value) }
    }

    fun applyContrast(value: Float) {
        applyAdjustments { it.copy(contrast = value) }
    }

    fun applyTemperature(value: Float) {
        applyAdjustments { it.copy(temperature = value) }
    }

    fun applySharpen(value: Float) {
        applyAdjustments { it.copy(sharpen = value) }
    }

    fun applyRotation(degrees: Float) {
        saveState()
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val current = _editorState.value?.sourceBitmap ?: return@withLock
                val rotated = ImageProcessor.rotateBitmap(current, degrees)
                val s = _editorState.value
                val rendered = if (s != null) fullRender(rotated, s) else rotated
                _editorState.postValue(_editorState.value?.copy(
                    sourceBitmap = rotated, currentBitmap = rendered,
                    rotation = (_editorState.value?.rotation ?: 0f) + degrees,
                    preFillBitmap = null
                ))
            }
            _processing.postValue(false)
        }
    }

    fun applyMirror(horizontal: Boolean) {
        saveState()
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val current = _editorState.value?.sourceBitmap ?: return@withLock
                val mirrored = ImageProcessor.mirrorBitmap(current, horizontal)
                val s = _editorState.value
                val rendered = if (s != null) fullRender(mirrored, s) else mirrored
                val curState = _editorState.value
                val newIsH = if (horizontal) !(curState?.isMirroredH ?: false) else (curState?.isMirroredH ?: false)
                val newIsV = if (!horizontal) !(curState?.isMirroredV ?: false) else (curState?.isMirroredV ?: false)
                _editorState.postValue(_editorState.value?.copy(
                    sourceBitmap = mirrored, currentBitmap = rendered,
                    isMirroredH = newIsH, isMirroredV = newIsV,
                    preFillBitmap = null
                ))
            }
            _processing.postValue(false)
        }
    }

    fun commitAdjustments() {
        saveState()
        pendingAdjustJob?.cancel()
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (isActive) {
                    transformMutex.withLock {
                        val s = _editorState.value ?: return@withLock
                        val src = s.sourceBitmap ?: return@withLock
                        val rendered = fullRender(src, s)
                        _editorState.postValue(s.copy(
                            sourceBitmap = rendered,
                            currentBitmap = rendered,
                            brightness = 0, contrast = 0f, temperature = 0f,
                            sharpen = 0f,
                            rotation = 0f, isMirroredH = false, isMirroredV = false,
                            preFillBitmap = null
                        ))
                    }
                }
            } finally {
                _processing.postValue(false)
            }
        }
    }

    fun resetAdjustments() {
        pendingAdjustJob?.cancel()
        val s = _editorState.value ?: return
        val source = s.sourceBitmap ?: return
        val prevCurrent = s.currentBitmap
        _editorState.value = s.copy(
            currentBitmap = source,
            brightness = 0, contrast = 0f, temperature = 0f,
            sharpen = 0f
        )
        if (prevCurrent != null && prevCurrent !== source) recycleSafe(prevCurrent)
    }

    fun setCompressedImage(bitmap: Bitmap, quality: Int = -1) {
        saveState()
        _editorState.value = _editorState.value?.copy(
            sourceBitmap = bitmap,
            currentBitmap = bitmap,
            compressQuality = quality,
            preFillBitmap = null
        )
    }

    fun setFullPageResult(bitmap: Bitmap) {
        saveState()
        _editorState.value = _editorState.value?.copy(
            sourceBitmap = bitmap,
            currentBitmap = bitmap,
            preFillBitmap = null
        )
    }

    fun updateFullPageResult(bitmap: Bitmap) {
        val s = _editorState.value ?: return
        val prevCurrent = s.currentBitmap
        val prevSource = s.sourceBitmap
        _editorState.value = s.copy(
            sourceBitmap = bitmap,
            currentBitmap = bitmap,
            preFillBitmap = null
        )
        if (prevCurrent != null && prevCurrent !== bitmap) recycleSafe(prevCurrent)
        if (prevSource != null && prevSource !== bitmap && prevSource !== prevCurrent) recycleSafe(prevSource)
    }

    fun createBlankPage(width: Int, height: Int) {
        val blank = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(blank)
        canvas.drawColor(android.graphics.Color.WHITE)
        setOriginalImage(blank)
    }

    fun addStamp(stamp: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
        saveState()
        val current = _editorState.value?.sourceBitmap ?: return
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val combined = ImageProcessor.compositeImage(current, stamp, left, top, right, bottom)
                val s = _editorState.value
                val rendered = if (s != null) fullRender(combined, s) else combined
                _editorState.postValue(_editorState.value?.copy(sourceBitmap = combined, currentBitmap = rendered, preFillBitmap = null))
            }
            _processing.postValue(false)
            _message.postValue(null)
        }
    }

    fun applyCrop(left: Int, top: Int, right: Int, bottom: Int) {
        saveState()
        val current = _editorState.value?.sourceBitmap ?: return
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val cropped = ImageProcessor.cropBitmap(current, left, top, right, bottom)
                val s = _editorState.value
                val rendered = if (s != null) fullRender(cropped, s) else cropped
                _editorState.postValue(_editorState.value?.copy(sourceBitmap = cropped, currentBitmap = rendered, preFillBitmap = null))
            }
            _processing.postValue(false)
        }
    }

    fun applyErase(points: List<Pair<Float, Float>>, brushRadius: Float) {
        saveState()
        viewModelScope.launch(Dispatchers.Default) {
            val current = withContext(Dispatchers.Main) {
                _editorState.value?.sourceBitmap
            } ?: return@launch
            val erased = ImageProcessor.eraseBitmap(current, points, brushRadius)
            transformMutex.withLock {
                withContext(Dispatchers.Main) {
                    val s = _editorState.value
                    val rendered = if (s != null) fullRender(erased, s) else erased
                    _editorState.value = _editorState.value?.copy(sourceBitmap = erased, currentBitmap = rendered, preFillBitmap = null)
                }
            }
        }
    }

    fun applyColorErase(x: Float, y: Float) {
        saveState()
        val current = _editorState.value?.sourceBitmap ?: return
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val erased = ImageProcessor.colorEraseBitmap(current, x, y)
                val s = _editorState.value
                val rendered = if (s != null) fullRender(erased, s) else erased
                _editorState.postValue(_editorState.value?.copy(sourceBitmap = erased, currentBitmap = rendered, preFillBitmap = null))
            }
            _processing.postValue(false)
        }
    }

    fun applyMagicRemove(targetColor: Int, tolerance: Int = 40) {
        saveState()
        val current = _editorState.value?.sourceBitmap ?: return
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val erased = ImageProcessor.magicRemoveBitmap(current, targetColor, tolerance)
                val s = _editorState.value
                val rendered = if (s != null) fullRender(erased, s) else erased
                _editorState.postValue(_editorState.value?.copy(sourceBitmap = erased, currentBitmap = rendered, preFillBitmap = null))
            }
            _processing.postValue(false)
        }
    }

    val currentState: EditorState?
        get() = _editorState.value

    fun restoreSnapshot(snapshot: Bitmap) {
        _editorState.value = _editorState.value?.copy(
            sourceBitmap = snapshot,
            currentBitmap = snapshot,
            preFillBitmap = null
        )
    }

    fun applyBgRemoveResult(resultBitmap: Bitmap) {
        saveState()
        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                val s = _editorState.value
                val rendered = if (s != null) fullRender(resultBitmap, s) else resultBitmap
                _editorState.postValue(_editorState.value?.copy(
                    sourceBitmap = resultBitmap,
                    currentBitmap = rendered,
                    preFillBitmap = null
                ))
            }
            _processing.postValue(false)
        }
    }

    fun saveStateToCache(ctx: Context) {
        val s = _editorState.value ?: return
        val currUndo = undoStack.map { it.currentBitmap }
        val currRedo = redoStack.map { it.currentBitmap }
        StatePersistence.saveEditorState(
            ctx = ctx,
            original = s.originalBitmap,
            source = s.sourceBitmap,
            current = s.currentBitmap,
            preFill = s.preFillBitmap,
            undoStack = currUndo.filterNotNull(),
            redoStack = currRedo.filterNotNull(),
            compressQuality = s.compressQuality
        )
    }

    fun restoreStateFromCache(ctx: Context): Boolean {
        val saved = StatePersistence.loadEditorState(ctx) ?: return false
        if (saved.current == null) return false

        val s = EditorState(
            originalBitmap = saved.original,
            sourceBitmap = saved.source,
            currentBitmap = saved.current,
            preFillBitmap = saved.preFill,
            compressQuality = saved.compressQuality
        )
        _editorState.value = s

        undoStack.clear()
        for (bmp in saved.undoStack) {
            undoStack.add(EditorState(
                originalBitmap = null,
                sourceBitmap = bmp,
                currentBitmap = bmp
            ))
        }
        redoStack.clear()
        for (bmp in saved.redoStack) {
            redoStack.add(EditorState(
                originalBitmap = null,
                sourceBitmap = bmp,
                currentBitmap = bmp
            ))
        }
        updateUndoRedoState()
        return true
    }

    fun applyFinalTouches(
        featherRadius: Int = 0,
        fillColor: Int? = null,
        outlineRemovePx: Int = 0
    ) {
        saveState()
        val state = _editorState.value ?: return

        val baseBitmap = if (fillColor != null && state.preFillBitmap != null) {
            state.preFillBitmap
        } else {
            state.sourceBitmap ?: return
        }
        val isFirstFill = fillColor != null && state.preFillBitmap == null

        _processing.value = true
        viewModelScope.launch(Dispatchers.Default) {
            transformMutex.withLock {
                var result = baseBitmap

                if (isFirstFill || fillColor == null) {
                    if (outlineRemovePx > 0)
                        result = ImageProcessor.removeOutline(result, outlineRemovePx)

                    if (featherRadius > 0)
                        result = ImageProcessor.featherEdges(result, featherRadius)
                }

                val s = _editorState.value
                val rendered = if (s != null) fullRender(result, s) else result
                val display = if (fillColor != null) ImageProcessor.fillBackground(rendered, fillColor) else rendered

                val newPreFill = when {
                    isFirstFill -> result.copy(Bitmap.Config.ARGB_8888, true)
                    fillColor != null -> state.preFillBitmap
                    else -> null
                }

                _editorState.postValue(_editorState.value?.copy(
                    sourceBitmap = if (fillColor != null) display else result,
                    currentBitmap = display,
                    preFillBitmap = newPreFill
                ))
            }
            _processing.postValue(false)
        }
    }

}
