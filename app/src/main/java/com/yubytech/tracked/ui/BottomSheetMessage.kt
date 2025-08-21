package com.yubytech.tracked.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetMessage(
    message: String,
    buttonText: String? = null,
    onButtonClick: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    title: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = Color(0xFF1976D2),
    preventDismiss: Boolean = false
) {
    val sheetState = if (preventDismiss) {
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { false }
        )
    } else {
        rememberModalBottomSheetState()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (title != null) {
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF003366),
                    modifier = Modifier.padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
            }
            Text(
                message,
                fontSize = 16.sp,
                color = Color(0xFF003366),
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center
            )
            if (buttonText != null && onButtonClick != null) {
                Button(
                    onClick = onButtonClick,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2))
                ) {
                    Text(buttonText)
                }
            }
        }
    }
} 