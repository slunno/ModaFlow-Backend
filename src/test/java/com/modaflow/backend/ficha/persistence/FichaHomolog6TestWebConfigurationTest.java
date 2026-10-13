package com.modaflow.backend.ficha.persistence;

import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

class FichaHomolog6TestWebConfigurationTest {
    @Test void normalProfileDoesNotRegisterTestProtocolCustomizer() {
        new ApplicationContextRunner().withUserConfiguration(FichaHomolog6TestWebConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean("fichaHomolog6TestNio2"));
    }

    @Test void testProfileChoosesNio2WithoutStartingServer() {
        new ApplicationContextRunner().withUserConfiguration(FichaHomolog6TestWebConfiguration.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("ficha-homolog6-test"))
                .run(context -> {
                    var factory = new RecordingFactory();
                    @SuppressWarnings("unchecked")
                    var customizer = (WebServerFactoryCustomizer<TomcatServletWebServerFactory>) context.getBean("fichaHomolog6TestNio2");
                    customizer.customize(factory);
                    assertThat(factory.selectedProtocol).isEqualTo("org.apache.coyote.http11.Http11Nio2Protocol");
                });
    }

    private static final class RecordingFactory extends TomcatServletWebServerFactory {
        private String selectedProtocol;
        @Override public void setProtocol(String protocol) {
            super.setProtocol(protocol);
            selectedProtocol = protocol;
        }
    }
}
