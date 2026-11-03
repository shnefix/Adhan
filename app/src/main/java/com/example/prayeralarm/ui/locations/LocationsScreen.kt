package com.example.prayeralarm.ui.locations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.prayeralarm.domain.model.SavedLocation
import com.example.prayeralarm.ui.locations.components.AddEditLocationDialog
import com.example.prayeralarm.ui.locations.components.DeleteConfirmationDialog
import com.example.prayeralarm.ui.locations.components.LocationItemRow
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.NavyCard
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.util.ValidationResult

@Composable
fun LocationsScreen(
    uiState: LocationsUiState,
    onSelectLocation: (SavedLocation) -> Unit,
    onOpenAddDialog: () -> Unit,
    onOpenEditDialog: (SavedLocation) -> Unit,
    onDismissDialog: () -> Unit,
    onSaveLocation: (LocationFormInput) -> ValidationResult,
    onRequestDelete: (SavedLocation) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenAddDialog,
                containerColor = GoldPrimary,
                contentColor = NavyDark,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Location"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        color = GoldPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.locations.isEmpty() -> {
                    EmptyLocationsPlaceholder(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Saved Locations",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 88.dp)
                        ) {
                            items(
                                items = uiState.locations,
                                key = { it.id }
                            ) { location ->
                                val isSelected = location.id == uiState.activeLocation?.id
                                LocationItemRow(
                                    location = location,
                                    isSelected = isSelected,
                                    onSelect = { onSelectLocation(location) },
                                    onEdit = { onOpenEditDialog(location) },
                                    onDelete = { onRequestDelete(location) }
                                )
                            }
                        }
                    }
                }
            }

            // Dialogs
            if (uiState.showAddEditDialog) {
                AddEditLocationDialog(
                    initialLocation = uiState.editingLocation,
                    calculationSettings = uiState.calculationSettings,
                    onDismiss = onDismissDialog,
                    onSave = onSaveLocation
                )
            }

            if (uiState.locationToDelete != null) {
                DeleteConfirmationDialog(
                    locationName = uiState.locationToDelete.name,
                    onConfirm = onConfirmDelete,
                    onDismiss = onCancelDelete
                )
            }
        }
    }
}

@Composable
private fun EmptyLocationsPlaceholder(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(32.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.LocationOff,
                contentDescription = null,
                tint = GoldPrimary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Locations Saved",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

        }
    }
}
