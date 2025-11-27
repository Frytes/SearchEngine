package searchengine.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.liquibase.enabled=false"
})
class ApplicationContextTest {

    @Test
    @DisplayName("Приложение запускается и успешно работает")
    void contextLoads() {
        // Этот тест проверит, что приложение в принципе может запуститься в тестовом режиме.
    }
}