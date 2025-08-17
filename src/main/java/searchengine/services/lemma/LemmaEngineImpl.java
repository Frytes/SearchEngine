package searchengine.services.lemma;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.ru.RussianAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LemmaEngineImpl implements LemmaEngine {

    private final RussianAnalyzer russianAnalyzer;


    public LemmaEngineImpl() {
        this.russianAnalyzer = new RussianAnalyzer();
    }

    @Override
    public String cleanHtml(String html) {
        return Jsoup.parse(html).text();
    }

    @Override
    public Map<String, Integer> getLemmaMap(String text) {
        Map<String, Integer> lemmaMap = new HashMap<>();
        String cleanText = text.toLowerCase().replaceAll("[^а-я\\s]", " ").trim();

        try (var tokenStream = russianAnalyzer.tokenStream("content", new StringReader(cleanText))) {
            CharTermAttribute attribute = tokenStream.addAttribute(CharTermAttribute.class);
            tokenStream.reset();

            while (tokenStream.incrementToken()) {
                String lemma = attribute.toString();
                if (!lemma.isEmpty()) {
                    lemmaMap.put(lemma, lemmaMap.getOrDefault(lemma, 0) + 1);
                }
            }
        } catch (IOException e) {
            System.err.println("Произошла ошибка при анализе текста: " + e.getMessage());
            e.printStackTrace();
        }
        return lemmaMap;
    }
}