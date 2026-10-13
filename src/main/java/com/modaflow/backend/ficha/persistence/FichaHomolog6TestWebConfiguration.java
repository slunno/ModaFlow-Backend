package com.modaflow.backend.ficha.persistence;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Somente o teste isolado: evita o selector/pipe AF_UNIX problemático desta JVM Windows. */
@Configuration(proxyBeanMethods = false)
@Profile("ficha-homolog6-test")
public class FichaHomolog6TestWebConfiguration {
    @Bean public WebServerFactoryCustomizer<TomcatServletWebServerFactory> fichaHomolog6TestNio2() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
