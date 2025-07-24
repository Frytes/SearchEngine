package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.indexing.LemmaDto;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LemmaService {

    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;

    @Transactional
    public void saveLemmasForBatch(List<LemmaDto> batch) {
        if (batch.isEmpty()) {
            return;
        }

        Map<Site, Set<String>> siteToLemmaStrings = new HashMap<>();
        for (LemmaDto dto : batch) {
            siteToLemmaStrings
                    .computeIfAbsent(dto.getPage().getSite(), k -> new HashSet<>())
                    .addAll(dto.getLemmaMap().keySet());
        }

        Map<String, Lemma> existingLemmasMap = new HashMap<>();
        for (Map.Entry<Site, Set<String>> entry : siteToLemmaStrings.entrySet()) {
            List<Lemma> foundLemmas = lemmaRepository.findAllBySiteAndLemmaIn(entry.getKey(), entry.getValue());
            for (Lemma lemma : foundLemmas) {
                existingLemmasMap.put(lemma.getLemma() + "_" + lemma.getSite().getId(), lemma);
            }
        }

        List<Lemma> lemmasToUpdate = new ArrayList<>();
        List<Lemma> lemmasToCreate = new ArrayList<>();

        for (LemmaDto dto : batch) {
            Site site = dto.getPage().getSite();
            for (String lemmaString : dto.getLemmaMap().keySet()) {
                String key = lemmaString + "_" + site.getId();
                if (existingLemmasMap.containsKey(key)) {
                    Lemma lemma = existingLemmasMap.get(key);
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    if (!lemmasToUpdate.contains(lemma)) {
                        lemmasToUpdate.add(lemma);
                    }
                } else {
                    Lemma newLemma = new Lemma();
                    newLemma.setSite(site);
                    newLemma.setLemma(lemmaString);
                    newLemma.setFrequency(1);
                    lemmasToCreate.add(newLemma);
                    existingLemmasMap.put(key, newLemma);
                }
            }
        }

        if (!lemmasToUpdate.isEmpty()) {
            lemmaRepository.saveAll(lemmasToUpdate);
        }
        if (!lemmasToCreate.isEmpty()) {
            lemmaRepository.saveAll(lemmasToCreate);
        }

        List<SearchIndex> searchIndicesToSave = new ArrayList<>();
        for (LemmaDto dto : batch) {
            Page page = dto.getPage();
            Site site = page.getSite();
            for (Map.Entry<String, Integer> lemmaEntry : dto.getLemmaMap().entrySet()) {
                String key = lemmaEntry.getKey() + "_" + site.getId();
                Lemma lemma = existingLemmasMap.get(key);
                if (lemma != null) {
                    SearchIndex searchIndex = new SearchIndex();
                    searchIndex.setPage(page);
                    searchIndex.setLemma(lemma);
                    searchIndex.setRank(lemmaEntry.getValue().floatValue());
                    searchIndicesToSave.add(searchIndex);
                }
            }
        }

        if (!searchIndicesToSave.isEmpty()) {
            indexRepository.saveAll(searchIndicesToSave);
        }
    }

    @Transactional
    public void saveLemmasForPage(Page page, Map<String, Integer> lemmas) {
        if (lemmas.isEmpty()) {
            return;
        }

        Site site = page.getSite();
        Set<String> lemmaStrings = lemmas.keySet();
        List<Lemma> existingLemmas = lemmaRepository.findAllBySiteAndLemmaIn(site, lemmaStrings);
        Map<String, Lemma> existingLemmasMap = existingLemmas.stream()
                .collect(Collectors.toMap(Lemma::getLemma, l -> l, (first, second) -> first));

        List<Lemma> lemmasToUpdate = new ArrayList<>();
        List<Lemma> lemmasToCreate = new ArrayList<>();

        for (String lemmaString : lemmaStrings) {
            if (existingLemmasMap.containsKey(lemmaString)) {
                Lemma lemma = existingLemmasMap.get(lemmaString);
                lemma.setFrequency(lemma.getFrequency() + 1);
                lemmasToUpdate.add(lemma);
            } else {
                Lemma newLemma = new Lemma();
                newLemma.setSite(site);
                newLemma.setLemma(lemmaString);
                newLemma.setFrequency(1);
                lemmasToCreate.add(newLemma);
            }
        }

        if (!lemmasToUpdate.isEmpty()) {
            lemmaRepository.saveAll(lemmasToUpdate);
        }
        if (!lemmasToCreate.isEmpty()) {
            List<Lemma> createdLemmas = lemmaRepository.saveAll(lemmasToCreate);
            createdLemmas.forEach(l -> existingLemmasMap.put(l.getLemma(), l));
        }

        List<SearchIndex> searchIndices = new ArrayList<>();
        for(Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            Lemma lemma = existingLemmasMap.get(entry.getKey());
            if (lemma != null) {
                SearchIndex index = new SearchIndex();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(entry.getValue());
                searchIndices.add(index);
            }
        }
        indexRepository.saveAll(searchIndices);
    }

    @Transactional
    public void decrementLemmaFrequency(Page page) {
        List<SearchIndex> indices = indexRepository.findAllByPage(page);
        if (indices.isEmpty()) {
            return;
        }

        List<Lemma> lemmasToUpdate = indices.stream()
                .map(SearchIndex::getLemma)
                .collect(Collectors.toList());

        for (Lemma lemma : lemmasToUpdate) {
            lemma.setFrequency(lemma.getFrequency() - 1);
        }

        List<Lemma> lemmasToDelete = lemmasToUpdate.stream().filter(l -> l.getFrequency() <= 0).collect(Collectors.toList());
        lemmasToUpdate.removeAll(lemmasToDelete);

        if (!lemmasToUpdate.isEmpty()) {
            lemmaRepository.saveAll(lemmasToUpdate);
        }
        if (!lemmasToDelete.isEmpty()) {
            lemmaRepository.deleteAll(lemmasToDelete);
        }
    }
}