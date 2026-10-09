package com.example.pikpak.web;

import com.example.pikpak.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "pikpak.enabled", havingValue = "true")
@Import({PikPakController.class, PikPakErrors.class})
public class PikPakConfiguration {
    @Bean(destroyMethod = "close") PikPakDuplicateScanner pikPakDuplicateScanner(PikPakModule module) {
        return new PikPakDuplicateScanner(module);
    }
    @Bean PikPakModule pikPakModule(JdbcTemplate jdbc, Environment env) {
        return new PikPakModule(new JdbcPikPakStore(jdbc, env.getRequiredProperty("pikpak.secret-key")),
            new HttpPikPakGateway(env.getProperty("pikpak.proxy-url", "")), Clock.systemUTC());
    }
}
