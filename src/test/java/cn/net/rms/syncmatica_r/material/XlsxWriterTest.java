package cn.net.rms.syncmatica_r.material;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxWriterTest {

    @Test
    void writesStyledSheetWithEscapedText() throws IOException {
        final XlsxWriter writer = new XlsxWriter("Materials", 5);
        writer.startRow();
        writer.textCell(0, "材料 <A>&\"B\"", XlsxWriter.STYLE_HEADER);
        writer.numberCell(1, 42, XlsxWriter.STYLE_HEADER);
        writer.startRow();
        writer.textCell(0, "stone", XlsxWriter.STYLE_EVEN);
        writer.numberCell(3, 7, XlsxWriter.STYLE_CLAIMED);
        writer.textCell(4, "alice, bob", XlsxWriter.STYLE_EVEN);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeTo(out);
        final Map<String, String> entries = unzip(out.toByteArray());

        final String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertTrue(sheet.contains("t=\"inlineStr\""));
        assertTrue(sheet.contains("材料 &lt;A&gt;&amp;&quot;B&quot;"));
        assertTrue(sheet.contains("<v>42</v>"));
        assertTrue(sheet.contains("r=\"B1\""));
        assertTrue(sheet.contains("s=\"4\""));
        assertTrue(sheet.contains("<cols>"));
        assertTrue(sheet.contains("customWidth=\"1\""));

        final String styles = entries.get("xl/styles.xml");
        assertTrue(styles.contains("<b/>"));
        assertTrue(styles.contains("rgb=\"FF404040\""));
        assertTrue(styles.contains("rgb=\"FF00FF00\""));
        assertTrue(styles.contains("rgb=\"FFFFFF00\""));

        final String workbook = entries.get("xl/workbook.xml");
        assertTrue(workbook.contains("name=\"Materials\""));

        final String contentTypes = entries.get("[Content_Types].xml");
        assertTrue(contentTypes.contains("/xl/workbook.xml"));
    }

    @Test
    void sanitizesIllegalSheetNames() throws IOException {
        final XlsxWriter writer = new XlsxWriter("a[b]:*?/\\c", 1);
        writer.startRow();
        writer.textCell(0, "x", XlsxWriter.STYLE_ODD);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeTo(out);
        assertTrue(unzip(out.toByteArray()).get("xl/workbook.xml").contains("name=\"abc\""));
    }

    @Test
    void computesColumnWidthFromContent() throws IOException {
        final XlsxWriter writer = new XlsxWriter("Materials", 2);
        writer.startRow();
        writer.textCell(0, "a", XlsxWriter.STYLE_ODD);
        writer.textCell(1, "b", XlsxWriter.STYLE_ODD);
        writer.startRow();
        writer.textCell(0, "很长的中文材料名称很长的中文材料名称", XlsxWriter.STYLE_ODD);

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.writeTo(out);
        final String sheet = unzip(out.toByteArray()).get("xl/worksheets/sheet1.xml");
        // The CJK row counts double-width characters, so column A must end up
        // wider than the single-character column B.
        // 18 CJK characters count double-width (36) plus padding of 2.
        assertTrue(sheet.contains("<col min=\"1\" max=\"1\" width=\"38\""));
        assertTrue(sheet.contains("<col min=\"2\" max=\"2\" width=\"10\""));
    }

    private static Map<String, String> unzip(final byte[] bytes) throws IOException {
        final Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                final ByteArrayOutputStream content = new ByteArrayOutputStream();
                final byte[] buffer = new byte[4096];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    content.write(buffer, 0, read);
                }
                entries.put(entry.getName(), content.toString(StandardCharsets.UTF_8));
            }
        }
        assertEquals(6, entries.size());
        return entries;
    }
}
