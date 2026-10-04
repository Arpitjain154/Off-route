package com.idr.navigator.cloud;

import java.io.*;
import java.net.URI;
import org.json.*;

public final class CloudConfig {

    public static final String URL = "https://bepfbvpwdguupquuthoe.supabase.co";
    // Publishable identifiers are safe in mobile clients; RLS controls access.
    public static final String KEY = "sb_publishable_odINW-vhk95Lg3563j7rbQ_aBpT7PXO";
    public final String projectUrl, publishableKey, journeyBucket, modelBucket;

    public CloudConfig(String url, String key, String trips, String models) throws IOException {
        try {
            URI u = URI.create(url);
            if (
                !"https".equals(u.getScheme()) ||
                u.getHost() == null ||
                !u.getHost().endsWith(".supabase.co") ||
                u.getUserInfo() != null ||
                u.getQuery() != null ||
                u.getFragment() != null ||
                u.getPort() != -1 ||
                !u.getPath().isEmpty()
            ) throw new Exception();
            if (
                !key.startsWith("sb_publishable_") ||
                !trips.matches("[a-z0-9][a-z0-9-]{0,62}") ||
                !models.matches("[a-z0-9][a-z0-9-]{0,62}") ||
                trips.equals(models)
            ) throw new Exception();
        } catch (Exception e) {
            throw new IOException(
                "Use a Supabase HTTPS project URL, publishable key and distinct bucket names"
            );
        }
        projectUrl = url;
        publishableKey = key;
        journeyBucket = trips;
        modelBucket = models;
    }

    public static boolean authorized(
        String expected,
        String actual,
        long generation,
        long current,
        boolean consent
    ) {
        return consent && expected.equals(actual) && generation == current;
    }
}
