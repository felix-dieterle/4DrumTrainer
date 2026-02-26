package com.drumtrainer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.drumtrainer.databinding.ActivityTeacherSearchBinding
import com.drumtrainer.model.TeacherResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI

/**
 * Allows the user to discover nearby music schools and drum teachers.
 *
 * Uses two free, no-API-key-required services:
 *  - Nominatim (OpenStreetMap) for geocoding a city name to coordinates.
 *  - Overpass API (OpenStreetMap) to query for music schools near those
 *    coordinates within a configurable radius.
 *
 * The user can either grant the app location permission (GPS/network) or
 * manually type a city name / postal code. Tapping a result opens the
 * location in the device's default maps application.
 */
class TeacherSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherSearchBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.title_teacher_search)

        binding.buttonSearchLocation.setOnClickListener {
            requestLocationAndSearch()
        }

        binding.buttonSearchCity.setOnClickListener {
            val city = binding.editCity.text?.toString()?.trim().orEmpty()
            if (city.isEmpty()) {
                Toast.makeText(this, getString(R.string.error_city_required), Toast.LENGTH_SHORT).show()
            } else {
                searchByCity(city)
            }
        }

        binding.buttonOpenMaps.setOnClickListener {
            openMapsSearch()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            searchByCurrentLocation()
        } else {
            Toast.makeText(
                this, getString(R.string.error_location_permission), Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Location helpers ──────────────────────────────────────────────────────

    private fun requestLocationAndSearch() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineGranted || coarseGranted) {
            searchByCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_LOCATION
            )
        }
    }

    private fun searchByCurrentLocation() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val location = try {
            val fineGranted = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarseGranted = ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }
        } catch (_: SecurityException) {
            null
        }

        if (location != null) {
            searchNearby(location.latitude, location.longitude)
        } else {
            Toast.makeText(
                this, getString(R.string.error_location_unavailable), Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Search entry points ───────────────────────────────────────────────────

    private fun searchByCity(city: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val coords = withContext(Dispatchers.IO) { geocodeCity(city) }
                if (coords != null) {
                    val results = withContext(Dispatchers.IO) { queryOverpass(coords.first, coords.second) }
                    setLoading(false)
                    showResults(results, coords.first, coords.second)
                } else {
                    setLoading(false)
                    Toast.makeText(this@TeacherSearchActivity, getString(R.string.error_city_not_found), Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                setLoading(false)
                Toast.makeText(this@TeacherSearchActivity, getString(R.string.error_search_failed), Toast.LENGTH_LONG).show()
            } catch (e: JSONException) {
                setLoading(false)
                Toast.makeText(this@TeacherSearchActivity, getString(R.string.error_search_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun searchNearby(lat: Double, lon: Double) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val results = withContext(Dispatchers.IO) { queryOverpass(lat, lon) }
                setLoading(false)
                showResults(results, lat, lon)
            } catch (e: IOException) {
                setLoading(false)
                Toast.makeText(this@TeacherSearchActivity, getString(R.string.error_search_failed), Toast.LENGTH_LONG).show()
            } catch (e: JSONException) {
                setLoading(false)
                Toast.makeText(this@TeacherSearchActivity, getString(R.string.error_search_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Network calls ─────────────────────────────────────────────────────────

    /** Geocode [city] to (lat, lon) using the free Nominatim API. */
    private fun geocodeCity(city: String): Pair<Double, Double>? {
        val encoded = URLEncoder.encode(city, "UTF-8")
        val url = URL("https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "4DrumTrainer/1.0")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return try {
            val json = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(json)
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                Pair(obj.getDouble("lat"), obj.getDouble("lon"))
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Query the free Overpass API for music schools within [SEARCH_RADIUS_M] metres
     * of ([lat], [lon]).
     */
    private fun queryOverpass(lat: Double, lon: Double): List<TeacherResult> {
        val query = """
            [out:json][timeout:15];
            (
              node["amenity"="music_school"](around:$SEARCH_RADIUS_M,$lat,$lon);
              way["amenity"="music_school"](around:$SEARCH_RADIUS_M,$lat,$lon);
              node["leisure"="music_school"](around:$SEARCH_RADIUS_M,$lat,$lon);
              way["leisure"="music_school"](around:$SEARCH_RADIUS_M,$lat,$lon);
            );
            out center;
        """.trimIndent()

        val url = URL("https://overpass-api.de/api/interpreter")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.connectTimeout = 15_000
        conn.readTimeout = 25_000
        return try {
            conn.outputStream.bufferedWriter().use { writer ->
                writer.write("data=${URLEncoder.encode(query, "UTF-8")}")
            }
            val json = conn.inputStream.bufferedReader().readText()
            parseOverpassResults(json)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseOverpassResults(json: String): List<TeacherResult> {
        val root = JSONObject(json)
        val elements = root.getJSONArray("elements")
        val results = mutableListOf<TeacherResult>()

        for (i in 0 until elements.length()) {
            val elem = elements.getJSONObject(i)
            val tags = elem.optJSONObject("tags") ?: continue
            val name = tags.optString("name")
            if (name.isEmpty()) continue

            val lat = when {
                elem.has("lat") -> elem.getDouble("lat")
                elem.has("center") -> elem.getJSONObject("center").getDouble("lat")
                else -> continue
            }
            val lon = when {
                elem.has("lon") -> elem.getDouble("lon")
                elem.has("center") -> elem.getJSONObject("center").getDouble("lon")
                else -> continue
            }

            val street = tags.optString("addr:street")
            val houseNumber = tags.optString("addr:housenumber")
            val city = tags.optString("addr:city")
            val address = buildString {
                if (street.isNotEmpty()) append(street)
                if (houseNumber.isNotEmpty()) {
                    if (isNotEmpty()) append(" ")
                    append(houseNumber)
                }
                if (city.isNotEmpty()) {
                    if (isNotEmpty()) append(", ")
                    append(city)
                }
            }

            val type = tags.optString("amenity").ifEmpty { tags.optString("leisure") }
            results.add(TeacherResult(elem.getLong("id"), name, type, lat, lon, address))
        }
        return results
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun showResults(results: List<TeacherResult>, userLat: Double, userLon: Double) {
        binding.resultsContainer.removeAllViews()

        if (results.isEmpty()) {
            binding.textNoResults.visibility = View.VISIBLE
            binding.buttonOpenMaps.visibility = View.VISIBLE
            return
        }

        binding.textNoResults.visibility = View.GONE
        binding.buttonOpenMaps.visibility = View.GONE

        val sorted = results.sortedBy { r -> haversineKm(userLat, userLon, r.lat, r.lon) }

        for (result in sorted) {
            val itemView = layoutInflater.inflate(
                R.layout.item_teacher_result, binding.resultsContainer, false
            )
            itemView.findViewById<TextView>(R.id.textResultName).text = result.name

            val distKm = haversineKm(userLat, userLon, result.lat, result.lon)
            itemView.findViewById<TextView>(R.id.textResultDistance).text =
                if (distKm < 1.0) {
                    getString(R.string.distance_meters, (distKm * 1000).toInt())
                } else {
                    getString(R.string.distance_km, "%.1f".format(distKm))
                }

            val addrView = itemView.findViewById<TextView>(R.id.textResultAddress)
            if (result.address.isNotEmpty()) {
                addrView.text = result.address
            } else {
                addrView.visibility = View.GONE
            }

            itemView.setOnClickListener { openInMaps(result) }
            binding.resultsContainer.addView(itemView)
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = sin(dLat / 2).let { it * it } +
                cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
                sin(dLon / 2).let { it * it }
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun openInMaps(result: TeacherResult) {
        val geoUri = Uri.parse("geo:${result.lat},${result.lon}?q=${Uri.encode(result.name)}")
        val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri)
        if (mapsIntent.resolveActivity(packageManager) != null) {
            startActivity(mapsIntent)
        } else {
            val webUri = Uri.parse(
                "https://www.openstreetmap.org/?mlat=${result.lat}&mlon=${result.lon}&zoom=16"
            )
            startActivity(Intent(Intent.ACTION_VIEW, webUri))
        }
    }

    /** Fallback: open Google Maps / browser search for drum teachers. */
    private fun openMapsSearch() {
        val query = URLEncoder.encode("Musikschule Schlagzeugunterricht", "UTF-8")
        val uri = Uri.parse("https://www.google.com/maps/search/$query")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.buttonSearchLocation.isEnabled = !loading
        binding.buttonSearchCity.isEnabled = !loading
        if (loading) {
            binding.textNoResults.visibility = View.GONE
            binding.buttonOpenMaps.visibility = View.GONE
        }
    }

    companion object {
        private const val REQUEST_LOCATION = 1002
        private const val SEARCH_RADIUS_M = 15_000
    }
}
