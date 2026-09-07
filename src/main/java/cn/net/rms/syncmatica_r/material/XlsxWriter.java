package cn.net.rms.syncmatica_r.material;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Minimal XLSX writer for the material export table. Produces a single styled
 * sheet from plain OOXML parts so the mod does not need to shade Apache POI.
 */
final class XlsxWriter {

    static final int STYLE_HEADER = 0;
    static final int STYLE_EVEN = 1;
    static final int STYLE_ODD = 2;
    static final int STYLE_FINISHED = 3;
    static final int STYLE_CLAIMED = 4;

    // Fill colors matching the IndexedColors the previous POI export used.
    private static final String[] FILL_RGB = {
            "FF404040", // header: grey 80 percent
            "FFC0C0C0", // even rows: grey 25 percent
            "FFFFFFFF", // odd rows: white
            "FF00FF00", // finished: light green
            "FFFFFF00"  // claimed: light yellow
    };

    private static final int MIN_COLUMN_WIDTH = 10;
    private static final int MAX_COLUMN_WIDTH = 60;

    private final String sheetName;
    private final int columnCount;
    private final List<Cell[]> rows = new ArrayList<>();
    private final int[] columnWidths;
    private Cell[] currentRow;

    XlsxWriter(final String sheetName, final int columnCount) {
        this.sheetName = sanitizeSheetName(sheetName);
        this.columnCount = columnCount;
        this.columnWidths = new int[columnCount];
    }

    void startRow() {
        currentRow = new Cell[columnCount];
        rows.add(currentRow);
    }

    void textCell(final int column, final String value, final int style) {
        currentRow[column] = Cell.text(value, style);
        widen(column, value);
    }

    void numberCell(final int column, final long value, final int style) {
        final String digits = Long.toString(value);
        currentRow[column] = Cell.numeric(digits, style);
        widen(column, digits);
    }

    void writeTo(final OutputStream out) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            put(zip, "[Content_Types].xml", contentTypes());
            put(zip, "_rels/.rels", rootRels());
            put(zip, "xl/workbook.xml", workbook());
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels());
            put(zip, "xl/styles.xml", styles());
            put(zip, "xl/worksheets/sheet1.xml", worksheet());
        }
    }

    private void widen(final int column, final String value) {
        final int width = displayWidth(value);
        if (width > columnWidths[column]) {
            columnWidths[column] = width;
        }
    }

    private String worksheet() {
        final StringBuilder sb = new StringBuilder(1024 + rows.size() * 64);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<cols>");
        for (int c = 0; c < columnCount; c++) {
            final int width = Math.max(MIN_COLUMN_WIDTH, Math.min(MAX_COLUMN_WIDTH, columnWidths[c] + 2));
            sb.append("<col min=\"").append(c + 1)
                    .append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(width)
                    .append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            sb.append("<row r=\"").append(r + 1).append("\">");
            final Cell[] cells = rows.get(r);
            for (int c = 0; c < cells.length; c++) {
                final Cell cell = cells[c];
                if (cell == null) {
                    continue;
                }
                sb.append("<c r=\"").append(columnName(c)).append(r + 1)
                        .append("\" s=\"").append(cell.style).append('"');
                if (cell.numeric) {
                    sb.append("><v>").append(cell.value).append("</v></c>");
                } else {
                    sb.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                            .append(escape(cell.value))
                            .append("</t></is></c>");
                }
            }
            sb.append("</row>");
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    private String styles() {
        final StringBuilder sb = new StringBuilder(1024);
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
                .append("<fonts count=\"2\">")
                .append("<font><b/><color rgb=\"FFFFFFFF\"/></font>")
                .append("<font><color rgb=\"FF000000\"/></font>")
                .append("</fonts><fills count=\"").append(FILL_RGB.length + 2).append("\">")
                .append("<fill><patternFill patternType=\"none\"/></fill>")
                .append("<fill><patternFill patternType=\"gray125\"/></fill>");
        for (final String rgb : FILL_RGB) {
            sb.append("<fill><patternFill patternType=\"solid\"><fgColor rgb=\"").append(rgb)
                    .append("\"/></patternFill></fill>");
        }
        sb.append("</fills>")
                .append("<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>")
                .append("<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>")
                .append("<cellXfs count=\"").append(FILL_RGB.length).append("\">");
        for (int style = 0; style < FILL_RGB.length; style++) {
            // Style 0 pairs the bold white header font with the dark header fill.
            final int fontId = style == STYLE_HEADER ? 0 : 1;
            sb.append("<xf numFmtId=\"0\" fontId=\"").append(fontId)
                    .append("\" fillId=\"").append(style + 2)
                    .append("\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>");
        }
        return sb.append("</cellXfs>")
                .append("<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>")
                .append("</styleSheet>").toString();
    }

    private String workbook() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escape(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";
    }

    private String workbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
                + "</Relationships>";
    }

    private String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String contentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
                + "</Types>";
    }

    private static void put(final ZipOutputStream zip, final String name, final String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String columnName(final int column) {
        String name = "";
        int index = column;
        while (index >= 0) {
            name = (char) ('A' + index % 26) + name;
            index = index / 26 - 1;
        }
        return name;
    }

    private static String sanitizeSheetName(final String name) {
        final StringBuilder sb = new StringBuilder();
        for (final char c : name.toCharArray()) {
            if ("[]:*?/\\".indexOf(c) < 0) {
                sb.append(c);
            }
            if (sb.length() == 31) {
                break;
            }
        }
        return sb.length() == 0 ? "Sheet1" : sb.toString();
    }

    private static String escape(final String value) {
        final StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    // Approximate Excel column width in default-font characters; CJK and other
    // wide scripts render roughly two characters wide.
    private static int displayWidth(final String value) {
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            width += value.charAt(i) >= 0x2E80 ? 2 : 1;
        }
        return width;
    }

    private static final class Cell {
        private final String value;
        private final int style;
        private final boolean numeric;

        private Cell(final String value, final int style, final boolean numeric) {
            this.value = value;
            this.style = style;
            this.numeric = numeric;
        }

        private static Cell text(final String value, final int style) {
            return new Cell(value, style, false);
        }

        private static Cell numeric(final String digits, final int style) {
            return new Cell(digits, style, true);
        }
    }
}
