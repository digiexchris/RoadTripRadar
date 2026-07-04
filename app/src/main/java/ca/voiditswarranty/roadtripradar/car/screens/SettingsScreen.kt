package ca.voiditswarranty.roadtripradar.car.screens

import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.ParkedOnlyOnClickListener
import androidx.car.app.model.Row
import androidx.car.app.model.Toggle
import ca.voiditswarranty.roadtripradar.R
import ca.voiditswarranty.roadtripradar.car.CarViewModelHolder
import ca.voiditswarranty.roadtripradar.car.nextCycle
import ca.voiditswarranty.roadtripradar.data.PreferencesRepository
import ca.voiditswarranty.roadtripradar.model.PrefsDefaults
import ca.voiditswarranty.roadtripradar.ui.tempUnitSymbol
import ca.voiditswarranty.roadtripradar.ui.windUnitLabel

/**
 * Settings: units, map style, keep-screen-on, auto-advance. Reset is parked-only and
 * pushes a confirmation [MessageTemplate]. Tutorials/terms/changelog/theme-import are
 * phone-only. If terms haven't been accepted, a parked-only row directs the user to the
 * phone app.
 */
class SettingsScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {

    override fun buildTemplate(): ListTemplate {
        val termsAccepted = vm.prefsRepo.acceptedTermsVersion >= PrefsDefaults.TERMS_VERSION

        val itemList = ItemList.Builder().apply {
            if (!termsAccepted) {
                addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_terms_not_accepted))
                        .setOnClickListener(
                            ParkedOnlyOnClickListener.create {
                                push(TermsMessageScreen(carContext))
                            }
                        )
                        .build()
                )
            }

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_metric))
                    .setToggle(
                        Toggle.Builder { on -> vm.updateUseMetric(on) }
                            .setChecked(vm.useMetric)
                            .build()
                    )
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_wind_unit))
                    .addText(windUnitLabel(carContext, vm.windSpeedUnit))
                    .setOnClickListener { vm.updateWindSpeedUnit(vm.windSpeedUnit.nextCycle()) }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_temp_unit))
                    .addText(tempUnitSymbol(vm.temperatureUnit))
                    .setOnClickListener { vm.updateTemperatureUnit(vm.temperatureUnit.nextCycle()) }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_map_style))
                    .addText(carContext.getString(vm.mapStyle.displayNameRes))
                    .setOnClickListener { vm.updateMapStyle(vm.mapStyle.nextCycle()) }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_keep_screen_on))
                    .setToggle(
                        Toggle.Builder { on -> vm.updateKeepScreenOn(on) }
                            .setChecked(vm.keepScreenOn)
                            .build()
                    )
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_auto_advance))
                    .setToggle(
                        Toggle.Builder { on -> vm.updateAutoAdvanceEnabled(on) }
                            .setChecked(vm.autoAdvanceEnabled)
                            .build()
                    )
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_threshold))
                    .addText("${vm.autoAdvanceThresholdMeters} m")
                    .setOnClickListener {
                        vm.updateAutoAdvanceThreshold(nextThreshold(vm.autoAdvanceThresholdMeters))
                        vm.saveAutoAdvanceThreshold()
                    }
                    .build()
            )

            addItem(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_reset))
                    .setOnClickListener(
                        ParkedOnlyOnClickListener.create { push(ResetConfirmScreen(carContext)) }
                    )
                    .build()
            )
        }.build()

        return ListTemplate.Builder()
            .setSingleList(itemList)
            .setTitle(carContext.getString(R.string.car_settings_title))
            .setHeaderAction(Action.BACK)
            .build()
    }

    private fun nextThreshold(current: Int): Int {
        val presets = listOf(25, 50, 100, 200, 500)
        val idx = presets.indexOf(current).takeIf { it >= 0 } ?: 2
        return presets[(idx + 1) % presets.size]
    }
}

/** Confirmation screen for "reset to defaults" (parked-only to reach). */
class ResetConfirmScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {
    override fun buildTemplate(): MessageTemplate {
        return MessageTemplate.Builder(carContext.getString(R.string.car_settings_reset_confirm))
            .setTitle(carContext.getString(R.string.car_settings_reset))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.car_settings_reset))
                    .setOnClickListener {
                        val systemDefault = PreferencesRepository.defaultMapStyleFor(carContext)
                        vm.resetToDefaults(systemDefault) { vm.updateMapStyle(it) }
                        pop()
                    }
                    .build()
            )
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_cancel))
                    .setOnClickListener { pop() }
                    .build()
            )
            .build()
    }
}

/** Parked-only message directing the user to accept terms on the phone app. */
class TermsMessageScreen(carContext: CarContext) : BaseCarScreen(
    carContext,
    CarViewModelHolder.ensureInitialized(carContext.applicationContext),
) {
    override fun buildTemplate(): MessageTemplate {
        return MessageTemplate.Builder(carContext.getString(R.string.car_terms_message))
            .setTitle(carContext.getString(R.string.car_terms_not_accepted))
            .setHeaderAction(Action.BACK)
            .addAction(
                Action.Builder()
                    .setTitle(carContext.getString(R.string.action_back))
                    .setOnClickListener { pop() }
                    .build()
            )
            .build()
    }
}