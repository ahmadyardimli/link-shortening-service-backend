package com.urlshortener.link_shortening_service.util;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;

// this class is resp for blocing unsafe targets. localhost/private networks
// lightweight version. Can be extended in future based on needs
@Component
public class UrlSafetyValidator {

    private final Set<String> blockedHosts = new HashSet<>();

    public UrlSafetyValidator() {
        blockedHosts.add("localhost");
        blockedHosts.add("127.0.0.1");
        blockedHosts.add("0.0.0.0");
        // or we can add our own domains.
    }

    public boolean isSafe(String url) {
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;

            // host denylist
            if (blockedHosts.contains(host.toLowerCase())) return false;

            // IP checks
            InetAddress address = InetAddress.getByName(host);
            if (address.isAnyLocalAddress() ||
                    address.isLoopbackAddress() ||
                    address.isLinkLocalAddress() ||
                    address.isSiteLocalAddress() ||    // 10/8, 172.16/12, 192.168/16
                    address.isMulticastAddress()) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}