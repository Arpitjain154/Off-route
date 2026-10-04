package com.idr.navigator.cloud;

import java.io.InterruptedIOException;
import java.net.SocketException;
import java.net.UnknownHostException;

final class CloudFailures {
    static boolean retry(Exception error) {
        return error instanceof SupabaseClient.HttpError
            ? ((SupabaseClient.HttpError)error).retry()
            : error instanceof InterruptedIOException || error instanceof SocketException || error instanceof UnknownHostException;
    }
    static boolean pausedEntry(String state,String message){
        return "action-required".equals(state)&&"Upload paused".equals(message);
    }
}
