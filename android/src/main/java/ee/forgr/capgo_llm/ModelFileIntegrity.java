package ee.forgr.capgo_llm;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class ModelFileIntegrity {
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private ModelFileIntegrity() {}

    static byte[] parseExpectedSha256(String expectedSha256) {
        if (expectedSha256 == null || !expectedSha256.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                "MODEL_CHECKSUM_UNAVAILABLE: expected SHA-256 must contain exactly 64 hexadecimal characters"
            );
        }

        byte[] result = new byte[32];
        for (int i = 0; i < result.length; i++) {
            int offset = i * 2;
            result[i] = (byte) Integer.parseInt(expectedSha256.substring(offset, offset + 2), 16);
        }
        return result;
    }

    static MessageDigest newSha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("MODEL_CHECKSUM_UNAVAILABLE: SHA-256 is not available on this device", exception);
        }
    }

    static boolean matches(byte[] actualDigest, byte[] expectedDigest) {
        return actualDigest != null
            && expectedDigest != null
            && MessageDigest.isEqual(actualDigest, expectedDigest);
    }

    static void verify(File modelFile, String expectedSha256) throws IOException {
        final byte[] expected;
        try {
            expected = parseExpectedSha256(expectedSha256);
        } catch (IllegalArgumentException exception) {
            throw new IOException(exception.getMessage(), exception);
        }

        if (modelFile == null || !modelFile.isFile() || !modelFile.canRead()) {
            throw new IOException("MODEL_CHECKSUM_UNAVAILABLE: model file is missing or unreadable");
        }

        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (FileInputStream input = new FileInputStream(modelFile)) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        } catch (IOException exception) {
            throw new IOException("MODEL_CHECKSUM_UNAVAILABLE: could not read the model file for verification", exception);
        }

        if (!matches(digest.digest(), expected)) {
            throw new IOException("MODEL_CHECKSUM_MISMATCH: model SHA-256 does not match the pinned artifact");
        }
    }

    static String toHex(byte[] digest) {
        if (digest == null) return "";
        char[] output = new char[digest.length * 2];
        for (int i = 0; i < digest.length; i++) {
            int value = digest[i] & 0xff;
            output[i * 2] = HEX[value >>> 4];
            output[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(output);
    }
}
