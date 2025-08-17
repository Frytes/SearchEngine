package searchengine.services.statistics;

import searchengine.dto.statistics.StatisticsResponse;

/**
 * Сервис для сбора и предоставления статистики по сайтам.
 */
public interface StatisticsService {
    /**
     * Собирает и формирует полную статистику по всем сайтам,
     * хранящимся в базе данных.
     * @return {@link StatisticsResponse} с общей и детализированной статистикой.
     */
    StatisticsResponse getStatistics();
}