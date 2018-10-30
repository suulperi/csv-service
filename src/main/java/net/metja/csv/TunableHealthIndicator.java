package net.metja.csv;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Created by Janne Metso on 2017-10-27.
 */
@Component
public class TunableHealthIndicator extends AbstractHealthIndicator {

    private boolean ok = true;

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if(ok) {
            builder.up();
        } else {
            builder.down();
        }
    }

    public void setOk(boolean ok) {
        this.ok = ok;
    }

}