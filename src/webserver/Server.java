package webserver;

import java.io.PrintWriter;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class Server<S> {
    public static Map<Integer, String> statusCodes = Map.of(
        200, "OK",
        201, "Created",
        204, "No Content",
        400, "Bad Request",
        401, "Unauthorized",
        403, "Forbidden",
        404, "Not Found",
        408, "Request Timeout",
        500, "Internal Server Error",
        505, "HTTP Version Not Supported"
    );

    public static Map<String, Integer> successCodes = Map.of(
        "GET", 200,
        "POST", 201,
        "PUT", 200,
        "DELETE", 200,
        "PATCH", 200,
        "HEAD", 200,
        "OPTIONS", 200
    );

    private static final Logger log = Logger.getLogger(Server.class.getName());

    private DeserializeLambda<String, Type, S, Object, Exception> deserializer;
    private SerializeLambda<Object, S, String, Exception> serializer;
    private S serializerObject; 
    private Map<String, Method> getMethods;
    private Map<String, Method> putMethods;
    private Map<String, Method> postMethods;
    private Map<String, Method> patchMethods;
    private Map<String, Method> deleteMethods;
    private Map<String, Method> optionsMethods;
    private Map<InetAddress, Integer> clientRequestCounts = new ConcurrentHashMap<>(); // for rate limiting

    private String path;
    private int timeout = 300000; // 5 mins
    private String contentType;
    private int maxRequestSize = 5000000; // 5 mb
    private int maxConnections = 20;
    private boolean serverOn = true;
    private int maxRequestsPerMinute = 1000;


    protected Server (
        int port,
        DeserializeLambda<String, Type, S, Object, Exception> deserializer, 
        SerializeLambda<Object, S, String, Exception> serializer, 
        S serializerObject,
        int timeoutMillis,
        String contentType,
        int maxRequestSize,
        int maxConnections,
        int rateLimitRequestsPerMinute
    ) {
        this.deserializer = deserializer;
        this.serializer = serializer;
        this.serializerObject = serializerObject;
        this.timeout = timeoutMillis;
        this.contentType = contentType;
        this.maxRequestSize = maxRequestSize;
        this.maxConnections = maxConnections;
        this.maxRequestsPerMinute = rateLimitRequestsPerMinute;


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

        // get path
        Endpoint endpointAnnotation = this.getClass().getAnnotation(Endpoint.class);
        this.path = (endpointAnnotation != null)? endpointAnnotation.value() : "";

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit( () -> setUpServer(port) );
    }


    private void setUpServer(int port) {
        /*
            POST /path/to/resource HTTP/1.1
            Host: www.example.com
            User-Agent: Mozilla/5.0
            Content-Type: application/x-www-form-urlencoded
            Content-Length: 27

            key1=value1&key2=value2



            HTTP/1.1 200 OK
            Date: Mon, 23 May 2005 22:38:34 GMT
            Content-Type: text/html; charset=UTF-8
            Content-Encoding: UTF-8
            Content-Length: 138
            Last-Modified: Wed, 08 Jan 2003 23:11:55 GMT
            Server: Apache/1.3.3.7 (Unix) (Red-Hat/Linux)
            ETag: "3f80f-1b6-3e1cb03b"
            Accept-Ranges: bytes
            Connection: close

            <html>
            <head>
            <title>An Example Page</title>
            </head>
            <body>
            Hello World, this is a very simple HTML document.
            </body>
            </html>
         */
        
        
        // rate limiter
        ScheduledExecutorService rateLimiterResetter = Executors.newScheduledThreadPool(1);
        rateLimiterResetter.scheduleAtFixedRate(() -> this.clientRequestCounts.clear(), 1, 1, TimeUnit.MINUTES);

        // set up client connection thread pool
        ExecutorService executor = new ThreadPoolExecutor(
                0, // core pool size
                this.maxConnections, // maximum pool size
                60, // keep-alive time for idle threads
                TimeUnit.SECONDS, // unit for keep-alive time
                new LinkedBlockingQueue<>() // work queue
        );

        try (
            ServerSocket serverSocket = new ServerSocket(port)
        ) {
            while (serverOn) {
                Socket socket = serverSocket.accept();
                // handle the connection in a separate thread
                executor.submit(() -> {

                    BufferedReader in = null;
                    PrintWriter out = null;
                    try {

                        // rate limit socket
                        this.rateLimitSocket(socket, false);

                        // set timeout and connect to client
                        socket.setSoTimeout(this.timeout);
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                        out = new PrintWriter(socket.getOutputStream());

                        // handle the connection (the connection loops inside until it is closed)
                        handleConnection(socket, in, out);
                            
    
                    } catch (SocketTimeoutException e) {
                        buildAndSendHttpResponse(408, "Request Timed Out", out);
                    } catch (Exception e) {
                        buildAndSendHttpResponse(500, handleErrorResponse(e).body, out);
                    }
                    finally {
                        try {
                            if (in != null) in.close();
                            if (out != null) out.close();
                            socket.close();

                        } catch (IOException e) {
                            throw new ServerException("Failed to close connection", e);
                        }
                    }
    
                });
                
            }
        }
        catch(IOException e) {
            throw new ServerException("Failed to create server socket", e);
        }
        finally {
            executor.shutdown();
        }
    }


    private void handleConnection(Socket socket, BufferedReader in, PrintWriter out) throws IOException, URISyntaxException {

        // keep the connection open until the client closes it or times out
        while (!socket.isClosed()) {

            // rate limit socket
            rateLimitSocket(socket, true);

            // read the request line
            Count currentRequestSize = new Count();
            String requestLine = readLine(in, currentRequestSize);
            if (requestLine == null) {
                // client closed the connection
                break;
            }
            
            String[] splits = requestLine.split(" ");

            // check http type
            if (splits[2].equals("HTTP/1.1")) {
                String method = splits[0];
                URI uri = new URI(splits[1]);
                
                // read the headers
                Map<String, String> headers = new HashMap<>();
                String line = readLine(in, currentRequestSize);
                while (!line.isEmpty()) {
                    int separator = line.indexOf(":");
                    if (separator != -1) {
                        headers.put(line.substring(0, separator), line.substring(separator + 1).trim());
                    }
                    line = readLine(in, currentRequestSize);
                }

                // check for host header
                if (headers.containsKey("Host")) {

                    // read the body
                    StringBuilder body = new StringBuilder();
                    while (in.ready()) {
                        body.append((char) in.read());
                        if (body.length() + currentRequestSize.getCount() > this.maxRequestSize)
                            throw new ServerException("Request exceeded maximum size of " + this.maxRequestSize, null);
                    }

                    // get the response from the endpoint methods
                    String responseString = getResponseFromClass(method, uri, headers, body.toString());

                    // send success response
                    int status = successCodes.get(method);
                    buildAndSendHttpResponse(status, responseString, out);
                }
                else {
                    buildAndSendHttpResponse(400, "Bad Request: Missing 'Host' header", out);
                }
            }
            else {
                buildAndSendHttpResponse(505, "HTTP Version Not Supported", out);
            }
        }
    }

    private void rateLimitSocket(Socket socket, boolean incrementCurrentConnection) throws IOException {
        InetAddress clientAddress = socket.getInetAddress();
        Integer numRequests = clientRequestCounts.get(clientAddress);
        if (numRequests == null) numRequests = 0;
        if (numRequests >= this.maxRequestsPerMinute) {
            socket.close();
            String message = String.format(
                "client submitted %d requests in the last minute, which is the max %d configured. Client Address %s (Closing connection now)",
                numRequests,
                this.maxRequestsPerMinute,
                clientAddress.toString()
            );
            log.severe(message);
        }
        else if (incrementCurrentConnection)
            clientRequestCounts.put(clientAddress, numRequests + 1);
    }


    private String readLine(BufferedReader in, Count currentRequestSize) throws IOException {
        StringBuilder requestLineBuilder = new StringBuilder();
        int ch;
        int lastChar = -1;
        while ((ch = in.read()) != -1) {
            currentRequestSize.add(1);
            if (lastChar == '\r' && ch == '\n') {
                break;
            }
            requestLineBuilder.append((char) ch);
            if (currentRequestSize.getCount() > this.maxRequestSize) {
                throw new ServerException("Request exceeded maximum size of " + String.valueOf(this.maxRequestSize), null);
            }
            lastChar = ch;
        }

        // Remove trailing '\r' from the line
        if (requestLineBuilder.length() > 0 && requestLineBuilder.charAt(requestLineBuilder.length() - 1) == '\r') {
            requestLineBuilder.setLength(requestLineBuilder.length() - 1);
        }

        return requestLineBuilder.toString();
    }

    private void buildAndSendHttpResponse(int status, String responseString, PrintWriter out) {
        // send status line
        String statusLine = "HTTP/1.1 " + status + " ";
        if (statusCodes.containsKey(status)) statusLine += statusCodes.get(status);
        out.println(statusLine);

        out.println("Date: " + ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        out.println("Content-Type: " + this.contentType);
        out.println("Content-Length: " + responseString.length());
        out.println("Connection: keep-alive");
        out.println();
        out.println(responseString);
        out.flush();
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

    private String getResponseFromClass(String requestMethod, URI uri, Map<String, String> requestHeaders, String requestBody) {
        
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
                    Object res = requestHeaders.get(headerAnnotation.value());
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
        if (res == null) return res;
        
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
    public static class ServerException extends RuntimeException {
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


    private static class Count {
        private int count = 0;

        public void add(int amount) {
            count += amount;
        }


        public int getCount() {
            return count;
        }
    }
}
