package searchengine.services.crawler;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.dto.indexing.LemmaDto;
import searchengine.model.Page;
import searchengine.model.SiteStatus;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemma.DataCollector;
import searchengine.services.indexing.IndexingServiceImpl;
import searchengine.services.lemma.LemmaEngine;

import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
public class SiteCrawler extends RecursiveAction {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaEngine lemmaEngine;
    private final DataCollector dataCollector;
    private final int delay;
    private final String userAgent;
    private final String referrer;
    private final searchengine.model.Site siteEntity;
    private final String pageUrl;
    private final Set<String> visitedUrls;
    private final boolean isRootPage;

    @Override
    protected void compute() {
        if (!IndexingServiceImpl.isIndexing()) {
            return;
        }

        try {
            Thread.sleep(delay);

            Connection.Response response = Jsoup.connect(pageUrl)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(15000)
                    .ignoreHttpErrors(true)
                    .execute();

            String contentType = response.contentType();
            if (contentType != null && !contentType.startsWith("text/html")) {
                return;
            }

            int statusCode = response.statusCode();
            Document document = response.parse();
            String content = document.outerHtml();
            String relativePath = new URI(pageUrl).getPath();
            if (relativePath.isEmpty()) {
                relativePath = "/";
            }

            Page page = new Page();
            page.setSite(siteEntity);
            page.setPath(relativePath);
            page.setCode(statusCode);
            page.setContent(content);
            pageRepository.save(page);

            siteEntity.setStatusTime(LocalDateTime.now(ZoneOffset.UTC));
            siteRepository.save(siteEntity);

            if (statusCode >= 200 && statusCode < 300) {
                Map<String, Integer> lemmas = lemmaEngine.getLemmaMap(lemmaEngine.cleanHtml(content));
                dataCollector.addLemmaDto(new LemmaDto(page, lemmas));

                document.setBaseUri(pageUrl);
                Set<String> links = extractLinks(document, siteEntity.getUrl());
                createAndForkSubtasks(links);
            }
        } catch (org.jsoup.UnsupportedMimeTypeException e) {
            System.err.println("Пропуск (неподдерживаемый тип контента): " + pageUrl);
        } catch (java.net.SocketTimeoutException | java.net.SocketException | UnknownHostException e) {
            if (isRootPage) {
                String errorMessage = "Критическая ошибка: не удалось подключиться к главной странице сайта " + pageUrl;
                System.err.println(errorMessage + " " + e.getMessage());
                siteEntity.setLastError(errorMessage);
                siteEntity.setStatus(SiteStatus.FAILED);
                siteRepository.save(siteEntity);
            } else {
                System.err.println("Ошибка сети для " + pageUrl + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        } catch (java.lang.IllegalArgumentException e) {
            System.err.println("Игнорирую некорректный URL: " + pageUrl + " - " + e.getMessage());
        } catch (InterruptedException | CancellationException e) {
            Thread.currentThread().interrupt();
            System.out.println("Задача для " + pageUrl + " была прервана.");
        } catch (Exception e) {
            String errorMessage = "Критическая ошибка обхода " + pageUrl + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
            System.err.println(errorMessage);
            siteEntity.setLastError(errorMessage);
            siteEntity.setStatus(SiteStatus.FAILED);
            siteRepository.save(siteEntity);
        }
    }

    private void createAndForkSubtasks(Set<String> links) {
        List<SiteCrawler> subTasks = new ArrayList<>();
        for (String link : links) {
            if (visitedUrls.add(link)) {
                SiteCrawler task = new SiteCrawler(
                        pageRepository, siteRepository, lemmaEngine, dataCollector, delay, userAgent, referrer, siteEntity, link, visitedUrls, false);
                subTasks.add(task);
            }
        }
        if (!subTasks.isEmpty()) {
            invokeAll(subTasks);
        }
    }

    private String normalizeHost(String host) {
        if (host != null && host.startsWith("www.")) {
            return host.substring(4);
        }
        return host;
    }

    private Set<String> extractLinks(Document document, String siteBaseUrl) {
        Set<String> links = new HashSet<>();
        try {
            String normalizedBaseHost = normalizeHost(new URI(siteBaseUrl).getHost());
            Elements elements = document.select("a[href]");

            for (Element element : elements) {
                String absUrl = element.attr("abs:href");
                if (absUrl.isEmpty() || absUrl.contains("#")) {
                    continue;
                }
                if (absUrl.matches("(?i).*\\.(pdf|docx?|xlsx?|ppt|pptx|jpg|jpeg|png|gif|webp|zip|rar|exe|bin|mp3|mp4|avi)(\\?.*)?$")) {
                    continue;
                }
                try {
                    URI linkUri = new URI(absUrl);
                    String normalizedLinkHost = normalizeHost(linkUri.getHost());

                    if (normalizedLinkHost == null || !normalizedLinkHost.equalsIgnoreCase(normalizedBaseHost)) {
                        continue;
                    }
                } catch (Exception e) {
                    continue;
                }

                absUrl = absUrl.replaceAll("(\\.html)/.*", "$1");
                if (absUrl.endsWith("/")) {
                    absUrl = absUrl.substring(0, absUrl.length() - 1);
                }
                links.add(absUrl);
            }
        } catch (Exception e) {
            System.err.println("Критическая ошибка при получении базового хоста из " + siteBaseUrl);
        }
        return links;
    }
}