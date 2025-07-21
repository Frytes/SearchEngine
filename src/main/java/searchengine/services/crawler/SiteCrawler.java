package searchengine.services.crawler;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.SiteStatus;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
public class SiteCrawler extends RecursiveAction {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final int delay;
    private final String userAgent;
    private final String referrer;
    private final searchengine.model.Site siteEntity;
    private final String pageUrl;
    private final Set<String> visitedUrls;

    @Override
    protected void compute() {
        if (visitedUrls.contains(pageUrl) || !IndexingServiceImpl.isIndexingRunning()) {
            return;
        }
        System.out.println("Начинаю обработку: " + pageUrl);
        visitedUrls.add(pageUrl);

        try {
            Thread.sleep(delay);

            Connection.Response response = Jsoup.connect(pageUrl)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(60000)
                    .ignoreHttpErrors(true)
                    .execute();

            String contentType = response.contentType();
            if (contentType != null && !contentType.startsWith("text/html")) {
                System.out.println("Пропуск (не HTML по заголовку): " + pageUrl);
                return;
            }

            int statusCode = response.statusCode();
            Document document = response.parse();
            String content = document.outerHtml();
            String relativePath = new URI(pageUrl).getPath();
            if (relativePath.isEmpty()) {
                relativePath = "/";
            }

            synchronized (pageRepository) {
                Page page = new Page();
                page.setSite(siteEntity);
                page.setPath(relativePath);
                page.setCode(statusCode);
                page.setContent(content);
                pageRepository.save(page);
            }

            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);

            if (statusCode >= 200 && statusCode < 300) {
                Set<String> links = extractLinks(document, siteEntity.getUrl());
                System.out.println("Успешно обработано: " + pageUrl + ". Найдено новых ссылок: " + links.size());
                createAndForkSubtasks(links);
            } else {
                System.err.println("Страница " + pageUrl + " вернула неуспешный статус: " + statusCode);
            }
        } catch (org.jsoup.UnsupportedMimeTypeException e) {
            System.err.println("Пропуск (неподдерживаемый тип контента): " + pageUrl);
        } catch (java.net.SocketTimeoutException | java.net.SocketException e) {
            System.err.println("Ошибка сети для " + pageUrl + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
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
            if (!visitedUrls.contains(link)) {
                SiteCrawler task = new SiteCrawler(
                        pageRepository, siteRepository, delay, userAgent, referrer, siteEntity, link, visitedUrls);
                task.fork();
                subTasks.add(task);
            }
        }
        for (SiteCrawler task : subTasks) {
            task.join();
        }
    }

    private Set<String> extractLinks(Document document, String siteBaseUrl) {
        Set<String> links = new HashSet<>();
        try {
            String baseHost = new URI(siteBaseUrl).getHost();
            if (baseHost.startsWith("www.")) {
                baseHost = baseHost.substring(4);
            }
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
                    String linkHost = linkUri.getHost();
                    if (linkHost == null) {
                        continue;
                    }
                    if (linkHost.startsWith("www.")) {
                        linkHost = linkHost.substring(4);
                    }
                    if (!linkHost.equalsIgnoreCase(baseHost)) {
                        continue;
                    }
                } catch (Exception e) {
                    System.err.println("Игнорирую битую ссылку: " + absUrl);
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