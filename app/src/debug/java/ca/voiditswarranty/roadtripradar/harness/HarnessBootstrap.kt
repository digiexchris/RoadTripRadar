package ca.voiditswarranty.roadtripradar.harness

import android.content.Context
import android.util.Log
import ca.voiditswarranty.roadtripradar.BuildConfig
import ca.voiditswarranty.roadtripradar.viewmodel.MapViewModel

/**
 * Entry point for the dev-only test harness server. Called reflectively from
 * `RoadTripRadarApp` (in the main source set) so the main source set does not
 * compile against any harness classes — keeping the release build free of
 * harness code at the source-set level, not just behind a runtime flag.
 *
 * `BuildConfig.DEBUG` is checked at the call site; this class only exists in
 * the `debug` build variant's source set, so both halves line up: the
 * reflective call is only attempted in debug builds, and the class is only
 * compiled into debug builds.
 */
object HarnessBootstrap {
    private const val TAG = "HarnessServer"

    @JvmStatic
    fun start(context: Context, vm: MapViewModel): HarnessServer {
        val port = BuildConfig.HARNESS_PORT
        val server = HarnessServer(vm, port)
        server.start(NANO_HTTPD_TIMEOUT, false)
        Log.i(TAG, "listening on 127.0.0.1:$port — run: adb forward tcp:$port tcp:$port")
        return server
    }

    /** NanoHTTPD's recommended socket read timeout for keep-alive connections. */
    private const val NANO_HTTPD_TIMEOUT = 5000
}