package com.idr.navigator.cloud;

import static org.junit.Assert.*;
import org.junit.Test;

public class CloudAuthErrorTest {
    @Test public void explainsInvalidCredentialsWithoutClaimingAccountExists() {
        String message = SupabaseClient.authError(400, "{\"error_code\":\"invalid_credentials\"}").getMessage();
        assertTrue(message.contains("Create account"));
        assertTrue(message.contains("email or password"));
    }
    @Test public void explainsConfirmationAndRateLimits() {
        assertTrue(SupabaseClient.authError(400, "{\"error_code\":\"email_not_confirmed\"}").getMessage().contains("Confirm"));
        assertTrue(SupabaseClient.authError(429, "{}").getMessage().contains("wait"));
        assertTrue(SupabaseClient.authError(401, "{}").getMessage().contains("project key"));
    }
    @Test public void doesNotEchoUntrustedResponseOrCredentials() {
        String response = "{\"msg\":\"private@example.com secret-token\"}";
        assertFalse(SupabaseClient.authError(400, response).getMessage().contains("secret-token"));
        assertTrue(SupabaseClient.authError(503, "not json").retry());
    }
}
