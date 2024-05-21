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

        

        // Test the RestClient class
        RestClient<Void> client = new RestClient<>((json, type, serializer) -> json, (json, serializer) -> json.toString(), 300000, null);
        // String response = client.get("https://attractions-api.accessdevelopment-stage.com/v1/attractions/admin/summary/attractions", null, String.class);
        // System.out.println(response);

        String body = """
            {
                "order_identifier" : "ord_0G3VERYTGK0XJ"
            }
        """;
        
        try {
            String response2 = client.post(
                "https://attractions-api.accessdevelopment-stage.com/v1/order/admin/query?page=0&pageSize=10", 
                Map.of("Content-Type", "application/json"), 
                body, 
                String.class
            );
            System.out.println(response2);
        } catch (RestClientException e) {
            System.out.println("Status Code: " + e.getStatusCode());
            System.out.println("Body: " + e.getBody());
        }

    }

}
