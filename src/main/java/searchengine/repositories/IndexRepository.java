package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.SearchIndex;
import searchengine.model.Site;

import java.util.Collection;
import java.util.List;

public interface IndexRepository extends JpaRepository<SearchIndex, Integer> {
    @Transactional
    void deleteAllByPageSite(Site site);
    @Transactional
    void deleteAllByPage(Page page);
    List<SearchIndex> findAllByPage(Page page);
    List<SearchIndex> findAllByPageAndLemmaIn(Page page, List<Lemma> lemmas);

    @Query("SELECT i.page FROM SearchIndex i WHERE i.lemma = :lemma")
    List<Page> findDistinctPagesByLemma(@Param("lemma") Lemma lemma);

    @Query("SELECT i.page FROM SearchIndex i WHERE i.lemma.lemma = :lemmaString")
    List<Page> findDistinctPagesByLemmaString(@Param("lemmaString") String lemmaString);
    List<SearchIndex> findAllByPageInAndLemmaIn(Collection<Page> pages, Collection<Lemma> lemmas);
}