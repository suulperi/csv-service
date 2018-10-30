package net.metja.csv;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Data folder health indicator.
 *
 * Created by Janne Metso on 2017-10-25.
 */
@Component
public class DataFolderHealthIndicator extends AbstractHealthIndicator {

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        String dataFolder;
        if(System.getenv(APIController.DATA_FOLDER_KEY) != null) {
            dataFolder = System.getenv(APIController.DATA_FOLDER_KEY);
        } else {
            dataFolder = "/";
        }
        if(new File(dataFolder).exists()) {
            builder.up();
        } else {
            builder.down();
        }

    }
}
