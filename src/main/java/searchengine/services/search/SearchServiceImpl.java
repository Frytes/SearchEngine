package searchengine.services.search;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.lemma.LemmaEngine;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaEngine lemmaEngine;
    private final SiteRepository siteRepository;
    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final IndexRepository indexRepository;

    private static final double FREQUENCY_THRESHOLD_PERCENT = 0.95;

    @Override
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        if (query == null || query.isBlank()) {
            return new SearchResponse(false, "Задан пустой поисковый запрос");
        }

        List<Site> sitesToSearch = getSitesToSearch(siteUrl);
        if (sitesToSearch.isEmpty()) {
            return new SearchResponse(false, "Сайты для поиска не найдены или не проиндексированы");
        }

        Set<String> queryLemmaStrings = lemmaEngine.getLemmaMap(query).keySet();
        List<Lemma> foundLemmas = lemmaRepository.findAllByLemmaInAndSiteIn(queryLemmaStrings, sitesToSearch);

        List<Lemma> filteredLemmas = filterAndSortLemmas(foundLemmas, queryLemmaStrings, sitesToSearch);
        if (filteredLemmas.isEmpty() || (!isSingleSiteSearch(siteUrl) && queryLemmaStrings.size() > countUniqueLemmas(filteredLemmas))) {
            return createEmptyResponse();
        }

        List<Page> pages = findPagesWithAllLemmas(filteredLemmas);
        if (pages.isEmpty()) {
            return createEmptyResponse();
        }

        List<SearchData> searchData = calculateRelevanceAndPrepareData(pages, queryLemmaStrings, query);
        List<SearchData> paginatedResults = paginateResults(searchData, offset, limit);

        return new SearchResponse(true, searchData.size(), paginatedResults);
    }

    private List<Site> getSitesToSearch(String siteUrl) {
        if (isSingleSiteSearch(siteUrl)) {
            return siteRepository.findByUrl(siteUrl)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        }
        return siteRepository.findAll();
    }

    private List<Lemma> filterAndSortLemmas(List<Lemma> lemmas, Set<String> queryLemmas, List<Site> sites) {
        if (isSingleSiteSearch(sites) && queryLemmas.size() == 1) {
            lemmas.sort(Comparator.comparingInt(Lemma::getFrequency));
            return lemmas;
        }

        long totalPages = sites.stream().mapToLong(pageRepository::countBySite).sum();
        if (totalPages == 0) {
            return Collections.emptyList();
        }

        Map<String, Integer> lemmaFrequencies = lemmas.stream()
                .collect(Collectors.groupingBy(Lemma::getLemma, Collectors.summingInt(Lemma::getFrequency)));

        Set<String> frequentLemmas = lemmaFrequencies.entrySet().stream()
                .filter(entry -> (double) entry.getValue() / totalPages >= FREQUENCY_THRESHOLD_PERCENT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        List<Lemma> result = lemmas.stream()
                .filter(lemma -> !frequentLemmas.contains(lemma.getLemma()))
                .sorted(Comparator.comparingInt(Lemma::getFrequency))
                .collect(Collectors.toList());

        return result.isEmpty() && !lemmas.isEmpty() ? lemmas : result;
    }

    private List<Page> findPagesWithAllLemmas(List<Lemma> lemmas) {
        if (lemmas.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<Lemma>> lemmasByString = lemmas.stream().collect(Collectors.groupingBy(Lemma::getLemma));
        List<String> sortedLemmaStrings = lemmasByString.keySet().stream()
                .sorted(Comparator.comparingInt(key -> lemmasByString.get(key).stream().mapToInt(Lemma::getFrequency).sum()))
                .collect(Collectors.toList());

        String rarestLemma = sortedLemmaStrings.get(0);
        List<Page> initialPages = findPagesForLemmaString(rarestLemma, lemmasByString);
        if (initialPages.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Page> resultMap = initialPages.stream()
                .collect(Collectors.toMap(Page::getId, Function.identity(), (a, b) -> a));

        for (int i = 1; i < sortedLemmaStrings.size(); i++) {
            String nextLemma = sortedLemmaStrings.get(i);
            List<Page> nextPages = findPagesForLemmaString(nextLemma, lemmasByString);
            Set<Integer> nextPageIds = nextPages.stream().map(Page::getId).collect(Collectors.toSet());
            resultMap.keySet().retainAll(nextPageIds);
            if (resultMap.isEmpty()) {
                return Collections.emptyList();
            }
        }
        return new ArrayList<>(resultMap.values());
    }

    private List<Page> findPagesForLemmaString(String lemmaString, Map<String, List<Lemma>> allLemmas) {
        return allLemmas.get(lemmaString).stream()
                .flatMap(lemma -> indexRepository.findDistinctPagesByLemma(lemma).stream())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<SearchData> calculateRelevanceAndPrepareData(List<Page> pages, Set<String> queryLemmas, String query) {
        List<Page> pagesWithSites = pageRepository.findPagesWithSites(pages);

        Map<Page, Float> pageRelevance = new HashMap<>();
        float maxAbsoluteRelevance = 0.0f;

        for (Page page : pagesWithSites) {
            List<Lemma> lemmasForSite = lemmaRepository.findAllBySiteAndLemmaIn(page.getSite(), queryLemmas);
            if (lemmasForSite.isEmpty()) {
                continue;
            }

            List<SearchIndex> indexes = indexRepository.findAllByPageAndLemmaIn(page, lemmasForSite);
            float absoluteRelevance = indexes.stream().map(SearchIndex::getRank).reduce(0f, Float::sum);

            if (absoluteRelevance > 0) {
                pageRelevance.put(page, absoluteRelevance);
                if (absoluteRelevance > maxAbsoluteRelevance) {
                    maxAbsoluteRelevance = absoluteRelevance;
                }
            }
        }

        if (pageRelevance.isEmpty()) {
            return Collections.emptyList();
        }

        final float finalMaxRelevance = maxAbsoluteRelevance > 0 ? maxAbsoluteRelevance : 1.0f;

        return pageRelevance.entrySet().stream()
                .map(entry -> {
                    Page page = entry.getKey();
                    float absoluteRelevance = entry.getValue();
                    return new SearchData(
                            page.getSite().getUrl(),
                            page.getSite().getName(),
                            page.getPath(),
                            Jsoup.parse(page.getContent()).title(),
                            generateSnippet(page.getContent(), query, queryLemmas),
                            absoluteRelevance / finalMaxRelevance
                    );
                })
                .sorted(Comparator.comparing(SearchData::getRelevance).reversed())
                .collect(Collectors.toList());
    }

    private String generateSnippet(String htmlContent, String originalQuery, Set<String> queryLemmas) {
        String text = Jsoup.parse(htmlContent).text().replaceAll("\\s+", " ").trim();

        Optional<String> bestSentenceOpt = Arrays.stream(text.split("(?<=[.!?])\\s*"))
                .map(s -> new AbstractMap.SimpleEntry<>(s, countLemmaOccurrences(s, queryLemmas)))
                .filter(entry -> entry.getValue() > 0)
                .max(Comparator.comparingInt(AbstractMap.SimpleEntry::getValue))
                .map(AbstractMap.SimpleEntry::getKey);

        String snippetText = bestSentenceOpt.orElse(text.substring(0, Math.min(text.length(), 250)));

        Set<String> queryWords = new HashSet<>(Arrays.asList(originalQuery.toLowerCase().split("\\s+")));
        String[] snippetParts = snippetText.split("(?<=[\\s.,!?;:\"'()])|(?=[\\s.,!?;:\"'()])");

        StringBuilder resultSnippet = new StringBuilder();

        for (String part : snippetParts) {
              if (queryWords.contains(part.toLowerCase())) {
                resultSnippet.append("<b>").append(part).append("</b>");
            } else {
                resultSnippet.append(part);
            }
        }

        return resultSnippet.toString();
    }

    private int countLemmaOccurrences(String sentence, Set<String> queryLemmas) {
        return lemmaEngine.getLemmaMap(sentence).keySet().stream()
                .filter(queryLemmas::contains)
                .mapToInt(l -> 1)
                .sum();
    }

    private List<SearchData> paginateResults(List<SearchData> data, int offset, int limit) {
        data.sort(Comparator.comparing(SearchData::getRelevance).reversed());
        if (offset >= data.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(offset + limit, data.size());
        return data.subList(offset, toIndex);
    }

    private boolean isSingleSiteSearch(String siteUrl) {
        return siteUrl != null;
    }

    private boolean isSingleSiteSearch(List<Site> sites) {
        return sites.size() == 1;
    }

    private long countUniqueLemmas(List<Lemma> lemmas) {
        return lemmas.stream().map(Lemma::getLemma).distinct().count();
    }

    private SearchResponse createEmptyResponse() {
        return new SearchResponse(true, 0, Collections.emptyList());
    }
}