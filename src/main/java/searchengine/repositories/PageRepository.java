package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Integer> {
    @Transactional
    void deleteAllBySite(Site site);
    int countBySite(Site site);
    Optional<Page> findBySiteAndPath(Site site, String path);
}