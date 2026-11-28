package ee.forgr.capgo_llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ModelFileIntegrityTest {
    private static final String SHA256_ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void verifiesCorrectFileUsingStreamingDigest() throws Exception {
        File file = writeModel("abc");
        ModelFileIntegrity.verify(file, SHA256_ABC);
        ModelFileIntegrity.verify(file, SHA256_ABC.toUpperCase());
    }

    @Test
    public void rejectsSameFileWhenOneByteChanges() throws Exception {
        File file = writeModel("abd");
        try {
            ModelFileIntegrity.verify(file, SHA256_ABC);
            fail("Expected checksum mismatch");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("MODEL_CHECKSUM_MISMATCH"));
        }
    }

    @Test
    public void rejectsTruncatedFile() throws Exception {
        File file = writeModel("ab");
        try {
            ModelFileIntegrity.verify(file, SHA256_ABC);
            fail("Expected truncated file rejection");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("MODEL_CHECKSUM_MISMATCH"));
        }
    }

    @Test
    public void rejectsMissingOrMalformedExpectedDigest() throws Exception {
        File file = writeModel("abc");
        for (String invalid : new String[] { null, "", "1234", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz" }) {
            try {
                ModelFileIntegrity.verify(file, invalid);
                fail("Expected invalid digest rejection");
            } catch (IOException exception) {
                assertTrue(exception.getMessage().contains("MODEL_CHECKSUM_UNAVAILABLE"));
            }
        }
    }

    @Test
    public void rejectsMissingModelFile() throws Exception {
        File missing = new File(temporaryFolder.getRoot(), "missing.litertlm");
        try {
            ModelFileIntegrity.verify(missing, SHA256_ABC);
            fail("Expected missing file rejection");
        } catch (IOException exception) {
            assertTrue(exception.getMessage().contains("MODEL_CHECKSUM_UNAVAILABLE"));
        }
    }

    @Test
    public void encodesDigestAsLowercaseHex() throws Exception {
        File file = writeModel("abc");
        byte[] expected = ModelFileIntegrity.parseExpectedSha256(SHA256_ABC);
        assertEquals(SHA256_ABC, ModelFileIntegrity.toHex(expected));
    }

    private File writeModel(String value) throws Exception {
        File file = temporaryFolder.newFile("model.litertlm");
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }
}
