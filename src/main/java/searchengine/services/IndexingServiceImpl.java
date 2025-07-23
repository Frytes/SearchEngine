package searchengine.services;

import lombok.RequiredArgsConstructor;
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
import searchengine.services.crawler.SiteCrawler;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final StatisticsService statisticsService;
    private final SitesList sitesConfig;
    private final TaskExecutor siteIndexingExecutor;

    @Value("${search-settings.user-agent}")
    private String userAgent;

    @Value("${search-settings.referrer}")
    private String referrer;

    @Value("${search-settings.delay}")
    private int delay;

    @PostConstruct
    public void init() {
        List<Site> indexingSites = siteRepository.findByStatus(SiteStatus.INDEXING);
        if (!indexingSites.isEmpty()) {
            System.out.println("Обнаружены незавершенные процессы индексации. Сброс статуса...");
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
            activePools.clear();
            new Thread(this::logQueueStatus).start();

            sitesConfig.getSites().forEach(siteConfig ->
                    siteIndexingExecutor.execute(() -> processSite(siteConfig))
            );
            return new IndexingResponse(true);
        } else {
            return new IndexingResponse(false, "Индексация уже запущена");
        }
    }

    private void processSite(searchengine.config.Site siteConfig) {
        if (!isIndexingRunning.get()) {
            return;
        }

        ForkJoinPool forkJoinPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors());
        activePools.put(siteConfig.getName(), forkJoinPool);
        Site siteEntity = null;
        try {
            deleteSiteDataByUrl(siteConfig.getUrl());

            siteEntity = new Site();
            siteEntity.setUrl(siteConfig.getUrl());
            siteEntity.setName(siteConfig.getName());
            siteEntity.setStatus(SiteStatus.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now(ZoneOffset.UTC));
            siteRepository.save(siteEntity);

            Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
            SiteCrawler mainTask = new SiteCrawler(pageRepository, siteRepository, delay, userAgent, referrer, siteEntity, siteEntity.getUrl(), visitedUrls, true);
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
            if (siteEntity != null) {
                siteEntity.setStatus(SiteStatus.FAILED);
                siteEntity.setLastError("Внутренняя ошибка индексатора: " + e.getMessage());
            }
        } finally {
            if (siteEntity != null) {
                siteEntity.setStatusTime(LocalDateTime.now(ZoneOffset.UTC));
                siteRepository.save(siteEntity);
            }
            if (!forkJoinPool.isShutdown()) {
                forkJoinPool.shutdown();
            }
            activePools.remove(siteConfig.getName());
        }
    }

    private void deleteSiteDataByUrl(String url) {
        siteRepository.findByUrl(url).ifPresent(this::deleteSiteData);
        try {
            URL urlObj = new URL(url);
            String host = urlObj.getHost();
            String protocol = urlObj.getProtocol();
            String rootUrlWithoutWww = protocol + "://" + host.replaceFirst("www\\.", "");
            String rootUrlWithWww = protocol + "://www." + host.replace("www.", "");

            siteRepository.findByUrl(rootUrlWithoutWww).ifPresent(this::deleteSiteData);
            siteRepository.findByUrl(rootUrlWithoutWww + "/").ifPresent(this::deleteSiteData);
            siteRepository.findByUrl(rootUrlWithWww).ifPresent(this::deleteSiteData);
            siteRepository.findByUrl(rootUrlWithWww + "/").ifPresent(this::deleteSiteData);
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
            return new IndexingResponse(true);
        } else {
            return new IndexingResponse(false, "Индексация не запущена");
        }
    }

    private void logQueueStatus() {
        try {
            Thread.sleep(15000);
            while (isIndexingRunning.get()) {
                System.out.println("\n--- Статус очередей индексации ---");
                if (activePools.isEmpty()) {
                    System.out.println("Все задачи по индексации сайтов завершены или еще не начаты.");
                } else {
                    activePools.forEach((siteName, pool) -> {
                        System.out.printf(
                                "Сайт: %-20s | В очереди: %-5d | Активных потоков: %d/%d%n",
                                siteName,
                                pool.getQueuedTaskCount(),
                                pool.getActiveThreadCount(),
                                pool.getPoolSize()
                        );
                    });
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

    public static boolean isIndexing() {
        return isIndexingRunning.get();
    }
}