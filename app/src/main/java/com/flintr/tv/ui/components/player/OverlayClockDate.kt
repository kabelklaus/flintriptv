package com.flintr.tv.ui.components.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class OverlayDateTimeText(
    val time: String,
    val date: String
)

internal fun formatOverlayDateTime(
    instant: Instant,
    zoneId: ZoneId,
    locale: Locale
): OverlayDateTimeText {
    val zonedDateTime = instant.atZone(zoneId)
    val timeText = DateTimeFormatter.ofPattern("HH:mm", locale).format(zonedDateTime)
    val dateText = DateTimeFormatter.ofPattern("d. MMMM yyyy", locale).format(zonedDateTime)
    return OverlayDateTimeText(time = timeText, date = dateText)
}

@Composable
fun OverlayClockDate(modifier: Modifier = Modifier) {
    var currentMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = System.currentTimeMillis()
            val delayToNextMinute = 60_000L - (now % 60_000L)
            delay(delayToNextMinute)
            currentMillis = System.currentTimeMillis()
        }
    }

    val dateTimeText = remember(currentMillis) {
        formatOverlayDateTime(
            instant = Instant.ofEpochMilli(currentMillis),
            zoneId = ZoneId.systemDefault(),
            locale = Locale.getDefault()
        )
    }

    Column(
        horizontalAlignment = Alignment.End,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.45f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = dateTimeText.time,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = dateTimeText.date,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
