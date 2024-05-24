package cache;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Cache {


    public final Map<String, Object> cacheMap = new ConcurrentHashMap<>();
    public final String id;

    public Cache() {
        this.id = UUID.randomUUID().toString();
    }

    public void put(String key, Object value) {
        cacheMap.put(key, value);
    }
    
    public <T> T get(String key) {
        Object value = cacheMap.get(key);

        if (value != null) 
            return (T) value;
    
        return null;
    }


    public void clear() {
        cacheMap.clear();
    }

    public static class ParamType<Q> implements Type {

        private final Type type;

        public ParamType() {
            Type genericSuperclass = getClass().getGenericSuperclass();
            ParameterizedType paramType = (ParameterizedType) genericSuperclass;
            this.type = paramType.getActualTypeArguments()[0];
        }


        public Type getType() {
            return this.type;
        }

    }




    public String getId() {
        return id;
    }

}
