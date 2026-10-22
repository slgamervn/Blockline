package com.blockline.app.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrarchiveCodecTest {
    @Test
    fun roundTripsTextEmptyAndCompiledBinaryEntries() {
        val text = """{"format_version":1,"text":"vanilla"}""".toByteArray(Charsets.UTF_8)
        val mcb = byteArrayOf(0x7f, 0x4d, 0x43, 0x42, 0x01, 0x02)
        val source = listOf(
            BrarchiveCodec.createEntry("blocks.json", text),
            BrarchiveCodec.createEntry("empty.json", byteArrayOf()),
            BrarchiveCodec.createEntry("compiled.mcb", mcb)
        )

        val decoded = BrarchiveCodec.decode(BrarchiveCodec.encode(source))

        assertEquals(3, decoded.size)
        assertEquals("blocks.json", decoded[0].name)
        assertTrue(decoded[0].isUtf8Text)
        assertEquals("", BrarchiveCodec.textContent(decoded[1]))
        assertTrue(decoded[2].isMcbBinary)
        assertFalse(decoded[2].isUtf8Text)
        assertArrayEquals(mcb, decoded[2].content)
        assertArrayEquals(BrarchiveCodec.encode(source), BrarchiveCodec.encode(decoded))
    }

    @Test
    fun preservesDuplicateNamesBecauseTheFormatAllowsThem() {
        val entries = listOf(
            BrarchiveCodec.createEntry("same.json", "{}".toByteArray()),
            BrarchiveCodec.createEntry("same.json", """{"last":true}""".toByteArray())
        )
        val decoded = BrarchiveCodec.decode(BrarchiveCodec.encode(entries))
        assertEquals(listOf("same.json", "same.json"), decoded.map { it.name })
        assertEquals(2, decoded.size)
    }

    @Test
    fun rejectsTruncatedHeadersAndEntryTables() {
        val encoded = BrarchiveCodec.encode(listOf(BrarchiveCodec.createEntry("a.json", "{}".toByteArray())))
        try {
            BrarchiveCodec.decode(encoded.copyOf(10))
            throw AssertionError("Truncated header was accepted")
        } catch (_: IllegalArgumentException) {
        }
        try {
            BrarchiveCodec.decode(encoded.copyOf(100))
            throw AssertionError("Truncated descriptor table was accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun rejectsUnsupportedVersionAndOutOfBoundsContentRanges() {
        val encoded = BrarchiveCodec.encode(listOf(BrarchiveCodec.createEntry("a.json", "{}".toByteArray())))
        val versioned = encoded.copyOf()
        ByteBuffer.wrap(versioned).order(ByteOrder.LITTLE_ENDIAN).putInt(12, 2)
        try {
            BrarchiveCodec.decode(versioned)
            throw AssertionError("Unsupported archive version was accepted")
        } catch (_: IllegalArgumentException) {
        }

        val outOfBounds = encoded.copyOf()
        ByteBuffer.wrap(outOfBounds).order(ByteOrder.LITTLE_ENDIAN).putInt(16 + 248, Int.MAX_VALUE)
        try {
            BrarchiveCodec.decode(outOfBounds)
            throw AssertionError("Out-of-range entry was accepted")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun rejectsUnsafeZipEntryNamesAndDuplicateZipNames() {
        val traversal = listOf(BrarchiveCodec.createEntry("../manifest.json", "{}".toByteArray()))
        try {
            BrarchiveCodec.encodeZip(traversal)
            throw AssertionError("Traversal entry was accepted for ZIP")
        } catch (_: IllegalArgumentException) {
        }
        val duplicates = listOf(
            BrarchiveCodec.createEntry("a.json", "{}".toByteArray()),
            BrarchiveCodec.createEntry("A.JSON", "{}".toByteArray())
        )
        try {
            BrarchiveCodec.encodeZip(duplicates)
            throw AssertionError("Case-insensitive duplicate was accepted for ZIP")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun validatesUtf8AndReadsWithinSizeLimit() {
        assertFalse(BrarchiveCodec.createEntry("binary", byteArrayOf(0xc3.toByte(), 0x28)).isUtf8Text)
        try {
            BrarchiveCodec.readBounded(byteArrayOf(1, 2, 3).inputStream(), 2L)
            throw AssertionError("Input above the limit was accepted")
        } catch (_: IllegalArgumentException) {
        }
    }
}

