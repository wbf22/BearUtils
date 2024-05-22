package restclient;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;


/**
 * A basic REST client that can make GET, POST, PUT, PATCH, and DELETE requests.
 * This client uses plain java and is contained in this single file. 
 * 
 * <p> To use this client you need to provide a deserializer and a serializer lambda functions.
 * Here's an example of how you could do that with the jackson objectmapper
 * <pre>
 * {@code
 * RestClient client = new RestClient(
 *     (json, type, objectMapper) -> { // deserializer
 *          JavaType javaType = objectMapper.getTypeFactory().constructType(type); 
 *          return objectMapper.readValue( json, javaType )
 *     },  
 *     (body, objectMapper) -> { // serializer
 *          return objectMapper.writeValueAsString(body);
 *     }, 
 *     30000,  // timeout in milliseconds
 *     new ObjectMapper() // object mapper
 * );
 * }
 * </pre>
 * 
 * To use the client you can do something like this:
 * <pre>
 * {@code
 * MyObject response = client.get("https://some-url.com/endpoint", Map.of("Content-Type", "application/json"), MyObject.class);
 * }
 * </pre>
 * 
 * Or for List or other collection types you should do this:
 * <pre>
 * {@code
 * List<MyObject> response = client.get("https://some-url.com/endpoint", Map.of("Content-Type", "application/json"), MyObject.class);
 * }
 * </pre>
 * 
 */
public class RestClient<S> {


    private DeserializeLambda<String, Type, S, Object, Exception> deserializer;
    private SerializeLambda<Object, S, String, Exception> serializer;
    private S serializerObject;
    private int timeout = 300000; // 5 minutes in milliseconds


    /**
     * Creates a simple REST client that can make GET, POST, PUT, PATCH, and DELETE requests.
     * 
     * <p> To create this client you need to provide a deserializer and a serializer lambda functions.
     * Here's an example of how you could do that with the jackson objectmapper
     * <pre>
     * {@code
     * RestClient client = new RestClient(
     *     (json, type, objectMapper) -> { // deserializer
     *          JavaType javaType = objectMapper.getTypeFactory().constructType(type); 
     *          return objectMapper.readValue( json, javaType )
     *     },  
     *     (body, objectMapper) -> { // serializer
     *          return objectMapper.writeValueAsString(body);
     *     }, 
     *     30000,  // timeout in milliseconds
     *     new ObjectMapper() // object mapper
     * );
     * }
     * </pre>
     * 
     */
    public RestClient(DeserializeLambda<String, Type, S, Object, Exception> deserializer, SerializeLambda<Object, S, String, Exception> serializer, int timeoutMillis, S serializerObject) {
        this.deserializer = deserializer;
        this.serializer = serializer;
        this.timeout = timeoutMillis;
        this.serializerObject = serializerObject;
    }


    public <T> T get(String url, Map<String, String> headers, Type responseClass) {
        return makeRequest(url, "GET", headers, null, responseClass);
    }

    public <T, B> T post(String url, Map<String, String> headers, B body, Type responseClass) {
        return makeRequest(url, "POST", headers, body, responseClass);
    }

    public <T, B> T put(String url, Map<String, String> headers, B body,  Type responseClass) {
        return makeRequest(url, "PUT", headers, body, responseClass);
    }

    public <T, B> T patch(String url, Map<String, String> headers, B body,  Type responseClass) {
        return makeRequest(url, "PATCH", headers, body, responseClass);
    }

    public <T, B> T delete(String url, Map<String, String> headers, B body, Type responseClass) {
        return makeRequest(url, "DELETE", headers, body, responseClass);
    }

    
    public <T, B> T makeRequest(String url, String method, Map<String, String> headers, B body, Type responseClass) {
        
        HttpURLConnection conn = null;
        StringBuilder content = new StringBuilder();
        int statusCode = 0;
        try {
            // set up connection
            URL urlSpec = new URL(url);
            conn = (HttpURLConnection) urlSpec.openConnection();
            conn.setRequestMethod(method);
            conn.setReadTimeout(timeout);
            conn.setConnectTimeout(timeout);
    
            // Set headers
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    conn.setRequestProperty(header.getKey(), header.getValue());
                }
            }

            // Set body
            if (body != null) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    String bodyString = serializer.apply(body, serializerObject);
                    byte[] input = bodyString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }

            // Read response
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    content.append(inputLine);
                }    
            } catch (IOException errorException) {
                statusCode = conn.getResponseCode();
                try (BufferedReader errorIn = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    String inputLine;
                    while ((inputLine = errorIn.readLine()) != null) {
                        content.append(inputLine);
                    }
                } catch (IOException e2) { 
                    System.out.println("THIS SHOULDN'T HAPPEN");
                }
            }
            
            // Check status Code
            statusCode = conn.getResponseCode();
            if (statusCode >= 200 && statusCode < 300) {
                return (T) deserializer.apply(content.toString(), responseClass, serializerObject);
            }
        } catch (Exception e) {
            throw new RestClientException(statusCode, "Request failed", content.toString(), e);
        } 
        finally {
            // cleanup
            if (conn != null) {
                conn.disconnect();
            }
        }
        throw new RestClientException(statusCode, "Request failed", content.toString(), null);
    }
    


    public static class RestClientException extends RuntimeException {
        private final int statusCode;
        private String body;

        public RestClientException(int statusCode, String message, String body, Exception cause) {
            super(message, cause);
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }
    }


    @FunctionalInterface
    public static interface DeserializeLambda<J, T, S, R, E extends Exception> {
        R apply(J json, T type, S serializer) throws E;
    }


    @FunctionalInterface
    public static interface SerializeLambda<O, S, R, E extends Exception> {
        R apply(O object, S serializer) throws E;
    }


    public static class ParamType<Q> {

        private final java.lang.reflect.Type type;

        public ParamType() {
            Type superClass = getClass().getGenericSuperclass();
            if (superClass instanceof Class) {
                throw new IllegalArgumentException("Missing type parameter.");
            }
            this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
        }


        public Type getType() {
            return this.type;
        }

    }

}


