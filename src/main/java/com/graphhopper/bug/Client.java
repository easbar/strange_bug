package com.graphhopper.bug;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Client {
    private static final Logger logger = LoggerFactory.getLogger(Client.class);
    private static final OkHttpClient client = createClient();
    public static final int PORT = 8989;
    private static final int NUM_WORK_A_REQUESTS = 1000;
    private static final int NUM_WORK_B_REQUESTS = 1000;
    private static final int NUM_INTERFERE_REQUESTS = 10;

    public static void main(String[] args) throws Exception {
        Thread t1 = new Thread(() -> work("A", NUM_WORK_A_REQUESTS));
        t1.start();

        Thread t2 = new Thread(() -> work("B", NUM_WORK_B_REQUESTS));
        t2.start();

        interfere(NUM_INTERFERE_REQUESTS);

        t1.join();
        t2.join();
        logger.info("finished");
    }

    private static void work(String type, int count) {
        try {
            logger.info("Start sending work requests of type " + type);
            for (int i = 0; i < count; i++) {
                if (i > 0 && i % 1000 == 0)
                    logger.info("Sent " + i + " work requests of type " + type);
                sleep(System.currentTimeMillis() % 37);
                sendWorkRequest(type);
            }
            logger.info("Work request of type " + type + " are all fine");
        } catch (Bug bug) {
            logger.error("BUG DETECTED", bug);
        } catch (IOException ex) {
            logger.error("Error when sending work requests of type: " + type, ex);
        }
    }

    private static void sendWorkRequest(String type) throws IOException {
        Response rsp = null;
        try {
            Request.Builder reqBuilder = new Request.Builder().url("http://localhost:" + PORT + "/work");
            reqBuilder.post(RequestBody.create("{\"type\":\"" + type + "\"}", MediaType.parse("application/json")));
            rsp = Client.client.newCall(reqBuilder.build()).execute();
            String header = rsp.header("endpoint", "none");
            if ("none".equals(header))
                throw new IllegalStateException("Missing header for response of work request");
            if (!"work".equals(header))
                throw new Bug("BUG: Wrong header! Expected: work, Given: " + header);
            String str = rsp.body().string();
//            Object jsonobj = new ObjectMapper().readValue(str, Object.class);
//            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonobj);
            Map<String, Object> json = getJson(str);
            if (json.get("type") == null)
                throw new IllegalStateException("Invalid response for work request of type: " + type + ": " + rsp.toString());

            String rspType = (String) json.get("type");
            if (!rspType.equals(type))
                throw new Bug("BUG: Wrong response type for work request! Expected: " + type + ", Given: " + rspType);
        } finally {
            if (rsp != null) rsp.body().close();
        }
    }

    private static void interfere(int count) {
        try {
            logger.info("Start sending interfere requests");
            for (int i = 0; i < count; i++) {
                if (i > 0 && i % 10 == 0)
                    logger.info("Sent " + i + " interfere requests");
                sleep(150);
                sendInterfereRequest();
            }
            logger.info("Interfere requests all fine");
        } catch (IOException ex) {
            logger.error("Error when sending interfere requests");
        }
    }

    private static void sendInterfereRequest() throws IOException {
        Response rsp = null;
        try {
            Request.Builder reqBuilder = new Request.Builder().url("http://localhost:" + PORT + "/interfere");
            reqBuilder.post(RequestBody.create("<bomb>", MediaType.parse("application/json")));

            rsp = client.newCall(reqBuilder.build()).execute();
            if (rsp.code() != 400)
                throw new IllegalStateException("Expected 400 response from /interfere, but got: " + rsp.code());
        } finally {
            if (rsp != null) rsp.body().close();
        }
    }

    private static OkHttpClient createClient() {
        return new OkHttpClient.Builder().
                connectTimeout(6, TimeUnit.SECONDS).
                readTimeout(12, TimeUnit.SECONDS).
                writeTimeout(20, TimeUnit.SECONDS).
                // default is 5 connections and 5min
//                        connectionPool(new ConnectionPool(100, 15, TimeUnit.MINUTES)).
        build()
                .newBuilder().addInterceptor(new GzipRequestInterceptor()).
                        build();
    }

    static Map<String, Object> getJson(String str) {
        try {
            return new ObjectMapper().readValue(str, new TypeReference<HashMap<String, Object>>() {
            });
        } catch (Exception ex) {
            // if it is empty or none-POST or similar
            return new HashMap<>(5);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception ex) {
        }
    }

    private static class Bug extends RuntimeException {
        Bug(String message) {
            super(message);
        }
    }
}