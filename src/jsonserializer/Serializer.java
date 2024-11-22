package jsonserializer;


import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;



public class Serializer {

    private Serializer() {}


    public static <T> String json(T object, boolean pretty) {
        if (object == null) return null;
        if (object instanceof String) return (String) object;

        if (object instanceof List) {
            List<?> list = (List<?>) object;
            List<Map<String, Object>> newList = new ArrayList<>();
            for (Object o : list) {
                newList.add(mapify(o));
            }
            return convertListToJson(newList, pretty);
        }

        Map<String, Object> mapObject = mapify(object);

        if (pretty) {
            return mapToPrettyJsonString(mapObject);
        }
        else {
            return mapToJsonString(mapObject);
        }
    }

    public static <T> T fromJson(String json, Type type) {
        // remove whitespace
        json = json.replaceAll("[ \n\t]", "");
        Class<T> clazz = (Class<T>) typeToClassWildcard(type);

        // if map or object
        if (json.charAt(0) == '{') {
            Map<String, Object> map = jsonStringToMap(json);
            
            if (Map.class.isAssignableFrom(clazz)) {
                return safeCast(map, clazz);
            }
            else {
                T object = getDefault(clazz);
                return convertMapToObj(object, map);
            }
        }
        // if list
        else if (json.charAt(0) == '[') {
            // parse json into list of maps
            List<Map<String, Object>> value = (List<Map<String, Object>>) getObjectFromString(json);

            // convert maps into objects
            Class<?> listType = (Class<?>) ((ParameterizedType) type).getActualTypeArguments()[0];
            List<Object> newList = new ArrayList<>();
            for (Map<String, Object> elem : value) {
                if (Map.class.isAssignableFrom(listType)) {
                    newList.add(elem);
                }
                else {
                    Object converted = convertMapToObj(
                        getDefault(listType),
                        elem
                    );
                    newList.add(converted);
                }
            }

            return safeCast(newList, clazz);
        }
        // if string
        else if (clazz == String.class) {
            return safeCast(json, clazz);
        }
        else {
            throw new SerializerException("Invalid json string", null);
        }
        
    }

    public static <T> T fromJson(String json, ParamType<T> type) {
        return fromJson(json, type.getType());
    }



