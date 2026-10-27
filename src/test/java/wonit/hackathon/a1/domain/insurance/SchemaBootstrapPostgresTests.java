package wonit.hackathon.a1.domain.insurance;

import java.nio.file.*;
import java.util.UUID;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** All DDL is confined to a random schema and rolled back. Existing tables remain untouched. */
@SpringBootTest(properties = "spring.jpa.show-sql=false")
@EnabledIfEnvironmentVariable(named="WONMATE_DB_URL", matches=".+")
class SchemaBootstrapPostgresTests {
    @Autowired DataSource datasource;
    @Autowired EntityManagerFactory emf;

    @Test void sqlCreatesFinalSchemaAndHibernateOnlyValidatesIt() throws Exception {
        String schema = "rag_bootstrap_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = datasource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE SCHEMA " + schema);
                    statement.execute("SET LOCAL search_path TO " + schema + ", public");
                    for (var path : new String[]{"db/rag/00-existing-domain-enums.sql",
                            "db/bootstrap/01-create-business-tables.sql", "db/rag/01-create-rag-tables.sql"}) {
                        String sql = Files.readString(Path.of(path)).replace("public.", schema + ".")
                                .replaceAll("(?m)^--.*$", "").replaceAll("(?m)^(BEGIN|COMMIT);\\s*$", "");
                        statement.execute(sql);
                    }
                }
                var registry = new StandardServiceRegistryBuilder()
                        .applySetting("hibernate.connection.datasource", new SingleConnectionDataSource(connection, true))
                        .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                        .applySetting("hibernate.default_schema", schema)
                        .applySetting("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl")
                        .applySetting("hibernate.hbm2ddl.auto", "validate").build();
                try {
                    var sources = new MetadataSources(registry);
                    emf.getMetamodel().getEntities().forEach(e -> sources.addAnnotatedClass(e.getJavaType()));
                    try (var factory = sources.buildMetadata().buildSessionFactory()) { assertTrue(factory.isOpen()); }
                } finally { StandardServiceRegistryBuilder.destroy(registry); }
                try (var query = connection.prepareStatement("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema=? AND table_name='product_documents'
                          AND column_name IN ('document_version','effective_date')
                        """)) {
                    query.setString(1, schema);
                    try (var result = query.executeQuery()) { result.next(); assertEquals(0, result.getInt(1)); }
                }
                try (var query = connection.prepareStatement("""
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_schema=? AND table_name='product_documents'
                          AND column_name IN ('created_at','updated_at')
                          AND is_nullable='NO' AND column_default IS NOT NULL
                        """)) {
                    query.setString(1, schema);
                    try (var result = query.executeQuery()) { result.next(); assertEquals(2, result.getInt(1)); }
                }
            } finally { connection.rollback(); connection.setAutoCommit(true); }
        }
    }
}
