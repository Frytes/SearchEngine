package searchengine.services.lemma;

import searchengine.dto.indexing.LemmaDto;
import searchengine.model.Page;
import java.util.List;
import java.util.Map;

public interface LemmaService {
    void saveLemmasForBatch(List<LemmaDto> batch);
    void saveLemmasForPage(Page page, Map<String, Integer> lemmas);
    void decrementLemmaFrequency(Page page);
}