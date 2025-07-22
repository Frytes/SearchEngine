package searchengine.dto.settings;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "frontend-settings")
@Getter
@Setter
public class FrontendSettings {
    private int updateIntervalMs;
}