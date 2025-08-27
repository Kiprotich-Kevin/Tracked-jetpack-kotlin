package com.yubytech.tracked.ui

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun SwipeToCheckIn(
    isCheckedIn: Boolean,
    isLoading: Boolean = false,
    showSuccess: Boolean = false, // <-- new param driven by ViewModel (uiState)
    modifier: Modifier = Modifier,
    onSwipeComplete: () -> Unit
) {
    val swipeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val checkInColor = Color(0xFF0084FF)
    val checkOutColor = Color(0xFF006FD6) // darker, deeper blue

    val buttonColor = if (isCheckedIn) checkOutColor else checkInColor
    val buttonText = if (isCheckedIn) "Swipe to check out" else "Swipe to check in"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(70.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEFEFEF),
                        Color(0xFFD0D0D0)
                    )
                )
            )
            .drawBehind {
                val strokeWidth = 3.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFDCDBDB),
                    size = size,
                    cornerRadius = CornerRadius(35.dp.toPx()),
                    style = Stroke(width = strokeWidth)
                )
            }
            .padding(horizontal = 8.dp)
    ) {
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.toPx() }
        val thumbSizePx = with(density) { 50.dp.toPx() }
        val paddingPx = with(density) { 10.dp.toPx() }
        val endLimit = maxWidthPx - thumbSizePx - paddingPx

        // Text follows the swipe but stops before it overlaps thumb
        val textRowMaxOffset = endLimit - with(density) { 90.dp.toPx() }
        val textOffset = swipeOffset.value.coerceAtMost(textRowMaxOffset).toInt()

        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(30.dp)
                        .align(Alignment.Center),
                    strokeWidth = 2.dp,
                    color = buttonColor
                )
            }
            showSuccess -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Success",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
            else -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset { IntOffset(textOffset, 0) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buttonText,
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Arrow",
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Thumb only shows when not loading or already success
        if (!isLoading && !showSuccess) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(buttonColor)
                    .draggable(
                        orientation = Orientation.Horizontal,
                        state = rememberDraggableState { delta ->
                            coroutineScope.launch {
                                val newValue = (swipeOffset.value + delta).coerceIn(0f, endLimit)
                                swipeOffset.snapTo(newValue)
                            }
                        },
                        onDragStopped = {
                            coroutineScope.launch {
                                if (swipeOffset.value >= endLimit * 0.98f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    swipeOffset.snapTo(0f)
                                    onSwipeComplete()
                                } else {
                                    swipeOffset.animateTo(0f)
                                }
                            }
                        }
                    )
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Swipe Thumb",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}