package searchengine.services.statistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.SiteStatus;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
public class StatisticsServiceImplTest {

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;

    @BeforeEach
    void setUp() {
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();

        // Создаем тестовые данные: 1 сайт, 2 страницы, 3 леммы
        Site site = new Site();
        site.setUrl("https://test.com");
        site.setName("Тестовый сайт");
        site.setStatus(SiteStatus.INDEXED);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        Page page1 = new Page();
        page1.setSite(site);
        page1.setPath("/page1");
        page1.setCode(200);
        page1.setContent("content1");
        pageRepository.save(page1);

        Page page2 = new Page();
        page2.setSite(site);
        page2.setPath("/page2");
        page2.setCode(200);
        page2.setContent("content2");
        pageRepository.save(page2);

        Lemma lemma1 = new Lemma();
        lemma1.setSite(site);
        lemma1.setLemma("лемма1");
        lemma1.setFrequency(2);
        lemmaRepository.save(lemma1);

        Lemma lemma2 = new Lemma();
        lemma2.setSite(site);
        lemma2.setLemma("лемма2");
        lemma2.setFrequency(1);
        lemmaRepository.save(lemma2);

        Lemma lemma3 = new Lemma();
        lemma3.setSite(site);
        lemma3.setLemma("лемма3");
        lemma3.setFrequency(1);
        lemmaRepository.save(lemma3);
    }

    @Test
    @DisplayName("Статистика должна корректно подсчитывать общее количество сущностей")
    void getStatistics_shouldReturnCorrectTotalCounts() {
        StatisticsResponse response = statisticsService.getStatistics();

        assertThat(response.isResult()).isTrue();

        var total = response.getStatistics().getTotal();
        assertThat(total.getSites()).isEqualTo(1);
        assertThat(total.getPages()).isEqualTo(2);
        assertThat(total.getLemmas()).isEqualTo(3);
        assertThat(total.isIndexing()).isFalse();

        var detailed = response.getStatistics().getDetailed();
        assertThat(detailed).hasSize(1);
        var siteStats = detailed.get(0);
        assertThat(siteStats.getName()).isEqualTo("Тестовый сайт");
        assertThat(siteStats.getPages()).isEqualTo(2);
        assertThat(siteStats.getLemmas()).isEqualTo(3);
    }
}