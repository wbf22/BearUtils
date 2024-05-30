
# OneFile

This class can be used to condense an entire repo down into one file. This allows users to easily copy the repo and use it in their project.

To convert a repo into a single file run something like this:

```
    public static void main(String[] args) {
        OneFile.buildOneFile("MyRepo", "src/main/java/.../...");
    }
```

This will collect all the files in the repo that end with '.java' and add them all into one large class that might look like this
```
public class MyRepo {

    public static class MyFirstClass {
        ...
    }

    public static class MySecondClass {
        ...
    }

}
```


You can then use those classes in another project with an import statement like this:

```
import path.to.myrepo.file.MyRepo.MyFirstClass;
```