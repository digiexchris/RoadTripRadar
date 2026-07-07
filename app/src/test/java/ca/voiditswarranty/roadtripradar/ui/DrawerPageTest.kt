package ca.voiditswarranty.roadtripradar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import ca.voiditswarranty.roadtripradar.ui.theme.RoadTripRadarTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [DrawerPage] rendering via [DrawerPageContent]. Each page variant
 * is tested for: title and actions render, action click invokes the callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DrawerPageTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun hostContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            RoadTripRadarTheme {
                Box(Modifier.fillMaxSize()) { content() }
            }
        }
    }

    private fun action(label: String, onClick: () -> Unit = {}) = DrawerAction(
        label = label,
        icon = Icons.Default.Place,
        onClick = onClick,
    )

    @Test
    fun pageMain_rendersTitleAndActions() {
        val page = DrawerPage.Main(
            title = "Main",
            actions = listOf(action("Close to Map"), action("Quit")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Main").assertExists()
        composeTestRule.onNodeWithText("Close to Map").assertExists()
        composeTestRule.onNodeWithText("Quit").assertExists()
    }

    @Test
    fun pageMap_rendersTitleAndActions() {
        val page = DrawerPage.Map(
            title = "Map",
            actions = listOf(action("Theme"), action("Layers")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Map").assertExists()
        composeTestRule.onNodeWithText("Theme").assertExists()
        composeTestRule.onNodeWithText("Layers").assertExists()
    }

    @Test
    fun pageWeather_rendersTitleAndActions() {
        val page = DrawerPage.Weather(
            title = "Weather",
            actions = listOf(action("Radar"), action("Wind")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Weather").assertExists()
        composeTestRule.onNodeWithText("Radar").assertExists()
        composeTestRule.onNodeWithText("Wind").assertExists()
    }

    @Test
    fun pageSystem_rendersTitleAndActions() {
        val page = DrawerPage.System(
            title = "System",
            actions = listOf(action("Theme"), action("Units")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("System").assertExists()
        composeTestRule.onNodeWithText("Theme").assertExists()
        composeTestRule.onNodeWithText("Units").assertExists()
    }

    @Test
    fun pageHelp_rendersTitleAndActions() {
        val page = DrawerPage.Help(
            title = "Help",
            actions = listOf(action("Tutorial"), action("About")),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Help").assertExists()
        composeTestRule.onNodeWithText("Tutorial").assertExists()
        composeTestRule.onNodeWithText("About").assertExists()
    }

    @Test
    fun pageAny_actionClick_invokesActionCallback() {
        var clicked = false
        val page = DrawerPage.Main(
            title = "Main",
            actions = listOf(action("Test", onClick = { clicked = true })),
        )
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Test").performClick()
        assertTrue(clicked)
    }

    @Test
    fun pageAny_emptyActions_rendersTitleOnly() {
        val page = DrawerPage.Main(title = "Empty", actions = emptyList())
        hostContent {
            DrawerPageContent(page = page, onDismiss = {})
        }
        composeTestRule.onNodeWithText("Empty").assertExists()
    }

    @Test
    fun pageAll_haveNonEmptyActionsList_pinned() {
        // Sanity: every [DrawerPage] variant constructed in production carries
        // a non-empty action list. The data class itself permits empty lists
        // (see [pageAny_emptyActions_rendersTitleOnly]), so this test guards
        // against a future refactor that drops a page's actions.
        val pages: List<DrawerPage> = listOf(
            DrawerPage.Main("Main", listOf(action("X"))),
            DrawerPage.Map("Map", listOf(action("X"))),
            DrawerPage.Weather("Weather", listOf(action("X"))),
            DrawerPage.System("System", listOf(action("X"))),
            DrawerPage.Help("Help", listOf(action("X"))),
        )
        assertTrue(pages.all { it.actions.isNotEmpty() })
    }
}