    private static String convertListToJson(List<Map<String, Object>> mapifiedList, boolean pretty) {
        StringBuilder stringBuilder = new StringBuilder("[");
        if (pretty) {
            stringBuilder.append("\n");
            for (Map<String, Object> map : mapifiedList) {
                String mapString = mapToPrettyJsonString(map);
                String[] lines = mapString.split("\n");
                for (String line : lines) {
                    stringBuilder.append("    ").append(line).append("\n");
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 1);
                stringBuilder.append(",\n");
            }
        }
        else {
            stringBuilder.append(" ");
            for (Map<String, Object> map : mapifiedList) {
                String mapString = mapToJsonString(map);
                stringBuilder.append(mapString).append(", ");
            }
        }
        
        stringBuilder.deleteCharAt(stringBuilder.length() - 2);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    public static <T> T convertMapToObj(T object, Map<String, Object> map) {

        Field[] fields = object.getClass().getFields();

        for (Field field : fields) {
            
            try {

                if (map.containsKey(field.getName())) {
                    boolean originalAccessibility = field.canAccess(object);
                    field.setAccessible(true);

                    Object value = map.get(field.getName());
                    Object convertedValue = convertObjectToType(value, field.getGenericType());
                    field.set(object, convertedValue);

                    field.setAccessible(originalAccessibility);
                }
                
            } catch (Exception e) {
                throw new SerializerException("Error trying to create a(n) '" + object.getClass().getName() + "' object", e);
            } 
        }

        return object;
    }

    private static <T> T convertObjectToType(Object value, Type genericType) {

        Class<T> type = (Class<T>) typeToClassWildcard(genericType);

        // list type
        if (List.class.isAssignableFrom(type)) {

            // convert to a list
            List<Object> list = new ArrayList<>();
            List<?> resList = safeCast(value, List.class);

            // get the list type
            Type listType = Object.class;
            if (genericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                listType = parameterizedType.getActualTypeArguments()[0];
            }

            // iterate over the list and convert each object
            for (Object o : resList) {
                Object val = convertObjectToType(o, typeToClassWildcard(listType));
                list.add(val);
            }
            return safeCast(list, type);
        }
        else if (Map.class.isAssignableFrom(type)) {
            // convert to a map
            Map<Object, Object> map = new LinkedHashMap<>(); // linked to maintain order
            Map<?, ?> resMap = safeCast(value, Map.class);

            // get the map types
            Type keyType = Object.class;
            Type valueType = Object.class;
            if (genericType instanceof ParameterizedType) {
                ParameterizedType parameterizedType = (ParameterizedType) genericType;
                keyType = parameterizedType.getActualTypeArguments()[0];
                valueType = parameterizedType.getActualTypeArguments()[1];
            }

            // iterate over the map and convert each object
            for (Entry<?, ?> entry : resMap.entrySet()) {
                Object key = convertObjectToType(entry.getKey(), typeToClassWildcard(keyType));
                Object val = convertObjectToType(entry.getValue(), typeToClassWildcard(valueType));
                map.put(key, val);
            }
            return safeCast(map, type);
        }
        // numbers
        else if (isNumericClass(type)) {
            boolean notBigDecimal = !(value instanceof BigDecimal);
            if (notBigDecimal) {
                value = new BigDecimal(value.toString());
            }

            Object numberValue = convertBigDecimalToType(
                safeCast(value, BigDecimal.class), 
                type
            );

            return (T) numberValue;
        }
        // date types
        else if (Temporal.class.isAssignableFrom(type)) {
            return safeCast(
                convertStringToDate(value.toString(), type),
                type
            );
        }
        // boolean
        else if (isBoolean(type)) {
            return safeCast(value, type);
        }
        // string
        else if (type == String.class) {
            return safeCast(value, type);
        }
        // enum
        else if (type.isEnum()) {
            Object enumValue = Enum.valueOf((Class<Enum>) type, value.toString());
            return safeCast(enumValue, type);
        }
        // a user defined object
        else {
            T object = getDefault(type);
            return convertMapToObj(
                object,
                safeCastMap(value, String.class, Object.class)
            );
        }
    }

    private static Class<?> typeToClassWildcard(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        else if (type instanceof ParameterizedType parameterizedType) {
            return (Class<?>) parameterizedType.getRawType();
        }
        else {
            throw new SerializerException("Failed to convert type to class", null);
        }
    }

    private static <T> T getDefault(Class<T> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new SerializerException("Missing no args constructor for type " + type.getName(), e);
        }
    }


    /**
     * Converts an object to a map ommiting null fields
     * 
     * @param object
     * @return
     */
    public static <T> Map<String, Object> mapify(T object) {

        Map<String, Object> mappedResponse = new LinkedHashMap<>(); // linked to maintain order
        
        Class<?> objectType = object.getClass();

        Field[] fields = objectType.getFields();

        for (Field field : fields) {
            
            try {
                // check for ignore tag
                if (field.isAnnotationPresent(JsonIgnore.class)) continue;

                // get the original accessibility and set accessible during the method
                boolean originalAccessibility = field.canAccess(object);
                field.setAccessible(true);


                if (field.get(object) != null) {

                    Object value = null;
    
                    // check if the field is a list
                    if (List.class.isAssignableFrom(field.getType())) {
                        List<Object> list = new ArrayList<>();

                        Object fieldResponse = field.get(object);

                        List<?> resList = safeCast(fieldResponse, List.class);
                        for (Object o : resList) {
                            Object val = (isBasicJavaType(o.getClass()) || o.getClass().isEnum())? o : mapify(o);
                            list.add(val);
                        }
                        value = list;
                    }
                    // check if the field is a map
                    else if (Map.class.isAssignableFrom(field.getType())) {
                        Map<Object, Object> map = new LinkedHashMap<>();

                        Map<?, ?> resMap = safeCast(field.get(object), Map.class);
                        for (Entry<?, ?> entry : resMap.entrySet()) {
                            Object key = entry.getKey();
                            Object val = entry.getValue();
                            val = (isBasicJavaType(val.getClass()) || val.getClass().isEnum())? val : mapify(val);
                            map.put(key, val);
                        }

                        value = map;
                    }
                    // check if the field is a basic java type or enum
                    else if (isBasicJavaType(field.getType()) || field.getType().isEnum()) {
                        value = field.get(object);
                    }
                    // otherwise we need to fulfill it as well
                    else {
                        Object fieldResponse = field.get(object);
                        value = mapify(fieldResponse);
                    }


                    mappedResponse.put(field.getName(), value);
    
                }
                field.setAccessible(originalAccessibility);
                
            } catch (IllegalArgumentException e) {
                throw new SerializerException("Tried to map bad value to field '" + field.getName() + "' in static class '" + objectType.getName() + "'", e);
            }  catch (IllegalAccessException e) {
                throw new SerializerException("Couldn't access field '" + field.getName() + "' in static class '" + objectType.getName() + "'", e);
            } 
        }

        return mappedResponse;
    }

