package searchengine.dto.search;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SearchResponse {

    private boolean result;
    private String error;
    private int count;
    private List<SearchData> data;

    /**
     * Конструктор для ответов с ошибкой.
     * @param result всегда false для ошибок.
     * @param error текст ошибки.
     */
    public SearchResponse(boolean result, String error) {
        this.result = result;
        this.error = error;
    }

    /**
     * Конструктор для успешных поисковых ответов.
     * @param result всегда true для успеха.
     * @param count общее количество найденных результатов.
     * @param data список найденных страниц.
     */
    public SearchResponse(boolean result, int count, List<SearchData> data) {
        this.result = result;
        this.count = count;
        this.data = data;
    }
}