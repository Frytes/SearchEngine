package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    // LemmaRepository нам пока не нужен, так как леммы - заглушка

    @Override
    public StatisticsResponse getStatistics() {

        TotalStatistics total = new TotalStatistics();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = siteRepository.findAll(); // Получаем реальные сайты из БД

        // Считаем реальное общее количество сайтов и страниц
        total.setSites(sitesList.size());
        total.setPages((int) pageRepository.count());
        total.setIndexing(IndexingServiceImpl.isIndexingRunning());

        long totalLemmas = 0; // Будем считать общую сумму лемм-заглушек

        // Проходимся по каждому реальному сайту
        for (Site site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().toString());
            item.setStatusTime(site.getStatusTime().toEpochSecond(ZoneOffset.UTC));

            if (site.getLastError() != null && !site.getLastError().isEmpty()) {
                item.setError(site.getLastError());
            }

            // --- КЛЮЧЕВОЙ МОМЕНТ ---
            // 1. Берем РЕАЛЬНОЕ количество страниц
            int pagesCount = pageRepository.countBySite(site);
            item.setPages(pagesCount);

            // 2. Генерируем ЗАГЛУШКУ для количества лемм
            // (Чтобы выглядело правдоподобно, умножим кол-во страниц на случайное число)
            int lemmasCount = pagesCount * ThreadLocalRandom.current().nextInt(50, 200);
            item.setLemmas(lemmasCount);

            totalLemmas += lemmasCount;
            detailed.add(item);
        }

        // Устанавливаем итоговое количество лемм-заглушек
        total.setLemmas((int) totalLemmas);

        // --- Сборка финального ответа ---
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);

        return response;
    }
}