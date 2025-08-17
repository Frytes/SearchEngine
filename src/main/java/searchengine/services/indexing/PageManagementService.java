package searchengine.services.indexing;

import searchengine.dto.indexing.IndexingResponse;

/**
 * Сервис для управления индексацией отдельных страниц.
 */
public interface PageManagementService {
    /**
     * Индексирует или переиндексирует одну страницу по ее URL.
     * <p>
     * Проверяет, что URL принадлежит одному из сайтов в конфигурации.
     * Если страница уже существует в индексе, она будет удалена и проиндексирована заново.
     *
     * @param url абсолютный URL страницы для индексации.
     * @return {@link IndexingResponse} с результатом операции.
     */
    IndexingResponse indexPage(String url);
}