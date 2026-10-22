package com.blockline.app.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class BrarchiveEntry(
    val name: String,
    val content: ByteArray,
    val isUtf8Text: Boolean,
    val isMcbBinary: Boolean
)

object BrarchiveCodec {
    const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L
    const val MAX_ENTRIES = 100_000
    private const val HEADER_SIZE = 16
    private const val ENTRY_SIZE = 256
    private const val NAME_CAPACITY = 247
    private val MAGIC = byteArrayOf(0x7d, 0x27, 0x25, 0xb1.toByte(), 0xa0.toByte(), 0x52, 0x70, 0x26)

    fun readBounded(input: InputStream, maxBytes: Long = MAX_ARCHIVE_BYTES): ByteArray {
        require(maxBytes >= 0L) { "Read limit must not be negative." }
        val output = ByteArrayOutputStream(minOf(maxBytes, 32_768L).toInt())
        val buffer = ByteArray(8_192)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            require(total <= maxBytes) { "Input exceeds the safe limit of " + maxBytes + " bytes." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    fun decode(bytes: ByteArray): List<BrarchiveEntry> {
        require(bytes.size >= HEADER_SIZE) { "Archive is truncated: expected a 16-byte header." }
        require(bytes.size.toLong() <= MAX_ARCHIVE_BYTES) { "Archive exceeds the safe size limit." }
        for (index in MAGIC.indices) {
            require(bytes[index] == MAGIC[index]) { "Invalid .brarchive magic number." }
        }

        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val count = Integer.toUnsignedLong(header.getInt(8))
        val version = Integer.toUnsignedLong(header.getInt(12))
        require(version == 1L) { "Unsupported .brarchive version: " + version + "." }
        require(count <= MAX_ENTRIES.toLong()) { "Archive contains too many entries." }

        val contentStartLong = HEADER_SIZE.toLong() + count * ENTRY_SIZE.toLong()
        require(contentStartLong <= bytes.size.toLong()) { "Archive entry table is truncated." }
        val result = ArrayList<BrarchiveEntry>(count.toInt())
        var combinedContentBytes = 0L

        repeat(count.toInt()) { index ->
            val descriptor = HEADER_SIZE + index * ENTRY_SIZE
            val nameLength = bytes[descriptor].toInt() and 0xff
            require(nameLength <= NAME_CAPACITY) { "Entry " + index + " has an invalid name length (" + nameLength + ")." }
            val nameBytes = bytes.copyOfRange(descriptor + 1, descriptor + 1 + nameLength)
            val name = decodeUtf8(nameBytes)
                ?: throw IllegalArgumentException("Entry " + index + " has a malformed UTF-8 filename.")
            require(!name.contains('\u0000')) { "Entry " + index + " filename contains a NUL character." }

            val offset = Integer.toUnsignedLong(header.getInt(descriptor + 248))
            val length = Integer.toUnsignedLong(header.getInt(descriptor + 252))
            val start = contentStartLong + offset
            val end = start + length
            require(start >= contentStartLong && end >= start && end <= bytes.size.toLong()) {
                "Entry '" + name + "' points outside the archive content section."
            }
            combinedContentBytes += length
            require(combinedContentBytes <= MAX_ARCHIVE_BYTES) {
                "Archive declares too much combined entry data."
            }

            result += createEntry(name, bytes.copyOfRange(start.toInt(), end.toInt()))
        }
        return result
    }

    fun createEntry(name: String, content: ByteArray): BrarchiveEntry {
        require(!name.contains('\u0000')) { "Entry filename cannot contain a NUL character." }
        return BrarchiveEntry(
            name = name,
            content = content,
            isUtf8Text = decodeUtf8(content) != null,
            isMcbBinary = content.size >= 4 &&
                content[0] == 0x7f.toByte() && content[1] == 0x4d.toByte() &&
                content[2] == 0x43.toByte() && content[3] == 0x42.toByte()
        )
    }

    fun textContent(entry: BrarchiveEntry): String? = decodeUtf8(entry.content)

    fun encode(entries: List<BrarchiveEntry>): ByteArray {
        require(entries.size <= MAX_ENTRIES) { "Too many archive entries." }
        val names = entries.map { entry ->
            require(!entry.name.contains('\u0000')) { "Entry filename cannot contain a NUL character." }
            val encoded = StandardCharsets.UTF_8.encode(entry.name)
            val nameBytes = ByteArray(encoded.remaining())
            encoded.get(nameBytes)
            require(nameBytes.size <= NAME_CAPACITY) {
                "Entry filename exceeds " + NAME_CAPACITY + " UTF-8 bytes: " + entry.name
            }
            nameBytes
        }
        var payloadLength = 0L
        entries.forEach { entry ->
            payloadLength += entry.content.size.toLong()
            require(payloadLength <= MAX_ARCHIVE_BYTES) { "Archive content exceeds the safe size limit." }
        }
        val totalLength = HEADER_SIZE.toLong() + entries.size.toLong() * ENTRY_SIZE + payloadLength
        require(totalLength <= MAX_ARCHIVE_BYTES && totalLength <= Int.MAX_VALUE) {
            "Archive exceeds the safe size limit."
        }

        val bytes = ByteArray(totalLength.toInt())
        System.arraycopy(MAGIC, 0, bytes, 0, MAGIC.size)
        val output = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        output.putInt(8, entries.size)
        output.putInt(12, 1)
        val contentStart = HEADER_SIZE + entries.size * ENTRY_SIZE
        var contentOffset = 0
        entries.forEachIndexed { index, entry ->
            val descriptor = HEADER_SIZE + index * ENTRY_SIZE
            val nameBytes = names[index]
            bytes[descriptor] = nameBytes.size.toByte()
            System.arraycopy(nameBytes, 0, bytes, descriptor + 1, nameBytes.size)
            output.putInt(descriptor + 248, contentOffset)
            output.putInt(descriptor + 252, entry.content.size)
            System.arraycopy(entry.content, 0, bytes, contentStart + contentOffset, entry.content.size)
            contentOffset += entry.content.size
        }
        return bytes
    }

    fun encodeZip(entries: List<BrarchiveEntry>): ByteArray {
        require(entries.size <= MAX_ENTRIES) { "Too many archive entries for ZIP export." }
        val names = HashSet<String>()
        var totalContent = 0L
        entries.forEach { entry ->
            val name = entry.name
            require(name.isNotBlank() && name != "." && name != ".." &&
                !name.contains('/') && !name.contains('\\') && !name.contains('\u0000') &&
                !name.contains(':') && !name.startsWith('/')
            ) { "Unsafe ZIP entry name: " + name }
            require(names.add(name.lowercase(Locale.ROOT))) {
                "ZIP export cannot preserve duplicate entry names safely: " + name
            }
            totalContent += entry.content.size.toLong()
            require(totalContent <= MAX_ARCHIVE_BYTES) { "ZIP contents exceed the safe size limit." }
        }

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { entry ->
                zip.putNextEntry(ZipEntry(entry.name))
                zip.write(entry.content)
                zip.closeEntry()
            }
        }
        val bytes = output.toByteArray()
        require(bytes.size.toLong() <= MAX_ARCHIVE_BYTES) { "ZIP output exceeds the safe size limit." }
        return bytes
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}

