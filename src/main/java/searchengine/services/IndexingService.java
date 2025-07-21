package searchengine.services;

import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.search.SearchResponse;

public interface IndexingService {
    IndexingResponse startIndexing();
    IndexingResponse stopIndexing();
    IndexingResponse indexPage(String url);
    StatisticsResponse getStatistics();
    SearchResponse search(String query, String site, int offset, int limit);
}