package searchengine.services.lemma;

import searchengine.dto.indexing.LemmaDto;
import searchengine.model.Page;

import java.util.List;
import java.util.Map;

/**
 * Сервис для работы с леммами и поисковым индексом.
 */
public interface LemmaService {
    /**
     * Сохраняет в базу данных леммы и соответствующие им индексы для пакета страниц.
     * Оптимизирован для массовых операций.
     * @param batch список DTO, каждый из которых содержит страницу и карту ее лемм.
     */
    void saveLemmasForBatch(List<LemmaDto> batch);

    /**
     * Сохраняет леммы и индексы для одной конкретной страницы.
     * Используется при индивидуальной индексации.
     * @param page сущность страницы.
     * @param lemmas карта лемм, найденных на странице (лемма -> количество).
     */
    void saveLemmasForPage(Page page, Map<String, Integer> lemmas);

    /**
     * Уменьшает частоту (frequency) лемм, связанных с удаляемой страницей.
     * Если частота леммы становится равной нулю, лемма удаляется из БД.
     * @param page страница, которая будет удалена.
     */
    void decrementLemmaFrequency(Page page);
}