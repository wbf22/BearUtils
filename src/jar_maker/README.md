# Jar Maker
A simple tool for packaging a project into a jar file.


## Usage
Run the command like so to package a project into a jar file:

```bash
java -jar JarMaker.jar -m <main> -n <name> <src> <src> ...
```

Arguments:
- `<src>` The source directory(s) of the Java project relative to the current directory. Eg 'src' (you can include dependencies in the jar by adding more source directories)

Options:
- `-m <main>` The name of main class of the Java project. If you main class is 'Main.java' in package 'com.example' then the main class is 'com.example.Main'
- `-n <name>` The name of the jar file to created.

By default, the main class is the first class in the first source directory, the name of the jar will assume the same name as that class.

Any other files in the source directories will be copied to the JAR file and can be accessed using the getResourceAsStream() method with a ClassLoader object.

```java
    public void printResource(String location) throws IOException {

        ClassLoader classLoader = getClass().getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(location);

        if (inputStream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            reader.close();
            System.out.println("File read successfully!");
        }
        else {
            System.out.println("File not found! Available Files:");
            Enumeration<URL> resources = classLoader.getResources("");
            while (resources.hasMoreElements()) {
                URL resourceUrl = resources.nextElement();
                System.out.println(resourceUrl.getFile());
            }
        }
    }
```


## Building

If you trust me you can use the jar file here. 

To be safe, it's better to build the jar file yourself (with Java 17 installed). You can do that like so:
```bash
javac -d bin src/jar_maker/JarMaker.java
echo Main-Class: jar_maker.JarMaker > MANIFEST.MF
jar cfm JarMaker.jar MANIFEST.MF -C bin .
rm MANIFEST.MF
rm -r bin
```