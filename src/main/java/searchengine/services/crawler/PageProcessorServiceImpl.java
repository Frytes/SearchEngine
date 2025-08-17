package searchengine.services.crawler;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.UnsupportedMimeTypeException;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.crawler.PageProcessingResult;
import searchengine.dto.indexing.LemmaDto;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemma.DataCollector;
import searchengine.services.lemma.LemmaEngine;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PageProcessorServiceImpl implements PageProcessorService {

    private final PageRepository pageRepository;
    private final SiteRepository siteRepository;
    private final LemmaEngine lemmaEngine;
    private final DataCollector dataCollector;

    @Value("${search-settings.user-agent}")
    private String userAgent;

    @Value("${search-settings.referrer}")
    private String referrer;

    @Override
    public PageProcessingResult process(String pageUrl, Site siteEntity) {
        try {
            Connection.Response response = Jsoup.connect(pageUrl)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .timeout(15000)
                    .ignoreHttpErrors(true)
                    .execute();

            String contentType = response.contentType();
            if (contentType != null && !contentType.startsWith("text/html")) {
                return PageProcessingResult.failure();
            }

            Document document = response.parse();
            String content = document.outerHtml();
            int statusCode = response.statusCode();
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
                return PageProcessingResult.success(links);
            } else {
                return PageProcessingResult.failure();
            }

        } catch (UnsupportedMimeTypeException e) {
            System.err.println("Пропуск (неподдерживаемый тип контента): " + pageUrl);
        } catch (SocketTimeoutException | java.net.SocketException | UnknownHostException e) {
            handleNetworkError(pageUrl, siteEntity, e);
        } catch (IllegalArgumentException e) {
            System.err.println("Игнорирую некорректный URL: " + pageUrl + " - " + e.getMessage());
        } catch (Exception e) {
            handleGenericError(pageUrl, siteEntity, e);
        }
        return PageProcessingResult.failure();
    }

    private void handleNetworkError(String pageUrl, Site site, Exception e) {
        String errorMessage = "Ошибка сети для " + pageUrl + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
        System.err.println(errorMessage);
        site.setLastError(errorMessage);
        site.setStatus(SiteStatus.FAILED);
        siteRepository.save(site);
    }

    private void handleGenericError(String pageUrl, Site site, Exception e) {
        String errorMessage = "Критическая ошибка обхода " + pageUrl + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
        System.err.println(errorMessage);
        site.setLastError(errorMessage);
        site.setStatus(SiteStatus.FAILED);
        siteRepository.save(site);
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

    private String normalizeHost(String host) {
        if (host != null && host.startsWith("www.")) {
            return host.substring(4);
        }
        return host;
    }
}