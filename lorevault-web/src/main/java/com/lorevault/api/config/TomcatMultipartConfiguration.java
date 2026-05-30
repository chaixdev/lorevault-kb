package com.lorevault.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TomcatMultipartConfiguration implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${lorevault.web.multipart.max-part-count:200}")
    private int maxPartCount;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector ->
                connector.setProperty("maxPartCount", Integer.toString(maxPartCount)));
    }
}
