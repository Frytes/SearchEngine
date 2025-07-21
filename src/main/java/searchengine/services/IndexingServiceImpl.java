package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.crawler.SiteCrawler;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final StatisticsService statisticsService;
    private final SitesList sitesConfig;

    @Value("${search-settings.user-agent}")
    private String userAgent;

    @Value("${search-settings.referrer}")
    private String referrer;

    @Value("${search-settings.delay}")
    private int delay;

    private static volatile boolean isIndexingRunning = false;

    @Override
    public IndexingResponse startIndexing() {
        if (isIndexingRunning) {
            return new IndexingResponse(false, "Индексация уже запущена");
        }

        isIndexingRunning = true;

        new Thread(() -> {
            List<Thread> siteThreads = new ArrayList<>();
            for (searchengine.config.Site siteConfig : sitesConfig.getSites()) {
                Thread siteThread = new Thread(() -> {
                    if (!isIndexingRunning) return;

                    findAndRemoveSiteData(siteConfig.getUrl());

                    Site newSiteEntity = new Site();
                    newSiteEntity.setUrl(siteConfig.getUrl());
                    newSiteEntity.setName(siteConfig.getName());
                    newSiteEntity.setStatus(SiteStatus.INDEXING);
                    newSiteEntity.setStatusTime(LocalDateTime.now());
                    siteRepository.save(newSiteEntity);

                    ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
                    Set<String> visitedUrlsForSite = ConcurrentHashMap.newKeySet();
                    SiteCrawler mainTask = new SiteCrawler(
                            pageRepository, siteRepository, delay, userAgent, referrer, newSiteEntity, newSiteEntity.getUrl(), visitedUrlsForSite
                    );

                    forkJoinPool.invoke(mainTask);

                    if (isIndexingRunning) {
                        newSiteEntity.setStatus(SiteStatus.INDEXED);
                        newSiteEntity.setLastError(null);
                    } else {
                        newSiteEntity.setStatus(SiteStatus.FAILED);
                        newSiteEntity.setLastError("Индексация остановлена пользователем");
                    }
                    newSiteEntity.setStatusTime(LocalDateTime.now());
                    siteRepository.save(newSiteEntity);
                });
                siteThreads.add(siteThread);
                siteThread.start();
            }

            try {
                for (Thread thread : siteThreads) {
                    thread.join();
                }
            } catch (InterruptedException e) {
                System.err.println("Главный поток индексации был прерван.");
                Thread.currentThread().interrupt();
            }

            isIndexingRunning = false;
        }).start();

        return new IndexingResponse(true);
    }

    private void findAndRemoveSiteData(String urlFromConfig) {
        siteRepository.findByUrl(urlFromConfig).ifPresent(this::deleteSiteData);
        try {
            URL url = new URL(urlFromConfig);
            String host = url.getHost();
            String protocol = url.getProtocol();
            String alternativeUrl;
            if (host.startsWith("www.")) {
                alternativeUrl = protocol + "://" + host.substring(4);
            } else {
                alternativeUrl = protocol + "://www." + host;
            }
            siteRepository.findByUrl(alternativeUrl).ifPresent(this::deleteSiteData);
            siteRepository.findByUrl(alternativeUrl + "/").ifPresent(this::deleteSiteData);
            siteRepository.findByUrl(urlFromConfig + "/").ifPresent(this::deleteSiteData);
        } catch (MalformedURLException e) {
            System.err.println("Некорректный URL в конфигурации: " + urlFromConfig);
        }
    }

    private void deleteSiteData(Site site) {
        System.out.println("Очистка данных для сайта: " + site.getName() + " (ID: " + site.getId() + ")");
        indexRepository.deleteAllByPageSite(site);
        lemmaRepository.deleteAllBySite(site);
        pageRepository.deleteAllBySite(site);
        siteRepository.delete(site);
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!isIndexingRunning) {
            return new IndexingResponse(false, "Индексация не запущена");
        }

        isIndexingRunning = false;

        List<Site> sitesInProgress = siteRepository.findByStatus(SiteStatus.INDEXING);
        for (Site site : sitesInProgress) {
            site.setStatus(SiteStatus.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }

        return new IndexingResponse(true);
    }

    @Override
    public IndexingResponse indexPage(String url) {
        return new IndexingResponse(true, "Функция индексации отдельной страницы пока не реализована.");
    }

    @Override
    public StatisticsResponse getStatistics() {
        return statisticsService.getStatistics();
    }

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        return new SearchResponse(false, "Поиск в данный момент не реализован.");
    }

    public static boolean isIndexingRunning() {
        return isIndexingRunning;
    }
}