package com.trian.zimswitch.simulator.service;

import com.trian.zimswitch.simulator.iso.IsoMessageBuilder;
import com.trian.zimswitch.simulator.iso.IsoResponseMapper;
import com.trian.zimswitch.simulator.util.PanMasker;
import org.jpos.iso.ISOChannel;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class ZimSwitchClientService {
    private static final Logger log = LoggerFactory.getLogger(ZimSwitchClientService.class);

    private final ISOChannel channel;
    private final IsoMessageBuilder builder;

    public ZimSwitchClientService(ISOChannel channel, IsoMessageBuilder builder) {
        this.channel = channel;
        this.builder = builder;
    }

    public Map<String, Object> sendFinancial(Map<String, String> overrides) throws Exception {
        ISOMsg req = builder.build1200(orDefault(overrides));
        ISOMsg resp = sendAndReceive(req);
        validateResponse(req, resp);
        return IsoResponseMapper.toJson(resp);
    }

    public Map<String, Object> sendReversal(Map<String, String> overrides) throws Exception {
        ISOMsg req = builder.build1400(orDefault(overrides));
        ISOMsg resp = sendAndReceive(req);
        validateResponse(req, resp);
        return IsoResponseMapper.toJson(resp);
    }

    public Map<String, Object> sendEcho(Map<String, String> overrides) throws Exception {
        ISOMsg req = builder.buildEcho(orDefault(overrides));
        ISOMsg resp = sendAndReceive(req);
        validateResponse(req, resp);
        return IsoResponseMapper.toJson(resp);
    }

    private Map<String, String> orDefault(Map<String, String> m) {
        return m == null ? new HashMap<>() : m;
    }

    private ISOMsg sendAndReceive(ISOMsg request) throws Exception {
        maskAndLog("REQUEST", request);
        ISOMsg response;
        synchronized (channel) {
            ensureConnected();
            try {
                channel.send(request);
                response = channel.receive();
            } catch (Exception e) {
                // Attempt one reconnect and retry once
                log.warn("Channel send/receive error: {}. Reconnecting once...", e.getMessage());
                reconnectSilently();
                channel.send(request);
                response = channel.receive();
            }
        }
        maskAndLog("RESPONSE", response);
        return response;
    }

    private void ensureConnected() throws Exception {
        if (!channel.isConnected()) {
            log.info("ISOChannel not connected. Connecting...");
            channel.connect();
            log.info("ISOChannel connected");
        }
    }

    private void reconnectSilently() {
        try {
            if (channel.isConnected()) channel.disconnect();
        } catch (Exception ignore) { }
        try {
            channel.connect();
        } catch (Exception ex) {
            throw new RuntimeException("Reconnect failed: " + ex.getMessage(), ex);
        }
    }

    private static void maskAndLog(String label, ISOMsg m) {
        String pan = m.hasField(2) ? m.getString(2) : null;
        String masked = pan != null ? PanMasker.mask(pan) : null;
        String mti = null;
        try {
            mti = m.getMTI();
        } catch (ISOException e) {
            try { mti = m.getString(0); } catch (Exception ignored) { }
        }
        String f3 = m.hasField(3) ? m.getString(3) : "";
        String f4 = m.hasField(4) ? m.getString(4) : "";
        String rrn = m.hasField(37) ? m.getString(37) : "";
        log.info("{} MTI={} PAN={} F3={} F4={} RRN={}", label, mti, masked, f3, f4, rrn);
    }

    private static void validateResponse(ISOMsg request, ISOMsg response) throws Exception {
        String reqMTI = request.getMTI();
        String expected = reqMTI.substring(0, 2) + "1" + reqMTI.substring(3);
        String actual = response.getMTI();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Invalid response MTI. Expected=" + expected + ", actual=" + actual);
        }
        if (!response.hasField(39)) {
            throw new IllegalStateException("Missing field 39 (Response Code) in response");
        }
    }
}
