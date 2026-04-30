package com.lorevault.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lorevault.web.multipart")
public class TomcatMultipartProperties {

    /**
     * Raised above Tomcat's default (50) because the batch ingestion UI submits several
     * multipart fields per staged file: clientIds, chapterNumbers, chapterTitles, and files.
     */
    private int maxPartCount = 200;

    public int getMaxPartCount() {
        return maxPartCount;
    }

    public void setMaxPartCount(int maxPartCount) {
        this.maxPartCount = maxPartCount;
    }
}
