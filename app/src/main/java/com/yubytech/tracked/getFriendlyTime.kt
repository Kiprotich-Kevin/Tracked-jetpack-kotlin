package com.yubytech.tracked

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun getFriendlyTime(eventTime: String): String {
    return try {
        val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val outputTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")
        val outputDateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a")

        val eventDateTime = LocalDateTime.parse(eventTime, inputFormatter)
        val eventDate = eventDateTime.toLocalDate()
        val now = LocalDate.now()

        when {
            eventDate.isEqual(now) -> "Today at ${eventDateTime.format(outputTimeFormatter)}"
            eventDate.isEqual(now.minusDays(1)) -> "Yesterday at ${eventDateTime.format(outputTimeFormatter)}"
            else -> eventDateTime.format(outputDateFormatter)
        }
    } catch (e: Exception) {
        // fallback to just time portion if format is bad
        eventTime.substringAfter(" ")
    }
}
