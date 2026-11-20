package com.matthung.ecospeak.quota;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("ecospeak.quota")
public class QuotaProperties {
    private int dailyLimit = 2;
    private int weeklyLimit = 10;
    private String timezone = "Asia/Taipei";

    @PostConstruct
    public void validate() {
        if (dailyLimit < 1 || weeklyLimit < 1 || timezone == null || timezone.isBlank()) {
            throw new IllegalArgumentException("Invalid quota configuration");
        }
        ZoneId.of(timezone);
    }
}
