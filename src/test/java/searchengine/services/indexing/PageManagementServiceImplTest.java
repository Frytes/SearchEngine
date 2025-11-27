package searchengine.services.indexing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import searchengine.config.SitesList;
import searchengine.dto.indexing.IndexingResponse;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
public class PageManagementServiceImplTest {

    @Autowired
    private PageManagementService pageManagementService;

    @Autowired
    private SiteRepository siteRepository;
    @Autowired
    private PageRepository pageRepository;
    @Autowired
    private LemmaRepository lemmaRepository;
    @Autowired
    private IndexRepository indexRepository;

    @MockBean
    private SitesList sitesConfig;

    private final String PAGE_URL = "https://test.com/page1";
    private final String SITE_URL = "https://test.com";
    private final String SITE_NAME = "Тестовый сайт";

    @BeforeEach
    void setUp() {

        searchengine.config.Site siteConfig = new searchengine.config.Site();
        siteConfig.setUrl(SITE_URL);
        siteConfig.setName(SITE_NAME);
        when(sitesConfig.getSites()).thenReturn(List.of(siteConfig));

        indexRepository.deleteAll();
        lemmaRepository.deleteAll();
        pageRepository.deleteAll();
        siteRepository.deleteAll();
    }

    @Test
    @DisplayName("Индексация одной страницы должна корректно создавать записи в БД")
    void indexPage_shouldCreatePageLemmasAndIndexes() {

        IndexingResponse response = pageManagementService.indexPage(PAGE_URL);

        assertThat(response.isResult()).isFalse();
        assertThat(response.getError()).contains("Не удалось получить доступ к странице");

        IndexingResponse disallowedResponse = pageManagementService.indexPage("https://another-site.com");
        assertThat(disallowedResponse.isResult()).isFalse();
        assertThat(disallowedResponse.getError()).isEqualTo("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
    }
}