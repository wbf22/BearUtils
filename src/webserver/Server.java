package webserver;

import com.sun.net.httpserver.HttpServer;

import _test.TestServer;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.OutputStream;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Server<S> implements HttpHandler {

    
    private DeserializeLambda<String, Type, S, Object, Exception> deserializer;
    private SerializeLambda<Object, S, String, Exception> serializer;
    private S serializerObject; 
    private Map<String, Method> getMethods;
    private Map<String, Method> putMethods;
    private Map<String, Method> postMethods;
    private Map<String, Method> patchMethods;
    private Map<String, Method> deleteMethods;
    private Map<String, Method> optionsMethods;
    private String path;
    private int timeout;


    protected Server (
        int port,
        DeserializeLambda<String, Type, S, Object, Exception> deserializer, 
        SerializeLambda<Object, S, String, Exception> serializer, 
        S serializerObject,
        int timeoutMillis
    ) {
        this.deserializer = deserializer;
        this.serializer = serializer;
        this.serializerObject = serializerObject;
        this.timeout = timeoutMillis;


        // parse endpoint methods
        getMethods = new HashMap<>();
        putMethods = new HashMap<>();
        postMethods = new HashMap<>();
        patchMethods = new HashMap<>();
        deleteMethods = new HashMap<>();
        optionsMethods = new HashMap<>();
        for (Method method : this.getClass().getMethods()) {
            Get getAnnotation = method.getAnnotation(Get.class);
            if (getAnnotation != null) {
                getMethods.put(getAnnotation.value(), method);
                break;
            }
            
            Put putAnnotation = method.getAnnotation(Put.class);
            if (putAnnotation != null) {
                putMethods.put(putAnnotation.value(), method);
                break;
            }
            
            Post postAnnotation = method.getAnnotation(Post.class);
            if (postAnnotation != null) {
                postMethods.put(postAnnotation.value(), method);
                break;
            }
            
            Patch patchAnnotation = method.getAnnotation(Patch.class);
            if (patchAnnotation != null) {
                patchMethods.put(patchAnnotation.value(), method);
                break;
            }
            
            Delete deleteAnnotation = method.getAnnotation(Delete.class);
            if (deleteAnnotation != null) {
                deleteMethods.put(deleteAnnotation.value(), method);
                break;
            }
            
            Options optionsAnnotation = method.getAnnotation(Options.class);
            if (optionsAnnotation != null) {
                optionsMethods.put(optionsAnnotation.value(), method);
                break;
            }
        }


        // start up server
        try {
            HttpServer server;
            server = HttpServer.create(new InetSocketAddress(port), 0);

            Endpoint endpointAnnotation = this.getClass().getAnnotation(Endpoint.class);
            this.path = endpointAnnotation == null? "" : endpointAnnotation.value();
            server.createContext(this.path, this);
            server.setExecutor(
                new ThreadPoolExecutor(
                    2, // core pool size
                    20, // maximum pool size
                    60, // keep-alive time for idle threads
                    TimeUnit.SECONDS, // unit for keep-alive time
                    new LinkedBlockingQueue<Runnable>() // work queue
                )
            ); // using a thread pool of 10 to handle requests
            server.start();
        } catch (IOException e) {
            throw new ServerException("Error starting server", e);
        }
    }


    @Override
    public void handle(HttpExchange t) throws IOException {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<?> future = executor.submit(() -> {
            OutputStream os = null;
            try {
                Object responseBody = getResponseFromClass(
                    t.getRequestMethod(),
                    t.getRequestURI(), 
                    t.getRequestHeaders(),
                    readInputStream(t.getRequestBody())
                );
        
                if (responseBody == null) {
                    t.sendResponseHeaders(404, -1);
                    return;
                }
        
                String response = this.serializer.apply(responseBody, serializerObject);
        
                t.sendResponseHeaders(200, response.length());
                os = t.getResponseBody();
                os.write(response.getBytes());
            }
            catch(Exception e) {
                ErrorResponse response = handleErrorResponse(e);
                t.sendResponseHeaders(response.statusCode, response.body.length());
                os = t.getResponseBody();
                os.write(response.body.getBytes());
                
            }
            finally {
                if (os != null) 
                    os.close();
            }
        });

        try {
            future.get(60, TimeUnit.SECONDS); // wait for 60 seconds before timing out
        } catch (TimeoutException e) {
            t.sendResponseHeaders(408, -1); // send a 408 Request Timeout response
        } catch (InterruptedException | ExecutionException e) {
            // handle other exceptions
        } finally {
            executor.shutdown(); // make sure to shut down the executor
        }
        
    }

    public ErrorResponse handleErrorResponse(Exception e) {
        StringBuilder body = new StringBuilder("{ \"message\" : \"Encountered exception processing request: ");
        String message = e.getMessage();
        String cause = e.getCause() == null? "" : e.getCause().getMessage();

        if (message != null) body.append(message);
        if (cause != null) body.append(cause);

        body.append(" \" }");
        return new ErrorResponse(
            500, 
            body.toString()
        );
    }


    private String getResponseFromClass(String requestMethod, URI uri, Headers requestHeaders, String requestBody) {
        
        // /hot-dog?burnt=false&sauce=mustard

        // get resource path
        String resourceString = uri.getPath();
        resourceString = resourceString.replace(this.path, "");


        // call endpoint method if a matching endpoint if found
        Method method = switch(requestMethod) {
            case "GET"-> this.getMethods.get(resourceString);
            case "PUT"-> this.putMethods.get(resourceString);
            case "POST"-> this.postMethods.get(resourceString);
            case "PATCH"-> this.patchMethods.get(resourceString);
            case "DELETE"-> this.deleteMethods.get(resourceString);
            case "OPTIONS"-> this.optionsMethods.get(resourceString);
            default -> null;
        };
        if (method != null) {
            // parse params
            List<String> params = new ArrayList<>();
            String reqString = uri.getQuery();
            if (reqString.length() > 1) {
                for (String param : reqString.split("&")) {
                    String[] pair = param.split("=");
                    params.add(pair[1]);
                }
            }

            // collect arguments for method
            Object[] arguments = new Object[method.getParameters().length];
            Parameter[] methodParams = method.getParameters();
            int currentParam = 0;
            for (int i = 0; i < arguments.length; i++) {

                Parameter param = methodParams[i];

                Body bodyAnnotation = param.getAnnotation(Body.class);
                if (bodyAnnotation != null) {
                    try {
                        arguments[i] = this.deserializer.apply(requestBody.toString(), param.getParameterizedType(), this.serializerObject);
                    } catch (Exception e) {
                        throw new ServerException("Error deserializing body to type " + param.getParameterizedType().getTypeName(), e);
                    }
                    continue;
                }

                Param paramAnnotation = param.getAnnotation(Param.class);
                if (paramAnnotation != null) {
                    Object res = params.get(currentParam);
                    currentParam++;
                    Class<?> type = param.getType();
                    res = parseBasicType(res, type);

                    arguments[i] = res;
                    continue;
                }

                Header headerAnnotation = param.getAnnotation(Header.class);
                if (headerAnnotation != null) {
                    Object res = requestHeaders.getFirst(headerAnnotation.value());
                    arguments[i] = parseBasicType(res, param.getType());
                    continue;
                }
            }


            // call method and serialize result
            try {
                Object result = method.invoke(this, arguments);

                return this.serializer.apply(result, serializerObject);
            } 
            catch (ReflectiveOperationException e) {
                throw new ServerException("Matching endpoint method wasn't public or had some other failure", e);
            } catch (Exception e) {
                throw new ServerException("Error deserializing result from endpoint", e);
            }
        }

        return null;
    }

    private Object parseBasicType(Object res, Class<?> type) {
        
        if ( boolean.class.isAssignableFrom(type) ) {
            res = Boolean.parseBoolean(res.toString());
        }
        else if ( Number.class.isAssignableFrom(type) ) {
            if (type == BigDecimal.class) {
                res = new BigDecimal(res.toString());
            }
            else if (Integer.class.isAssignableFrom(type)) {
                res = Integer.parseInt(res.toString());
            }
            else if (Long.class.isAssignableFrom(type)) {
                res = Long.parseLong(res.toString());
            }
            else if (Double.class.isAssignableFrom(type)) {
                res = Double.parseDouble(res.toString());
            }
        }

        return res;
    }


    public String readInputStream(InputStream requestBody) throws IOException {
        try (InputStreamReader isr = new InputStreamReader(requestBody, StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }



    // helper classes
    static class ServerException extends RuntimeException {
        public ServerException(String message, Exception cause) {
            super(message, cause);
        }
    }

    
    public static class ErrorResponse {
        private final int statusCode;
        private String body;

        public ErrorResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
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

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
    public static @interface Endpoint {
        String value() default "";
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface Get {
        String value() default "";
    }
    
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface Put {
        String value() default "";
    }
    
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface Post {
        String value() default "";
    }
    
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface Patch {
        String value() default "";
    }
    
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface Delete {
        String value() default "";
    }
    
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public static @interface Options {
        String value() default "";
    }

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.PARAMETER})
    public static @interface Body {}

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.PARAMETER})
    public static @interface Param {}

    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.PARAMETER})
    public static @interface Header {
        String value() default "";
    }

}
