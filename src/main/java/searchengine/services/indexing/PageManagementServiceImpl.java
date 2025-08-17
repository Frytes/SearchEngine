package searchengine.services.indexing;

import lombok.RequiredArgsConstructor;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemma.LemmaEngine;
import searchengine.services.lemma.LemmaService;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PageManagementServiceImpl implements PageManagementService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;
    private final LemmaService lemmaService;
    private final LemmaEngine lemmaEngine;
    private final SitesList sitesConfig;
    private final EntityManager entityManager;

    @Value("${search-settings.user-agent}")
    private String userAgent;

    @Value("${search-settings.referrer}")
    private String referrer;

    @Override
    @Transactional
    public IndexingResponse indexPage(String url) {
        if (url == null || url.trim().isEmpty()) {
            return new IndexingResponse(false, "URL страницы не указан");
        }

        Optional<searchengine.config.Site> optSiteConfig = sitesConfig.getSites().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst();

        if (optSiteConfig.isEmpty()) {
            return new IndexingResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        searchengine.config.Site siteConfig = optSiteConfig.get();
        Site site = siteRepository.findByUrl(siteConfig.getUrl())
                .orElseGet(() -> {
                    Site newSite = new Site();
                    newSite.setUrl(siteConfig.getUrl());
                    newSite.setName(siteConfig.getName());
                    newSite.setStatus(SiteStatus.INDEXED);
                    newSite.setStatusTime(LocalDateTime.now(ZoneOffset.UTC));
                    return siteRepository.save(newSite);
                });

        String path = url.substring(site.getUrl().length());
        if (path.isEmpty()) {
            path = "/";
        }


        pageRepository.findBySiteAndPath(site, path).ifPresent(page -> {
            lemmaService.decrementLemmaFrequency(page);
            indexRepository.deleteAllByPage(page);
            pageRepository.delete(page);
            entityManager.flush();
        });

        try {
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(userAgent)
                    .referrer(referrer)
                    .execute();

            if (response.statusCode() >= 400) {
                return new IndexingResponse(false, "Ошибка получения страницы, статус: " + response.statusCode());
            }

            Page newPage = new Page();
            newPage.setSite(site);
            newPage.setPath(path);
            newPage.setCode(response.statusCode());
            newPage.setContent(response.body());
            Page savedPage = pageRepository.saveAndFlush(newPage);

            Map<String, Integer> lemmas = lemmaEngine.getLemmaMap(lemmaEngine.cleanHtml(newPage.getContent()));
            lemmaService.saveLemmasForPage(savedPage, lemmas);
            entityManager.flush();

        } catch (IOException e) {
            return new IndexingResponse(false, "Не удалось получить доступ к странице: " + e.getMessage());
        }

        return new IndexingResponse(true);
    }
}