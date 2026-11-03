package com.example.prayeralarm.ui.locations.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.prayeralarm.data.location.LocationLookup
import com.example.prayeralarm.data.location.LookupResult
import com.example.prayeralarm.domain.calculator.PrayerCalculationMethod
import com.example.prayeralarm.domain.calculator.PrayerCalculationSettings
import com.example.prayeralarm.domain.calculator.PrayerTimeCalculator
import com.example.prayeralarm.domain.model.PrayerTimeAdjustments
import com.example.prayeralarm.domain.model.PrayerType
import com.example.prayeralarm.domain.model.SavedLocation
import com.example.prayeralarm.ui.locations.LocationFormInput
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.NavyBorder
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.NavySurface
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.ui.theme.TextSecondary
import com.example.prayeralarm.util.LocationValidator
import com.example.prayeralarm.util.TimeZoneGuesser
import com.example.prayeralarm.util.ValidationResult
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun AddEditLocationDialog(
    initialLocation: SavedLocation?,
    onDismiss: () -> Unit,
    onSave: (LocationFormInput) -> ValidationResult,
    calculationSettings: PrayerCalculationSettings = PrayerCalculationSettings()
) {
    var name by remember { mutableStateOf(initialLocation?.name ?: "") }
    var latitude by remember { mutableStateOf(initialLocation?.latitude?.toString() ?: "") }
    var longitude by remember { mutableStateOf(initialLocation?.longitude?.toString() ?: "") }
    var timezoneId by remember { mutableStateOf(initialLocation?.timeZoneId ?: deviceTimeZoneId()) }
    var calculationMethod by remember { mutableStateOf(initialLocation?.calculationMethod) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var latitudeError by remember { mutableStateOf<String?>(null) }
    var longitudeError by remember { mutableStateOf<String?>(null) }
    var timezoneError by remember { mutableStateOf<String?>(null) }

    // One text field per prayer, in PrayerType order; blank means "no adjustment".
    val initialAdjustments = initialLocation?.adjustments ?: PrayerTimeAdjustments.NONE
    val adjustments = remember {
        mutableStateListOf<String>().apply {
            PrayerType.entries.forEach { type ->
                val minutes = initialAdjustments.minutesFor(type)
                add(if (minutes == 0) "" else minutes.toString())
            }
        }
    }
    var adjustmentError by remember { mutableStateOf<String?>(null) }

    val isEditing = initialLocation != null
    val title = if (isEditing) "Edit Location" else "Add New Location"

    fun fillFrom(result: LookupResult) {
        name = result.name.take(50)
        latitude = result.latitude.toString()
        longitude = result.longitude.toString()
        timezoneId = result.timeZoneId
        nameError = null
        latitudeError = null
        longitudeError = null
        timezoneError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .border(1.dp, NavyBorder, RoundedCornerShape(20.dp)),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = GoldPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                PlaceFinder(onPlaceFound = ::fillFrom)

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Or enter coordinates in decimal degrees and an IANA timezone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Location Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("City / Location Name") },
                    placeholder = { Text("e.g. Sfax") },
                    isError = nameError != null,
                    supportingText = {
                        if (nameError != null) Text(nameError!!, color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = true,
                    colors = dialogTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Latitude Field
                OutlinedTextField(
                    value = latitude,
                    onValueChange = {
                        latitude = it
                        latitudeError = null
                    },
                    label = { Text("Latitude (-90.0 to 90.0)") },
                    placeholder = { Text("e.g. 34.7406") },
                    isError = latitudeError != null,
                    supportingText = {
                        if (latitudeError != null) Text(latitudeError!!, color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = dialogTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Longitude Field
                OutlinedTextField(
                    value = longitude,
                    onValueChange = {
                        longitude = it
                        longitudeError = null
                    },
                    label = { Text("Longitude (-180.0 to 180.0)") },
                    placeholder = { Text("e.g. 10.7603") },
                    isError = longitudeError != null,
                    supportingText = {
                        if (longitudeError != null) Text(longitudeError!!, color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = dialogTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Timezone ID Field
                OutlinedTextField(
                    value = timezoneId,
                    onValueChange = {
                        timezoneId = it
                        timezoneError = null
                    },
                    label = { Text("IANA Timezone ID") },
                    placeholder = { Text("e.g. Africa/Tunis") },
                    isError = timezoneError != null,
                    supportingText = {
                        if (timezoneError != null) Text(timezoneError!!, color = MaterialTheme.colorScheme.error)
                    },
                    singleLine = true,
                    colors = dialogTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                TimeZoneSuggestions(
                    query = timezoneId,
                    onPick = {
                        timezoneId = it
                        timezoneError = null
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                MethodPicker(
                    selected = calculationMethod,
                    appDefault = calculationSettings.method,
                    onSelect = { calculationMethod = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Fine-tune times (minutes)",
                    style = MaterialTheme.typography.titleSmall,
                    color = GoldPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Match your local mosque or official timetable, e.g. +2 or -3.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PrayerType.entries.forEachIndexed { index, type ->
                        // Name above the box: a floating label is truncated in a fifth of the width.
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = type.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                softWrap = false
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            OutlinedTextField(
                                value = adjustments[index],
                                onValueChange = { value ->
                                    if (value.length <= 3 && value.all { it.isDigit() || it == '-' || it == '+' }) {
                                        adjustments[index] = value
                                        adjustmentError = null
                                    }
                                },
                                placeholder = {
                                    Text("0", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                },
                                isError = adjustmentError != null &&
                                    LocationValidator.validateAdjustment(adjustments[index]) != null,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = dialogTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                if (adjustmentError != null) {
                    Text(
                        text = adjustmentError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                TodayPreview(
                    latitude = latitude,
                    longitude = longitude,
                    timezoneId = timezoneId,
                    adjustments = adjustments,
                    calculationMethod = calculationMethod,
                    calculationSettings = calculationSettings
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = onSave(
                        LocationFormInput(
                            name = name,
                            latitude = latitude,
                            longitude = longitude,
                            timeZoneId = timezoneId,
                            adjustments = adjustments.toList(),
                            calculationMethod = calculationMethod
                        )
                    )
                    if (!result.isValid) {
                        nameError = result.nameError
                        latitudeError = result.latitudeError
                        longitudeError = result.longitudeError
                        timezoneError = result.timezoneError
                        adjustmentError = result.adjustmentError
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldPrimary,
                    contentColor = NavyDark
                )
            ) {
                Text(
                    text = if (isEditing) "Save Changes" else "Add Location",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** The device zone when java.time knows it (it always should), else the app's home zone. */
private fun deviceTimeZoneId(): String {
    val id = ZoneId.systemDefault().id
    return if (LocationValidator.validateTimezoneId(id) == null) id else "Africa/Tunis"
}

/**
 * City search and "use my location". Both fill the form below; the user can still
 * edit every field before saving.
 */
@Composable
private fun PlaceFinder(onPlaceFound: (LookupResult) -> Unit) {
    val context = LocalContext.current
    val lookup = remember { LocationLookup(context) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<LookupResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    fun locate() {
        busy = true
        status = null
        scope.launch {
            val found = lookup.currentLocation()
            busy = false
            if (found != null) onPlaceFound(found)
            else status = "Couldn't get your position. Turn on location services, or search for a city."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) locate()
        else status = "Location permission denied. Search for a city or enter coordinates instead."
    }

    fun search() {
        if (query.isBlank()) return
        focusManager.clearFocus()
        busy = true
        status = null
        results = emptyList()
        scope.launch {
            results = lookup.search(query)
            busy = false
            if (results.isEmpty()) {
                status = "No places found. Check your internet connection or enter coordinates manually."
            }
        }
    }

    OutlinedButton(
        onClick = {
            if (lookup.hasLocationPermission()) locate()
            else permissionLauncher.launch(LocationLookup.LOCATION_PERMISSIONS)
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.MyLocation, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Use my current location", color = GoldPrimary)
    }

    if (lookup.canGeocode) {
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search a city") },
            placeholder = { Text("e.g. Sousse or Paris, France") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { search() }),
            trailingIcon = {
                IconButton(onClick = ::search, enabled = !busy && query.isNotBlank()) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = GoldPrimary)
                }
            },
            colors = dialogTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (busy) {
        Spacer(modifier = Modifier.height(8.dp))
        CircularProgressIndicator(color = GoldPrimary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
    }
    status?.let {
        Spacer(modifier = Modifier.height(4.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    results.forEach { result ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    focusManager.clearFocus()
                    onPlaceFound(result)
                    results = emptyList()
                    query = ""
                }
                .padding(vertical = 8.dp)
        ) {
            Icon(Icons.Default.Place, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(result.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(
                    text = "%.4f°, %.4f° • %s".format(result.latitude, result.longitude, result.timeZoneId),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

/** Matching zone IDs while the typed timezone is not (yet) a valid one. */
@Composable
private fun TimeZoneSuggestions(query: String, onPick: (String) -> Unit) {
    if (LocationValidator.validateTimezoneId(query) == null) return
    val suggestions = remember(query) { TimeZoneGuesser.suggestions(query) }
    suggestions.forEach { zone ->
        Text(
            text = zone,
            style = MaterialTheme.typography.bodyMedium,
            color = GoldPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPick(zone) }
                .padding(vertical = 6.dp, horizontal = 4.dp)
        )
    }
}

/** Per-location method; null keeps following the app-wide setting. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MethodPicker(
    selected: PrayerCalculationMethod?,
    appDefault: PrayerCalculationMethod,
    onSelect: (PrayerCalculationMethod?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val followSettingsLabel = "Same as Settings (${appDefault.displayName})"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.displayName ?: followSettingsLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Calculation method") },
            supportingText = { selected?.let { Text(it.summary, color = TextSecondary) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = dialogTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(followSettingsLabel) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            PrayerCalculationMethod.entries.forEach { method ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(method.displayName)
                            Text(method.summary, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    onClick = {
                        onSelect(method)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Today's times for the coordinates being typed, so the user can compare them with
 * their reference timetable while tuning. Hidden until the coordinates are valid.
 */
@Composable
private fun TodayPreview(
    latitude: String,
    longitude: String,
    timezoneId: String,
    adjustments: List<String>,
    calculationMethod: PrayerCalculationMethod?,
    calculationSettings: PrayerCalculationSettings
) {
    val inputsValid = LocationValidator.validateLatitude(latitude) == null &&
        LocationValidator.validateLongitude(longitude) == null &&
        LocationValidator.validateTimezoneId(timezoneId) == null &&
        adjustments.all { LocationValidator.validateAdjustment(it) == null }
    if (!inputsValid) return

    val parsed = adjustments.map(LocationValidator::parseAdjustment)
    val location = SavedLocation(
        name = "Preview",
        latitude = LocationValidator.parseCoordinate(latitude)!!,
        longitude = LocationValidator.parseCoordinate(longitude)!!,
        timeZoneId = timezoneId.trim(),
        adjustments = PrayerTimeAdjustments(parsed[0], parsed[1], parsed[2], parsed[3], parsed[4]),
        calculationMethod = calculationMethod
    )
    val timetable = PrayerTimeCalculator.calculateTimetable(
        location,
        LocalDate.now(PrayerTimeCalculator.zoneIdFor(location)),
        calculationSettings.method,
        calculationSettings.madhab
    )
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Today here • ${timetable.calculationMethod.displayName}",
        style = MaterialTheme.typography.titleSmall,
        color = GoldPrimary,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        timetable.items.forEach { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(item.type.displayName, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(
                    text = item.time.format(formatter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    if (timetable.usedNearestLatitude) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "The sun doesn't rise or set here today, so times follow the nearest " +
                "latitude where it does (${PrayerTimeCalculator.NEAREST_LATITUDE.toInt()}°).",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    locationName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NavySurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, NavyBorder, RoundedCornerShape(20.dp)),
        title = {
            Text(
                text = "Delete Location?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to remove \"$locationName\" from your saved locations? This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = TextPrimary
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = GoldPrimary,
    unfocusedBorderColor = NavyBorder,
    focusedLabelColor = GoldPrimary,
    unfocusedLabelColor = TextSecondary,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = GoldPrimary,
    errorBorderColor = MaterialTheme.colorScheme.error
)
