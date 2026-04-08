package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.compose.runtime.LaunchedEffect
import com.google.maps.android.compose.*
import com.google.android.gms.maps.model.*
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place

@Composable
fun MapScreen(
    currentLat: Double?,
    currentLng: Double?,
    remotePois: List<RemotePoi>,
    onSelectPoi: (RemotePoi) -> Unit
) {
    val defaultPosition = LatLng(55.9533, -3.1883) // Edinburgh fallback

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            if (currentLat != null && currentLng != null)
                LatLng(currentLat, currentLng)
            else
                defaultPosition,
            14f
        )
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState
    ) {

        // 当前定位 Marker
        if (currentLat != null && currentLng != null) {
            val current = LatLng(currentLat, currentLng)

            Marker(
                state = MarkerState(position = current),
                title = "Current Location"
            )

            // radius（200m）
            Circle(
                center = current,
                radius = 200.0,
                fillColor = Color(0x220000FF),
                strokeColor = Color(0x550000FF)
            )
        }

        // remote POI Markers
        remotePois.forEach { poi ->
            if (poi.lat != null && poi.lng != null) {
                val position = LatLng(poi.lat, poi.lng)

                Marker(
                    state = MarkerState(position = position),
                    title = poi.name,
                    onClick = {
                        onSelectPoi(poi)
                        true
                    }
                )
            }
        }
    }
}
private const val TAG = "DiaryApp"

class MainActivity : ComponentActivity() {

    private val diaryVm: DiaryViewModel by viewModels()

    // Activity-level state: Compose will automatically reorganize and refresh.
    private var statusText by mutableStateOf("Ready.")
    private var lastLocation by mutableStateOf<Location?>(null)

