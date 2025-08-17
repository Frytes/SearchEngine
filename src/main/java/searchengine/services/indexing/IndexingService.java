package searchengine.services.indexing;

import searchengine.dto.indexing.IndexingResponse;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.statistics.StatisticsResponse;

/**
 * Сервис, управляющий процессами индексации и поиска.
 * Является основным API для контроллера.
 */
public interface IndexingService {
    /**
     * Запускает процесс полной индексации всех сайтов, указанных в конфигурации.
     * Процесс выполняется асинхронно в отдельном потоке.
     * @return объект с результатом операции (успех или причина ошибки).
     */
    IndexingResponse startIndexing();

    /**
     * Останавливает текущий процесс индексации.
     * @return объект с результатом операции.
     */
    IndexingResponse stopIndexing();

    /**
     * Индексирует или переиндексирует отдельную страницу по ее URL.
     * Если страница уже проиндексирована, старые данные по ней удаляются.
     * @param url URL страницы для индексации.
     * @return объект с результатом операции.
     */
    IndexingResponse indexPage(String url);

    /**
     * Возвращает статистику по всем проиндексированным сайтам.
     * @return объект со статистическими данными.
     */
    StatisticsResponse getStatistics();

    /**
     * Выполняет поиск по проиндексированным сайтам на основе поискового запроса.
     * @param query поисковый запрос.
     * @param site URL сайта, по которому нужно искать (если null, поиск по всем сайтам).
     * @param offset смещение от начала для постраничной выдачи.
     * @param limit количество результатов на странице.
     * @return объект с результатами поиска.
     */
    SearchResponse search(String query, String site, int offset, int limit);
}