package ee.forgr.capgo_llm;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ModelDownloadFilesTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void removesAgathiaStalePartialFilesAndPreservesCompletedFiles() throws Exception {
        File directory = temporaryFolder.getRoot();
        File stale = new File(directory, "agathia-gemma.litertlm.123.part");
        File unrelated = new File(directory, "agathia-other-model.456.part");
        File completed = new File(directory, "gemma.litertlm");
        Files.write(stale.toPath(), "partial".getBytes(StandardCharsets.UTF_8));
        Files.write(unrelated.toPath(), "keep".getBytes(StandardCharsets.UTF_8));
        Files.write(completed.toPath(), "complete".getBytes(StandardCharsets.UTF_8));

        ModelDownloadFiles.cleanupStaleTemps(directory);

        assertFalse(stale.exists());
        assertFalse(unrelated.exists());
        assertTrue(completed.exists());
    }

    @Test
    public void allowsOnlySafeHttpAndHttpsRedirectTransitions() {
        assertTrue(ModelDownloadFiles.isAllowedRedirectProtocol("http", "http"));
        assertTrue(ModelDownloadFiles.isAllowedRedirectProtocol("http", "https"));
        assertTrue(ModelDownloadFiles.isAllowedRedirectProtocol("https", "https"));
        assertFalse(ModelDownloadFiles.isAllowedRedirectProtocol("https", "http"));
        assertFalse(ModelDownloadFiles.isAllowedRedirectProtocol("https", "file"));
        assertFalse(ModelDownloadFiles.isAllowedRedirectProtocol("ftp", "https"));
    }

    @Test
    public void reusesOnlyReadableRegularFilesWithTheExactExpectedSize() throws Exception {
        File exact = new File(temporaryFolder.getRoot(), "embedding.litertlm");
        Files.write(exact.toPath(), "12345".getBytes(StandardCharsets.UTF_8));
        File shortFile = new File(temporaryFolder.getRoot(), "short.litertlm");
        Files.write(shortFile.toPath(), "1234".getBytes(StandardCharsets.UTF_8));
        File directory = new File(temporaryFolder.getRoot(), "directory.litertlm");
        assertTrue(directory.mkdir());

        assertTrue(ModelDownloadFiles.isCompleteExistingModel(exact, 5L));
        assertFalse(ModelDownloadFiles.isCompleteExistingModel(exact, 4L));
        assertFalse(ModelDownloadFiles.isCompleteExistingModel(shortFile, 5L));
        assertFalse(ModelDownloadFiles.isCompleteExistingModel(directory, 5L));
        assertFalse(ModelDownloadFiles.isCompleteExistingModel(null, 5L));
        assertFalse(ModelDownloadFiles.isCompleteExistingModel(exact, 0L));
    }

    @Test
    public void enforcesDownloadByteLimitWithoutOverflow() {
        assertTrue(ModelDownloadFiles.isWithinDownloadLimit(0L, 64 * 1024, 128L * 1024L));
        assertTrue(ModelDownloadFiles.isWithinDownloadLimit(900L, 124, 1024L));
        assertFalse(ModelDownloadFiles.isWithinDownloadLimit(900L, 125, 1024L));
        assertFalse(ModelDownloadFiles.isWithinDownloadLimit(Long.MAX_VALUE, 1, Long.MAX_VALUE));
        assertFalse(ModelDownloadFiles.isWithinDownloadLimit(-1L, 1, 1024L));
        assertFalse(ModelDownloadFiles.isWithinDownloadLimit(0L, -1, 1024L));
        assertFalse(ModelDownloadFiles.isWithinDownloadLimit(0L, 1, 0L));
    }

    @Test
    public void safelyIgnoresInvalidCleanupArguments() throws java.io.IOException {
        ModelDownloadFiles.cleanupStaleTemps(null);
    }
}
