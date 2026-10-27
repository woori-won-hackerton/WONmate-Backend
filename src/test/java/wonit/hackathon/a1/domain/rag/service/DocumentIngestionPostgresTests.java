package wonit.hackathon.a1.domain.rag.service;

import jakarta.persistence.EntityManager;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;
import wonit.hackathon.a1.domain.rag.client.EmbeddingClient;
import wonit.hackathon.a1.domain.rag.repository.DocumentIngestionRepository;
import wonit.hackathon.a1.domain.rag.ArticleChunker;
import wonit.hackathon.a1.domain.rag.PdfExtractor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 실제 DB에서 문서 교체/롤백을 검증한다. 임베딩은 mock이므로 OpenAI 호출 비용은 발생하지 않는다.
 * 테스트 전체를 롤백하면 실제 커밋 동작을 확인할 수 없어 각 Repository 트랜잭션을 커밋하고 finally에서 정리한다.
 */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named="WONMATE_DB_URL", matches=".+")
class DocumentIngestionPostgresTests {
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired DocumentIngestionRepository repository;
    @TempDir Path root;

    private void pdf(Path path, String text) throws Exception {
        try (var pdf = new PDDocument()) {
            var page = new PDPage(); pdf.addPage(page);
            try (var stream = new PDPageContentStream(pdf,page)) {
                stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);
                stream.newLineAtOffset(40,700); stream.showText(text); stream.endText();
            }
            pdf.save(path.toFile());
        }
    }

    @Test void replacesAtomicallySkipsUnchangedAndPreservesDataOnApiAndDatabaseFailures() throws Exception {
        var tx = new TransactionTemplate(manager);
        Long id = tx.execute(s -> {
            var p = InsuranceProduct.create("stage3-test-"+UUID.randomUUID(),null,null,"TEST",null,null);
            em.persist(p); em.flush(); return p.getId();
        });
        try {
            var dir = Files.createDirectory(root.resolve("product"));
            Files.writeString(dir.resolve("metadata.json"), "{\"productId\":"+id+",\"documents\":[{\"fileName\":\"test.pdf\",\"documentType\":\"TERMS\"}]}");
            var path = dir.resolve("test.pdf"); pdf(path,"original text");
            var embeddings = mock(EmbeddingClient.class);
            when(embeddings.embed(anyList())).thenReturn(Collections.singletonList(new float[1536]));
            var service = new DocumentIngestionService(embeddings,repository,new PdfExtractor(),
                    new ArticleChunker(new ArticleChunker.Settings(750,1000,80,1.5)),root.toString());
            assertEquals(1,service.ingestAll().success());
            var old = service.list().stream().filter(d -> id.equals(d.productId())).findFirst().orElseThrow();
            Long documentId = old.documentId();
            assertNull(repository.resolveProduct(id, "different product"));
            assertEquals(id, repository.resolveProduct(id, null));
            assertEquals(1,service.ingestAll().skipped()); verify(embeddings,times(1)).embed(anyList());
            pdf(path,"changed text");
            when(embeddings.embed(anyList())).thenThrow(new IllegalStateException("simulated API failure"));
            assertEquals(1,service.ingestAll().failed());
            assertEquals(old,service.list().stream().filter(d -> documentId.equals(d.documentId())).findFirst().orElseThrow());
            // 기존 청크를 삭제한 뒤 잘못된 차원을 만나도 트랜잭션 롤백으로 원문이 복구되어야 한다.
            doReturn(Collections.singletonList(new float[3])).when(embeddings).embed(anyList());
            assertEquals(1,service.ingestAll().failed());
            assertEquals("original text", tx.execute(s -> em.createQuery("select c.content from ProductChunk c where c.productDocument.id=:id",String.class).setParameter("id",documentId).getSingleResult()));
            when(embeddings.embed(anyList())).thenReturn(Collections.singletonList(new float[1536]));
            assertEquals(1,service.ingestAll().success());
            assertEquals("changed text", tx.execute(s -> em.createQuery("select c.content from ProductChunk c where c.productDocument.id=:id",String.class).setParameter("id",documentId).getSingleResult()));
            assertEquals(1,service.ingestAll().skipped());
            // 손상된 다른 문서가 있어도 기존 정상 문서는 건너뛰고 그대로 보존한다.
            Files.writeString(dir.resolve("broken.pdf"), "not a PDF");
            Files.writeString(dir.resolve("metadata.json"), "{\"productId\":"+id+",\"documents\":[{\"fileName\":\"test.pdf\",\"documentType\":\"TERMS\"},{\"fileName\":\"broken.pdf\",\"documentType\":\"SUMMARY\"}]}");
            var partial = service.ingestAll();
            assertEquals(1,partial.failed());
            assertEquals(1,partial.skipped());
            assertEquals("changed text", tx.execute(s -> em.createQuery("select c.content from ProductChunk c where c.productDocument.id=:id",String.class).setParameter("id",documentId).getSingleResult()));
            Files.writeString(dir.resolve("metadata.json"),"{\"productId\":-1,\"documents\":[{\"fileName\":\"test.pdf\",\"documentType\":\"TERMS\"}]}");
            assertEquals("MAPPING_REQUIRED",service.ingestAll().documents().getFirst().status());
        } finally {
            tx.executeWithoutResult(s -> {
                em.createQuery("delete from ProductChunk c where c.productDocument.insuranceProduct.id=:id").setParameter("id",id).executeUpdate();
                em.createQuery("delete from ProductDocument d where d.insuranceProduct.id=:id").setParameter("id",id).executeUpdate();
                em.createQuery("delete from InsuranceProduct p where p.id=:id").setParameter("id",id).executeUpdate();
            });
        }
    }
}
