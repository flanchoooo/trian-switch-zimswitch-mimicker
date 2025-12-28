package com.trian.zimswitch.simulator.server;

import org.jpos.iso.ISOMsg;
import org.jpos.iso.ISOSource;
import org.jpos.iso.ISOException;
import org.jpos.iso.ISORequestListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Minimal ISO8583 request listener that logs and replies with 00.
 */
@Component
public class PosIsoRequestListener implements ISORequestListener {
    private static final Logger log = LoggerFactory.getLogger(PosIsoRequestListener.class);

    @Override
    public boolean process(ISOSource source, ISOMsg m) {
        try {
            String mti;
            try {
                mti = m.getMTI();
            } catch (ISOException e) {
                mti = m.hasField(0) ? m.getString(0) : "";
            }
            String f2 = m.hasField(2) ? maskPan(m.getString(2)) : "";
            String f3 = m.hasField(3) ? m.getString(3) : "";
            String f4 = m.hasField(4) ? m.getString(4) : "";
            String rrn = m.hasField(37) ? m.getString(37) : "";
            log.info("ACCEPT MTI={} PAN={} F3={} F4={} RRN={}", mti, f2, f3, f4, rrn);

            ISOMsg resp = (ISOMsg) m.clone();
            try {
                resp.setResponseMTI();
            } catch (ISOException e) {
                // fallback: attempt to flip 3rd MTI char to '1'
                String req = m.hasField(0) ? m.getString(0) : "";
                if (req != null && req.length() == 4) {
                    String r = req.substring(0, 2) + '1' + req.substring(3);
                    resp.set(0, r);
                }
            }
            if (!resp.hasField(39)) {
                resp.set(39, "00");
            }
            source.send(resp);
        } catch (Exception e) {
            log.warn("Error processing inbound ISO message: {}", e.getMessage());
        }
        return true;
    }

    private static String maskPan(String pan) {
        if (pan == null || pan.length() < 10) return "********";
        int keepStart = 6;
        int keepEnd = 4;
        int maskLen = Math.max(0, pan.length() - keepStart - keepEnd);
        StringBuilder sb = new StringBuilder();
        sb.append(pan, 0, Math.min(keepStart, pan.length()));
        for (int i = 0; i < maskLen; i++) sb.append('*');
        if (pan.length() > keepEnd) sb.append(pan.substring(pan.length() - keepEnd));
        return sb.toString();
    }
}
