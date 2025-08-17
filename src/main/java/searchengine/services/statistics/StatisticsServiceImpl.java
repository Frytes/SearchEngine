package searchengine.services.statistics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.indexing.IndexingServiceImpl;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = siteRepository.findAll();

        boolean isIndexing = IndexingServiceImpl.isIndexing() || sitesList.stream().anyMatch(s -> s.getStatus() == searchengine.model.SiteStatus.INDEXING);
        total.setIndexing(isIndexing);

        total.setSites(sitesList.size());
        total.setIndexing(sitesList.stream().anyMatch(s -> s.getStatus() == SiteStatus.INDEXING));

        long totalPages = 0;
        long totalLemmas = 0;

        for (Site site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().toString());
            item.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.UTC) * 1000);

            if (site.getLastError() != null && !site.getLastError().isEmpty()) {
                item.setError(site.getLastError());
            }

            int pagesCount = pageRepository.countBySite(site);
            int lemmasCount = lemmaRepository.countBySite(site);

            item.setPages(pagesCount);
            item.setLemmas(lemmasCount);

            totalPages += pagesCount;
            totalLemmas += lemmasCount;
            detailed.add(item);
        }

        total.setPages((int) totalPages);
        total.setLemmas((int) totalLemmas);

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);

        return response;
    }
}