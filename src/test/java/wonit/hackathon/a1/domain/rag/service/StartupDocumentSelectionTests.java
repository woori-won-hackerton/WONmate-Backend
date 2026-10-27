package wonit.hackathon.a1.domain.rag.service;

import java.nio.file.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wonit.hackathon.a1.domain.rag.ArticleChunker;
import wonit.hackathon.a1.domain.rag.PdfExtractor;
import wonit.hackathon.a1.domain.rag.client.EmbeddingClient;
import wonit.hackathon.a1.domain.rag.repository.DocumentIngestionRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StartupDocumentSelectionTests {
    @TempDir Path root;

    @Test void newAndChangedDocumentsEmbedButUnchangedCompletedDocumentsDoNot() throws Exception {
        Path dir = Files.createDirectory(root.resolve("product_01"));
        Path pdf = dir.resolve("sample.pdf");
        Files.writeString(pdf, "first PDF content");
        Files.writeString(dir.resolve("metadata.json"), """
                {"productId":null,"productName":"Sample","documents":[
                  {"fileName":"sample.pdf","documentType":"TERMS"}]}
                """);
        var repository = mock(DocumentIngestionRepository.class);
        var extractor = mock(PdfExtractor.class);
        var embeddings = mock(EmbeddingClient.class);
        when(repository.resolveProduct(null, "Sample")).thenReturn(1L);
        var savedHash = new AtomicReference<String>();
        when(repository.isCompleted(anyLong(), any(), anyString(), anyString()))
                .thenAnswer(call -> call.getArgument(3).equals(savedHash.get()));
        when(repository.replace(anyLong(), any(), anyString(), anyString(), anyString(), anyList(), anyList()))
                .thenAnswer(call -> { savedHash.set(call.getArgument(4)); return true; });
        when(extractor.extract(any())).thenReturn(List.of(new PdfExtractor.Page(1, "Sample insurance terms")));
        when(embeddings.embed(anyList())).thenReturn(List.of(new float[1536]));
        var service = new DocumentIngestionService(embeddings, repository, extractor,
                new ArticleChunker(new ArticleChunker.Settings(750, 1000, 80, 1.5)), root.toString());

        assertEquals(1, service.ingestAll().success());
        assertEquals(1, service.ingestAll().skipped());
        verify(embeddings, times(1)).embed(anyList());
        verify(extractor, times(1)).extract(any());
        Files.writeString(pdf, "changed PDF content");
        assertEquals(1, service.ingestAll().success());
        verify(embeddings, times(2)).embed(anyList());
        assertEquals(1, service.ingestAll().skipped());
    }
}
