package com.drumtrainer.model

import org.junit.Assert.*
import org.junit.Test

class TeacherResultTest {

    @Test
    fun `TeacherResult holds all fields correctly`() {
        val result = TeacherResult(
            id = 42L,
            name = "Musikschule Berlin",
            type = "music_school",
            lat = 52.5200,
            lon = 13.4050,
            address = "Hauptstraße 1, Berlin"
        )
        assertEquals(42L, result.id)
        assertEquals("Musikschule Berlin", result.name)
        assertEquals("music_school", result.type)
        assertEquals(52.5200, result.lat, 0.0001)
        assertEquals(13.4050, result.lon, 0.0001)
        assertEquals("Hauptstraße 1, Berlin", result.address)
    }

    @Test
    fun `two TeacherResults with identical data are equal`() {
        val r1 = TeacherResult(1L, "Test", "music_school", 52.0, 13.0, "Addr")
        val r2 = TeacherResult(1L, "Test", "music_school", 52.0, 13.0, "Addr")
        assertEquals(r1, r2)
    }

    @Test
    fun `TeacherResults with different ids are not equal`() {
        val r1 = TeacherResult(1L, "Test", "music_school", 52.0, 13.0, "Addr")
        val r2 = TeacherResult(2L, "Test", "music_school", 52.0, 13.0, "Addr")
        assertNotEquals(r1, r2)
    }

    @Test
    fun `TeacherResult with empty address is valid`() {
        val result = TeacherResult(1L, "Drumschule", "music_school", 48.1, 11.5, "")
        assertTrue(result.address.isEmpty())
    }
}
