package dm1sh.android_tracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dm1sh.android_tracker.domain.SettingsRepository

@Composable
fun SettingsContent(
    state: SettingsViewModel.SettingsUiState,
    unsynced: Pair<Int, Int>,
    onServerUrlChange: (String) -> Unit,
    onFetchIntervalChange: (String) -> Unit,
    onPushIntervalChange: (String) -> Unit,
    onDeviceIdChange: (String) -> Unit,
    onSave: () -> Unit,
    onRunLocalUpdate: () -> Unit,
    onRunPush: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onGrantLocation: () -> Unit,
    onGrantLocalNetwork: () -> Unit,
    onFormatTimestamp: (Long) -> String,
    onDismissHealthCheck: () -> Unit,
    onSaveCancel: () -> Unit,
    onSaveUrl: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Settings", style = MaterialTheme.typography.titleMedium)

                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = onServerUrlChange,
                    label = { Text("Server URL") },
                    placeholder = { Text("http://10.0.0.5:8080") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.fetchIntervalMin,
                    onValueChange = onFetchIntervalChange,
                    label = { Text("Fetch interval (minutes)") },
                    supportingText = { Text("Minimum ${SettingsRepository.MIN_PERIODIC_INTERVAL_MIN} minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.pushIntervalMin,
                    onValueChange = onPushIntervalChange,
                    label = { Text("Push interval (minutes)") },
                    supportingText = { Text("Minimum ${SettingsRepository.MIN_PERIODIC_INTERVAL_MIN} minutes") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.deviceId,
                    onValueChange = onDeviceIdChange,
                    label = { Text("Device ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Button(
                    onClick = onSave,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.saving) "Saving..." else "Save & apply")
                }
            }
        }

        // Unsynced counts
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Unsynced records", style = MaterialTheme.typography.titleMedium)
                Text("Usage events: ${unsynced.first}")
                Text("Device metrics: ${unsynced.second}")
            }
        }

        // Status
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text("Last fetch: ${onFormatTimestamp(state.lastFetchTime)}")
                state.lastFetchError?.let { error ->
                    Text(
                        "Fetch error: $error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Text("Last push: ${onFormatTimestamp(state.lastPushTime)}")
                state.lastPushError?.let { error ->
                    Text(
                        "Push error: $error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Permissions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)

                PermissionRow(
                    title = "Usage access",
                    description = "Required to read usage events",
                    granted = state.usageAccessGranted,
                    onGrant = onGrantUsageAccess
                )
                PermissionRow(
                    title = "Location",
                    description = "Required to read Wi-Fi SSID",
                    granted = state.locationGranted,
                    onGrant = onGrantLocation
                )
                PermissionRow(
                    title = "Local network",
                    description = "Required on Android 16+ to reach local servers",
                    granted = state.localNetworkGranted,
                    onGrant = onGrantLocalNetwork
                )
            }
        }

        // Manual actions
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Actions", style = MaterialTheme.typography.titleMedium)

                OutlinedButton(
                    onClick = onRunLocalUpdate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Fetch events & metrics now")
                }

                OutlinedButton(
                    onClick = onRunPush,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Push to server now")
                }
            }
        }

        state.message?.let { message ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    HealthCheckDialog(
        state = state.healthCheck,
        onFormatTimestamp = onFormatTimestamp,
        onDismiss = onDismissHealthCheck,
        onSaveCancel = onSaveCancel,
        onSaveUrl = onSaveUrl
    )
}

@Composable
private fun HealthCheckDialog(
    state: SettingsViewModel.HealthCheckState,
    onFormatTimestamp: (Long) -> String,
    onDismiss: () -> Unit,
    onSaveCancel: () -> Unit,
    onSaveUrl: () -> Unit
) {
    when (state) {
        SettingsViewModel.HealthCheckState.Idle -> Unit

        SettingsViewModel.HealthCheckState.Loading -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Checking server...") },
                text = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Text("Contacting /api/v1/health")
                    }
                },
                confirmButton = {}
            )
        }

        is SettingsViewModel.HealthCheckState.Success -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Server reachable") },
                text = {
                    Column {
                        Text("Status: ${state.status}")
                        state.serverTime?.let { serverTime ->
                            Text("Server time: ${onFormatTimestamp(serverTime)}")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            )
        }

        is SettingsViewModel.HealthCheckState.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Server check failed") },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            )
        }

        is SettingsViewModel.HealthCheckState.SavePrompt -> {
            AlertDialog(
                onDismissRequest = onSaveCancel,
                title = { Text("Server unreachable") },
                text = {
                    Column {
                        Text(state.message)
                        Text(
                            "Save other settings anyway, or save with the new server URL?",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = onSaveCancel) { Text("Cancel") }
                },
                confirmButton = {
                    TextButton(onClick = onSaveUrl) { Text("Save") }
                }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Text(
                "Granted",
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            OutlinedButton(onClick = onGrant) {
                Text("Grant")
            }
        }
    }
}
