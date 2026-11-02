package com.angus.xliff.convert;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xml.sax.SAXException;

public class XmlSourceExtractorTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void consecutiveCompleteWorkflowsReportEachFilesOwnCount() throws Exception {
        runWorkflow(input("first.xliff", 3), 3);
        runWorkflow(input("second.xliff", 1), 1);
    }

    @Test public void repeatedExtractionAndEmptyFileDoNotAccumulate() throws Exception {
        Path input = input("repeated.xliff", 2);
        XmlSourceExtractor.main(new String[] {input.toString()});
        assertEquals(2, XmlSourceExtractor.getExtractedCount());
        byte[] first = Files.readAllBytes(Paths.get(input + Constans.SOURCE_FILE_NAME_POSTFIX));
        XmlSourceExtractor.main(new String[] {input.toString()});
        assertEquals(2, XmlSourceExtractor.getExtractedCount());
        assertArrayEquals(first, Files.readAllBytes(Paths.get(input + Constans.SOURCE_FILE_NAME_POSTFIX)));
        XmlSourceExtractor.main(new String[] {input("empty.xliff", 0).toString()});
        assertEquals(0, XmlSourceExtractor.getExtractedCount());
    }

    @Test public void failedExtractionClearsPreviousCountAndNextRunStartsFresh() throws Exception {
        XmlSourceExtractor.main(new String[] {input("good.xliff", 2).toString()});
        assertEquals(2, XmlSourceExtractor.getExtractedCount());
        Path invalid = temporary.newFile("invalid.xliff").toPath();
        Files.write(invalid, "<xliff>".getBytes(StandardCharsets.UTF_8));
        assertThrows(SAXException.class, () -> XmlSourceExtractor.main(new String[] {invalid.toString()}));
        assertEquals(0, XmlSourceExtractor.getExtractedCount());
        XmlSourceExtractor.main(new String[] {input("next.xliff", 1).toString()});
        assertEquals(1, XmlSourceExtractor.getExtractedCount());
    }

    private static void runWorkflow(Path input, int expected) throws Exception {
        XmlSourceExtractor.main(new String[] {input.toString()});
        assertEquals(expected, XmlSourceExtractor.getExtractedCount());
        TranslationProgress progress = TranslationProgress.open(input.toString());
        assertEquals(expected, progress.total());
        LinkedHashMap<String, String> batch = progress.nextBatch(50);
        StringBuilder translated = new StringBuilder();
        for (String id : batch.keySet()) translated.append("<source id=\"").append(id).append("\">translated</source>\n");
        assertEquals(expected, ChatGPTClient.processBatch(progress, batch, translated::toString,
                ignored -> fail("No retry expected"), ignored -> {}));
        UpdateXliffTarget.main(new String[] {input.toString()});
        assertEquals(expected, UpdateXliffTarget.getUpdatedCount());
        assertEquals(expected, XliffXml.newDocumentBuilder()
                .parse(Paths.get(UpdateXliffTarget.getUpdatedFilePath()).toFile())
                .getElementsByTagName("target").getLength());
    }

    private Path input(String name, int count) throws Exception {
        Path input = temporary.newFile(name).toPath();
        StringBuilder xml = new StringBuilder("<xliff version=\"1.2\"><file><body>");
        for (int i = 0; i < count; i++) xml.append("<trans-unit id=\"").append(i)
                .append("\"><source>original</source><target state=\"needs-translation\"/></trans-unit>");
        xml.append("</body></file></xliff>");
        Files.write(input, xml.toString().getBytes(StandardCharsets.UTF_8));
        return input;
    }
}
