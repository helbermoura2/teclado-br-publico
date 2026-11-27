package com.wasimaster.wmkeyboard.core.tools

import org.junit.Assert.*
import org.junit.Test

class HbxProofreadingTest {
    @Test fun punctuatesShortGreetings() {
        assertEquals("Olá, tudo bem?", HbxProofreading.greeting("Ola tudo bem"))
        assertEquals("Olá, Helber, você está bem?", HbxProofreading.greeting("Ola Helber você está bem"))
        assertNull(HbxProofreading.greeting("Ola tudo bem e depois vamos sair"))
    }

    @Test fun onlyReturnsUsableText() {
        val result = "Olá, tudo bem?"
        assertEquals(result, HbxProofreading.finalText("<|channel>thought\nAnalysis<channel|>$result","Ola tudo bem"))
        assertEquals(result, HbxProofreading.finalText("<think>Notes</think>$result","Ola tudo bem"))
        assertNull(HbxProofreading.finalText("<|channel>thought\nAnalysis","Ola tudo bem"))
        assertNull(HbxProofreading.finalText("Analysis<channel|>$result","Ola tudo bem"))
        assertNull(HbxProofreading.finalText("Corrigido: $result","Ola tudo bem"))
    }
}
