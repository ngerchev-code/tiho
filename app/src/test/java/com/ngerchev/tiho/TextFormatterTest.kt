package com.ngerchev.tiho

import com.ngerchev.tiho.speech.TextFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class TextFormatterTest {

    private fun bg(text: String) = TextFormatter.autoPunctuate(text, "bg-BG")
    private fun en(text: String) = TextFormatter.autoPunctuate(text, "en-US")

    @Test
    fun bulgarianStatementGetsCommasAndFullStop() {
        assertEquals(
            "Здравейте, ще закъснея с десет минути, защото има задръстване.",
            bg("здравейте ще закъснея с десет минути защото има задръстване")
        )
        assertEquals("Казах му, че ще дойда, но той не вярва.", bg("казах му че ще дойда но той не вярва"))
        assertEquals(
            "Добър ден, изпращам ви офертата, за да я прегледате.",
            bg("добър ден изпращам ви офертата за да я прегледате")
        )
    }

    @Test
    fun bulgarianQuestionsGetQuestionMark() {
        assertEquals("Можеш ли да ми изпратиш офертата?", bg("можеш ли да ми изпратиш офертата"))
        assertEquals("Къде си?", bg("къде си"))
    }

    @Test
    fun noCommaAfterAnd() {
        assertEquals(
            "Ще дойда утре и ако мога ще донеса документите.",
            bg("ще дойда утре и ако мога ще донеса документите")
        )
    }

    @Test
    fun existingPunctuationIsKept() {
        assertEquals("Добре, ще дойда.", bg("добре, ще дойда."))
        assertEquals("Наистина ли?", bg("наистина ли?"))
    }

    @Test
    fun english() {
        assertEquals("Hello, I will call you later.", en("hello I will call you later"))
        assertEquals("Can you send it today?", en("can you send it today"))
        assertEquals("I wanted to come, but I was late.", en("I wanted to come but I was late"))
    }

    @Test
    fun spokenPunctuation() {
        assertEquals(
            "здравейте, ще закъснея.",
            TextFormatter.applySpokenPunctuation("здравейте запетая ще закъснея точка", "bg-BG")
        )
        assertEquals(
            "hello, are you there?",
            TextFormatter.applySpokenPunctuation("hello comma are you there question mark", "en-US")
        )
    }

    @Test
    fun sentencesAreSplitAndJoined() {
        assertEquals(
            listOf("Първо изречение.", "Второ ли е?", "Трето!"),
            TextFormatter.splitSentences("Първо изречение. Второ ли е? Трето!")
        )
        assertEquals("Здравей. Как си", TextFormatter.join("Здравей.", "как си"))
        assertEquals("ще дойда утре", TextFormatter.join("ще дойда", "утре"))
        assertEquals("Утре", TextFormatter.join("", "утре"))
    }
}
