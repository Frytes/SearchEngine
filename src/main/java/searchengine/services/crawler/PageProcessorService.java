package searchengine.services.crawler;

import searchengine.dto.crawler.PageProcessingResult;
import searchengine.model.Site;

/**
 * Сервис, отвечающий за обработку одной веб-страницы.
 * Выполняет скачивание, парсинг, сохранение в БД и извлечение ссылок.
 */
public interface PageProcessorService {
    /**
     * Обрабатывает одну страницу по-заданному URL.
     * <p>
     * Включает в себя:
     * - Скачивание HTML-содержимого.
     * - Сохранение сущности Page в базу данных.
     * - Лемматизацию контента и передачу его в DataCollector.
     * - Извлечение и фильтрацию ссылок для дальнейшего обхода.
     *
     * @param pageUrl URL страницы для обработки.
     * @param siteEntity сущность сайта, к которому принадлежит страница.
     * @return {@link PageProcessingResult}, содержащий статус успеха и найденные ссылки.
     */
    PageProcessingResult process(String pageUrl, Site siteEntity);
}