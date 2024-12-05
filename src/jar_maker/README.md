# Jar Maker
A simple tool for packaging a project into a jar file.


## Usage
Run the command like so to package a project into a jar file:

```bash
java -jar JarMaker.jar <main> <name> <src> <src> ...
```

Where:
- `<main>` The name of main class of the Java project. If you main class is 'Main.java' in package 'com.example' then the main class is 'com.example.Main'
- `<name>` The name of the jar file to created.
- `<src>` The source directory(s) of the Java project relative to the current directory. Eg 'src' (you can include dependencies in the jar by adding more source directories)