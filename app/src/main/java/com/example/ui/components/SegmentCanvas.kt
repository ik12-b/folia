package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.BlockRegionEntity
import com.example.data.model.LineSegmentEntity
import com.example.ui.viewmodel.CanvasToolMode

/**
 * SegmentCanvas - Adapter delegating to ManuscriptOverlayCanvas
 * Draws PP-OCRv5 DBNet bounding boxes, baseline vectors, and scholarly block regions over the manuscript image.
 */
@Composable
fun SegmentCanvas(
    imageResName: String?,
    blocks: List<BlockRegionEntity>,
    lines: List<LineSegmentEntity>,
    selectedLineId: Long?,
    canvasToolMode: CanvasToolMode,
    showOverlay: Boolean = true,
    scale: Float,
    onScaleChange: (Float) -> Unit,
    onLineSelected: (Long) -> Unit,
    onPolygonCompleted: (String) -> Unit,
    onBaselineCompleted: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    ManuscriptOverlayCanvas(
        imageResName = imageResName,
        blocks = blocks,
        lines = lines,
        selectedLineId = selectedLineId,
        canvasToolMode = canvasToolMode,
        showBoundingBoxes = showOverlay,
        showBaselines = showOverlay,
        showLineBadges = showOverlay,
        showBlockRegions = showOverlay,
        showConfidenceTags = showOverlay,
        scale = scale,
        onScaleChange = onScaleChange,
        onLineSelected = onLineSelected,
        onPolygonCompleted = onPolygonCompleted,
        onBaselineCompleted = onBaselineCompleted,
        modifier = modifier
    )
}
