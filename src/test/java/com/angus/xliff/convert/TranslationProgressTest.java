package com.angus.xliff.convert;

import static org.junit.Assert.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TranslationProgressTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void restoresLegacy2050Of7093AndSelectsOnlyPendingIds() throws Exception {
        Path input = input();
        StringBuilder source = new StringBuilder();
        StringBuilder target = new StringBuilder("```xml\n");
        for (int i = 0; i < 7093; i++) source.append(row(i, "original"));
        for (int i = 5043; i < 7093; i++) target.append(row(i, "譯文"));
        target.append("```\n");
        source(input, source.toString());
        target(input, target.toString());
        TranslationProgress progress = TranslationProgress.open(input.toString());
        assertEquals(7093, progress.total());
        assertEquals(2050, progress.completedCount());
        assertEquals(50, progress.nextBatch(50).size());
        assertEquals("4993", progress.nextBatch(50).keySet().iterator().next());
        assertEquals(target.toString(), readTarget(input));
    }

    @Test public void retrySuccessIsWrittenOnlyOnceAndSurvivesRestart() throws Exception {
        Path input = input();
        source(input, row(1, "a") + row(2, "b") + row(3, "c"));
        target(input, row(3, "舊譯文"));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        LinkedHashMap<String, String> batch = progress.nextBatch(1);
        AtomicInteger calls = new AtomicInteger();
        int count = ChatGPTClient.processBatch(progress, batch, () -> {
            if (calls.incrementAndGet() == 1) return ChatGPTClient.readResponse(520, "test-secret");
            return row(2, "新譯文");
        }, ignored -> {}, ignored -> {});
        assertEquals(1, count);
        assertEquals(2, calls.get());
        assertEquals(2, progress.completedCount());
        assertEquals(row(3, "舊譯文") + row(2, "新譯文"), readTarget(input));
        TranslationProgress reopened = TranslationProgress.open(input.toString());
        assertEquals(2, reopened.completedCount());
        assertEquals(Arrays.asList("1"), new ArrayList<>(reopened.nextBatch(50).keySet()));
    }

    @Test public void exhaustedRetriesPreserveOutputBytesAndProgress() throws Exception {
        Path input = input();
        source(input, row(1, "a") + row(2, "b"));
        target(input, row(2, "saved"));
        byte[] before = Files.readAllBytes(output(input));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        assertThrows(IOException.class, () -> ChatGPTClient.processBatch(progress, progress.nextBatch(50),
                () -> ChatGPTClient.readResponse(520, "failed"), ignored -> {}, ignored -> {}));
        assertArrayEquals(before, Files.readAllBytes(output(input)));
        assertEquals(1, progress.completedCount());
        assertEquals(1, TranslationProgress.open(input.toString()).completedCount());
    }

    @Test public void rejectsPartialWrongOrDuplicateBatchBeforeWriting() throws Exception {
        Path input = input();
        source(input, row(1, "a") + row(2, "b"));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        for (String response : new String[] {row(1, "only-one"), row(99, "wrong"), row(1, "a") + row(1, "b"), "<source id=\"1\">partial"}) {
            assertThrows(IOException.class, () -> progress.commit(progress.nextBatch(50), response));
            assertEquals(0, progress.completedCount());
            assertFalse(Files.exists(output(input)));
        }
    }

    @Test public void acceptsReorderedAndMultilineRecordsAndNormalizesForMerge() throws Exception {
        Path input = input();
        source(input, row(1, "a") + row(2, "b"));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        progress.commit(progress.nextBatch(50), "```xml\n" + row(2, "第一段\n第二段") + row(1, "內容") + "```");
        assertEquals(2, progress.completedCount());
        assertTrue(readTarget(input).contains("第一段${NEW_LINE}第二段"));
        assertFalse(readTarget(input).contains("```"));
    }

    @Test public void changedSourceIsRejectedOnRestart() throws Exception {
        Path input = input();
        source(input, row(1, "a"));
        TranslationProgress.open(input.toString());
        source(input, row(1, "changed"));
        assertThrows(IOException.class, () -> TranslationProgress.open(input.toString()));
    }

    @Test public void changedSourceDuringRequestIsRejectedBeforeSaving() throws Exception {
        Path input = input();
        source(input, row(1, "a"));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> ChatGPTClient.processBatch(progress, progress.nextBatch(1), () -> {
            calls.incrementAndGet();
            source(input, row(1, "changed"));
            return row(1, "response");
        }, ignored -> fail("Source changes must not trigger requests"), ignored -> {}));
        assertEquals(1, calls.get());
        assertFalse(Files.exists(output(input)));
        assertEquals(0, progress.completedCount());
    }

    @Test public void outputChangedDuringRequestIsNotOverwrittenOrRetried() throws Exception {
        Path input = input();
        source(input, row(1, "a"));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> ChatGPTClient.processBatch(progress, progress.nextBatch(1), () -> {
            calls.incrementAndGet();
            target(input, row(1, "written-elsewhere"));
            return row(1, "response");
        }, ignored -> fail("Disk conflicts must not trigger requests"), ignored -> {}));
        assertEquals(1, calls.get());
        assertEquals(row(1, "written-elsewhere"), readTarget(input));
        assertEquals(0, progress.completedCount());
    }

    @Test public void diskFailureDoesNotAdvanceProgressOrRetryTheApi() throws Exception {
        Path input = input();
        source(input, row(1, "a"));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        Files.createDirectory(output(input));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IOException.class, () -> ChatGPTClient.processBatch(progress, progress.nextBatch(1), () -> {
            calls.incrementAndGet(); return row(1, "result");
        }, ignored -> fail("Must not retry"), ignored -> {}));
        assertEquals(1, calls.get());
        assertEquals(0, progress.completedCount());
    }

    @Test public void refusesCorruptedDuplicateOrForeignLegacyOutput() throws Exception {
        Path input = input();
        source(input, row(1, "a"));
        for (String text : new String[] {"<source id=\"1\">partial", row(1, "a") + row(1, "b"), row(99, "foreign")}) {
            target(input, text);
            assertThrows(IOException.class, () -> TranslationProgress.open(input.toString()));
            assertEquals(text, readTarget(input));
        }
    }

    @Test public void finishedTranslationHasNoPendingBatch() throws Exception {
        Path input = input();
        source(input, row(1, "a"));
        target(input, row(1, "done"));
        TranslationProgress progress = TranslationProgress.open(input.toString());
        assertEquals(progress.total(), progress.completedCount());
        assertTrue(progress.nextBatch(50).isEmpty());
    }

    @Test public void parserHandlesSingleQuotedIdsAndAdditionalAttributes() throws Exception {
        LinkedHashMap<String, String> records = TranslationProgress.parseRecords("<source translate='no' id='123'>text</source>");
        assertEquals(Arrays.asList("123"), new ArrayList<>(records.keySet()));
        assertEquals("text", TranslationProgress.content(records.get("123")));
    }

    private Path input() throws IOException { return temporary.newFile("file" + System.nanoTime() + ".xliff").toPath(); }
    private static Path output(Path input) { return Paths.get(input + Constans.TARGET_FILE_NAME_POSTFIX); }
    private static void source(Path input, String text) throws IOException { Files.write(Paths.get(input + Constans.SOURCE_FILE_NAME_POSTFIX), text.getBytes(StandardCharsets.UTF_8)); }
    private static void target(Path input, String text) throws IOException { Files.write(output(input), text.getBytes(StandardCharsets.UTF_8)); }
    private static String readTarget(Path input) throws IOException { return new String(Files.readAllBytes(output(input)), StandardCharsets.UTF_8); }
    private static String row(int id, String text) { return "<source id=\"" + id + "\">" + text + "</source>\n"; }
}
