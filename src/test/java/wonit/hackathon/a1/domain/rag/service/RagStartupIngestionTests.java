package wonit.hackathon.a1.domain.rag.service;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import wonit.hackathon.a1.domain.rag.dto.IngestionReport;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RagStartupIngestionTests {
    @Test void enabledForWebStartupAndCanBeDisabled() {
        var runner = new WebApplicationContextRunner()
                .withBean(RagIngestionJob.class, () -> mock(RagIngestionJob.class))
                .withUserConfiguration(RagStartupIngestion.class);
        runner.run(context -> assertNotNull(context.getBean(RagStartupIngestion.class)));
        runner.withPropertyValues("rag.ingestion.on-startup=false")
                .run(context -> assertEquals(0, context.getBeansOfType(RagStartupIngestion.class).size()));
        new ApplicationContextRunner()
                .withBean(RagIngestionJob.class, () -> mock(RagIngestionJob.class))
                .withUserConfiguration(RagStartupIngestion.class)
                .run(context -> assertEquals(0, context.getBeansOfType(RagStartupIngestion.class).size()));
    }

    @Test void runsJobAndKeepsServerAliveOnDocumentOrBootstrapFailure() throws Exception {
        var job = mock(RagIngestionJob.class);
        when(job.run()).thenReturn(new IngestionReport(0, 0, 1, 0, 0, List.of()))
                .thenThrow(new IllegalStateException("private upstream details"));
        var startup = new RagStartupIngestion(job);
        assertDoesNotThrow(() -> startup.run(new DefaultApplicationArguments()));
        assertDoesNotThrow(() -> startup.run(new DefaultApplicationArguments()));
        verify(job, times(2)).run();
    }
}
