package wonit.hackathon.a1.domain.rag.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;

/** PDF → 페이지별 텍스트. 페이지 번호는 실제 PDF 페이지(1부터)이며 인쇄된 쪽 번호는 쓰지 않음 */
public class PdfExtractor {

    public record Page(int number, String text, boolean containsImages) {
        /** 글자가 없고 이미지만 있는 페이지 (OCR 필요 — OCR은 하지 않음) */
        public boolean needsOcr() {
            return text.isBlank() && containsImages;
        }
    }

    public List<Page> extract(Path path) throws IOException {
        List<Page> pages = new ArrayList<>();
        try (var document = Loader.loadPDF(path.toFile())) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("PDF does not permit text extraction: " + path.getFileName());
            }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(document).replace("\r\n", "\n");
                boolean images = containsImages(document.getPage(i - 1).getResources(), new HashSet<>());
                pages.add(new Page(i, text, images));
            }
        }
        return List.copyOf(pages);
    }

    private boolean containsImages(PDResources resources, Set<Object> visited) throws IOException {
        if (resources == null || !visited.add(resources.getCOSObject())) {
            return false;
        }
        for (var name : resources.getXObjectNames()) {
            var object = resources.getXObject(name);
            if (object instanceof PDImageXObject) {
                return true;
            }
            if (object instanceof PDFormXObject form && containsImages(form.getResources(), visited)) {
                return true;
            }
        }
        return false;
    }
}