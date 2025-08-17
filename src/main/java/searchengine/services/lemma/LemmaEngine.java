package searchengine.services.lemma;

import java.util.Map;

public interface LemmaEngine {
    /**
     * Очищает HTML-код, оставляя только текст.
     * @param html HTML-содержимое страницы
     * @return чистый текст
     */
    String cleanHtml(String html);

    /**
     * Разбирает текст на леммы и подсчитывает их количество.
     * @param text чистый текст
     * @return Карта, где ключ - лемма, значение - количество вхождений
     */
    Map<String, Integer> getLemmaMap(String text);
}