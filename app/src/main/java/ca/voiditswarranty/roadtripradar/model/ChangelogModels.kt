package ca.voiditswarranty.roadtripradar.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChangelogBundle(
    val releases: List<ChangelogRelease> = emptyList(),
)

@Serializable
data class ChangelogRelease(
    @SerialName("versionName") val versionName: String,
    @SerialName("versionCode") val versionCode: Int,
    val items: List<String> = emptyList(),
    /**
     * When false, this release is hidden from the in-app changelog UI (both the post-update
     * What's New sheet and the Help & Info full changelog). Defaults to true. Does not affect
     * Fastlane/Play Store or GitHub Release note generation — those are still built from the
     * raw `items`.
     */
    @SerialName("showInApp") val showInApp: Boolean = true,
)
