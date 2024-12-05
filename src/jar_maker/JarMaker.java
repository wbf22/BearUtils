package jar_maker;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;



/**
 * Class used for creating a jar file in a project.
 */
public class JarMaker {
    public static void main(String[] args) {

        try {
            String main = args[0];
            if (main.contains("help")) {
                printHelp();
                return;
            }
    
            String name = args[1];
            List<String> sources = new ArrayList<>();
            for (int i = 2; i < args.length; i++) {
                sources.add(args[i]);
            }

            try {

                // Compile Java files
                List<String> javaFiles = sources.stream()
                    .flatMap(sourceDir -> getFilesInDirectoryRecursively(sourceDir, ".java").stream())
                    .toList();
                StringBuilder compileCommand = new StringBuilder("javac -d bin ");
                for (String javaFile : javaFiles)  compileCommand.append(javaFile).append(" ");
                runProcess(compileCommand.toString()); // javac -d bin src/jar_maker/JarMaker.java
                
                // Create manifest file
                runProcess("echo Main-Class: " + main + " > MANIFEST.MF"); // echo Main-Class: jar_maker.JarMaker > MANIFEST.MF
    
                // Create JAR file
                List<String> classFiles = sources.stream()
                    .flatMap(sourceDir -> getFilesInDirectoryRecursively(sourceDir, ".class").stream())
                    .toList();
                StringBuilder jarCommand = new StringBuilder("jar cfm " + name + " MANIFEST.MF -C bin .");
                for (String classFile : classFiles)  jarCommand.append(classFile).append(" ");
                runProcess(jarCommand.toString()); // jar cfm JarMaker.jar MANIFEST.MF -C bin .
    
                // delete the manifest file
                Files.deleteIfExists(Paths.get("MANIFEST.MF"));

                // delete bin
                deleteDirectory(new File("bin"));

                // runProcess("java -jar " + name); // java -jar JarMaker.jar
    
                System.out.println("Java project compiled and JAR file created successfully.");
                
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println();
            printHelp();
            
        }
        
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("******** JarMaker ********");
        System.out.println();
        System.out.println("Usage: java -jar JarMaker <main> <name> <src...>");
        System.out.println("main: The name of main class of the Java project. If you main class is 'Main.java' in package 'com.example' then the main class is 'com.example.Main'");
        System.out.println("name: The name of the JAR file to be created. Eg 'MyJar.jar'");
        System.out.println("src: The source directory(s) of the Java project relative to the current directory. Eg 'src'");
    }

    private static void runProcess(String command) throws IOException, InterruptedException {

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("bash", "-c", command);
        Process process = processBuilder.start();
        process.waitFor();

        process.waitFor();
        String error = new String(process.getErrorStream().readAllBytes());
        if (!error.isBlank()) throw new RuntimeException(error);
        String out = new String(process.getInputStream().readAllBytes());
        if (!out.isBlank()) System.out.println(out);
    }

    private static List<String> getFilesInDirectoryRecursively(String directory, String extension) {
        try {
            return Files.walk(Paths.get(directory))
                .filter(path -> !directory.equals(path.toString()))
                .flatMap(path -> {
                    if (Files.isDirectory(path)) {
                        return getFilesInDirectoryRecursively(path.toString(), extension).stream();
                    }
                    else if (path.toString().endsWith(extension)) {
                        return List.of(path).stream();
                    }
                    else {
                        return new ArrayList<Path>().stream();
                    }
                })
                .map(path -> path.toString())
                .toList();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDirectory(child);
                }
            }
        }
        file.delete();
    }
}