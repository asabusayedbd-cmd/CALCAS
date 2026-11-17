package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

        // Tap 7 + 5 =
        composeTestRule.onNodeWithTag("key_7").performClick()
        composeTestRule.onNodeWithTag("key_add").performClick()
        composeTestRule.onNodeWithTag("key_5").performClick()
        composeTestRule.onNodeWithTag("key_equals").performClick()

        composeTestRule.waitUntil(timeoutMillis = 3_000) {
            runCatching {
                composeTestRule.onNodeWithTag("lcd_result_text").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        composeTestRule.onNodeWithText("12").assertIsDisplayed()

        // Open MODE menu and verify modes appear
        composeTestRule.onNodeWithTag("key_mode").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("mode_item_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mode_item_5").assertIsDisplayed()
    }
}
