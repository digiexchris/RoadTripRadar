package ca.voiditswarranty.roadtripradar.data

import android.content.Context
import ca.voiditswarranty.roadtripradar.model.ChangelogBundle
import ca.voiditswarranty.roadtripradar.model.ChangelogRelease
import kotlinx.serialization.json.Json

object ChangelogRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private const val ASSET_NAME = "changelog.json"

    fun loadBundled(context: Context): ChangelogBundle? {
        return try {
            context.assets.open(ASSET_NAME).use { stream ->
                val text = stream.bufferedReader().readText()
                json.decodeFromString<ChangelogBundle>(text)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Releases with versionCode in (lastSeenCode, currentVersionCode], sorted ascending by versionCode.
     */
    fun releasesNewSince(
        bundle: ChangelogBundle,
        lastSeenCode: Int,
        currentVersionCode: Int,
    ): List<ChangelogRelease> {
        return bundle.releases
            .filter { it.versionCode > lastSeenCode && it.versionCode <= currentVersionCode }
            .sortedBy { it.versionCode }
    }

    fun allReleasesSortedNewestFirst(bundle: ChangelogBundle): List<ChangelogRelease> {
        return bundle.releases
            .filter { it.versionCode > 0 }
            .sortedByDescending { it.versionCode }
    }
}
