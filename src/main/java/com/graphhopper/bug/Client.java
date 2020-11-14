package com.graphhopper.bug;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.client.entity.GzipCompressingEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Client {
    public static final int PORT = 8989;
    private static final int NUM_WORK_A_REQUESTS = 1000;
    private static final int NUM_WORK_B_REQUESTS = 1000;
    private static final int NUM_INTERFERE_REQUESTS = 100;
    private static final CloseableHttpClient client = HttpClients.createDefault();

    public static void main(String[] args) throws Exception {
        Thread t1 = new Thread(() -> work("A", NUM_WORK_A_REQUESTS));
        t1.start();

        Thread t2 = new Thread(() -> work("B", NUM_WORK_B_REQUESTS));
        t2.start();

        interfere(NUM_INTERFERE_REQUESTS);

        t1.join();
        t2.join();
        System.out.println("finished");
        client.close();
    }

    private static void work(String type, int count) {
        try {
            System.out.println("Start sending work requests of type " + type);
            for (int i = 0; i < count; i++) {
                if (i > 0 && i % 1000 == 0)
                    System.out.println("Sent " + i + " work requests of type " + type);
                sleep(System.currentTimeMillis() % 37);
                sendWorkRequest(type);
            }
            System.out.println("Work request of type " + type + " are all fine");
        } catch (Bug bug) {
            System.out.println("BUG DETECTED " + bug.getMessage());
        }
    }

    private static void sendWorkRequest(String type) {
        HttpPost post = new HttpPost("http://localhost:" + PORT + "/work");
        post.setEntity(new GzipCompressingEntity(new StringEntity("{\"type\":\"" + type + "\"}", ContentType.APPLICATION_JSON)));
        try (CloseableHttpResponse rsp = client.execute(post)) {
            Header header = rsp.getFirstHeader("endpoint");
            if (header == null)
                throw new IllegalStateException("Missing header for response of work request");
            else if (!"work".equals(header.getValue()))
                throw new Bug("BUG: Wrong header! Expected: work, Given: " + header.getValue());
            String str = EntityUtils.toString(rsp.getEntity());
//            Object jsonobj = new ObjectMapper().readValue(str, Object.class);
//            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(jsonobj);
            Map<String, Object> json = getJson(str);
            if (json.get("type") == null)
                throw new IllegalStateException("Invalid response for work request of type: " + type + ": " + rsp.toString());

            String rspType = (String) json.get("type");
            if (!rspType.equals(type))
                throw new Bug("BUG: Wrong response type for work request! Expected: " + type + ", Given: " + rspType);
        } catch (Exception ex) {
            System.out.println("Problem for work " + ex.getMessage());
        }
    }

    private static void interfere(int count) {
        try {
            System.out.println("Start sending interfere requests");
            for (int i = 0; i < count; i++) {
                if (i > 0 && i % 10 == 0)
                    System.out.println("Sent " + i + " interfere requests");
                sleep(150);
                sendInterfereRequest();
            }
            System.out.println("Interfere requests all fine");
        } catch (IOException ex) {
            System.out.println("Error when sending interfere requests");
        }
    }

    private static void sendInterfereRequest() throws IOException {
        HttpPost post = new HttpPost("http://localhost:" + PORT + "/interfere");
        post.setEntity(new GzipCompressingEntity(new StringEntity("<bomb>", ContentType.APPLICATION_XML)));
        try (CloseableHttpResponse rsp = client.execute(post)) {
            EntityUtils.consume(rsp.getEntity());
            if (rsp.getStatusLine().getStatusCode() != 400)
                System.out.println("Expected 400 response from /interfere, but got: " + rsp.getStatusLine().getStatusCode());
        }
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