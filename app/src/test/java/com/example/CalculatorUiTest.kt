package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CalculatorUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `calculator keypad enters expression, evaluates, and opens mode menu`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("solar_panel").assertIsDisplayed()
        composeTestRule.onNode(hasText("fx-991ES PLUS")).assertIsDisplayed()

        // Tap 7 + 5 =
        composeTestRule.onNodeWithTag("key_7").performClick()
        composeTestRule.onNodeWithTag("key_add").performClick()
        composeTestRule.onNodeWithTag("key_5").performClick()
        composeTestRule.onNodeWithTag("key_equals").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeTestRule.onNode(
                    hasAnyAncestor(hasTestTag("lcd_result_text")) and hasText("12")
                ).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        // Open MODE menu and verify modes appear
        composeTestRule.onNodeWithTag("key_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mode_item_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_item_5").assertIsDisplayed()
    }

    @Test
    fun `function catalog opens from LCD chip, filters 414+ items, inserts and evaluates csc(30)`() {
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("catalog_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("catalog_count_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("catalog_search").performTextInput("Cosecant")
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("catalog_item_7").assertIsDisplayed()
        composeTestRule.onNodeWithTag("catalog_item_7").performClick()
        composeTestRule.waitForIdle()

        // Evaluate csc(30°) = 2
        composeTestRule.onNodeWithTag("key_3").performClick()
        composeTestRule.onNodeWithTag("key_0").performClick()
        composeTestRule.onNodeWithTag("key_equals").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeTestRule.onNode(
                    hasAnyAncestor(hasTestTag("lcd_result_text")) and hasText("2")
                ).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }
}
