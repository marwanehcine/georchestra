package org.georchestra.console.events;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.io.IOException;

public class DefaultPropsInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        try {
            applicationContext.getEnvironment().getPropertySources().addFirst(
                    new ResourcePropertySource(new FileSystemResource("/etc/georchestra/default.properties")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}