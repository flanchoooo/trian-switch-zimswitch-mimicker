package com.trian.zimswitch.simulator.iso;

import com.trian.zimswitch.simulator.util.StanGenerator;
import org.jpos.iso.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds minimal ISO8583 requests for financial, reversal, and echo.
 * Fields populated:
 *   0: MTI
 *   2: PAN
 *   3: Processing Code
 *   4: Amount, transaction
 *   7: Transmission date & time (MMDDhhmmss)
 *   11: STAN
 *   12: Local time (hhmmss)
 *   13: Local date (MMDD)
 *   37: RRN (12 digits)
 *   41: Terminal ID (8)
 *   42: Merchant ID (15)
 *   49: Currency code (3)
 *   52: PIN block (dummy)
 *   64: MAC (dummy)
 */
public class IsoMessageBuilder {
    private final ISOPackager packager;

    public IsoMessageBuilder(ISOPackager packager) {
        this.packager = packager;
    }

    public ISOMsg build1200(Map<String, String> overrides) {
        return baseFinancial("1200", overrides);
    }

    public ISOMsg build1400(Map<String, String> overrides) {
        return baseFinancial("1400", overrides);
    }

    public ISOMsg build1804(Map<String, String> overrides) throws ISOException {
        ISOMsg m = new ISOMsg();
        m.setPackager(packager);
        m.setMTI("1804");

        String f7 = ISODate.formatDate(new Date(), "MMddHHmmss");

        // Transmission Date & Time (MMDDhhmmss)
        m.set(7, overrides.getOrDefault("7", f7));

        // System Trace Audit Number (STAN)
        m.set(11, overrides.getOrDefault("11", IsoUtil.generateStan()));

        // Local Transaction Time (hhmmss)
        m.set(12, overrides.getOrDefault("12", IsoUtil.localTime()));

        // Local Transaction Date (MMDD)
        m.set(13, overrides.getOrDefault("13", IsoUtil.localDate()));

        // Network Management Information Code
        // 301 = Echo Test
        m.set(70, overrides.getOrDefault("70", "301"));

        return m;
    }

    private ISOMsg baseFinancial(String mti, Map<String, String> overrides) {
        ISOMsg m = base(mti, overrides);
        m.set(2, overrides.getOrDefault("2", "4929390123456781"));
        m.set(3, overrides.getOrDefault("3", "000000"));
        m.set(4, overrides.getOrDefault("4", "000000001000")); // 1000 cents (10.00)
        // Placeholders for PIN/MAC if packager defines them (when using ISOBasePackager)
        if (hasFieldDefinition(m.getPackager(), 52)) {
            m.set(52, ISOUtil.hex2byte(overrides.getOrDefault("52", "0000000000000000"))); // 8-byte zero PIN block
        }
        if (hasFieldDefinition(m.getPackager(), 64)) {
            m.set(64, ISOUtil.hex2byte(overrides.getOrDefault("64", "0000000000000000"))); // 8-byte zero MAC
        }
        return m;
    }

    private ISOMsg base(String mti, Map<String, String> overrides) {
        ISOMsg m = new ISOMsg();
        m.setPackager(packager);
        try {
            m.setMTI(mti);
        } catch (ISOException e) {
            // Fallback to setting field 0 directly if setMTI is not supported
            m.set(0, mti);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("UTC"));

        String stan = overrides.getOrDefault("11", StanGenerator.nextStan());
        String rrn = overrides.getOrDefault("37", generateRRN(now, stan));

        m.set(7, format(now, "MMddHHmmss"));
        m.set(11, stan);
        m.set(12, format(now, "HHmmss"));
        m.set(13, format(now, "MMdd"));
        m.set(37, rrn);
        m.set(41, overrides.getOrDefault("41", "TERM1234"));
        m.set(42, overrides.getOrDefault("42", "MRC123456789012"));
        m.set(49, overrides.getOrDefault("49", "932")); // Default ZWL
        return m;
    }

    private static String format(ZonedDateTime dt, String pattern) {
        return dt.format(java.time.format.DateTimeFormatter.ofPattern(pattern));
    }

    private static String generateRRN(ZonedDateTime dt, String stan) {
        // Simple 12-digit RRN: mmddHH + last6(stan/epoch seconds)
        String prefix = format(dt, "MMddHH");
        String tail = String.format("%06d", Math.abs((int) (System.currentTimeMillis() / 1000) % 1000000));
        String rrn = (prefix + tail);
        if (rrn.length() > 12) rrn = rrn.substring(rrn.length() - 12);
        return rrn;
    }

    private static boolean hasFieldDefinition(ISOPackager packager, int field) {
        try {
            if (packager instanceof org.jpos.iso.ISOBasePackager) {
                return ((org.jpos.iso.ISOBasePackager) packager).getFieldPackager(field) != null;
            }
        } catch (ArrayIndexOutOfBoundsException ignored) {
            return false;
        }
        // If packager is not ISOBasePackager, we can't reliably check; be conservative
        return false;
    }


    public final class IsoUtil {

        private IsoUtil() {}

        public static String transmissionDateTime() {
            return LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMddHHmmss"));
        }

        public static String localTime() {
            return LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HHmmss"));
        }

        public static String localDate() {
            return LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("MMdd"));
        }

        public static String generateStan() {
            return String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        }
    }

}
