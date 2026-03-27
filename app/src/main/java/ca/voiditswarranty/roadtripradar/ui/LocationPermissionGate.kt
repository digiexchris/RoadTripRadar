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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

@Composable
fun LocationPermissionGate(
    vm: MapViewModel,
    content: @Composable (locationPermissionGranted: Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(hasFineLocation()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { ok ->
        granted = ok
        if (ok) {
            vm.updateUseGps(true)
        } else {
            activity?.finishAffinity()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = hasFineLocation()
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

    if (!granted && activity != null) {
        AlertDialog(
            onDismissRequest = { },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
            title = { Text("Location permission required") },
            text = {
                Text(
                    "Road Trip Radar needs access to your precise location to show where you are on the map. " +
                        "Tap Grant permission to continue, or Quit to exit the app.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
                ) {
                    Text("Grant permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { activity.finishAffinity() }) {
                    Text("Quit")
                }
            },
        )
    }
}
