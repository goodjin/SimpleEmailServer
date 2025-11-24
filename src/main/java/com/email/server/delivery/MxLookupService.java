package com.email.server.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.*;

/**
 * Service for looking up MX (Mail Exchange) records via DNS
 */
public class MxLookupService {
    private static final Logger logger = LoggerFactory.getLogger(MxLookupService.class);

    public List<String> lookupMxRecords(String domain) {
        List<MxRecord> mxRecords = new ArrayList<>();

        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);

            Attributes attrs = ctx.getAttributes(domain, new String[] { "MX" });
            Attribute mxAttr = attrs.get("MX");

            if (mxAttr == null || mxAttr.size() == 0) {
                logger.warn("No MX records found for domain: {}", domain);
                // Fallback: try the domain itself as mail server
                return Arrays.asList(domain);
            }

            NamingEnumeration<?> mxEnum = mxAttr.getAll();
            while (mxEnum.hasMore()) {
                String mxRecord = (String) mxEnum.next();
                // MX record format: "priority hostname"
                String[] parts = mxRecord.split("\\s+");
                if (parts.length >= 2) {
                    int priority = Integer.parseInt(parts[0]);
                    String hostname = parts[1];
                    // Remove trailing dot if present
                    if (hostname.endsWith(".")) {
                        hostname = hostname.substring(0, hostname.length() - 1);
                    }
                    mxRecords.add(new MxRecord(priority, hostname));
                }
            }

            ctx.close();
        } catch (Exception e) {
            logger.error("Error looking up MX records for {}: {}", domain, e.getMessage());
            // Fallback to domain itself
            return Arrays.asList(domain);
        }

        // Sort by priority (lower number = higher priority)
        Collections.sort(mxRecords);

        List<String> result = new ArrayList<>();
        for (MxRecord mx : mxRecords) {
            result.add(mx.hostname);
        }

        logger.info("Found {} MX records for {}: {}", result.size(), domain, result);
        return result;
    }

    private static class MxRecord implements Comparable<MxRecord> {
        final int priority;
        final String hostname;

        MxRecord(int priority, String hostname) {
            this.priority = priority;
            this.hostname = hostname;
        }

        @Override
        public int compareTo(MxRecord other) {
            return Integer.compare(this.priority, other.priority);
        }
    }
}
