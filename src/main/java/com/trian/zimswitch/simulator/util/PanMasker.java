package com.trian.zimswitch.simulator.util;

public final class PanMasker {
    private PanMasker() {}

    /**
     * Masks PAN leaving first 6 and last 4 digits visible, preserving length.
     */
    public static String mask(String pan) {
        if (pan == null) return null;
        String digits = pan.replaceAll("\\D", "");
        if (digits.length() <= 10) return pan; // too short; return as-is
        int unmaskedStart = 6;
        int unmaskedEnd = 4;
        int toMask = digits.length() - unmaskedStart - unmaskedEnd;
        if (toMask <= 0) return pan;
        StringBuilder sb = new StringBuilder();
        sb.append(digits, 0, unmaskedStart);
        for (int i = 0; i < toMask; i++) sb.append('X');
        sb.append(digits.substring(digits.length() - unmaskedEnd));
        return sb.toString();
    }
}

