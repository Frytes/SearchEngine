package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    @Transactional
    void deleteAllBySite(Site site);
    int countBySite(Site site);
    List<Lemma> findAllBySiteAndLemmaIn(Site site, Collection<String> lemmas);
    Optional<Lemma> findBySiteAndLemma(Site site, String lemmaString);
}



