package _test;

import webserver.Server;
import webserver.Server.Endpoint;


@Endpoint("/food")
public class TestServer extends Server<Void> {

    public TestServer() {
        super(
            8080,
            (json, type, serializer) -> json, 
            (json, serializer) -> json.toString(), 
            null,
            300000,
            "string",
            300000,
            20
        );

    }




    @Get("/hot-dog")
    public String developeHotDog(
        @Body String body, 
        @Param String sauce, 
        @Param boolean burnt, 
        @Header("meat") String meat
    ) {
        return body + sauce + meat;
    }
    
}
