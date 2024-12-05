# Serializer

This is a basic json serializer for serializing and deserializing json string and objects. Use it like so:


```java
public class MyDto {
    private String name;
    private int age;
    private boolean isStudent;
    private List<String> hobbies;
    private Map<String, Object> extra;
}

// Serializing
String json = prettyResponse = Serializer.json(
    new MyDto(
        "John Doe",
        25,
        true,
        Arrays.asList("Reading", "Swimming"),
        new HashMap<String, Object>() {{
            put("key1", "value1");
            put("key2", 2);
        }}
    ),
    true // flag for pretty print
);

// Deserializing
MyDto myObj = Serializer.fromJson(
    json, 
    MyDto.class
);

```


For more complex object such as parameterized types, you can use the `Serializer.ParamType` class to specify the type of the object to be deserialized. For example:

```java
List<Map<String, Object>> mapList = List.of(
    Map.of("key1", "value1"),
    Map.of("key2", 2)
);

// Serializing
String json = prettyResponse = Serializer.json(
    responseMapList,
    true // flag for pretty print
);

// Deserializing
List<Map<String, Object>> responseMapList = Serializer.fromJson(
    json, 
    new Serializer.ParamType<List<Map<String, Object>>>() {}
);

```

The way java types work you can't put `List<Map<String, Object>>.class` as a parameter to the `fromJson` method. This is why the `Serializer.ParamType` class is used to specify the type of the object to be deserialized.