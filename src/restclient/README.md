# Rest Client
This is a simple rest client that can make a request to a service. It allows you the ability to set a timeout as well as the ability to define how the request and response are serialized and deserialized. 

## Creating a Client

Here's an example of how to make a client with the jackson ObjectMapper for json:
```
RestClient client = new RestClient(
    (json, type, objectMapper) -> { // deserializer
         JavaType javaType = objectMapper.getTypeFactory().constructType(type); 
         return objectMapper.readValue( json, javaType )
    },  
    (body, objectMapper) -> { // serializer
         return objectMapper.writeValueAsString(body);
    }, 
     30000,  // timeout in milliseconds
    new ObjectMapper() // object mapper
);
```

First we define a lambda function that recieves the json string, the response type, and our provided serialization object which in this case is a jackson ObjectMapper. This is our deserializer.

Next we define a lambda function for our serializer, which converts the request to json. Here we recieve the provided request body object and our jackson ObjectMapper and we use the two to produce a json body string.

Then we set the timeout and provide an ObjectMapper instance which will be provided to our serializer and deserializer.

## Basic Requests

Here's how to do a GET request with the client:
```
MyObject response = client.get(
    "https://some-url.com/endpoint", 
    Map.of("Content-Type", 
    "application/json"), 
    MyObject.class
);
```

And for types that have parameters like lists or maps do this (you can do List\<String\>.class):
```
List<MyObject> response = client.get(
    "https://some-url.com/endpoint", 
    Map.of("Content-Type", "application/json"), 
    new ParamType<List<MyObject>>() {}
);
```

And here's an example of a POST operation
```
MyBodyObject body = new MyBodyObject();

MyObject response2 = client.post(
    "https://www.example.com", 
    Map.of("Content-Type", "application/json"), 
    body, 
    MyObject.class
);
```

## Handling Errors
A good practice would be to wrap the request call in a try catch block so you can handle errors. The RestClient will throw an exception if the response comes back with a non 200 error. To handle those errors you'll want to do something like this:

```

try {
    MyBodyObject body = new MyBodyObject();
    MyObject response2 = client.put(
        "https://www.example.com", 
        Map.of("Content-Type", "application/json"), 
        body, 
        MyObject.class
    );
    System.out.println(response);
}
catch (RestClientException e) {
    System.out.println("Status Code: " + e.getStatusCode());
    System.out.println("Body: " + e.getBody());
}
```
Here we catch the exception thrown if a non 200 status code is returned. We can then extract the status code and get the error response body. 
