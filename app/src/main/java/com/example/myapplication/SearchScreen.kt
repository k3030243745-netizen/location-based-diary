package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import kotlin.math.roundToInt

enum class SearchMode {
    PURPOSE, TYPE, DEBUG
}

data class TypeOption(
    val label: String,
    val categoryId: String
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    context: Context,
    currentLat: Double?,
    currentLng: Double?,
    remotePois: List<RemotePoi>,
    error: String?,
    suggestedCategories: List<SuggestedCategory>,
    recommendedRadiusM: Int?,
    intentSource: String?,
    onPingHealth: () -> Unit,
    onClassifyPurpose: (String) -> Unit,
    onSearchByCategory: (radius: Int, categoryId: String) -> Unit,
    onSearchRaw: (radius: Int, classesPrefix: String, classIn: String) -> Unit,
    onSelectPoi: (RemotePoi) -> Unit,
) {
    var mode by remember { mutableStateOf(SearchMode.PURPOSE) }

    var purpose by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("400") }

    var classesPrefix by remember { mutableStateOf("0947") }
    var classIn by remember { mutableStateOf("") }

    val typeOptions = listOf(
        TypeOption("Supermarket / Grocery", "retail_food_multiitem"),
        TypeOption("Cafe / Coffee", "cafes"),
        TypeOption("Restaurant", "restaurants"),
        TypeOption("Pharmacy", "chemists_pharmacies"),
        TypeOption("Cash Machine / Bank", "cash_machines"),
        TypeOption("Gym / Leisure", "sport_complex"),
        TypeOption("Post Office / Parcel", "post_offices"),
        TypeOption("Public Transport", "public_transport"),
        TypeOption("Printing / Copying", "printing_photocopying")
    )

    var selectedType by remember { mutableStateOf(typeOptions.first()) }

    val canSearch = currentLat != null && currentLng != null
    val topScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // ===== Upper section: Search control area (scrollable)=====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(topScroll)
        ) {
            Text(
                "Nearby Place Search",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = mode.ordinal) {
                Tab(
                    selected = mode == SearchMode.PURPOSE,
                    onClick = { mode = SearchMode.PURPOSE },
                    text = { Text("By Purpose") }
                )
                Tab(
                    selected = mode == SearchMode.TYPE,
                    onClick = { mode = SearchMode.TYPE },
                    text = { Text("By Type") }
                )
                Tab(
                    selected = mode == SearchMode.DEBUG,
                    onClick = { mode = SearchMode.DEBUG },
                    text = { Text("Raw Search") }
                )
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = radius,
                onValueChange = { radius = it },
                label = { Text("Radius (m)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            when (mode) {
                SearchMode.PURPOSE -> {
                    OutlinedTextField(
                        value = purpose,
                        onValueChange = { purpose = it },
                        label = { Text("Purpose (e.g. buy milk, get coffee)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = { onClassifyPurpose(purpose) },
                        enabled = purpose.isNotBlank()
                    ) {
                        Text("Suggest categories")
                    }

                    if (recommendedRadiusM != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Recommended radius: ${recommendedRadiusM}m",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (intentSource != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Classification source: $intentSource",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (suggestedCategories.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Suggested categories", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))

                        suggestedCategories.forEach { category ->
                            val confidencePct = (category.confidence * 100).roundToInt()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        text = category.displayName,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = "categoryId: ${category.categoryId}   confidence: ${confidencePct}%",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val r = radius.toIntOrNull() ?: (recommendedRadiusM ?: 400)
                                            onSearchByCategory(r, category.categoryId)
                                        },
                                        enabled = canSearch
                                    ) {
                                        Text(if (canSearch) "Search nearby POIs" else "Location is null")
                                    }
                                }
                            }
                        }
                    }
                }

                SearchMode.TYPE -> {
                    Text("Choose a place type", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))

                    typeOptions.forEach { option ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = if (selectedType == option) {
                                CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            } else {
                                CardDefaults.cardColors()
                            }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(option.label, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        selectedType = option
                                        val r = radius.toIntOrNull() ?: 400
                                        onSearchByCategory(r, option.categoryId)
                                    },
                                    enabled = canSearch
                                ) {
                                    Text("Search")
                                }
                            }
                        }
                    }
                }

                SearchMode.DEBUG -> {
                    OutlinedTextField(
                        value = classesPrefix,
                        onValueChange = { classesPrefix = it },
                        label = { Text("classesPrefix (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = classIn,
                        onValueChange = { classIn = it },
                        label = { Text("classIn (comma-separated, optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onPingHealth() }) {
                            Text("Ping /health")
                        }

                        Button(
                            onClick = {
                                val r = radius.toIntOrNull() ?: 400
                                onSearchRaw(r, classesPrefix, classIn)
                            },
                            enabled = canSearch
                        ) {
                            Text(if (canSearch) "Raw Search" else "Location is null")
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }

        Divider()

        Spacer(Modifier.height(8.dp))

        // ===== Lower section: Search result area=====
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = "Search Results",
                style = MaterialTheme.typography.titleSmall
            )

            Spacer(Modifier.height(8.dp))

            if (error != null) {
                Text(
                    text = "Status: $error",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
            }

            if (remotePois.isEmpty()) {
                Text(
                    text = "No POIs loaded yet.",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(remotePois) { p ->
                        val title = p.name ?: "(no name)"
                        val sub = buildString {
                            append(p.classname ?: "")
                            if (!p.pointx_class.isNullOrBlank()) append("  [${p.pointx_class}]")
                            val d = p.distance_m
                            if (d != null && !d.isNaN()) append("  ${d.roundToInt()}m")
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .combinedClickable(
                                    onClick = {
                                        onSelectPoi(p)
                                    },
                                    onLongClick = {
                                        val lat = p.lat
                                        val lng = p.lng
                                        if (lat != null && lng != null) {
                                            val uri =
                                                "geo:$lat,$lng?q=$lat,$lng(${Uri.encode(title)})".toUri()
                                            val intent = Intent(Intent.ACTION_VIEW, uri)
                                            intent.setPackage("com.google.android.apps.maps")
                                            context.startActivity(intent)
                                        }
                                    }
                                )
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(title, style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(sub, style = MaterialTheme.typography.bodySmall)

                                if (!p.street_name.isNullOrBlank() || !p.postcode.isNullOrBlank()) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${p.street_name ?: ""} ${p.postcode ?: ""}".trim(),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}