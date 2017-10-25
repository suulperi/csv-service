package net.metja.csv;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.bean.ColumnPositionMappingStrategy;
import au.com.bytecode.opencsv.bean.CsvToBean;
import net.metja.csv.pto.PersonPTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileReader;
import java.util.List;

/**
 * Created by Janne Metso on 7/13/17.
 */
@RestController
public class APIController {

    private final String DATA_FOLDER_KEY = "DATA_FOLDER";
    private Logger logger = LoggerFactory.getLogger(APIController.class);
    private String dataFolder = null;

    public APIController() {
        if(System.getenv(this.DATA_FOLDER_KEY) != null) {
            this.dataFolder = System.getenv(this.DATA_FOLDER_KEY);
            this.logger.info("Data folder is "+this.dataFolder);
        } else {
            this.logger.warn("Env variable "+this.DATA_FOLDER_KEY+" not set! Setting data folder as /.");
            this.dataFolder = "/";
        }
    }

    @RequestMapping(value = "/csv/v1/read/{filename}", produces= MediaType.APPLICATION_JSON_VALUE, method= RequestMethod.GET)
    public ResponseEntity<List<PersonPTO>> readPersonCSV(@PathVariable(name = "filename")String filename) {
        logger.info("Filename: "+filename);
        try {
            CSVReader csvReader = new CSVReader(new FileReader(new File(this.dataFolder, filename)), ',', '"', 0);
            CsvToBean<PersonPTO> csv = new CsvToBean<>();
            List<PersonPTO> persons = csv.parse(this.setColumnMapping(), csvReader);
            return new ResponseEntity<List<PersonPTO>>(persons, HttpStatus.OK);
        } catch(java.io.FileNotFoundException e) {
            return new ResponseEntity<List<PersonPTO>>(HttpStatus.BAD_REQUEST);
        }
    }

    @RequestMapping(value = "/csv/v1/read/{filename}/{id}", produces= MediaType.APPLICATION_JSON_VALUE, method= RequestMethod.GET)
    public ResponseEntity<PersonPTO> readPersonCSVWithID(@PathVariable(name = "filename")String filename, @PathVariable(name = "id")int id) {
        logger.info("Filename: "+filename);
        try {
            CSVReader csvReader = new CSVReader(new FileReader(new File(this.dataFolder, filename)), ',', '"', 0);
            CsvToBean<PersonPTO> csv = new CsvToBean<>();
            List<PersonPTO> persons = csv.parse(this.setColumnMapping(), csvReader);
            for(PersonPTO person: persons) {
                if(person.getId() == id) {
                    return new ResponseEntity<PersonPTO>(person, HttpStatus.OK);
                }
            }
            return new ResponseEntity<PersonPTO>(HttpStatus.NOT_FOUND);
        } catch(java.io.FileNotFoundException e) {
            return new ResponseEntity<PersonPTO>(HttpStatus.BAD_REQUEST);
        }
    }

    private ColumnPositionMappingStrategy<PersonPTO> setColumnMapping() {
        ColumnPositionMappingStrategy strategy = new ColumnPositionMappingStrategy();
        strategy.setType(PersonPTO.class);
        String[] columns = new String[] {"id", "firstname", "lastname"};
        strategy.setColumnMapping(columns);
        return strategy;
    }

    /**
     * Hello world for connectivity / deployment testing
     *
     * @param name
     * @return
     */
    @RequestMapping(value="/csv/v1/hello/{name}", produces= MediaType.APPLICATION_JSON_VALUE, method= RequestMethod.GET)
    public ResponseEntity<String> hello(@PathVariable(value="name", required=true)String name) {
        return new ResponseEntity("{ \"hello\":\""+name+"!\" }", HttpStatus.OK);
    }

    /**
     * Generates load via a busy loop for a given amount of seconds
     *
     * @param time time in seconds
     * @return
     */
    @RequestMapping(value="/csv/v1/load/{time}", produces = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.GET)
    public ResponseEntity<String> generateLoad(@PathVariable(value="time", required = true)int time) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long begin = System.currentTimeMillis();
                while(System.currentTimeMillis() < (begin+(time*1000))) {
                    int result = 1+1;
                }
            }
        }).start();
        return new ResponseEntity<String>("{\"load\": \""+time+"s\"}", HttpStatus.OK);
    }

}
