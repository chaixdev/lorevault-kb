package com.lorevault.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TomcatMultipartProperties.class)
public class TomcatMultipartConfiguration implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final TomcatMultipartProperties multipartProperties;

    public TomcatMultipartConfiguration(TomcatMultipartProperties multipartProperties) {
        this.multipartProperties = multipartProperties;
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector ->
                connector.setProperty("maxPartCount", Integer.toString(multipartProperties.getMaxPartCount())));
    }
}
