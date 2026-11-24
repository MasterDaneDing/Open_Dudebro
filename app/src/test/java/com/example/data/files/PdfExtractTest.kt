package com.example.data.files

import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PdfExtractTest {

  private fun buildFlatePdfWithText(text: String): ByteArray {
    val contentStream = "BT /F1 12 Tf 72 720 Td ($text) Tj ET".toByteArray(StandardCharsets.US_ASCII)
    val deflater = Deflater()
    deflater.setInput(contentStream)
    deflater.finish()
    val compressedOut = ByteArrayOutputStream()
    val buf = ByteArray(256)
    while (!deflater.finished()) {
      val n = deflater.deflate(buf)
      compressedOut.write(buf, 0, n)
    }
    deflater.end()
    val compressed = compressedOut.toByteArray()

    val out = ByteArrayOutputStream()
    out.write("%PDF-1.4\n".toByteArray(StandardCharsets.US_ASCII))
    out.write("1 0 obj << /Length ${compressed.size} /Filter /FlateDecode >>\nstream\n".toByteArray(StandardCharsets.US_ASCII))
    out.write(compressed)
    out.write("\nendstream\nendobj\n%%EOF".toByteArray(StandardCharsets.US_ASCII))
    return out.toByteArray()
  }

  @Test
  fun extractsFlateDecodeText() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val reader = DeviceAttachmentReader(ctx)
    val m = DeviceAttachmentReader::class.java.getDeclaredMethod("extractPdfText", ByteArray::class.java)
    m.isAccessible = true
    val pdfBytes = buildFlatePdfWithText("Invoice 12345 Total 99.00")
    val out = m.invoke(reader, pdfBytes) as String
    assertTrue("expected 12345, got: $out", out.contains("12345"))
  }
}
