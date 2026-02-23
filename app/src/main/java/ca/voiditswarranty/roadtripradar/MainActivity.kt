package ca.voiditswarranty.roadtripradar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.voiditswarranty.roadtripradar.model.MapStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import ca.voiditswarranty.roadtripradar.ui.MapScreen
import ca.voiditswarranty.roadtripradar.ui.TermsOverlay
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModelFactory

class MainActivity : ComponentActivity() {
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
    val vm: MapViewModel = viewModel(factory = MapViewModelFactory(context))

    val systemDefault = if (isSystemInDarkTheme()) MapStyle.LIBERTY_DARK else MapStyle.LIBERTY
    var mapStyle by remember {
        mutableStateOf(
            try { vm.prefsRepo.mapStyle }
            catch (_: IllegalArgumentException) { systemDefault }
        )
    }

    RoadTripRadarTheme(darkTheme = mapStyle.isDark) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapScreen(
                vm = vm,
                mapStyle = mapStyle,
                onStyleChange = { newStyle ->
                    mapStyle = newStyle
                    vm.prefsRepo.mapStyle = newStyle
                },
            )
            TermsOverlay(vm = vm)
        }
    }
}
