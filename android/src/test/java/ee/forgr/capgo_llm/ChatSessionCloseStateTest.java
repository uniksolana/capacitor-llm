package ee.forgr.capgo_llm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ChatSessionCloseStateTest {

    @Test
    public void retriesFailedConversationClose() throws Exception {
        ChatSessionCloseState state = new ChatSessionCloseState();
        int[] conversationAttempts = { 0 };
        int[] backendAttempts = { 0 };

        try {
            state.close(
                () -> {
                    conversationAttempts[0]++;
                    throw new IllegalStateException("native close failed");
                },
                () -> backendAttempts[0]++
            );
            fail("Expected the native close failure to propagate");
        } catch (IllegalStateException expected) {
            assertEquals("native close failed", expected.getMessage());
        }

        state.close(
            () -> conversationAttempts[0]++,
            () -> backendAttempts[0]++
        );

        assertEquals(2, conversationAttempts[0]);
        assertEquals(1, backendAttempts[0]);
    }

    @Test
    public void successfulConversationCloseIsNotRepeatedWhenBackendCloseFails() throws Exception {
        ChatSessionCloseState state = new ChatSessionCloseState();
        int[] conversationAttempts = { 0 };
        int[] backendAttempts = { 0 };

        try {
            state.close(
                () -> conversationAttempts[0]++,
                () -> {
                    backendAttempts[0]++;
                    throw new IllegalStateException("backend close failed");
                }
            );
            fail("Expected the backend close failure to propagate");
        } catch (IllegalStateException expected) {
            assertEquals("backend close failed", expected.getMessage());
        }

        state.close(
            () -> conversationAttempts[0]++,
            () -> backendAttempts[0]++
        );

        assertEquals(1, conversationAttempts[0]);
        assertEquals(2, backendAttempts[0]);
    }

    @Test
    public void successfulCleanupStagesRunOnlyOnce() throws Exception {
        ChatSessionCloseState state = new ChatSessionCloseState();
        int[] conversationAttempts = { 0 };
        int[] backendAttempts = { 0 };

        state.close(
            () -> conversationAttempts[0]++,
            () -> backendAttempts[0]++
        );
        state.close(
            () -> conversationAttempts[0]++,
            () -> backendAttempts[0]++
        );

        assertEquals(1, conversationAttempts[0]);
        assertEquals(1, backendAttempts[0]);
    }
}
