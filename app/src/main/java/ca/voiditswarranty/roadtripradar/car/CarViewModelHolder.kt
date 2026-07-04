package ca.voiditswarranty.roadtripradar.car

import android.content.Context
import ca.voiditswarranty.roadtripradar.data.CustomThemeRepository
import ca.voiditswarranty.roadtripradar.data.OpenMeteoRepository
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-wide holder for the shared [MapViewModel].
 *
 * The phone Activity and the Android Auto car [androidx.car.app.Session] share a single
 * `MapViewModel` so that continuous work (Open-Meteo polling, RainViewer 60s radar polling,
 * the POI single-worker pipeline) runs exactly once and both surfaces stay in sync. The
 * ViewModel is constructed with [appScope] — an app-scoped supervisor [CoroutineScope] — so
 * that polling survives Activity destruction (which would otherwise cancel `viewModelScope`)
 * while the car Session is still alive.
 *
 * The phone path ([ca.voiditswarranty.roadtripradar.viewmodel.MapViewModelFactory]) also
 * delegates here so there is exactly one instance per process regardless of which surface
 * spins it up first.
 */
object CarViewModelHolder {
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var vm: MapViewModel? = null

    fun ensureInitialized(appContext: Context): MapViewModel {
        vm?.let { return it }
        return synchronized(this) {
            vm ?: MapViewModel(
                appContext = appContext.applicationContext,
                prefsRepo = PreferencesRepository(appContext.applicationContext),
                customThemeRepo = CustomThemeRepository(appContext.applicationContext),
                openMeteoRepo = OpenMeteoRepository(),
                externalScope = appScope,
            ).also { vm = it }
        }
    }
}