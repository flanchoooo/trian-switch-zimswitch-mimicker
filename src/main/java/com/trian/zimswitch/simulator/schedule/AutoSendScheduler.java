package com.trian.zimswitch.simulator.schedule;

import com.trian.zimswitch.simulator.service.ZimSwitchClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AutoSendScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutoSendScheduler.class);

    private final ZimSwitchClientService clientService;

    public AutoSendScheduler(ZimSwitchClientService clientService) {
        this.clientService = clientService;
    }

    // Send an echo request every 10 seconds (fixed rate)
    @Scheduled(initialDelay = 10_000L, fixedRate = 10_000L)
    public void sendEchoPeriodically() {
        try {
            Map<String, Object> resp = clientService.sendEcho(new HashMap<>());
            Map<?, ?> fields = (Map<?, ?>) resp.get("fields");
            Object rc = fields != null ? fields.get("39") : null;
            log.info("Auto ECHO sent -> RC={}", rc);
        } catch (Exception e) {
            log.warn("Auto ECHO failed: {}", e.getMessage());
        }
    }

    // Send a financial request every 15 seconds (fixed rate)
//    @Scheduled(initialDelay = 15_000L, fixedRate = 15_000L)
//    public void sendFinancialPeriodically() {
//        try {
//            Map<String, Object> resp = clientService.sendFinancial(new HashMap<>());
//            Map<?, ?> fields = (Map<?, ?>) resp.get("fields");
//            Object rc = fields != null ? fields.get("39") : null;
//            log.info("Auto FINANCIAL sent -> RC={}", rc);
//        } catch (Exception e) {
//            log.warn("Auto FINANCIAL failed: {}", e.getMessage());
//        }
//    }
}
