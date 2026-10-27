package wonit.hackathon.a1.domain.insurance;

import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;
import wonit.hackathon.a1.domain.insurance.entity.ProductChunk;
import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;
import wonit.hackathon.a1.domain.insurance.repository.ProductChunkRepository;
import wonit.hackathon.a1.domain.insurance.repository.ProductDocumentRepository;

import static org.junit.jupiter.api.Assertions.*;

/** DB 접속 없이 Hibernate 매핑 및 Repository 파생 쿼리 구성을 검사한다. */
class RagMappingTests {
    @Test
    void postgresMappingsBootstrapWithoutDatabaseOrEmbeddingApi() {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none")
                .build();
        try {
            var metadata = new MetadataSources(registry)
                    .addAnnotatedClass(InsuranceProduct.class)
                    .addAnnotatedClass(ProductDocument.class)
                    .addAnnotatedClass(ProductChunk.class)
                    .buildMetadata();
            try (var factory = metadata.buildSessionFactory()) {
                var mappings = ((SessionFactoryImplementor) factory).getRuntimeMetamodels().getMappingMetamodel();
                var chunk = mappings.getEntityDescriptor(ProductChunk.class);
                assertEquals(SqlTypes.VECTOR, ((org.hibernate.metamodel.mapping.BasicValuedModelPart)
                        chunk.findAttributeMapping("embedding")).getJdbcMapping().getJdbcType().getDefaultSqlTypeCode());
                assertNotNull(chunk.findAttributeMapping("productDocument"));
                assertNotNull(chunk.findAttributeMapping("startPage"));
                assertNotNull(chunk.findAttributeMapping("endPage"));
                var document = mappings.getEntityDescriptor(ProductDocument.class);
                assertNotNull(document.findAttributeMapping("insuranceProduct"));
                assertNotNull(document.findAttributeMapping("createdAt"));
                assertNotNull(document.findAttributeMapping("updatedAt"));
                try (var entityManager = factory.createEntityManager()) {
                    var repositories = new JpaRepositoryFactory(entityManager);
                    assertNotNull(repositories.getRepository(ProductDocumentRepository.class));
                    assertNotNull(repositories.getRepository(ProductChunkRepository.class));
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
