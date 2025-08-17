package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
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
import searchengine.services.crawler.PageProcessorService;
import searchengine.services.crawler.SiteCrawler;
import searchengine.services.search.SearchService;
import searchengine.services.statistics.StatisticsService;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {

    private static final AtomicBoolean isIndexingRunning = new AtomicBoolean(false);
    private final Map<String, ForkJoinPool> activePools = new ConcurrentHashMap<>();

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesConfig;
    private final TaskExecutor siteIndexingExecutor;

    private final StatisticsService statisticsService;
    private final PageProcessorService pageProcessor;
    private final PageManagementService pageManagementService;
    private final SearchService searchService;

    @Value("${search-settings.delay}")
    private int delay;

    @PostConstruct
    public void init() {
        List<Site> indexingSites = siteRepository.findByStatus(SiteStatus.INDEXING);
        if (!indexingSites.isEmpty()) {
            for (Site site : indexingSites) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Индексация была прервана из-за перезапуска сервера");
                siteRepository.save(site);
            }
        }
    }

    @Override
    public IndexingResponse startIndexing() {
        if (isIndexingRunning.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    activePools.clear();
                    Thread loggerThread = new Thread(this::logQueueStatus);
                    loggerThread.start();

                    int numberOfSites = sitesConfig.getSites().size();
                    CountDownLatch latch = new CountDownLatch(numberOfSites);

                    sitesConfig.getSites().forEach(siteConfig ->
                            siteIndexingExecutor.execute(() -> {
                                try {
                                    processSite(siteConfig);
                                } finally {
                                    latch.countDown();
                                }
                            })
                    );

                    latch.await();
                    loggerThread.interrupt();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    isIndexingRunning.set(false);
                    System.out.println("Полная индексация всех сайтов завершена.");
                }
            }, "Indexing-Manager-Thread").start();

            return new IndexingResponse(true);
        } else {
            return new IndexingResponse(false, "Индексация уже запущена");
        }
    }

    private void processSite(searchengine.config.Site siteConfig) {
        if (!isIndexingRunning.get()) {
            return;
        }

        deleteSiteDataByUrl(siteConfig.getUrl());

        Site siteEntity = new Site();
        siteEntity.setUrl(siteConfig.getUrl());
        siteEntity.setName(siteConfig.getName());
        siteEntity.setStatus(SiteStatus.INDEXING);
        siteEntity.setStatusTime(LocalDateTime.now(ZoneOffset.UTC));
        siteRepository.save(siteEntity);

        try {
            Jsoup.connect(siteConfig.getUrl()).execute();
        } catch (Exception e) {
            String errorMessage = "Главная страница сайта недоступна: " + e.getMessage();
            System.err.println(errorMessage);
            siteEntity.setLastError(errorMessage);
            siteEntity.setStatus(SiteStatus.FAILED);
            siteRepository.save(siteEntity);
            return;
        }

        ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        activePools.put(siteConfig.getName(), forkJoinPool);

        try {
            Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
            visitedUrls.add(siteEntity.getUrl());

            SiteCrawler mainTask = new SiteCrawler(pageProcessor, delay, siteEntity, siteEntity.getUrl(), visitedUrls);
            forkJoinPool.invoke(mainTask);

            if (isIndexingRunning.get()) {
                siteEntity.setStatus(SiteStatus.INDEXED);
                siteEntity.setLastError(null);
            } else {
                siteEntity.setStatus(SiteStatus.FAILED);
                siteEntity.setLastError("Индексация остановлена пользователем");
            }
        } catch (Exception e) {
            System.err.println("Критическая ошибка при индексации сайта " + siteConfig.getName());
            e.printStackTrace();
            siteEntity.setStatus(SiteStatus.FAILED);
            siteEntity.setLastError("Внутренняя ошибка индексатора: " + e.getMessage());
        } finally {
            siteEntity.setStatusTime(LocalDateTime.now(ZoneOffset.UTC));
            siteRepository.save(siteEntity);
            if (!forkJoinPool.isShutdown()) {
                forkJoinPool.shutdown();
            }
            activePools.remove(siteConfig.getName());
        }
    }

    private void deleteSiteDataByUrl(String url) {
        try {
            URL urlObj = new URL(url);
            String host = urlObj.getHost().replaceFirst("www\\.", "");
            List<Site> sitesToDelete = siteRepository.findAllByUrlContaining(host);
            for (Site site : sitesToDelete) {
                deleteSiteData(site);
            }
        } catch (MalformedURLException e) {
            System.err.println("Некорректный URL в конфигурации: " + url);
        }
    }

    private void deleteSiteData(Site site) {
        indexRepository.deleteAllByPageSite(site);
        lemmaRepository.deleteAllBySite(site);
        pageRepository.deleteAllBySite(site);
        siteRepository.delete(site);
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (isIndexingRunning.compareAndSet(true, false)) {
            activePools.values().forEach(ForkJoinPool::shutdownNow);
            activePools.clear();
            List<Site> sitesInProgress = siteRepository.findByStatus(SiteStatus.INDEXING);
            for (Site site : sitesInProgress) {
                site.setStatus(SiteStatus.FAILED);
                site.setLastError("Индексация остановлена пользователем");
                site.setStatusTime(LocalDateTime.now(ZoneOffset.UTC));
                siteRepository.save(site);
            }
            return new IndexingResponse(true);
        } else {
            return new IndexingResponse(false, "Индексация не запущена");
        }
    }

    private void logQueueStatus() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                System.out.println("\n--- Статус очередей индексации ---");
                if (activePools.isEmpty()) {
                    System.out.println("Все задачи по индексации сайтов в данный момент завершены.");
                } else {
                    activePools.forEach((siteName, pool) -> System.out.printf(
                            "Сайт: %-20s | В очереди: %-5d | Активных потоков: %d/%d%n",
                            siteName,
                            pool.getQueuedTaskCount(),
                            pool.getActiveThreadCount(),
                            pool.getPoolSize()
                    ));
                }
                System.out.println("------------------------------------\n");
                Thread.sleep(15000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Поток мониторинга очередей остановлен.");
        }
    }

    @Override
    public IndexingResponse indexPage(String url) {
        return pageManagementService.indexPage(url);
    }

    @Override
    public StatisticsResponse getStatistics() {
        return statisticsService.getStatistics();
    }

    @Override
    public SearchResponse search(String query, String site, int offset, int limit) {
        return searchService.search(query, site, offset, limit);
    }

    public static boolean isIndexing() {
        return isIndexingRunning.get();
    }
}