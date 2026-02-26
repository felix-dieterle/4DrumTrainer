package com.drumtrainer.model

/**
 * Represents a music school or drum teacher found via the Overpass API
 * (OpenStreetMap data).
 */
data class TeacherResult(
    val id: Long,
    val name: String,
    val type: String,
    val lat: Double,
    val lon: Double,
    val address: String
)
