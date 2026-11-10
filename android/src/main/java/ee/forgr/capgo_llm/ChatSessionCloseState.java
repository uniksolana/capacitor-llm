package ee.forgr.capgo_llm;

/**
 * Tracks independently completed parts of a chat-session cleanup.
 * A successful stage is never repeated when a later stage fails and cleanup
 * is retried. A failed stage remains eligible for retry.
 */
final class ChatSessionCloseState {

    interface CloseAction {
        void run() throws Exception;
    }

    private boolean conversationClosed;
    private boolean backendSessionClosed;

    synchronized void close(CloseAction conversationAction, CloseAction backendAction) throws Exception {
        if (!conversationClosed) {
            conversationAction.run();
            conversationClosed = true;
        }
        if (!backendSessionClosed) {
            backendAction.run();
            backendSessionClosed = true;
        }
    }
}
