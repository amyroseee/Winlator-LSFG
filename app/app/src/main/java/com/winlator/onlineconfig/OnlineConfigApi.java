package com.winlator.onlineconfig;

import com.winlator.core.Callback;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public final class OnlineConfigApi {
    public static final String PUBLISH_URL = "https://winlator-config-api.ffsakura0.workers.dev/publish";
    private OnlineConfigApi() {}

    public static void publish(String json, Callback<Result> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection)new URL(PUBLISH_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                byte[] body = json.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }
                int status = connection.getResponseCode();
                callback.call(new Result(status >= 200 && status < 300, status));
            } catch (Exception e) {
                callback.call(new Result(false, 0));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    public static final class Result {
        public final boolean success;
        public final int statusCode;
        Result(boolean success, int statusCode) { this.success = success; this.statusCode = statusCode; }
    }
}
