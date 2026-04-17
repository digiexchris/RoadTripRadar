package ca.voiditswarranty.roadtripradar.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun LocationPermissionGate(
    vm: MapViewModel,
    content: @Composable (locationPermissionGranted: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    fun hasAnyLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    var granted by remember { mutableStateOf(hasAnyLocationPermission()) }
    var showDeniedDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val anyGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        granted = anyGranted
        if (anyGranted) {
            vm.updateUseGps(true)
        } else {
            showDeniedDialog = true
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = hasAnyLocationPermission()
                granted = now
                if (now) vm.updateUseGps(true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content(granted)
    }

    if (showDeniedDialog && activity != null) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text(stringResource(R.string.permission_location_denied_title)) },
            text = { Text(stringResource(R.string.permission_location_denied_body)) },
            confirmButton = {
                TextButton(onClick = { activity.finishAffinity() }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    } else if (!granted && activity != null) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text(stringResource(R.string.permission_location_title)) },
            text = {
                Text(
                    stringResource(R.string.permission_location_body),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) {
                    Text(stringResource(R.string.permission_grant))
                }
            },
            dismissButton = {
                TextButton(onClick = { activity.finishAffinity() }) {
                    Text(stringResource(R.string.action_quit))
                }
            },
        )
    }
}
