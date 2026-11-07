package io.moviesites;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(Settings.class)
public class MovieSitesApplication {
    public static void main(String[] args) { SpringApplication.run(MovieSitesApplication.class, args); }
}
