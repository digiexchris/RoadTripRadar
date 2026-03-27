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
)
