package com.trian.zimswitch.simulator.iso;

import com.trian.zimswitch.simulator.util.PanMasker;
import org.jpos.iso.*;

import java.util.LinkedHashMap;
import java.util.Map;

public class IsoResponseMapper {
    public static Map<String, Object> toJson(ISOMsg m) {
        Map<String, Object> out = new LinkedHashMap<>();
        String mti = null;
        try {
            mti = m.getMTI();
        } catch (ISOException e) {
            // Fallback to field 0 if available
            try { mti = m.getString(0); } catch (Exception ignored) { }
        }
        out.put("mti", mti);
        Map<String, String> fields = new LinkedHashMap<>();
        int max = m.getMaxField();
        for (int i = 0; i <= max; i++) {
            if (!m.hasField(i)) continue;
            String value;
            if (m.getBytes(i) != null && isBinaryField(m, i)) {
                value = ISOUtil.hexString(m.getBytes(i));
            } else {
                value = m.getString(i);
            }
            if (i == 2 && value != null) {
                value = PanMasker.mask(value);
            }
            fields.put(String.valueOf(i), value);
        }
        out.put("fields", fields);
        return out;
    }

    private static boolean isBinaryField(ISOMsg m, int field) {
        // Inspect the actual component type
        ISOComponent comp = m.getComponent(field);
        return (comp instanceof ISOBinaryField) || (comp instanceof ISOBitMap);
    }
}
