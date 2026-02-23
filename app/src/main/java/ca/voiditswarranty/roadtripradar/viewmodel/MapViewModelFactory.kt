package ca.voiditswarranty.roadtripradar.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository

class MapViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return MapViewModel(PreferencesRepository(context)) as T
    }
}
