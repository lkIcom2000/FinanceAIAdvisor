package de.financetool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(systemName = "Fine")
@SpringBootApplication
public class FinanceToolApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinanceToolApplication.class, args);
    }
}
