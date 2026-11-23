package com.example.data.files

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [33])
class PdfExtractTest {
  @Test fun extractsFlateDecodeText() {
    val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    val reader = DeviceAttachmentReader(ctx)
    val m = DeviceAttachmentReader::class.java.getDeclaredMethod("extractPdfText", ByteArray::class.java)
    m.isAccessible = true
    val out = m.invoke(reader, File("/tmp/t.pdf").readBytes()) as String
    println("EXTRACTED>>>" + out + "<<<")
    assertTrue("expected 12345, got: $out", out.contains("12345"))
  }
}
