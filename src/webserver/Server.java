package webserver;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class Server<S> implements HttpHandler {

    
    private DeserializeLambda<String, Type, S, Object, Exception> deserializer;
    private SerializeLambda<Object, S, String, Exception> serializer;
    private S serializerObject; 


    protected Server (
        DeserializeLambda<String, Type, S, Object, Exception> deserializer, 
        SerializeLambda<Object, S, String, Exception> serializer, 
        S serializerObject
    ) {
        this.deserializer = deserializer;
        this.serializer = serializer;
        this.serializerObject = serializerObject;

        try {
            HttpServer server;
            server = HttpServer.create(new InetSocketAddress(8000), 0);
            server.createContext("/test", this);
            server.setExecutor(
                Executors.newFixedThreadPool(10)
            ); // using a thread pool of 10 to handle requests
            server.start();
        } catch (IOException e) {
            throw new ServerException("Error starting server", e);
        }
    }


    @Override
    public void handle(HttpExchange t) throws IOException {
        Object responseBody = getResponseFromClass(
            t.getRequestMethod(),
            t.getRequestURI().getPath(), 
            t.getRequestHeaders(),
            t.getRequestBody()
        );

        if (responseBody == null) {
            t.sendResponseHeaders(404, -1);
            return;
        }

        String response = this.serializer.apply(responseBody, serializerObject);

        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    private String getResponseFromClass(String requestMethod, String path, Headers requestHeaders, InputStream requestBody) {
        

        
    }


    static class ServerException extends RuntimeException {
        public ServerException(String message, Exception cause) {
            super(message, cause);
        }
    }

    @FunctionalInterface
    public static interface DeserializeLambda<J, T, S, R, E extends Exception> {
        R apply(J json, T type, S serializer) throws E;
    }


    @FunctionalInterface
    public static interface SerializeLambda<J, S, R, E extends Exception> {
        R apply(J json, S serializer) throws E;
    }
}
