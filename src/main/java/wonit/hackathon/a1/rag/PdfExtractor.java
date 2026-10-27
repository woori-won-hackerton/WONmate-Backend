package wonit.hackathon.a1.rag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;

/** Physical PDF pages are one-based; printed page labels are never used as source coordinates. */
public class PdfExtractor {
    public record Page(int number, String text, boolean containsImages) {
        public Page(int number, String text) { this(number, text, false); }
        public boolean needsOcr() { return text.isBlank() && containsImages; }
    }
    public List<Page> extract(Path path) throws IOException {
        var pages = new ArrayList<Page>();
        try (var document = Loader.loadPDF(path.toFile())) {
            if (!document.getCurrentAccessPermission().canExtractContent()) {
                throw new IOException("PDF does not permit text extraction: " + path);
            }
            var stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            for (int i = 1; i <= document.getNumberOfPages(); i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                pages.add(new Page(i, stripper.getText(document).replace("\r\n", "\n"),
                        containsImages(document.getPage(i - 1).getResources(), new HashSet<>())));
            }
        }
        return List.copyOf(pages);
    }

    private boolean containsImages(PDResources resources, Set<Object> visited) throws IOException {
        if (resources == null || !visited.add(resources.getCOSObject())) return false;
        for (var name : resources.getXObjectNames()) {
            var object = resources.getXObject(name);
            if (object instanceof PDImageXObject) return true;
            if (object instanceof PDFormXObject form && containsImages(form.getResources(), visited)) return true;
        }
        return false;
    }
}
