package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.SearchIndex;
import searchengine.model.Site;


public interface IndexRepository extends JpaRepository<SearchIndex, Integer> {
    @Transactional
    void deleteAllByPageSite(Site site);
}

