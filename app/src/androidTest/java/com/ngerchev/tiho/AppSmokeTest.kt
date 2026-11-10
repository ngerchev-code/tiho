package com.ngerchev.tiho

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real app on an emulator and taps through its screens. It cannot speak into the
 * microphone or open other apps, but it catches crashes and broken screens before a release.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun s(id: Int): String = rule.activity.getString(id)

    private fun unique(prefix: String): String = prefix + " " + System.nanoTime()

    private fun sendTyped(text: String) {
        rule.onNode(hasSetTextAction()).performTextInput(text)
        rule.onNodeWithContentDescription(s(R.string.cd_send)).performClick()
        // The new message must scroll into view by itself.
        rule.waitUntil(5_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(text).assertExists()
    }

    /** Taps a message bubble the way a person would: keyboard away, bubble in view. */
    private fun tapBubble(text: String) {
        Espresso.closeSoftKeyboard()
        rule.waitForIdle()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        rule.onNodeWithText(text).assertIsDisplayed().performClick()
    }

    private fun openMenu(itemRes: Int) {
        rule.onNodeWithContentDescription(s(R.string.cd_menu)).performClick()
        rule.onNodeWithText(s(itemRes)).performClick()
    }

    private fun back() {
        rule.onNodeWithContentDescription(s(R.string.cd_back)).performClick()
        rule.onNodeWithContentDescription(s(R.string.cd_menu)).assertExists()
    }

    @Test
    fun startsOnTheChatScreen() {
        rule.onNodeWithContentDescription(s(R.string.cd_mic_start)).assertExists()
        rule.onNodeWithContentDescription(s(R.string.cd_attach)).assertExists()
        rule.onNodeWithContentDescription(s(R.string.cd_templates)).assertExists()
    }

    @Test
    fun typedMessageBecomesABubbleWithSendButtons() {
        sendTyped(unique("Hello"))
        rule.onAllNodesWithText("Viber").onFirst().assertExists()
        rule.onAllNodesWithText(s(R.string.msg_send_short)).onFirst().assertExists()
    }

    @Test
    fun punctuationKeyWritesIntoTheDraft() {
        val text = unique("Draft")
        rule.onNode(hasSetTextAction()).performTextInput(text)
        rule.onNodeWithText("?").performClick()
        rule.onNode(hasSetTextAction()).assert(hasText("$text? "))
        rule.onNodeWithContentDescription(s(R.string.cd_clear_draft)).performClick()
    }

    @Test
    fun messageCanBeEditedAgain() {
        val text = unique("Edit me")
        sendTyped(text)
        tapBubble(text)
        rule.onNodeWithText(s(R.string.msg_action_edit)).performClick()
        rule.onNode(hasSetTextAction()).assert(hasText(text))
        rule.onNodeWithContentDescription(s(R.string.cd_clear_draft)).performClick()
    }

    @Test
    fun severalMessagesCanBeSelectedAndDeleted() {
        val text = unique("Select me")
        sendTyped(text)
        tapBubble(text)
        rule.onNodeWithText(s(R.string.msg_action_select)).performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.selection_count, 1)).assertExists()
        rule.onNodeWithContentDescription(s(R.string.msg_action_delete)).performClick()
        rule.onNodeWithText(text).assertDoesNotExist()
        rule.onNodeWithContentDescription(s(R.string.cd_menu)).assertExists()
    }

    @Test
    fun attachMenuOffersFileCameraAndTranscription() {
        rule.onNodeWithContentDescription(s(R.string.cd_attach)).performClick()
        rule.onNodeWithText(s(R.string.attach_file)).assertExists()
        rule.onNodeWithText(s(R.string.attach_camera)).assertExists()
        rule.onNodeWithText(s(R.string.attach_transcribe)).performClick()
        // No key yet: the explanation and the key field must appear right here.
        rule.onNodeWithText(s(R.string.key_dialog_title)).assertExists()
        rule.onNodeWithText(s(R.string.action_cancel)).performClick()
        rule.onNodeWithContentDescription(s(R.string.cd_menu)).assertExists()
    }

    @Test
    fun readyPhrasesCanBeAddedAndUsed() {
        val phrase = unique("Phrase")
        rule.onNodeWithContentDescription(s(R.string.cd_templates)).performClick()
        rule.onNodeWithText(s(R.string.templates_edit)).performClick()
        rule.onNode(hasSetTextAction()).performScrollTo().performTextInput(phrase)
        rule.onNodeWithText(s(R.string.templates_add)).performScrollTo().performClick()
        rule.onNodeWithText(phrase).assertExists()
        back()
        rule.onNodeWithContentDescription(s(R.string.cd_templates)).performClick()
        rule.onNodeWithText(phrase).performClick()
        rule.onAllNodesWithText(phrase).onFirst().assertExists()
    }

    @Test
    fun settingsScreenWorks() {
        openMenu(R.string.menu_settings)
        rule.onNodeWithText(s(R.string.sec_appearance)).assertExists()
        rule.onNodeWithText(s(R.string.theme_dark)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.theme_light)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.theme_system)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.size_large)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.size_normal)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.set_haptics_title)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.set_haptics_title)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.set_ai_title)).performScrollTo().assertExists()
        rule.onNodeWithText(s(R.string.templates_title)).performScrollTo().assertExists()
        rule.onNodeWithText(s(R.string.ret_1h)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.ret_24h)).performScrollTo().performClick()
        rule.onNodeWithText(s(R.string.privacy_heading)).performScrollTo().assertExists()
        back()
    }

    @Test
    fun languageAndAboutScreensWork() {
        openMenu(R.string.menu_language)
        rule.onNodeWithText(s(R.string.lang_bg)).performClick()
        rule.onNodeWithText(s(R.string.lang_en)).performClick()
        back()
        openMenu(R.string.menu_about)
        rule.onNodeWithText(s(R.string.about_check_update)).performScrollTo().performClick()
        back()
    }
}
