package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Site;

import java.util.List;

public interface IndexRepository extends JpaRepository<SearchIndex, Integer> {
    @Transactional
    void deleteAllByPageSite(Site site);
    @Transactional
    void deleteAllByPage(Page page);
    List<SearchIndex> findAllByPage(Page page);
}