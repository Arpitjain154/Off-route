package com.idr.navigator.cloud;
import static org.junit.Assert.*;
import org.junit.Test;
public class StorageFailureTest {
 @Test public void onlyKnownPausedFailuresAreMigrated(){
  assertTrue(CloudFailures.pausedEntry("action-required","Upload paused"));
  assertFalse(CloudFailures.pausedEntry("action-required","Storage access denied"));
  assertFalse(CloudFailures.pausedEntry("uploaded","Upload paused"));
 }
 @Test public void missingApprovedIndexWrappedIn400IsRecognized(){
  assertEquals(404,SupabaseClient.storageError(400,"{\"statusCode\":\"404\",\"error\":\"not_found\",\"message\":\"Object not found\"}").status);
  assertEquals(404,SupabaseClient.storageError(404,"{\"code\":\"NoSuchKey\"}").status);
 }
 @Test public void permissionAndMalformedResponsesDoNotBecomeMissingModels(){
  assertEquals(403,SupabaseClient.storageError(400,"{\"statusCode\":\"403\",\"error\":\"Unauthorized\"}").status);
  assertEquals(400,SupabaseClient.storageError(400,"not json").status);
  assertFalse(SupabaseClient.storageError(400,"{\"message\":\"secret private@example.com\"}").getMessage().contains("secret"));
 }
 @Test public void interruptionsAndTransientFailuresCanRetryButPermissionsCannot(){
  assertTrue(CloudFailures.retry(new java.io.InterruptedIOException("Upload paused")));
  assertTrue(CloudFailures.retry(new SupabaseClient.HttpError(503)));
  assertFalse(CloudFailures.retry(new SupabaseClient.HttpError(403)));
  assertFalse(CloudFailures.retry(new java.io.IOException("Cloud account changed")));
 }
}
