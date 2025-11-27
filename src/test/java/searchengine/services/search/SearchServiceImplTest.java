package searchengine.services.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemma.LemmaEngine;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
public class SearchServiceImplTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private LemmaEngine lemmaEngine;

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;

    @BeforeEach
    void setUp() {
        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();

        Site site = new Site();
        site.setUrl("https://test.com");
        site.setName("Тестовый сайт");
        site.setStatus(SiteStatus.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        String content1 = "Милые котики играют в саду.";
        Page page1 = createPage(site, "/page1", "Страница про котиков", content1);
        processAndSaveLemmas(page1, content1);

        String content2 = "Веселые собаки бегают во дворе.";
        Page page2 = createPage(site, "/page2", "Страница про собак", content2);
        processAndSaveLemmas(page2, content2);
    }

    @Test
    @DisplayName("Поиск по запросу, который находит одну релевантную страницу")
    void search_whenQueryMatchesOnePage_shouldReturnCorrectResult() {
        String query = "милые котики";

        SearchResponse response = searchService.search(query, null, 0, 10);

        assertThat(response.isResult()).isTrue();
        assertThat(response.getCount()).isEqualTo(1);

        List<SearchData> data = response.getData();
        assertThat(data).hasSize(1);

        SearchData result = data.get(0);
        assertThat(result.getUri()).isEqualTo("/page1");
        assertThat(result.getTitle()).isEqualTo("Страница про котиков");
        assertThat(result.getSnippet()).contains("<b>Милые</b>", "<b>котики</b>");
        assertThat(result.getRelevance()).isEqualTo(1.0f);
    }

    @Test
    @DisplayName("Поиск по запросу, который не находит ни одной страницы")
    void search_whenQueryDoesNotMatch_shouldReturnEmptyResult() {
        String query = "грустные попугаи";

        SearchResponse response = searchService.search(query, null, 0, 10);

        assertThat(response.isResult()).isTrue();
        assertThat(response.getCount()).isEqualTo(0);
        assertThat(response.getData()).isEmpty();
    }

    private Page createPage(Site site, String path, String title, String textContent) {
        Page page = new Page();
        page.setSite(site);
        page.setPath(path);
        page.setCode(200);
        page.setContent("<html><title>" + title + "</title><body>" + textContent + "</body></html>");
        return pageRepository.save(page);
    }

    private void processAndSaveLemmas(Page page, String text) {
        Map<String, Integer> lemmas = lemmaEngine.getLemmaMap(text);
        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            Lemma lemma = lemmaRepository.findBySiteAndLemma(page.getSite(), entry.getKey())
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setSite(page.getSite());
                        newLemma.setLemma(entry.getKey());
                        newLemma.setFrequency(0);
                        return newLemma;
                    });
            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(entry.getValue());
            indexRepository.save(index);
        }
    }
    @Test
    @DisplayName("Поиск по конкретному сайту должен находить результаты только на этом сайте")
    void search_whenSiteIsSpecified_shouldSearchOnlyOnThatSite() {

        Site site2 = new Site();
        site2.setUrl("https://another-test.com");
        site2.setName("Другой тестовый сайт");
        site2.setStatus(SiteStatus.INDEXED);
        site2.setStatusTime(LocalDateTime.now());
        siteRepository.save(site2);

        String content = "Милые котики играют в саду.";
        Page pageOnSite2 = createPage(site2, "/page1_on_site2", "Котики на другом сайте", content);
        processAndSaveLemmas(pageOnSite2, content);

        String query = "милые котики";

        SearchResponse response = searchService.search(query, "https://test.com", 0, 10);

        assertThat(response.isResult()).isTrue();
        assertThat(response.getCount()).isEqualTo(1);

        List<SearchData> data = response.getData();
        assertThat(data).hasSize(1);
        assertThat(data.get(0).getSite()).isEqualTo("https://test.com");
        assertThat(data.get(0).getUri()).isEqualTo("/page1");
    }
}