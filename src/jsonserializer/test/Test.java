package jsonserializer.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import jsonserializer.Serializer;

public class Test {
    
    public static void main(String[] args) throws IOException {
        String json = Files.readString(Path.of("src/jsonserializer/test/failure3.json"));

        Completion completion = Serializer.fromJson(json, Completion.class);

        System.out.println(completion.id);

    }




    // DTOS

    public static class Prompt {
        public String model;
        public List<Message> messages;
        public int max_tokens = 100;

    }
    public enum Role {
        system, user, assistant
    }
    public static class Message {
        public String role;
        public String content;

        public Message() {}

        public Message(String content, String role) {
            this.content = content;
            this.role = role;
        }
    }


    public static class Completion {
        public String id;
        public String object;
        public long created;
        public String model;
        public List<Choice> choices;

    }

    public static class Choice {
        public Message message;
        public String finish_reason;
        public int index;
    }


}
