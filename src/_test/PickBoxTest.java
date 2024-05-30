package _test;

import java.util.List;
import java.util.Map;

import pickbox.PickBoxRepo.PickBox;
import pickbox.PickBoxRepo.PickerUtil;
import pickbox.PickBoxRepo.Resolver;

public class PickBoxTest {
    public static void main(String[] args) {
        Parent request = new Parent();
        request.name = "";

        Child child = new Child();
        child.age = 0;
        child.gender = Gender.MALE;
        child.name = "";

        request.children = List.of(child);


        ParentResolver parentResolver = new ParentResolver();
        ChildResolver childResolver = new ChildResolver();

        PickBox pickerBox = new PickBox(
            List.of(parentResolver, childResolver)
        );

        Parent result = pickerBox.resolveRequest(request, 1);

        assert result.gender == null;
        assert "Stinky Dad".equals(result.name);
        Child firstChild = result.children.get(0);
        assert 11 == firstChild.age;
        assert Gender.MALE.equals(firstChild.gender);


        Map<String, Object> resMap = PickerUtil.mapify(result);

        System.out.println(PickerUtil.jsonMap(resMap));
    }

    
    public static class Parent {
        


        public String name;
        public Gender gender;
        public List<Child> children;



    }

    public static enum Gender {
        MALE,
        FEMALE
    }

    public static class Child {
        


        public String name;
        public Gender gender;
        public Integer age;


    }

    public static class ParentResolver extends Resolver<Parent, Void, Integer> {



        @Override
        public Parent resolve(Void parent, Integer args) {
            Parent response = new Parent();
            response.name = "Stinky Dad";
            response.gender = Gender.MALE;

            return response;
        }
        
    }

    public static class ChildResolver extends Resolver<List<Child>, Parent, Integer> {

        @Override
        public List<Child> resolve(Parent parent, Integer args) {
            
            // normally you would use the key to look up the parent or something
            // but this is just fake
    
            Child child1Res = new Child();
            child1Res.age = 11;
            child1Res.name = "Dan";
            child1Res.gender = Gender.MALE;
    
            Child child2Res = new Child();
            child2Res.age = 4;
            child2Res.name = "Julie";
            child2Res.gender = Gender.FEMALE;
    
            return List.of(child1Res, child2Res);
        }
        
    }

}
