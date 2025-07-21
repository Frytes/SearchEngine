package searchengine.repositories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Site;

public interface LemmaRepository extends JpaRepository<Lemma, Integer> {
    @Transactional
    void deleteAllBySite(Site site);
    int countBySite(Site site);
}

