package com.yourproject;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to handle MAC and EUI-64 address manipulations.
 * Implements the core logic from the paper.
 */
public class MacUtils {

    // Renamed notations for clarity
    public static final int MAC_48_BIT_LENGTH = 6;
    public static final int EUI_64_IID_LENGTH = 8;
    public static final int IPV6_BYTE_LENGTH = 16;
    public static final int EUI_64_MAGIC_BYTE_1 = 8;
    public static final int EUI_64_MAGIC_BYTE_2 = 9;

    /**
     * Checks if an IPv6 address is likely in EUI-64 format.
     */
    public static boolean isEui64Address(byte[] ipAddressBytes) {
        if (ipAddressBytes.length != IPV6_BYTE_LENGTH) {
            return false;
        }
        return ipAddressBytes[EUI_64_MAGIC_BYTE_1] == (byte) 0xFF &&
               ipAddressBytes[EUI_64_MAGIC_BYTE_2] == (byte) 0xFE;
    }

    /**
     * Extracts the 48-bit WAN MAC from the 64-bit IID.
     * This reverses the EUI-64 standard process.
     */
    public static byte[] extractMacFromIid(byte[] iidBytes) {
        if (iidBytes.length != EUI_64_IID_LENGTH) {
            return null;
        }
        byte[] macBytes = new byte[MAC_48_BIT_LENGTH];
        macBytes[0] = (byte) (iidBytes[0] ^ 0x02); // Flip the "global/local" bit
        macBytes[1] = iidBytes[1];
        macBytes[2] = iidBytes[2];
        // Skip iidBytes[3] and iidBytes[4] (0xFF, 0xFE)
        macBytes[3] = iidBytes[5];
        macBytes[4] = iidBytes[6];
        macBytes[5] = iidBytes[7];
        return macBytes;
    }

    /**
     * Generates potential BSSIDs and maps them to their source offset.
     * Uses the paper's validated offset range of [-8, +8].
     *
     * **THIS IS THE FIX:** Returns Map<Integer, String> to match LocationFinder.
     */
    public static Map<Integer, String> getPotentialBssidsWithOffsets(byte[] wanMac) {
        Map<Integer, String> bssids = new HashMap<>();
        long wanMacLong = bytesToLong(wanMac);

        for (int offset = -8; offset <= 8; offset++) {
            long bssidLong = wanMacLong + offset;
            bssids.put(offset, longToMacString(bssidLong));
        }
        return bssids;
    }

    // --- Helper Methods ---
    
    public static String bytesToMacString(byte[] mac) {
        if (mac == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02x", mac[i]));
            if (i < mac.length - 1) sb.append(":");
        }
        return sb.toString();
    }
    
    public static String longToMacString(long macLong) {
        byte[] macBytes = new byte[MAC_48_BIT_LENGTH];
        for (int i = 5; i >= 0; i--) {
            macBytes[i] = (byte) (macLong & 0xFF);
            macLong >>= 8;
        }
        return bytesToMacString(macBytes);
    }
    
    public static long bytesToLong(byte[] bytes) {
        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }
}