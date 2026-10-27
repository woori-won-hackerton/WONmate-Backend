package wonit.hackathon.a1.domain.rag;

import java.nio.file.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Local visual evidence, no database or network. */
@EnabledIfEnvironmentVariable(named="RAG_PDF_REVIEW", matches="true")
class PdfEvidenceReviewTests {
    @Test void renderOriginalEvidencePages() throws Exception {
        var output=Files.createDirectories(Path.of("build/rag-audit/pages"));
        try(var files=Files.list(Path.of("documents/product_09"))) {
            for(var path:files.filter(p->p.toString().endsWith(".pdf")).toList()) {
                boolean terms=path.getFileName().toString().startsWith("통합");
                try(var pdf=Loader.loadPDF(path.toFile())) {
                    var renderer=new PDFRenderer(pdf);
                    for(int page:terms ? new int[]{37,77,86,90,159} : new int[]{2}) {
                        ImageIO.write(renderer.renderImageWithDPI(page-1,110),"png",output.resolve((terms?"terms-":"summary-")+page+".png").toFile());
                    }
                }
            }
        }
    }
}
