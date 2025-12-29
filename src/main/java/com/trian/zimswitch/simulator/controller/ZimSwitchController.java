package com.trian.zimswitch.simulator.controller;

import com.trian.zimswitch.simulator.service.ZimSwitchClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/zimswitch/send")
public class ZimSwitchController {

    private final ZimSwitchClientService clientService;

    public ZimSwitchController(ZimSwitchClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Triggers a 1200 Financial Request.
     * Optional JSON body can override fields (e.g. {"2":"<pan>", "4":"000000001000"}).
     */
    @PostMapping("/financial")
    public ResponseEntity<?> financial(@RequestBody(required = false) Map<String, String> body) throws Exception {
        return ResponseEntity.ok(clientService.sendFinancial(body));
    }

    /**
     * Triggers a 1400 Reversal.
     * Optional JSON body can override fields.
     */
    @PostMapping("/reversal")
    public ResponseEntity<?> reversal(@RequestBody(required = false) Map<String, String> body) throws Exception {
        return ResponseEntity.ok(clientService.sendReversal(body));
    }

    /**
     * Triggers an Echo Test. Defaults to MTI 0800 and F70=301.
     * You can override via JSON body, e.g. {"mti":"1804", "70":"303"}.
     */
    @PostMapping("/echo")
    public ResponseEntity<?> echo(@RequestBody(required = false) Map<String, String> body) throws Exception {
        return ResponseEntity.ok(clientService.sendEcho(body));
    }
}
