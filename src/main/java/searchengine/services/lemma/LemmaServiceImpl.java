package searchengine.services.lemma;

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

import javax.persistence.EntityManager;
import java.util.*;


@Service
@RequiredArgsConstructor
public class LemmaServiceImpl implements LemmaService {
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void saveLemmasForBatch(List<LemmaDto> batch) {
        if (batch.isEmpty()) {
            return;
        }

        Map<Site, Set<String>> siteToLemmas = new HashMap<>();
        for (LemmaDto dto : batch) {
            if (dto.getPage() == null || dto.getPage().getSite() == null) {
                continue;
            }
            siteToLemmas.computeIfAbsent(dto.getPage().getSite(), k -> new HashSet<>())
                    .addAll(dto.getLemmaMap().keySet());
        }

        Map<String, Lemma> existingLemmas = new HashMap<>();
        for (Map.Entry<Site, Set<String>> entry : siteToLemmas.entrySet()) {
            lemmaRepository.findAllBySiteAndLemmaIn(entry.getKey(), entry.getValue())
                    .forEach(lemma -> existingLemmas.put(
                            lemma.getLemma() + "_" + lemma.getSite().getId(),
                            lemma
                    ));
        }

        List<Lemma> toUpdate = new ArrayList<>();
        List<Lemma> toCreate = new ArrayList<>();
        List<SearchIndex> indices = new ArrayList<>();

        for (LemmaDto dto : batch) {
            if (dto.getPage() == null || dto.getPage().getSite() == null) {
                continue;
            }

            Site site = dto.getPage().getSite();
            Page page = dto.getPage();

            for (Map.Entry<String, Integer> entry : dto.getLemmaMap().entrySet()) {
                String lemmaKey = entry.getKey() + "_" + site.getId();
                Lemma lemma = existingLemmas.get(lemmaKey);

                if (lemma != null) {
                    lemma.setFrequency(lemma.getFrequency() + 1);
                    if (!toUpdate.contains(lemma)) {
                        toUpdate.add(lemma);
                    }
                } else {
                    lemma = new Lemma();
                    lemma.setSite(site);
                    lemma.setLemma(entry.getKey());
                    lemma.setFrequency(1);
                    toCreate.add(lemma);
                    existingLemmas.put(lemmaKey, lemma);
                }

                SearchIndex index = new SearchIndex();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank(entry.getValue().floatValue());
                indices.add(index);
            }
        }

        if (!toUpdate.isEmpty()) {
            lemmaRepository.saveAll(toUpdate);
        }
        if (!toCreate.isEmpty()) {
            List<Lemma> created = lemmaRepository.saveAll(toCreate);
            created.forEach(l -> existingLemmas.put(l.getLemma() + "_" + l.getSite().getId(), l));
        }

        if (!indices.isEmpty()) {
            indexRepository.saveAll(indices);
        }
        entityManager.flush();
    }

    @Override
    @Transactional
    public void saveLemmasForPage(Page page, Map<String, Integer> lemmas) {
        if (page == null || page.getSite() == null || lemmas == null) {
            return;
        }

        Site site = page.getSite();
        List<SearchIndex> indices = new ArrayList<>();

        lemmas.forEach((lemmaStr, count) -> {
            Lemma lemma = lemmaRepository.findBySiteAndLemma(site, lemmaStr)
                    .map(existingLemma -> {
                        existingLemma.setFrequency(existingLemma.getFrequency() + 1);
                        return lemmaRepository.save(existingLemma);
                    })
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setSite(site);
                        newLemma.setLemma(lemmaStr);
                        newLemma.setFrequency(1);
                        return lemmaRepository.save(newLemma);
                    });

            SearchIndex index = new SearchIndex();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank(count.floatValue());
            indices.add(index);
        });

        if (!indices.isEmpty()) {
            indexRepository.saveAll(indices);
        }
    }

    @Override
    @Transactional
    public void decrementLemmaFrequency(Page page) {
        if (page == null) {
            return;
        }

        List<SearchIndex> indices = indexRepository.findAllByPage(page);
        if (indices.isEmpty()) {
            return;
        }

        indexRepository.deleteAll(indices);

        indices.stream()
                .map(SearchIndex::getLemma)
                .distinct()
                .forEach(lemma -> {
                    int newFrequency = lemma.getFrequency() - 1;
                    if (newFrequency <= 0) {
                        lemmaRepository.delete(lemma);
                    } else {
                        lemma.setFrequency(newFrequency);
                        lemmaRepository.save(lemma);
                    }
                });
    }
}