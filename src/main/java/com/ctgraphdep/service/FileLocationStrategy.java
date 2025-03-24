package com.ctgraphdep.service;

import com.ctgraphdep.utils.LoggerUtil;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class FileLocationStrategy {

    @Value("${app.sync.enabled:true}")
    private boolean syncEnabled;

    private final AtomicBoolean forcedLocalMode = new AtomicBoolean(false);

    public FileLocationStrategy() {
        LoggerUtil.initialize(this.getClass(), null);
    }

    public void setForcedLocalMode(boolean forced) {
        boolean previous = forcedLocalMode.get();
        forcedLocalMode.set(forced);

        if (previous != forced) {
            LoggerUtil.info(this.getClass(), String.format("Forced local mode changed from %s to %s", previous, forced));
        }
    }
}