package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AppThemeMode

@Composable
fun ThemeModeToggleChip(
    themeMode: AppThemeMode,
    isDarkActive: Boolean,
    onToggleDayNight: () -> Unit,
    onSelectMode: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val icon = when (themeMode) {
        AppThemeMode.DAY -> Icons.Rounded.LightMode
        AppThemeMode.NIGHT -> Icons.Rounded.DarkMode
        AppThemeMode.SYSTEM -> if (isDarkActive) Icons.Rounded.DarkMode else Icons.Rounded.LightMode
    }
    val label = when (themeMode) {
        AppThemeMode.DAY -> "Day"
        AppThemeMode.NIGHT -> "Night"
        AppThemeMode.SYSTEM -> if (isDarkActive) "Night" else "Day"
    }

    Box(modifier = modifier) {
        Surface(
            onClick = onToggleDayNight,
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("theme_mode_toggle")
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (compact) 8.dp else 10.dp,
                    vertical = 6.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (isDarkActive) "Switch to Day view" else "Switch to Night view",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                if (!compact) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            AppThemeMode.entries.forEach { mode ->
                val modeIcon = when (mode) {
                    AppThemeMode.DAY -> Icons.Rounded.LightMode
                    AppThemeMode.NIGHT -> Icons.Rounded.DarkMode
                    AppThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                }
                DropdownMenuItem(
                    text = { Text("${mode.label} Mode") },
                    leadingIcon = { Icon(modeIcon, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onSelectMode(mode)
                    }
                )
            }
        }
    }
}
