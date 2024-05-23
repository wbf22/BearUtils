package _test;


import java.io.IOException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import restclient.RestClient;
import restclient.RestClient.ParamType;
import restclient.RestClient.RestClientException;
import restclient.RestClient.SerializeLambda;
import restclient.RestClient.DeserializeLambda;

public class Test {

    
    public static void main(String[] args) throws Exception {

        Beans.loadBeans();



        // Test the RestClient class
        RestClient<Void> client = Beans.restClient;

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
        }

        

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
