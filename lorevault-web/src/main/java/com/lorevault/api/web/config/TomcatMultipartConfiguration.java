package com.lorevault.api.web.config;

import com.lorevault.api.config.LoreVaultWebMultipartProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class TomcatMultipartConfiguration implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final LoreVaultWebMultipartProperties multipartProperties;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addConnectorCustomizers(connector ->
                connector.setProperty("maxPartCount", Integer.toString(multipartProperties.maxPartCount())));
    }
}
