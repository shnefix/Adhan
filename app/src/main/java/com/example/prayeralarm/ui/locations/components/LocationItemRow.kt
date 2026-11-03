package com.example.prayeralarm.ui.locations.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prayeralarm.domain.model.SavedLocation
import com.example.prayeralarm.ui.theme.GoldContainer
import com.example.prayeralarm.ui.theme.GoldPrimary
import com.example.prayeralarm.ui.theme.NavyBorder
import com.example.prayeralarm.ui.theme.NavyCard
import com.example.prayeralarm.ui.theme.NavyDark
import com.example.prayeralarm.ui.theme.NavySurface
import com.example.prayeralarm.ui.theme.TextPrimary
import com.example.prayeralarm.ui.theme.TextSecondary
import com.example.prayeralarm.ui.theme.TextTertiary

@Composable
fun LocationItemRow(
    location: SavedLocation,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) NavyCard else NavySurface
    val borderColor = if (isSelected) GoldPrimary.copy(alpha = 0.65f) else NavyBorder.copy(alpha = 0.5f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio/Check indicator + Location metadata
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radio icon
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = if (isSelected) "Active location" else "Select location",
                    tint = if (isSelected) GoldPrimary else TextTertiary,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) GoldPrimary else TextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(GoldContainer)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GoldPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = String.format(
                            "%.4f°, %.4f° • %s",
                            location.latitude,
                            location.longitude,
                            location.timeZoneId
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    val extras = listOfNotNull(
                        location.calculationMethod?.displayName,
                        "times fine-tuned".takeUnless { location.adjustments.isEmpty }
                    )
                    if (extras.isNotEmpty()) {
                        Text(
                            text = extras.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                }
            }

            // Edit and Delete Action Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit location ${location.name}",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete location ${location.name}",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

