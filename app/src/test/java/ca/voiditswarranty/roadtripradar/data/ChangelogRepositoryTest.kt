package ca.voiditswarranty.roadtripradar.data

import ca.voiditswarranty.roadtripradar.model.ChangelogBundle
import ca.voiditswarranty.roadtripradar.model.ChangelogRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ChangelogRepository] — pure functions that filter / sort the changelog bundle
 * for the post-update "What's New" sheet and the Help & Info full changelog. The IO side
 * (`loadBundled`) is tested separately with Robolectric in `ChangelogRepositoryLoadTest`.
 *
 * The two pure helpers are:
 * - [ChangelogRepository.releasesNewSince] — entries with `versionCode in (lastSeenCode,
 *   currentVersionCode]`, `showInApp = true`, sorted newest first.
 * - [ChangelogRepository.allReleasesSortedNewestFirst] — entries with `showInApp = true`
 *   and `versionCode > 0`, sorted newest first.
 */
class ChangelogRepositoryTest {

    private fun release(
        versionName: String = "v",
        versionCode: Int = 1,
        showInApp: Boolean = true,
        items: List<String> = emptyList(),
    ): ChangelogRelease = ChangelogRelease(
        versionName = versionName,
        versionCode = versionCode,
        items = items,
        showInApp = showInApp,
    )

    private fun bundle(vararg releases: ChangelogRelease): ChangelogBundle =
        ChangelogBundle(releases = releases.toList())

    // -------- releasesNewSince --------

    @Test
    fun releasesNewSince_emptyBundle_returnsEmpty() {
        val result = ChangelogRepository.releasesNewSince(
            bundle(),
            lastSeenCode = 0,
            currentVersionCode = 100,
        )
        assertEquals(emptyList<ChangelogRelease>(), result)
    }

    @Test
    fun releasesNewSince_lastSeenAtCurrentCode_returnsEmpty() {
        // No newer release than the user's last seen code.
        val result = ChangelogRepository.releasesNewSince(
            bundle(release(versionCode = 5)),
            lastSeenCode = 5,
            currentVersionCode = 5,
        )
        assertEquals(emptyList<ChangelogRelease>(), result)
    }

    @Test
    fun releasesNewSince_lastSeenBelowCurrentCode_returnsNewerOnly() {
        // Three releases: one older than lastSeen, two newer than lastSeen, one at current.
        val r1 = release(versionCode = 1)
        val r2 = release(versionCode = 2)
        val r3 = release(versionCode = 3)
        val r4 = release(versionCode = 5)
        val result = ChangelogRepository.releasesNewSince(
            bundle(r1, r2, r3, r4),
            lastSeenCode = 1,
            currentVersionCode = 5,
        )
        // (1, 5] → [2, 3, 5]. Newest first → [5, 3, 2].
        assertEquals(listOf(r4, r3, r2), result)
    }

    @Test
    fun releasesNewSince_includesTheCurrentVersion() {
        // The release whose versionCode == currentVersionCode is included. This is the
        // "you just updated — here's what's new" case.
        val r = release(versionCode = 10)
        val result = ChangelogRepository.releasesNewSince(
            bundle(r),
            lastSeenCode = 9,
            currentVersionCode = 10,
        )
        assertEquals(listOf(r), result)
    }

    @Test
    fun releasesNewSince_skipsShowInAppFalse() {
        // Releases with showInApp = false are filtered out — they exist for Fastlane/Play
        // Store but not the in-app sheet.
        val visible = release(versionCode = 2, showInApp = true)
        val hidden = release(versionCode = 3, showInApp = false)
        val result = ChangelogRepository.releasesNewSince(
            bundle(visible, hidden),
            lastSeenCode = 0,
            currentVersionCode = 10,
        )
        assertEquals(listOf(visible), result)
    }

    @Test
    fun releasesNewSince_skipsReleasesWithVersionCodeZeroOrNegative() {
        // The versionCode filter `> lastSeenCode && <= currentVersionCode` already excludes
        // non-positive codes (when lastSeenCode = 0) and pre-lastSeen codes. Pin the edge.
        val r = release(versionCode = 0)
        val result = ChangelogRepository.releasesNewSince(
            bundle(r),
            lastSeenCode = 0,
            currentVersionCode = 10,
        )
        assertEquals("versionCode 0 must be excluded (filter is > lastSeenCode)", emptyList<ChangelogRelease>(), result)
    }

    // -------- allReleasesSortedNewestFirst --------

    @Test
    fun allReleasesSortedNewestFirst_emptyBundle_returnsEmpty() {
        val result = ChangelogRepository.allReleasesSortedNewestFirst(bundle())
        assertEquals(emptyList<ChangelogRelease>(), result)
    }

    @Test
    fun allReleasesSortedNewestFirst_mixedVersions_returnsDescending() {
        val r1 = release(versionCode = 1)
        val r2 = release(versionCode = 2)
        val r3 = release(versionCode = 3)
        // Inserted out of order; result must be descending.
        val result = ChangelogRepository.allReleasesSortedNewestFirst(bundle(r3, r1, r2))
        assertEquals(listOf(r3, r2, r1), result)
    }

    @Test
    fun allReleasesSortedNewestFirst_skipsShowInAppFalse() {
        val visible = release(versionCode = 5, showInApp = true)
        val hidden = release(versionCode = 4, showInApp = false)
        val result = ChangelogRepository.allReleasesSortedNewestFirst(bundle(visible, hidden))
        assertEquals(listOf(visible), result)
    }

    @Test
    fun allReleasesSortedNewestFirst_skipsVersionCodeZero() {
        // The "next" placeholder has versionCode 99 in the JSON (per the CLAUDE.md note),
        // and `versionCode > 0` is the gate. Pin the behavior so a future bump of the
        // "next" placeholder number doesn't accidentally hide it.
        val placeholder = release(versionName = "next", versionCode = 99)
        val result = ChangelogRepository.allReleasesSortedNewestFirst(bundle(placeholder))
        assertEquals(listOf(placeholder), result)
    }

    @Test
    fun allReleasesSortedNewestFirst_skipsVersionCodeZeroOrNegative() {
        val r = release(versionCode = 0)
        val result = ChangelogRepository.allReleasesSortedNewestFirst(bundle(r))
        assertTrue(
            "versionCode 0 (or negative) must be excluded",
            result.isEmpty(),
        )
    }
}
