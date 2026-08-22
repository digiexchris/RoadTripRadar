package ca.voiditswarranty.roadtripradar.car

import androidx.car.app.CarAppService
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

/**
 * Car App Library entry point for Android Auto. The host (the Android Auto companion
 * app / car projector) binds this service and asks for a [RoadTripRadarSession] to build
 * the car UI. Declared in the manifest with the `androidx.car.app.category.POI` category
 * — a non-navigation category that fits this map/weather/POI app and is publishable on
 * the Play Store.
 */
class RoadTripRadarCarAppService : CarAppService() {
    // A projected Android Auto app is always driven through the Android Auto companion
    // app (gearhead), whether launched from the DHU in dev or from a real car in
    // production. The library's default HostValidator trust-list rejected the current
    // (Play Store) gearhead as an "Unrecognized host" / "Unknown host
    // 'com.google.android.projection.gearhead'", which the host surfaces to the driver as
    // the "encountered an error" screen. So we explicitly allow gearhead in both debug and
    // release. The fingerprint is Google's stable public signing cert for the Android Auto
    // companion app (the same value Google's own Car App samples hardcode); if Google ever
    // rotates it, a library update would accompany it.
    override fun createHostValidator(): HostValidator =
        HostValidator.Builder(applicationContext)
            .addAllowedHost(
                "com.google.android.projection.gearhead",
                "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf",
            )
            .build()

    override fun onCreateSession(sessionInfo: SessionInfo): androidx.car.app.Session =
        RoadTripRadarSession()
}