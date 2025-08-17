package searchengine.repositories;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Integer> {
    @Transactional
    void deleteAllBySite(Site site);
    int countBySite(Site site);
    Optional<Page> findBySiteAndPath(Site site, String path);
    long count();

    @Query("SELECT p FROM Page p JOIN FETCH p.site WHERE p IN :pages")
    List<Page> findPagesWithSites(@Param("pages") List<Page> pages);
}