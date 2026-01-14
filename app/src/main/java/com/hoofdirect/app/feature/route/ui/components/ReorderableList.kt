package com.hoofdirect.app.feature.route.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * State holder for reorderable list operations.
 */
class ReorderableState<T> {
    var items by mutableStateOf<List<T>>(emptyList())
    var draggingItemIndex by mutableIntStateOf(-1)
        private set
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    private var itemPositions = mutableMapOf<Int, Float>()

    fun updateItems(newItems: List<T>) {
        items = newItems
    }

    fun startDragging(index: Int) {
        draggingItemIndex = index
        dragOffsetY = 0f
    }

    fun onDrag(dragAmount: Float) {
        dragOffsetY += dragAmount
    }

    fun updateItemPosition(index: Int, positionY: Float) {
        itemPositions[index] = positionY
    }

    fun endDragging(): Pair<Int, Int>? {
        if (draggingItemIndex < 0) return null

        val fromIndex = draggingItemIndex
        val draggedPosition = (itemPositions[fromIndex] ?: 0f) + dragOffsetY

        // Find target index based on position
        var toIndex = fromIndex
        for ((index, position) in itemPositions) {
            if (index != fromIndex) {
                if (draggedPosition > position - 20 && index > toIndex) {
                    toIndex = index
                } else if (draggedPosition < position + 20 && index < toIndex) {
                    toIndex = index
                }
            }
        }

        draggingItemIndex = -1
        dragOffsetY = 0f

        return if (fromIndex != toIndex) Pair(fromIndex, toIndex) else null
    }

    val isDragging: Boolean
        get() = draggingItemIndex >= 0
}

@Composable
fun <T> rememberReorderableState(
    items: List<T>
): ReorderableState<T> {
    val state = remember { ReorderableState<T>() }
    state.updateItems(items)
    return state
}

/**
 * A simple reorderable list using long-press to drag.
 */
@Composable
fun <T> ReorderableColumn(
    state: ReorderableState<T>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, item: T, isDragging: Boolean) -> Unit
) {
    Column(modifier = modifier) {
        state.items.forEachIndexed { index, item ->
            val isDragging = state.draggingItemIndex == index

            val elevation by animateDpAsState(
                targetValue = if (isDragging) 8.dp else 0.dp,
                label = "elevation"
            )

            val offsetY by animateDpAsState(
                targetValue = if (isDragging) state.dragOffsetY.dp else 0.dp,
                label = "offsetY"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(0, if (isDragging) state.dragOffsetY.roundToInt() else 0) }
                    .zIndex(if (isDragging) 1f else 0f)
                    .shadow(elevation)
                    .onGloballyPositioned { coordinates ->
                        state.updateItemPosition(index, coordinates.positionInParent().y)
                    }
                    .pointerInput(index) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                state.startDragging(index)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                state.onDrag(dragAmount.y)
                            },
                            onDragEnd = {
                                state.endDragging()?.let { (from, to) ->
                                    onReorder(from, to)
                                }
                            },
                            onDragCancel = {
                                state.endDragging()
                            }
                        )
                    }
            ) {
                itemContent(index, item, isDragging)
            }
        }
    }
}
