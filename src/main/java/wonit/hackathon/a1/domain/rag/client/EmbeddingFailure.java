package wonit.hackathon.a1.domain.rag.client;

/** Safe diagnostic produced locally; never include upstream messages or causes. */
public final class EmbeddingFailure extends IllegalStateException {
    public EmbeddingFailure(String message) {
        super(message);
    }
}
