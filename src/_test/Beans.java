package _test;

import java.util.ArrayList;
import java.util.Arrays;

import restclient.RestClient;

public class Beans {

    public static RestClient<Void> restClient;
    public static RestClient<Void> restClient2;
    public static TestServer testServer;
    public static PrintTask printTask;


    public static void loadBeans() {
        restClient = new RestClient<>(
            (json, type, serializer) -> json, 
            (json, serializer) -> json.toString(), 
            300000, 
            null
        );

        restClient2 = new RestClient<>(
            (json, type, serializer) -> Arrays.stream(json.split(" ")).toList(), 
            (json, serializer) -> json.toString(), 
            300000, 
            null
        );

        testServer = new TestServer();

        // printTask = new PrintTask();
    }


}
