package searchengine.services.crawler;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import searchengine.dto.crawler.PageProcessingResult;
import searchengine.dto.indexing.LemmaDto;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemma.DataCollector;
import searchengine.services.lemma.LemmaEngine;

import java.net.URI;
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

        } catch (Exception e) {
            String errorMessage = "Ошибка обхода страницы " + pageUrl + ": " + e.getMessage();
            System.err.println(errorMessage);
            siteEntity.setLastError(errorMessage);
            siteRepository.save(siteEntity);
            return PageProcessingResult.failure();
        }
    }

    private Set<String> extractLinks(Document document, String siteBaseUrl) {
        Set<String> links = new HashSet<>();
        try {
            String normalizedBaseHost = normalizeHost(new URI(siteBaseUrl).getHost());
            Elements elements = document.select("a[href]");

            for (Element element : elements) {
                String absUrl = element.attr("abs:href");

                // --- ИЗМЕНЕНИЕ ЗДЕСЬ ---
                // Новое регулярное выражение, которое игнорирует параметры в URL
                if (absUrl.isEmpty() || absUrl.contains("#") || absUrl.matches("(?i).*\\.(pdf|docx?|xlsx?|jpg|jpeg|png|gif|webp|zip|rar|exe|mp3|mp4|avi|mov|svg)(\\?.*)?$")) {
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