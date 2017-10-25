package net.metja.csv;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.util.Arrays;

/**
 * Created by Janne Metso on 7/13/17.
 */
@SpringBootApplication
public class CSVService {

    public static void main(String[] args) {
        SpringApplication.run(CSVService.class, args);
    }

}
