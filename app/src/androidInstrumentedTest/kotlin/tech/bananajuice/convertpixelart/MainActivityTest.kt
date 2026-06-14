package tech.bananajuice.convertpixelart

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testSelectInputFileButtonIsDisplayed() {
        // Find the "Select Input File" button and assert it exists
        composeTestRule.onNodeWithText("Select Input File").assertExists()
    }
}
