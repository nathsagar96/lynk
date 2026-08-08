package ly.lynk.click;

public final class IpAnonymizer {

    private IpAnonymizer() {}

    public static String anonymize(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return ipAddress;
        }
        if (ipAddress.contains(":")) {
            return anonymizeIpv6(ipAddress);
        }
        return anonymizeIpv4(ipAddress);
    }

    private static String anonymizeIpv4(String ip) {
        int lastDot = ip.lastIndexOf('.');
        if (lastDot < 0) {
            return ip;
        }
        return ip.substring(0, lastDot) + ".0";
    }

    private static String anonymizeIpv6(String ip) {
        String[] groups = normalizeIpv6(ip);
        if (groups.length < 3) {
            return ip;
        }
        return padHex(groups[0]) + ":" + padHex(groups[1]) + ":" + padHex(groups[2]) + "::";
    }

    private static String[] normalizeIpv6(String ip) {
        String[] groups = ip.split(":");
        if (!ip.contains("::")) {
            return groups;
        }
        int nonEmptyGroups = 0;
        for (String g : groups) {
            if (!g.isEmpty()) {
                nonEmptyGroups++;
            }
        }
        int missingGroups = 8 - nonEmptyGroups;
        String[] expanded = new String[8];
        int idx = 0;
        boolean expandedOnce = false;
        for (String g : groups) {
            if (g.isEmpty() && !expandedOnce) {
                expandedOnce = true;
                for (int i = 0; i < missingGroups; i++) {
                    expanded[idx++] = "0000";
                }
            } else if (!g.isEmpty()) {
                expanded[idx++] = g;
            }
        }
        return expanded;
    }

    private static String padHex(String segment) {
        return String.format("%4s", segment).replace(' ', '0');
    }
}
