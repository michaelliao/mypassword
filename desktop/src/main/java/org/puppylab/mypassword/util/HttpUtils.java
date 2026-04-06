package org.puppylab.mypassword.util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpUtils {

    static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    public static String get(String url, Map<String, String> query, Map<String, String> headers) {
        url = appendQuery(url, query);
        logger.info("GET: {}", url);
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder().uri(URI.create(url));
            builder = bindHeaders(builder, headers);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String appendQuery(String url, Map<String, String> query) {
        if (query != null) {
            String q = toQueryString(query);
            int pos = url.indexOf('?');
            url = url + (pos < 0 ? "?" : "&") + q;
        }
        return url;
    }

    public static String postForm(String url, Map<String, String> query, Map<String, String> headers) {
        String body = toQueryString(query);
        logger.info("POST: {}, form data: {}", url, body);
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder().uri(URI.create(url));
            builder = bindHeaders(builder, headers);
            builder = builder.setHeader("Content-Type", "application/x-www-form-urlencoded");
            builder = builder.POST(HttpRequest.BodyPublishers.ofString(body));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static String postJson(String url, Object obj, Map<String, String> headers) {
        String body = JsonUtils.toJson(obj);
        logger.info("POST: {}, json data: {}", url, body);
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder().uri(URI.create(url));
            builder = bindHeaders(builder, headers);
            builder = builder.setHeader("Content-Type", "application/json");
            builder = builder.POST(HttpRequest.BodyPublishers.ofString(body));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    static HttpRequest.Builder bindHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            for (String key : headers.keySet()) {
                String value = headers.get(key);
                if (value != null && !value.isEmpty()) {
                    builder = builder.header(key, headers.get(key));
                }
            }
        }
        return builder;
    }

    static String toQueryString(Map<String, String> query) {
        StringBuilder sb = new StringBuilder();
        for (String key : query.keySet()) {
            String value = query.get(key);
            if (value != null && !value.isEmpty()) {
                sb.append(key).append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8)).append('&');
            }
        }
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