    private val requestForegroundLocation =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d(TAG, "Foreground location result = $result")
            statusText = "Foreground location result = $result"
        }

    private val requestBackgroundLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "Background location granted = $granted")
            statusText = "Background location granted = $granted"
        }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "Notifications granted = $granted")
            statusText = "Notifications granted = $granted"
        }

    private val requestActivityRecognition =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.d(TAG, "Activity recognition granted = $granted")
            statusText = "Activity recognition granted = $granted"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ai = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val key = ai.metaData?.getString("com.google.android.geo.API_KEY")
        println("MAPS_KEY_DEBUG key = ${key ?: "NULL"}")
        setContent {
            MaterialTheme {
                LaunchedEffect(Unit) {
                    diaryVm.restoreAllGeofences()
                    diaryVm.seedPoisIfEmpty()
                    diaryVm.loadAllPoisToMemory()
                }
                var tab by remember { mutableStateOf(0) } // 0=Add, 1=Entries, 2=Search


                val entries by diaryVm.entries.collectAsState(initial = emptyList())
                val nearbyPois by diaryVm.nearbyPois.collectAsState(initial = emptyList())
                val poiMap by diaryVm.poiMap.collectAsState(initial = emptyMap())
                val remotePois by diaryVm.remotePois.collectAsState(initial = emptyList())
                val remoteErr by diaryVm.remotePoiError.collectAsState(initial = null)
                val suggestedCategories by diaryVm.suggestedCategories.collectAsState(initial = emptyList())
                val recommendedRadiusM by diaryVm.recommendedRadiusM.collectAsState(initial = null)
                val intentSource by diaryVm.intentSource.collectAsState(initial = null)

                var selectedRemotePoi by remember { mutableStateOf<RemotePoi?>(null) }

                Scaffold(

                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                label = { Text("Add") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                label = { Text("Entries") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                label = { Text("Search") },
                                icon = {}
                            )
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { tab = 3 },
                                label = { Text("Map") },
                                icon = { Icon(Icons.Filled.Place, contentDescription = null) }
                            )
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .padding(padding)
                            .padding(12.dp)
                    ) {
                        when (tab) {

                            0 -> {
                                AddEntryScreen(
                                    statusText = statusText,
                                    lastLocation = lastLocation,
                                    selectedRemotePoi = selectedRemotePoi,


                                    onRefreshStatus = { refreshPermissionStatus() },
                                    onOpenAppSettings = { openAppSettings() },

                                    onRequestForegroundLocation = {
                                        statusText = "Requesting foreground location..."
                                        requestForegroundLocation.launch(
                                            arrayOf(
                                                Manifest.permission.ACCESS_FINE_LOCATION,
                                                Manifest.permission.ACCESS_COARSE_LOCATION
                                            )
                                        )
                                    },

                                    onRequestBackgroundLocation = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            statusText = "Requesting background location..."
                                            requestBackgroundLocation.launch(
                                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                                            )
                                        } else {
                                            statusText = "Background location not needed (< Android 10)."
                                        }
                                    },

                                    onRequestNotifications = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            statusText = "Requesting notifications..."
                                            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            statusText = "Notifications permission not needed (< Android 13)."
                                        }
                                    },

                                    onRequestActivityRecognition = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            statusText = "Requesting activity recognition..."
                                            requestActivityRecognition.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                        } else {
                                            statusText = "Activity recognition not available (< Android 10)."
                                        }
                                    },

                                    onGetCurrentLocationNow = {
                                        statusText = "Getting current location..."
                                        getCurrentLocationOnce(
                                            onSuccess = { loc ->
                                                lastLocation = loc
                                                statusText = if (loc != null) {
                                                    diaryVm.refreshNearbyPois(loc.latitude, loc.longitude, 1000.0)
                                                    "Location OK: lat=${loc.latitude}, lng=${loc.longitude}"
                                                } else {
                                                    "Location is null. (Emulator: More/⋮ → Location → Set & Send)"
                                                }
                                            },
                                            onError = { err ->
                                                Log.e(TAG, "getCurrentLocation failed", err)
                                                statusText = "Location failed: ${err.message}"
                                            }
                                        )
                                    },

                                    onSave = { title, note, radiusMeters ->
                                        val loc = lastLocation

                                        val lat = selectedRemotePoi?.lat ?: loc?.latitude
                                        val lng = selectedRemotePoi?.lng ?: loc?.longitude

                                        diaryVm.addEntry(
                                            title = title,
                                            note = note,
                                            lat = lat,
                                            lng = lng,
                                            radiusMeters = radiusMeters,
                                            poiId = null
                                        )

                                        statusText = "Saved: $title"
                                        selectedRemotePoi = null
                                        tab = 1
                                    }
                                )
                            }

                            1 -> {
                                EntriesScreen(
                                    entries = entries,
                                    poiMap = poiMap,
                                    onDelete = { id ->
                                        diaryVm.deleteEntryById(id)
                                        statusText = "Deleted entry id=$id"
                                    }
                                )
                            }

                            2 -> {
                                SearchScreen(
                                    context = this@MainActivity,
                                    currentLat = lastLocation?.latitude,
                                    currentLng = lastLocation?.longitude,
                                    remotePois = remotePois,
                                    error = remoteErr,
                                    suggestedCategories = suggestedCategories,
                                    recommendedRadiusM = recommendedRadiusM,
                                    intentSource = intentSource,
                                    onPingHealth = {
                                        Log.d("PING", "Ping clicked")
                                        statusText = "Ping clicked"
                                        diaryVm.pingHealth()
                                    },
                                    onClassifyPurpose = { purpose ->
                                        diaryVm.classifyIntent(purpose)
                                    },
                                    onSearchByCategory = { radiusMeters, categoryId ->
                                        val loc = lastLocation
                                        if (loc != null) {
                                            diaryVm.searchRemotePoisByCategory(
                                                lat = loc.latitude,
                                                lng = loc.longitude,
                                                radiusMeters = radiusMeters,
                                                categoryId = categoryId
                                            )
                                        } else {
                                            statusText = "Location is null. Tap 'Get Current Location Now' first."
                                        }
                                    },
                                    onSearchRaw = { radiusMeters, classesPrefix, classIn ->
                                        val loc = lastLocation
                                        if (loc != null) {
                                            diaryVm.searchRemotePois(
                                                lat = loc.latitude,
                                                lng = loc.longitude,
                                                radiusMeters = radiusMeters,
                                                classesPrefix = classesPrefix,
                                                classIn = classIn,
                                                categoryId = null
                                            )
                                        } else {
                                            statusText = "Location is null. Tap 'Get Current Location Now' first."
                                        }
                                    },
                                    onSelectPoi = { p ->
                                        selectedRemotePoi = p
                                        statusText = "Selected remote POI: ${p.name ?: "(no name)"}"
                                        tab = 0
                                    }
                                )
                            }
                            3 -> MapScreen(
                                currentLat = lastLocation?.latitude,
                                currentLng = lastLocation?.longitude,
                                remotePois = remotePois,
                                onSelectPoi = { poi ->
                                    selectedRemotePoi = poi
                                    tab = 0
                                }
                            )
                            else -> {
                                Text("Unknown tab = $tab")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun refreshPermissionStatus() {
        val fine = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val bg = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else true
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            isGranted(Manifest.permission.POST_NOTIFICATIONS) else true
        val act = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            isGranted(Manifest.permission.ACTIVITY_RECOGNITION) else true

        statusText = "Status → Fine:$fine Coarse:$coarse Bg:$bg Notif:$notif Activity:$act"
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun getCurrentLocationOnce(
        onSuccess: (Location?) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        val fineGranted = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseGranted = isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

        if (!fineGranted && !coarseGranted) {
            onSuccess(null)
            return
        }

        try {
            val client = LocationServices.getFusedLocationProviderClient(this)
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { loc ->
                onSuccess(loc)
            }.addOnFailureListener { e ->
                onError(e)
            }
        } catch (se: SecurityException) {
            onError(se)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}

@Composable
private fun AddEntryScreen(
    statusText: String,
    lastLocation: Location?,
    selectedRemotePoi: RemotePoi?,

    onRefreshStatus: () -> Unit,
    onOpenAppSettings: () -> Unit,

    onRequestForegroundLocation: () -> Unit,
    onRequestBackgroundLocation: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestActivityRecognition: () -> Unit,

    onGetCurrentLocationNow: () -> Unit,
    onSave: (
        title: String,
        note: String,
        radiusMeters: Double
    ) -> Unit
)
{

    var title by remember { mutableStateOf("") }

    LaunchedEffect(selectedRemotePoi) {
        if (selectedRemotePoi != null) {
            title = selectedRemotePoi.name ?: ""
        }
    }
    var note by remember { mutableStateOf("") }
    var radiusText by remember { mutableStateOf("100") }

    val latText = lastLocation?.latitude?.toString() ?: "?"
    val lngText = lastLocation?.longitude?.toString() ?: "?"
    val scroll = rememberScrollState()



    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(bottom = 80.dp), // Leave space at the bottom for the navigation, to prevent the buttons from being obscured.
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Status: $statusText")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefreshStatus) { Text("Refresh Status") }
            Button(onClick = onOpenAppSettings) { Text("App Settings") }
        }

        Divider()

        Text("Permissions")
        Button(onClick = onRequestForegroundLocation) { Text("Request Foreground Location") }
        Button(onClick = onRequestBackgroundLocation) { Text("Request Background Location") }
        Button(onClick = onRequestNotifications) { Text("Request Notifications") }
        Button(onClick = onRequestActivityRecognition) { Text("Request Activity Recognition") }

        Divider()

        Text("Location test")
        Text("Last location: lat=$latText, lng=$lngText")
        Button(onClick = onGetCurrentLocationNow) { Text("Get Current Location Now") }
        Divider()



        Text(
            "How to add an entry: 1) Tap 'Get Current Location Now' (optional) " +
                    "2) Enter Title / Note / Radius 3) Tap 'Save to DB' then check 'Entries'."
        )


        Divider()
        if (selectedRemotePoi != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Selected remote POI",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(selectedRemotePoi.name ?: "(no name)")
                    if (!selectedRemotePoi.classname.isNullOrBlank()) {
                        Text(
                            selectedRemotePoi.classname ?: "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (selectedRemotePoi.lat != null && selectedRemotePoi.lng != null) {
                        Text(
                            "lat=${selectedRemotePoi.lat}, lng=${selectedRemotePoi.lng}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
        Text("Create diary entry")
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = radiusText,
            onValueChange = { radiusText = it.filter { ch -> ch.isDigit() || ch == '.' } },
            label = { Text("Radius (meters)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Saving will use this POI's coordinates.",
            style = MaterialTheme.typography.bodySmall
        )
        val radius = radiusText.toDoubleOrNull() ?: 100.0

        Button(
            onClick = {
                onSave(title.trim(), note.trim(), radius,)

                title = ""
                note = ""
                radiusText = "100"

            },
            enabled = title.isNotBlank()
        ) {
            Text("Save to DB")
        }
    }
}

@Composable

private fun EntriesScreen(
    entries: List<DiaryEntry>,
    poiMap: Map<Long, Poi>,
    onDelete: (Long) -> Unit
) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete entry?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(pendingDeleteId!!)
                        pendingDeleteId = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Entries (${entries.size})", style = MaterialTheme.typography.titleLarge)

        if (entries.isEmpty()) {
            Text("No entries yet. Go to Add and save one.")
            return
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries) { e ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(e.title, style = MaterialTheme.typography.titleMedium)

                            TextButton(
                                onClick = { pendingDeleteId = e.id }
                            ) { Text("Delete") }
                        }

                        if (e.note.isNotBlank()) Text(e.note)
                        // Debug info (hidden in dissertation screenshots)
                        //Text("lat=${e.lat ?: "?"}, lng=${e.lng ?: "?"}, r=${e.radiusMeters}m")
                        e.poiId?.let { id ->
                            val poiName = poiMap[id]?.name
                            if (poiName != null) {
                                Text("POI: $poiName")
                            }
                        }
                        //Text("id=${e.id}, created=${e.createdAtEpochMs}")
                    }
                }
            }
        }
    }
}

