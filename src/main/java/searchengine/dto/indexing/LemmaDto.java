package searchengine.dto.indexing;

import lombok.Value;
import searchengine.model.Page;
import java.util.Map;

@Value
public class LemmaDto {
    Page page;
    Map<String, Integer> lemmaMap;
}