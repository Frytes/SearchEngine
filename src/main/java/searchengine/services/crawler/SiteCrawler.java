package searchengine.services.crawler;

import lombok.RequiredArgsConstructor;
import searchengine.dto.crawler.PageProcessingResult;
import searchengine.model.Site;
import searchengine.services.indexing.IndexingServiceImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.RecursiveAction;

@RequiredArgsConstructor
public class SiteCrawler extends RecursiveAction {

    private final PageProcessorService pageProcessor;
    private final int delay;
    private final Site siteEntity;
    private final String pageUrl;
    private final Set<String> visitedUrls;

    @Override
    protected void compute() {
        if (!IndexingServiceImpl.isIndexing() || Thread.currentThread().isInterrupted()) {
            return;
        }

        try {
            Thread.sleep(delay);

            PageProcessingResult result = pageProcessor.process(pageUrl, siteEntity);

            if (result.isSuccess()) {
                createAndForkSubtasks(result.getExtractedLinks());
            }

        } catch (InterruptedException | CancellationException e) {
            Thread.currentThread().interrupt();
            System.out.println("Задача для " + pageUrl + " была прервана.");
        } catch (Exception e) {

            System.err.println("Непредвиденная ошибка в задаче для URL: " + pageUrl);
            e.printStackTrace();
        }
    }

    private void createAndForkSubtasks(Set<String> links) {
        List<SiteCrawler> subTasks = new ArrayList<>();
        for (String link : links) {
            if (visitedUrls.add(link)) {
                SiteCrawler task = new SiteCrawler(
                        pageProcessor, delay, siteEntity, link, visitedUrls);
                subTasks.add(task);
            }
        }
        if (!subTasks.isEmpty()) {
            invokeAll(subTasks);
        }
    }
}