package ca.voiditswarranty.roadtripradar

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.voiditswarranty.roadtripradar.data.isDarkForAppTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ca.voiditswarranty.roadtripradar.ui.LocationPermissionGate
import ca.voiditswarranty.roadtripradar.ui.MapScreen
import ca.voiditswarranty.roadtripradar.ui.TermsOverlay
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModelFactory

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            RoadTripRadarApp()
        }
    }
}

@Composable
fun RoadTripRadarApp() {
    val context = LocalContext.current
    val uiMode = LocalConfiguration.current.uiMode
    val vm: MapViewModel = viewModel(factory = MapViewModelFactory(context))

    // Gate background polling on this surface being in the foreground. The shared VM
    // only polls (weather/radar/animation) while at least one surface is active; the
    // car Session accounts for itself, so the phone only needs to count itself in/out
    // on resume/stop.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, vm) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> vm.onSurfaceActive()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> vm.onSurfaceInactive()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val mapStyle = vm.mapStyle

    val appInDarkTheme = remember(uiMode, mapStyle) {
        mapStyle.isDarkForAppTheme(context)
    }

    RoadTripRadarTheme(darkTheme = appInDarkTheme) {
        LocationPermissionGate(vm = vm) { locationGranted ->
            Box(modifier = Modifier.fillMaxSize()) {
                MapScreen(
                    vm = vm,
                    mapStyle = mapStyle,
                    onStyleChange = { newStyle ->
                        vm.updateMapStyle(newStyle)
                    },
                    locationPermissionGranted = locationGranted,
                )
                TermsOverlay(vm = vm)
            }
        }
    }
}
