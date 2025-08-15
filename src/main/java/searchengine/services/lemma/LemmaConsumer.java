package searchengine.services.lemma;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.indexing.LemmaDto;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LemmaConsumer {

    private static final int BATCH_SIZE = 200;

    private final LemmaService lemmaService;
    private final DataCollector dataCollector;

    @PostConstruct
    public void init() {
        new Thread(() -> {
            while (true) {
                try {
                    List<LemmaDto> batch = new ArrayList<>();
                    while (true) {
                        LemmaDto lemmaDto = dataCollector.pollLemmaDto();
                        if (lemmaDto == null) {
                            break;
                        }
                        batch.add(lemmaDto);
                        if (batch.size() >= BATCH_SIZE) {
                            break;
                        }
                    }

                    if (!batch.isEmpty()) {
                        lemmaService.saveLemmasForBatch(batch);
                    } else {
                        Thread.sleep(500);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "LemmaConsumer-Thread").start();
    }
}