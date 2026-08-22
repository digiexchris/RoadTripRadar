package ca.voiditswarranty.roadtripradar.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder

/**
 * Returns the process-wide shared [MapViewModel] (see [CarViewModelHolder]).
 *
 * Both the phone Activity and the Android Auto car Session share a single ViewModel so
 * continuous work (weather/radar/POI polling) runs once and both surfaces stay in sync.
 * The holder constructs the VM on an app-scoped supervisor scope so polling survives
 * Activity destruction while the car Session is alive.
 */
class MapViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CarViewModelHolder.ensureInitialized(context) as T
    }
}