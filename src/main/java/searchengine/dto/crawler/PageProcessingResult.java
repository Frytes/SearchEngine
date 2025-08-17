package searchengine.dto.crawler;

import lombok.Getter;
import java.util.Collections;
import java.util.Set;

@Getter
public class PageProcessingResult {
    private final boolean success;
    private final Set<String> extractedLinks;

    private PageProcessingResult(boolean success, Set<String> extractedLinks) {
        this.success = success;
        this.extractedLinks = extractedLinks;
    }

    public static PageProcessingResult success(Set<String> links) {
        return new PageProcessingResult(true, links);
    }

    public static PageProcessingResult failure() {
        return new PageProcessingResult(false, Collections.emptySet());
    }
}