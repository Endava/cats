package com.endava.cats;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.jansi.Ansi;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

@ApplicationScoped
public class StartStopListener {

    void onStart(@Observes StartupEvent ev) {
        String ansiEnabled = System.getenv().get("NO_COLOR");
        Ansi.setEnabled(!StringUtils.isNotEmpty(ansiEnabled));
    }

    void onStop(@Observes ShutdownEvent ev) {
        //ntd
    }
}
