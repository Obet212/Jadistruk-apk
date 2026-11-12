package com.example

import com.example.data.model.CharRule
import com.example.data.model.ColumnConfig
import com.example.data.model.ColumnFunction
import com.example.engine.EscPosCharacterNormalizer
import com.example.engine.ReceiptOcrEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testBulletSensorReplacementPreservesLength() {
        val input = "••••••"
        val normalized = EscPosCharacterNormalizer.normalizeText(input)
        assertEquals("******", normalized)
        assertEquals(input.length, normalized.length)
    }

    @Test
    fun testMixedBulletSensorReplacement() {
        val input = "Rekening: 123••••••789"
        val normalized = EscPosCharacterNormalizer.normalizeText(input)
        assertEquals("Rekening: 123******789", normalized)
        assertEquals(input.length, normalized.length)
    }

    @Test
    fun testPreserveLeadingZeroInAccountNumber() {
        val col = ColumnConfig(
            templateId = 1,
            name = "Rekening",
            columnFunction = ColumnFunction.ACCOUNT_NUMBER.name,
            charRule = CharRule.NUMBERS_ONLY.name
        )
        val extracted = ReceiptOcrEngine.applyFunctionAndCharRules("0123456789", col)
        assertEquals("0123456789", extracted)
        assertTrue(extracted.startsWith("0"))
    }

    @Test
    fun testPreserveLeadingZeroInPhoneNumber() {
        val col = ColumnConfig(
            templateId = 1,
            name = "Nomor HP",
            columnFunction = ColumnFunction.PHONE_NUMBER.name,
            charRule = CharRule.NUMBERS_ONLY.name
        )
        // Standard 08...
        val extracted1 = ReceiptOcrEngine.applyFunctionAndCharRules("081234567890", col)
        assertEquals("081234567890", extracted1)

        // +628... normalized to 08...
        val extracted2 = ReceiptOcrEngine.applyFunctionAndCharRules("+6281234567890", col)
        assertEquals("081234567890", extracted2)
    }

    @Test
    fun testNominalFormattingPreservedWithoutError() {
        val col = ColumnConfig(
            templateId = 1,
            name = "Nominal",
            columnFunction = ColumnFunction.NOMINAL.name,
            charRule = CharRule.FREE_CHARS.name
        )
        val extracted = ReceiptOcrEngine.applyFunctionAndCharRules("Rp 1.500.000,00", col)
        assertEquals("Rp 1.500.000,00", extracted)
    }

    @Test
    fun testEscPosFormattingTwoColumns() {
        val line = EscPosCharacterNormalizer.formatTwoColumns(
            left = "Total",
            right = "Rp 50.000",
            maxWidth = 32
        )
        assertEquals(32, line.length)
        assertTrue(line.startsWith("Total"))
        assertTrue(line.endsWith("Rp 50.000"))
    }
}