    private static String mapToJsonString(Map<String, Object> map) {
        StringBuilder stringBuilder = new StringBuilder("{ ");

        for (Entry<String, Object> entry : map.entrySet()) {

            stringBuilder.append("\"").append(entry.getKey()).append("\" : ");
            if (entry.getValue() instanceof Map) {
                Map<String, Object> value = safeCast(entry.getValue(), Map.class);
                String mapAsString = mapToJsonString(value);
                stringBuilder.append(mapAsString);
            } 
            else if (entry.getValue() instanceof List) {
                List<?> list = (List<?>) entry.getValue();
                stringBuilder.append("[ ");
                for (Object o : list) {
                    if (o instanceof Map) {
                        Map<String, Object> value = safeCast(o, Map.class);
                        String mapAsString = mapToJsonString(value);
                        stringBuilder.append(mapAsString);
                    } 
                    else if (o instanceof List) {
                        throw new SerializerException("Double nested lists aren't supported for jsonMap conversion. Map key: " + entry.getKey(), null);
                    }
                    else if (o instanceof String || o.getClass().isEnum()) {
                        stringBuilder.append("\"").append(o).append("\"");
                    }
                    else {
                        stringBuilder.append(entry.getValue());
                    }
                    stringBuilder.append(", ");
                }
                stringBuilder.deleteCharAt(stringBuilder.length() - 2);
                stringBuilder.append("]");
            }
            else {
                if (entry.getValue() instanceof String || entry.getValue().getClass().isEnum()) {
                    stringBuilder.append("\"").append(entry.getValue()).append("\"");
                }
                else {
                    stringBuilder.append(entry.getValue());
                }
            }

            stringBuilder.append(", ");
        }

        stringBuilder.deleteCharAt(stringBuilder.length() - 2);
        stringBuilder.append("}");

        return stringBuilder.toString();
    }

    /**
     * Converts a map to a pretty json string
     */
    private static String mapToPrettyJsonString(Map<String, Object> map) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");

        for (Entry<String, Object> entry : map.entrySet()) {

            if (entry.getValue() instanceof Map) {
                builder.append("    \"").append(entry.getKey()).append("\": ");
                String mapString = mapToPrettyJsonString((Map<String, Object>) entry.getValue());
                String[] lines = mapString.split("\n");

                builder.append(lines[0]).append("\n");
                String[] otherLines = Arrays.copyOfRange(lines, 1, lines.length);
                for (String line : otherLines) {
                    builder.append("    ").append(line).append("\n");
                }
                builder.deleteCharAt(builder.length() - 1);
                builder.append(",\n");

            } 
            else if (entry.getValue() instanceof List) {
                builder.append("    \"").append(entry.getKey()).append("\": ");
                List<?> list = (List<?>) entry.getValue();
                builder.append("[\n");
                for (Object o : list) {
                    if (o instanceof Map) {
                        String mapString = mapToPrettyJsonString((Map<String, Object>) o);
                        String[] lines = mapString.split("\n");

                        builder.append("        ").append(lines[0]).append("\n");
                        String[] otherLines = Arrays.copyOfRange(lines, 1, lines.length);
                        for (String line : otherLines) {
                            builder.append("        ").append(line).append("\n");
                        }
                        builder.deleteCharAt(builder.length() - 1);
                        builder.append(",\n");
                    } 
                    else if (o instanceof List) {
                        throw new SerializerException("Double nested lists aren't supported for jsonMap conversion. Map key: " + entry.getKey(), null);
                    }
                    else if (o instanceof String || o.getClass().isEnum()) {
                        builder.append("\"").append(o).append("\"").append(",\n");
                    }
                    else {
                        builder.append(entry.getValue()).append(",\n");
                    }
                }
                builder.deleteCharAt(builder.length() - 2);
                builder.append("    ],\n");	
            }
            else {
                builder.append("    \"").append(entry.getKey()).append("\": ");
                if (entry.getValue() instanceof String || entry.getValue().getClass().isEnum()) {
                    builder.append("\"").append(entry.getValue()).append("\"").append(",\n");
                }
                else {
                    builder.append(entry.getValue()).append(",\n");
                }
            }

        }

