package ca.voiditswarranty.roadtripradar.ui.tutorial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure data + dispatcher in [TutorialModels]. The Compose-coupled
 * anchor-registration logic in [TutorialAnchors] is exercised in Phase 5's
 * Compose UI tests; here we pin the step-list shape, the anchor-id uniqueness,
 * and the `stepsFor` dispatch.
 */
class TutorialModelsTest {

    // -------- Step-list sizes (pin against silent drift) --------

    @Test
    fun mapTutorialSteps_has8Steps() {
        assertEquals(8, MAP_TUTORIAL_STEPS.size)
    }

    @Test
    fun menuMainTutorialSteps_has8Steps() {
        assertEquals(8, MENU_MAIN_TUTORIAL_STEPS.size)
    }

    @Test
    fun routeEditorTutorialSteps_has3Steps() {
        assertEquals(3, ROUTE_EDITOR_TUTORIAL_STEPS.size)
    }

    @Test
    fun mapSettingsTutorialSteps_has8Steps() {
        assertEquals(8, MAP_SETTINGS_TUTORIAL_STEPS.size)
    }

    // -------- stepsFor dispatcher --------

    @Test
    fun stepsFor_map_returnsMapSteps() {
        assertEquals(MAP_TUTORIAL_STEPS, stepsFor(TutorialGroup.MAP))
    }

    @Test
    fun stepsFor_menuMain_returnsMenuMainSteps() {
        assertEquals(MENU_MAIN_TUTORIAL_STEPS, stepsFor(TutorialGroup.MENU_MAIN))
    }

    @Test
    fun stepsFor_routeEditor_returnsRouteEditorSteps() {
        assertEquals(ROUTE_EDITOR_TUTORIAL_STEPS, stepsFor(TutorialGroup.ROUTE_EDITOR))
    }

    @Test
    fun stepsFor_mapSettings_returnsMapSettingsSteps() {
        assertEquals(MAP_SETTINGS_TUTORIAL_STEPS, stepsFor(TutorialGroup.MAP_SETTINGS))
    }

    @Test
    fun stepsFor_coversEveryEnumValue() {
        // Every TutorialGroup must have a step list — pin against adding a new
        // group and forgetting to add a case to the `when`.
        for (group in TutorialGroup.entries) {
            assertNotNull("missing steps for $group", stepsFor(group))
            assertTrue("steps for $group must be non-empty", stepsFor(group).isNotEmpty())
        }
    }

    // -------- Anchor IDs are distinct (excluding the `null` sentinel) --------

    @Test
    fun mapTutorialSteps_nonNullAnchorIds_areDistinct() {
        // Two full-screen steps (anchorId == null) are allowed; but the same
        // anchor must not appear twice (it would make the spotlight jump).
        val nonNullIds = MAP_TUTORIAL_STEPS.mapNotNull { it.anchorId }
        assertEquals("duplicate non-null anchor in MAP_TUTORIAL_STEPS", nonNullIds.size, nonNullIds.toSet().size)
    }

    @Test
    fun mapSettingsTutorialSteps_anchorIds_areDistinct() {
        val ids = MAP_SETTINGS_TUTORIAL_STEPS.mapNotNull { it.anchorId }
        assertEquals("duplicate non-null anchor in MAP_SETTINGS_TUTORIAL_STEPS", ids.size, ids.toSet().size)
    }

    @Test
    fun menuMainTutorialSteps_anchorIds_areDistinct() {
        val ids = MENU_MAIN_TUTORIAL_STEPS.mapNotNull { it.anchorId }
        assertEquals("duplicate non-null anchor in MENU_MAIN_TUTORIAL_STEPS", ids.size, ids.toSet().size)
    }

    // -------- First/last step identity --------

    @Test
    fun mapTutorialSteps_firstStepIsFullScreen() {
        // The first step is a "welcome" full-screen card (anchorId = null).
        assertEquals(null, MAP_TUTORIAL_STEPS.first().anchorId)
    }

    @Test
    fun mapTutorialSteps_everyStepHasTitleAndBody() {
        for (step in MAP_TUTORIAL_STEPS) {
            // titleRes and bodyRes are non-zero (R.id constants are non-zero).
            assertTrue("every step must have a title", step.titleRes != 0)
            assertTrue("every step must have a body", step.bodyRes != 0)
        }
    }
}
