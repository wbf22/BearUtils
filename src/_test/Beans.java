package _test;

import restclient.RestClient;

public class Beans {

    public static RestClient<Void> restClient;
    public static TestServer testServer;
    public static PrintTask printTask;


    public static void loadBeans() {
        restClient = new RestClient<>((json, type, serializer) -> json, (json, serializer) -> json.toString(), 300000, null);
        testServer = new TestServer();
        printTask = new PrintTask();
    }


}
