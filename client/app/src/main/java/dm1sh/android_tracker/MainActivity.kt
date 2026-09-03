package dm1sh.android_tracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dm1sh.android_tracker.ui.SettingsContent
import dm1sh.android_tracker.ui.SettingsViewModel
import dm1sh.android_tracker.ui.theme.AndroidTrackerTheme

@AndroidEntryPoint
class MainActivity : androidx.activity.ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AndroidTrackerTheme {
                TrackerScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.setUsageAccessGranted(hasUsageAccess(context))
        viewModel.setLocalNetworkGranted(hasLocalNetworkPermission(context))
        viewModel.setLocationGranted(hasLocationPermission(context))

        onPauseOrDispose { }
    }

    LaunchedEffect(Unit) {
        viewModel.scheduleWorkersOnStart()
    }

    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setLocalNetworkGranted(granted)
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.setLocationGranted(granted)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Android Tracker") }) }
    ) { padding ->
        SettingsContent(
            state = state,
            unsynced = viewModel.unsyncedCounts.collectAsState().value,
            onServerUrlChange = viewModel::onServerUrlChange,
            onFetchIntervalChange = viewModel::onFetchIntervalChange,
            onPushIntervalChange = viewModel::onPushIntervalChange,
            onDeviceIdChange = viewModel::onDeviceIdChange,
            onSave = viewModel::saveSettings,
            onRunLocalUpdate = viewModel::runLocalUpdate,
            onRunPush = viewModel::runPush,
            onGrantUsageAccess = {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            },
            onGrantLocation = {
                locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            onGrantLocalNetwork = {
                if (Build.VERSION.SDK_INT >= 36) {
                    localNetworkLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                }
            },
            onFormatTimestamp = viewModel::formatTimestamp,
            onDismissHealthCheck = viewModel::dismissHealthCheck,
            onSaveCancel = viewModel::onSaveCancel,
            onSaveUrl = viewModel::onSaveUrl,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        )
    }
}
