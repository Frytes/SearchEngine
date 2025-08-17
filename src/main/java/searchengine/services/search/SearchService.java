package searchengine.services.search;

import searchengine.dto.search.SearchResponse;

/**
 * Сервис, отвечающий за выполнение поисковых запросов.
 */
public interface SearchService {
    /**
     * Выполняет поиск по проиндексированным сайтам.
     *
     * @param query поисковый запрос.
     * @param site  URL сайта для поиска (если null - поиск по всем).
     * @param offset смещение для постраничной выдачи.
     * @param limit  количество результатов.
     * @return {@link SearchResponse} с результатами поиска или ошибкой.
     */
    SearchResponse search(String query, String site, int offset, int limit);
}