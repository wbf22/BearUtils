package jar_maker;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;



/**
 * Class used for creating a jar file in a project.
 */
public class JarMaker {
    public static void main(String[] args) {

        try {
            if (args[0].contains("help") || args[0].contains("-h")) {
                printHelp();
                return;
            }

            String main = null;
            String name = null;
            List<String> sources = new ArrayList<>();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("-m")) {
                    main = args[i + 1];
                    i++;
                }
                else if (arg.equals("-n")) {
                    name = args[i + 1];
                    i++;
                }
                else {
                    sources.add(arg);
                }

            }
    

            try {

                // Compile Java files
                List<String> files = sources.stream()
                    .flatMap(sourceDir -> getFilesInDirectoryRecursively(sourceDir, null).stream())
                    .toList();
                List<String> javaFiles = files.stream()
                    .filter(file -> file.endsWith(".java"))
                    .collect(Collectors.toList());

                // determine main class and jar name (if not provided)
                if (main == null) {
                    Collections.sort(javaFiles, (a, b) -> Integer.compare(a.split("/").length, b.split("/").length));
                    Path mainFile = Path.of(
                        javaFiles.stream().findFirst().get()
                    );
                    main = mainFile.toString().replace(".java", "");
                    if (main.contains("/")) {
                        main = main.replace("/", ".");
                        main = main.substring(main.indexOf(".") + 1);
                    }
                }
                if (name == null) {
                    name = main.contains(".") ? main.substring(main.lastIndexOf(".") + 1) : main;
                    name += ".jar";
                }
                
                StringBuilder compileCommand = new StringBuilder("javac -d bin ");
                for (String javaFile : javaFiles)  compileCommand.append(javaFile).append(" ");
                runProcess(compileCommand.toString()); // javac -d bin src/jar_maker/JarMaker.java

                // copy resources to bin
                files.stream()
                    .filter(file -> !file.endsWith(".java"))
                    .forEach(file -> {
                        try {
                            Path fullPath = Paths.get(file);
                            Path parentPath = fullPath;
                            while (parentPath.getParent() != null) {
                                parentPath = parentPath.getParent();
                            }
                            Path relativePath = parentPath.relativize(fullPath);
                            Path newPath = Paths.get("bin", relativePath.getParent().toString());
                            Files.createDirectories(newPath);
                            Path newFile = Paths.get("bin", relativePath.toString());
                            Files.deleteIfExists(newFile);
                            Files.copy(Paths.get(file), newFile);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                
                // Create manifest file
                runProcess("echo Main-Class: " + main + " > MANIFEST.MF"); // echo Main-Class: jar_maker.JarMaker > MANIFEST.MF
    
                // Create JAR file
                StringBuilder jarCommand = new StringBuilder("jar cfm " + name + " MANIFEST.MF -C bin .");
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
        System.out.println(
        """
        ******** JarMaker ********

        Usage: java -jar JarMaker <src...>

        Arguments:
            <src...>    The source directory(s) of the Java project relative to the current directory. Eg 'src'

        Options:
            -m <main>   The name of main class of the Java project. If you main class is 'Main.java' in package 'com.example' then the main class is 'com.example.Main'
            -n <name>   The name of the JAR file to be created. Eg 'MyJar.jar'

        By default, the main class is the first class in the first source directory, the name of the jar will assume the same name as that class.

        """
        );
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
                    else if (extension == null || path.toString().endsWith(extension)) {
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