package wonit.hackathon.a1.domain.rag.service;

import org.junit.jupiter.api.Test;
import wonit.hackathon.a1.domain.insurance.enums.InsuranceType;
import static org.junit.jupiter.api.Assertions.*;

class ProductCategoryCatalogTests {
    @Test void classifiesKnownDocumentsWithoutGuessingUnknownProducts() throws Exception {
        var catalog = new ProductCategoryCatalog();
        assertEquals(InsuranceType.CANCER, catalog.resolve("무배당우리WON하는암보험", ""));
        assertEquals(InsuranceType.CHILD, catalog.resolve("무배당우리WON하는꿈나무우리아이보험", null));
        assertEquals(InsuranceType.WHOLE_LIFE, catalog.resolve("무배당우리WON하는유니버셜종신보험", ""));
        assertEquals(InsuranceType.UNKNOWN, catalog.resolve("unregistered 암보험", ""));
    }

    @Test void supportsExplicitMetadataAndRejectsInvalidCodes() throws Exception {
        var catalog = new ProductCategoryCatalog();
        assertEquals(InsuranceType.CANCER, catalog.resolve("New product", "CANCER"));
        assertThrows(IllegalArgumentException.class, () -> catalog.resolve("New product", "invalid"));
    }
}