        builder.deleteCharAt(builder.length() - 2);
        builder.append("}");

        return builder.toString();
    }
	
    private static Map<String, Object> jsonStringToMap(String json) {

        // "{"ham":{"cheese":1,"list":[1,2,3]}}"
        // "ham":{"cheese":1,"list":[1,2,3]}

        Map<String, Object> map = new LinkedHashMap<>(); // linked to maintain order

        int i = 1;
        while (i < json.length()-1) {

            // get key
            String key = "";
            if (json.charAt(i) == '\"') {
                int j = i+1;
                while(json.charAt(j) != '\"') j++;
                
                key = json.substring(i+1, j);
                i = j+1;
            }

            // get value
            String valueString = "";
            if (json.charAt(i) == ':') {
                int j = i+1;
                if (json.charAt(j) == '{') {
                    int openBrackets = 1;
                    while (openBrackets > 0) {
                        j++;
                        if (json.charAt(j) == '{') openBrackets++;
                        if (json.charAt(j) == '}') openBrackets--;
                    }
                }
                else if (json.charAt(j) == '[') {
                    int openBrackets = 1;
                    while (openBrackets > 0) {
                        j++;
                        if (json.charAt(j) == '[') openBrackets++;
                        if (json.charAt(j) == ']') openBrackets--;
                    }
                }
                else {
                    while (json.charAt(j) != ',' && json.charAt(j) != '}') j++;
                    j--;
                }
                valueString = json.substring(i+1, j+1);
                i = j+1;
            }
            Object value = getObjectFromString(valueString);

            i++;
            map.put(key, value);
        }


        return map;
    }

    public static Object getObjectFromString(String valueString) {
        Object value;
        if (valueString.charAt(0) == '{') {
            value = jsonStringToMap(valueString);
        }
        else if (valueString.charAt(0) == '[') {
            // remove brackets
            valueString = valueString.substring(1, valueString.length()-1);

            // split by commas
            List<String> values = new ArrayList<>();
            int openBrackets = 0;
            int j = 0;
            for (int i = 0; i < valueString.length(); i++) {

                if (valueString.charAt(i) == '[' || valueString.charAt(i) == '{') openBrackets++;
                if (valueString.charAt(i) == ']' || valueString.charAt(i) == '}') openBrackets--;

                if (valueString.charAt(i) == ',' && openBrackets == 0) {
                    values.add(valueString.substring(j, i));
                    j = i+1;
                }
            }
            values.add(valueString.substring(j));

            // convert each value
            List<Object> list = new ArrayList<>();
            for (String val : values) {
                list.add(getObjectFromString(val));
            }

            value = list;
        }
        else if (valueString.charAt(0) == '\"') {
            value = valueString.substring(1, valueString.length()-1);
        }
        else {
            value = new BigDecimal(valueString);
        }

        return value;
    }

    public static Object convertBigDecimalToType(BigDecimal number, Class<?> numberType) {
        if (numberType == int.class || numberType == Integer.class) {
            return number.intValue();
        }
        else if (numberType == long.class || numberType == Long.class) {
            return number.longValue();
        }
        else if (numberType == double.class || numberType == Double.class) {
            return number.doubleValue();
        }
        else if (numberType == float.class || numberType == Float.class) {
            return number.floatValue();
        }
        else if (numberType == short.class || numberType == Short.class) {
            return number.shortValue();
        }
        else if (numberType == byte.class || numberType == Byte.class) {
            return number.byteValue();
        }
        else if (numberType == BigDecimal.class) {
            return number;
        }
        else {
            throw new SerializerException("Can't convert BigDecimal to type " + numberType.getName(), null);
        }
    }

    private static Object convertStringToDate(String dateString, Class<?> dateType) {
        try {
            if (dateType == ZonedDateTime.class) {
                return ZonedDateTime.parse(dateString);
            }
            else if (dateType == OffsetDateTime.class) {
                return OffsetDateTime.parse(dateString);
            }
            else if (dateType == LocalDateTime.class) {
                return LocalDateTime.parse(dateString);
            }
            else if (dateType == LocalDate.class) {
                return LocalDate.parse(dateString);
            }
            else {
                throw new SerializerException(dateType.getName() + " isn't a supported date type use these instead (ZonedDateTime, OffsetDateTime, LocalDateTime, LocatDate)", null);
            }
        }
        catch (DateTimeParseException e) {
            throw new SerializerException("Failed to parse date string '" + dateString + "' to type " + dateType.getName(), e);
        }
    }




    /**
     * Determines if a type is one of the following:
     * - String
     * - Date types
     * - Numeric Types
     * @param type
     * @return
     */
    private static boolean isBasicJavaType(Class<?> type) {
        return isNumericClass(type) || Temporal.class.isAssignableFrom(type) || type == String.class || isBoolean(type);
    }

    private static boolean isBoolean(Class<?> type) {
        return type == Boolean.class || type == boolean.class;
    }

    private static boolean isNumericClass(Class<?> type) {
        return Number.class.isAssignableFrom(type) || isPrimitiveNumericClass(type);
    }

    private static boolean isPrimitiveNumericClass(Class<?> type) {
        return type == int.class || type == long.class || type == double.class
                || type == float.class || type == short.class || type == byte.class;
    }

    /**
     * Tries to cast the object to the desired type. Throwing an exception if not possible.
     *
     * @param obj object to cast
     * @param desiredType type to cast too
     * @return cast object of type T
     * @param <T> type
     */
    private static <T> T safeCast(Object obj, Class<T> desiredType) {
        if (desiredType.isInstance(obj)) {
            return desiredType.cast(obj);
        }
        String message = "Failed trying to cast class " + obj.getClass().getName() + " to " + desiredType.getName();
        throw new SerializerException(message, null);
    }


    /**
     * Tries to cast the map to the desired type. Throwing an exception if not possible.
     *
     * @param obj map to cast
     * @param keyType type to cast keys too
     * @param valueType type to cast values too
     * @return cast map of type Map<T,S>
     * @param <T> type
     * @param <S> type
     */
    public static <T, S> Map<T,S> safeCastMap(Object obj, Class<T> keyType, Class<S> valueType) {
        try {
            Map<T, S> newMap = new HashMap<>();
            for(Object entry : safeCast(obj, Map.class).entrySet()) {
                if (entry instanceof Map.Entry<?, ?> newEntry) {
                    newMap.put(
                        safeCast(newEntry.getKey(), keyType),
                        safeCast(newEntry.getValue(), valueType)
                    );
                }
            }
            return newMap;
        } catch(Exception e){
            String message = "Failed trying to cast class " + obj.getClass().getName()
                + " to map of " + keyType.getName() + ", " + valueType.getName();
            throw new SerializerException(message, null);
        }
    }

    /**
     * Tries to cast the list to the desired type. Throwing an exception if not possible.
     *
     * @param list list to cast
     * @param type type to cast keys too
     * @return cast list of type List<T>
     * @param <T> type
     */
    public static <T> List<T> safeCastList(List<?> list, Class<T> type) {
        try {
            List<T> newList = new ArrayList<>();
            if (list == null) {
                return newList;
            }
            for(Object item : list) {
                newList.add(
                    safeCast(item, type)
                );
            }
            return newList;
        } catch(Exception e) {
            String message = "Failed trying to cast class " + list.getClass().getName()
                + " to list of " + type.getName();
            throw new SerializerException(message, e);
        }

    }


    /**
     * Annotation that can be used to ignore fields when serializing to json.
     * 
     * <p>Does not prevent the field being set when deserializing.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public static @interface JsonIgnore {}
    

    public static class SerializerException extends RuntimeException {
        public SerializerException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public abstract static class ParamType<T> {
        private final Type type;

        protected ParamType() {
            Type superClass = getClass().getGenericSuperclass();
            if (superClass instanceof ParameterizedType) {
                this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException("TypeReference must be parameterized");
            }
        }

        public Type getType() {
            return this.type;
        }

    }
}
