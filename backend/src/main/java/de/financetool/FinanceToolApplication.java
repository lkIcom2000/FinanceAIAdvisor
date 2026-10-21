package de.financetool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Fine")
@SpringBootApplication
@ConfigurationPropertiesScan
public class FinanceToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceToolApplication.class, args);
    }
}
