package com.angus.xliff.convert;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public class XliffXmlTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void acceptsMoreThanOldLimitAndPreservesEscapedText() throws Exception {
        String xml = document(repeat("&amp;", 100001) + "&amp;&lt;繁體中文&#x1F600;", "needs-translation");
        assertEquals(repeat("&", 100001) + "&<繁體中文\uD83D\uDE00", parse(xml).getElementsByTagName("source").item(0).getTextContent());
    }

    @Test public void acceptsExactlyFiveHundredThousandReferences() throws Exception {
        assertEquals(500000, parse(document(repeat("&amp;", 500000), "final"))
                .getElementsByTagName("source").item(0).getTextContent().length());
    }

    @Test public void stillRejectsReferencesOverTheLimit() throws Exception {
        SAXParseException error = assertThrows(SAXParseException.class,
                () -> parse(document(repeat("&amp;", 500001), "final")));
        assertTrue(error.getMessage(), error.getMessage().contains("JAXP00010003")
                || error.getMessage().contains("JAXP00010004"));
    }

    @Test public void ordinaryTextIsNotLimitedToFiveHundredThousandCharacters() throws Exception {
        assertEquals(600000, parse(document(repeat("A", 600000), "final"))
                .getElementsByTagName("source").item(0).getTextContent().length());
    }

    @Test public void rejectsExternalEntities() throws Exception {
        Path secret = temporary.newFile("external.txt").toPath();
        Files.write(secret, "must-not-be-read".getBytes(StandardCharsets.UTF_8));
        assertThrows(SAXParseException.class, () -> parse("<!DOCTYPE xliff [<!ENTITY ext SYSTEM \""
                + secret.toUri() + "\">]>" + document("&ext;", "final")));
    }

    @Test public void rejectsNestedEntityExpansion() {
        assertThrows(SAXParseException.class, () -> parse("<!DOCTYPE xliff [<!ENTITY a 'abc'>"
                + "<!ENTITY b '&a;&a;&a;'>]>" + document("&b;", "final")));
    }

    @Test public void allThreeEntryPointsHandleLargeEscapedDocuments() throws Exception {
        Path input = temporary.newFile("large.xliff").toPath();
        Files.write(input, document(repeat("&amp;", 500000), "needs-translation").getBytes(StandardCharsets.UTF_8));
        XmlSourceExtractor.main(new String[] {input.toString()});
        Path extracted = java.nio.file.Paths.get(input + Constans.SOURCE_FILE_NAME_POSTFIX);
        String expected = "<source id=\"1\">" + repeat("&", 500000) + "</source>\n";
        assertEquals(expected, new String(Files.readAllBytes(extracted), StandardCharsets.UTF_8));

        Files.write(input, document(repeat("&amp;", 500000), "final").getBytes(StandardCharsets.UTF_8));
        XmlSourceFinalExtractor.main(new String[] {input.toString()});
        assertEquals(expected, new String(Files.readAllBytes(extracted), StandardCharsets.UTF_8));

        Path translated = java.nio.file.Paths.get(input + Constans.TARGET_FILE_NAME_POSTFIX);
        Files.write(translated, "<source id=\"1\">更新譯文${NEW_LINE}第二段</source>\n".getBytes(StandardCharsets.UTF_8));
        UpdateXliffTarget.main(new String[] {input.toString()});
        Document updated = XliffXml.newDocumentBuilder().parse(new java.io.File(UpdateXliffTarget.getUpdatedFilePath()));
        assertEquals("更新譯文\n第二段", updated.getElementsByTagName("target").item(0).getTextContent());
        assertEquals("translated", updated.getElementsByTagName("target").item(0).getAttributes().getNamedItem("state").getNodeValue());
        assertEquals(500000, updated.getElementsByTagName("source").item(0).getTextContent().length());
    }

    @Test public void parserDoesNotChangeJvmWideLimits() throws Exception {
        String key = "jdk.xml.maxGeneralEntitySizeLimit";
        String before = System.getProperty(key);
        XliffXml.newDocumentBuilder();
        assertEquals(before, System.getProperty(key));
        // A fresh unconfigured factory must retain the original runtime settings.
        assertNotNull(DocumentBuilderFactory.newInstance().newDocumentBuilder());
    }

    private static Document parse(String xml) throws Exception {
        return XliffXml.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String document(String source, String state) {
        return "<xliff version=\"1.2\" xmlns=\"urn:oasis:names:tc:xliff:document:1.2\"><file><body>"
                + "<trans-unit id=\"1\"><source>" + source + "</source><target state=\"" + state
                + "\">old</target></trans-unit></body></file></xliff>";
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }
}
