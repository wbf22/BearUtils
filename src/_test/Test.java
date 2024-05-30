package _test;


import java.util.List;
import java.util.Map;

import cache.Cache;
import id.BearId;
import restclient.RestClient;
import restclient.RestClient.RestClientException;

public class Test {

    
    public static void main(String[] args) throws Exception {

        Beans.loadBeans();



        // Test the RestClient class
        RestClient<Void> client = Beans.restClient;

        // String response = client.get(
        //     "http://192.168.1.100:8080/food/hot-dog?sauce=mustard&burnt=false", 
        //     Map.of("meat", "beef"), 
        //     String.class
        // );
        // System.out.println(response);


        // 10.241.47.205
        String response2 = client.get(
            "http://147.185.221.19:57594/food/hot-dog?sauce=cheese&burnt=false", 
            Map.of("meat", "beef"), 
            String.class
        );
        System.out.println(response2);

        for (int i = 0; i < 1000; i++) {
            try {
                String response = client.get(
                    "http://localhost:8080/food/hot-dog?sauce=mustard&burnt=false", 
                    Map.of("meat", "beef"), 
                    String.class
                );
                System.out.println(response);
            }
            catch (RestClientException e) {
                System.out.println("Status Code: " + e.getStatusCode());
                System.out.println("Body: " + e.getBody());
                break;
            }
        }


        // RestClient<Void> client2 = Beans.restClient2;
        // try {
        //     List<String> response = client2.get(
        //         "http://localhost:8080/food/hot-dog?sauce=mustard&burnt=false", 
        //         Map.of("meat", "beef"), 
        //         new ParamType<List<String>>() {}
        //     );
        //     System.out.println(response);
        // }
        // catch (RestClientException e) {
        //     System.out.println("Status Code: " + e.getStatusCode());
        //     System.out.println("Body: " + e.getBody());
        // }


        

        // String body = """
        //     {
        //         "order_identifier" : "ord_0G3VERYTGK0XJ"
        //     }
        // """;
        
        // try {
        //     String response2 = client.post(
        //         "https://www.example.com", 
        //         Map.of("Content-Type", "application/json"), 
        //         body, 
        //         String.class
        //     );
        //     System.out.println(response2);
        // } catch (RestClientException e) {
        //     System.out.println("Status Code: " + e.getStatusCode());
        //     System.out.println("Body: " + e.getBody());
        // }

    }

}
