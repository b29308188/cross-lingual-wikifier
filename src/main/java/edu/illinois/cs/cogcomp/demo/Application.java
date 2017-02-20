package edu.illinois.cs.cogcomp.demo;

import org.apache.log4j.PropertyConfigurator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.boot.context.embedded.EmbeddedServletContainerCustomizer;

/**
 * Created by ctsai12 on 4/25/16.
 */
@SpringBootApplication
public class Application {
    @Controller
    public class ServletConfig {
        @Bean
        public EmbeddedServletContainerCustomizer containerCustomizer() {
            return (container -> {
                container.setPort(8303);
            });
        }
    }
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
