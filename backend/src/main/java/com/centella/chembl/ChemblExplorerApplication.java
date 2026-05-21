package com.centella.chembl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ChemblExplorerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChemblExplorerApplication.class, args);
    }
}
