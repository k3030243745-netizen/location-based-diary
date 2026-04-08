package com.example.myapplication

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "DiaryApp"

class DiaryViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).diaryDao()

    // Geofence
    private val geofencingClient = LocationServices.getGeofencingClient(app)

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(getApplication(), GeofenceBroadcastReceiver::class.java)
        PendingIntent.getBroadcast(
            getApplication(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    private val poiDao = AppDatabase.get(app).poiDao()
    private val _nearbyPois = MutableStateFlow<List<Poi>>(emptyList())
    val nearbyPois: StateFlow<List<Poi>> = _nearbyPois.asStateFlow()
    private val _poiMap = MutableStateFlow<Map<Long, Poi>>(emptyMap())
    val poiMap: StateFlow<Map<Long, Poi>> = _poiMap.asStateFlow()

    val entries: StateFlow<List<DiaryEntry>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val _remotePois = MutableStateFlow<List<RemotePoi>>(emptyList())
    val remotePois: StateFlow<List<RemotePoi>> = _remotePois.asStateFlow()
    private val _suggestedCategories = MutableStateFlow<List<SuggestedCategory>>(emptyList())
    val suggestedCategories: StateFlow<List<SuggestedCategory>> = _suggestedCategories.asStateFlow()

    private val _recommendedRadiusM = MutableStateFlow<Int?>(null)
    val recommendedRadiusM: StateFlow<Int?> = _recommendedRadiusM.asStateFlow()

    private val _intentSource = MutableStateFlow<String?>(null)
    val intentSource: StateFlow<String?> = _intentSource.asStateFlow()
    private val _remotePoiError = MutableStateFlow<String?>(null)
    val remotePoiError: StateFlow<String?> = _remotePoiError.asStateFlow()

    private val _remoteErr = MutableStateFlow<String?>(null)
    val remoteErr: StateFlow<String?> = _remoteErr

    fun pingHealth() {
        viewModelScope.launch {
            _remotePoiError.value = "Pinging /health..."
            try {
                val res = ApiClient.api.health()
                _remotePoiError.value = "Health OK: $res"
            } catch (t: Throwable) {
                _remotePoiError.value = "Health FAIL: ${t.message ?: t}"
            }
        }
    }
    fun classifyIntent(purpose: String) {
        viewModelScope.launch {
            try {
                _remotePoiError.value = null
                _suggestedCategories.value = emptyList()
                _recommendedRadiusM.value = null
                _intentSource.value = null

                val result = ApiClient.api.classifyIntent(purpose)

                _suggestedCategories.value = result.topCategories
                _recommendedRadiusM.value = result.recommendedRadiusM
                _intentSource.value = result.source
            } catch (e: Exception) {
                Log.e(TAG, "classifyIntent failed", e)
                _remotePoiError.value = e.message
                _suggestedCategories.value = emptyList()
                _recommendedRadiusM.value = null
                _intentSource.value = null
            }
        }
    }
    fun addEntry(
        title: String,
        note: String,
        lat: Double?,
        lng: Double?,
        radiusMeters: Double,
        poiId: Long? = null
    ) {
        viewModelScope.launch {
            val id = dao.insert(
                DiaryEntry(
                    title = title,
                    note = note,
                    lat = lat,
                    lng = lng,
                    radiusMeters = radiusMeters,
                    poiId = poiId
                )
            )

            // Only register the geofence after obtaining valid coordinates.
            if (lat != null && lng != null) {
                registerGeofenceForEntry(
                    entryId = id,
                    lat = lat,
                    lng = lng,
                    radiusMeters = radiusMeters.toFloat()
                )
            } else {
                Log.w(TAG, "Entry saved but lat/lng null, skip geofence. id=$id")
            }
        }
    }

    fun restoreAllGeofences() {
        Log.d(TAG, "restoreAllGeofences() called")   // print immediately

        viewModelScope.launch {
            try {
                val entries = dao.getAllWithLocationOnce()
                Log.d(TAG, "restoreAllGeofences(): entries=${entries.size}")  // ② 查到多少条

                entries.forEach { entry ->
                    val lat = entry.lat
                    val lng = entry.lng
                    if (lat != null && lng != null) {
                        registerGeofenceForEntry(
                            entryId = entry.id,
                            lat = lat,
                            lng = lng,
                            radiusMeters = entry.radiusMeters.toFloat()
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "restoreAllGeofences() failed", e)
            }
        }
    }
    fun deleteEntryById(id: Long) {
        viewModelScope.launch {
            dao.deleteById(id)
            removeGeofence(id)
        }
    }
    fun seedPoisIfEmpty() {
        viewModelScope.launch {
            val c = poiDao.count()
            if (c > 0) return@launch

            poiDao.insertAll(
                listOf(
                    //Example pois for funtion test

                    Poi(
                        name = "Edinburgh Airport Main Terminal",
                        category = "Airport",
                        lat = 55.9500,
                        lng = -3.3725,
                        source = "seed"
                    ),
                    Poi(
                        name = "Airport Parking A",
                        category = "Parking",
                        lat = 55.9510,
                        lng = -3.3710,  // ~150m
                        source = "seed"
                    ),
                    Poi(
                        name = "Airport Hotel",
                        category = "Hotel",
                        lat = 55.9490,
                        lng = -3.3740,  // ~250m
                        source = "seed"
                    ),
                    Poi(
                        name = "Airport Fuel Station",
                        category = "Service",
                        lat = 55.9525,
                        lng = -3.3690,  // ~500m+
                        source = "seed"
                    ),
                    Poi(
                        name = "Airport Logistics Hub",
                        category = "Industrial",
                        lat = 55.9550,
                        lng = -3.3650,  // ~900m
                        source = "seed"
                    )
                )
            )
            Log.d(TAG, "seedPoisIfEmpty(): inserted seed POIs")
        }
    }
    fun loadAllPoisToMemory() {
        viewModelScope.launch {
            val list = poiDao.queryByBoundingBox(
                -90.0, 90.0,
                -180.0, 180.0
            )
            _poiMap.value = list.associateBy { it.id }
        }
    }
    fun refreshNearbyPois(currentLat: Double, currentLng: Double, radiusMeters: Double) {
        viewModelScope.launch {
            val r = radiusMeters.coerceAtLeast(50.0)

            val latRad = Math.toRadians(currentLat)
            val deltaLat = r / 111_000.0
            val deltaLng = r / (111_000.0 * kotlin.math.cos(latRad)).coerceAtLeast(1e-6)

            val minLat = currentLat - deltaLat
            val maxLat = currentLat + deltaLat
            val minLng = currentLng - deltaLng
            val maxLng = currentLng + deltaLng

            val pois = poiDao.queryByBoundingBox(minLat, maxLat, minLng, maxLng)
            _nearbyPois.value = pois

            Log.d(TAG, "refreshNearbyPois(): r=$r, found=${pois.size}")
        }
    }

    fun searchRemotePois(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        classesPrefix: String?,
        classIn: String?,
        categoryId: String? = null,
        limit: Int = 20
    ) {
        viewModelScope.launch {
            try {
                _remotePoiError.value = null
                val result = ApiClient.api.nearPois(
                    lat = lat,
                    lng = lng,
                    radius = radiusMeters,
                    limit = limit,
                    classesPrefix = classesPrefix?.takeIf { it.isNotBlank() },
                    classIn = classIn?.takeIf { it.isNotBlank() },
                    categoryId = categoryId?.takeIf { it.isNotBlank() }
                )
                _remotePois.value = result

                if (result.isEmpty()) {
                    _remotePoiError.value = "No matching POIs found. Try a larger radius."
                }
            } catch (e: Exception) {
                Log.e(TAG, "searchRemotePois failed", e)
                _remotePoiError.value = e.message
                _remotePois.value = emptyList()
            }
        }
    }

    fun searchRemotePoisByCategory(
        lat: Double,
        lng: Double,
        radiusMeters: Int,
        categoryId: String,
        limit: Int = 20
    ) {
        searchRemotePois(
            lat = lat,
            lng = lng,
            radiusMeters = radiusMeters,
            classesPrefix = null,
            classIn = null,
            categoryId = categoryId,
            limit = limit
        )
    }
    private fun registerGeofenceForEntry(entryId: Long, lat: Double, lng: Double, radiusMeters: Float) {
        Log.d("DiaryApp", "Register geofence id=$entryId center=($lat,$lng) radius=${radiusMeters}m")

        val geofence = Geofence.Builder()
            .setRequestId(entryId.toString())
            .setCircularRegion(lat, lng, radiusMeters)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()


        try {
            geofencingClient.addGeofences(request, geofencePendingIntent)
                .addOnSuccessListener {
                    Log.d(TAG, "Geofence added for entryId=$entryId")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Geofence add failed for entryId=$entryId", e)
                }
        } catch (se: SecurityException) {
            Log.e(TAG, "Missing location permission for geofence", se)
        }
    }

    private fun removeGeofence(entryId: Long) {
        geofencingClient.removeGeofences(listOf(entryId.toString()))
            .addOnSuccessListener {
                Log.d(TAG, "Geofence removed for entryId=$entryId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Geofence remove failed for entryId=$entryId", e)
            }
    }
}
