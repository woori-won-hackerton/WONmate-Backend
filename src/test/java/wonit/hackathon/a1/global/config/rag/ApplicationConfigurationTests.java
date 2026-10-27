package wonit.hackathon.a1.global.config.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationConfigurationTests {
    @Test void oneYamlContainsApplicationAndStandalonePdfSettings() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        var properties = yaml.getObject();
        assertNotNull(properties);
        assertEquals("a1", properties.getProperty("spring.application.name"));
        assertEquals("local", properties.getProperty("spring.profiles.default"));
        assertEquals("validate", properties.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("false", properties.getProperty("spring.jpa.generate-ddl"));
        assertEquals("never", properties.getProperty("spring.sql.init.mode"));
        assertEquals("documents", properties.getProperty("rag.debug.documents"));
        assertFalse(new ClassPathResource("application-local.yaml").exists());
    }
}
