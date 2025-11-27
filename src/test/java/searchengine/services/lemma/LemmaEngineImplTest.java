package searchengine.services.lemma;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

public class LemmaEngineImplTest {

    private LemmaEngine lemmaEngine;

    @BeforeEach
    void setUp() {
        lemmaEngine = new LemmaEngineImpl();
    }

    @Test
    @DisplayName("Проверка корректной лемматизации русского текста")
    void getLemmaMap_shouldCorrectlyLemmatizeRussianText() {
        String text = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает.";

        Map<String, Integer> lemmaMap = lemmaEngine.getLemmaMap(text);

        assertThat(lemmaMap).isNotNull();
        assertThat(lemmaMap.get("леопард")).isEqualTo(2);
        assertThat(lemmaMap.containsKey("в")).isFalse();
    }
}