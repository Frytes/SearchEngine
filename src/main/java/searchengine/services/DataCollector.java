package searchengine.services;

import org.springframework.stereotype.Component;
import searchengine.dto.indexing.LemmaDto;

import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class DataCollector {
    private final ConcurrentLinkedQueue<LemmaDto> lemmaQueue = new ConcurrentLinkedQueue<>();

    public void addLemmaDto(LemmaDto lemmaDto) {
        lemmaQueue.add(lemmaDto);
    }

    public LemmaDto pollLemmaDto() {
        return lemmaQueue.poll();
    }
    public int getQueueSize() {
        return lemmaQueue.size();
    }
}