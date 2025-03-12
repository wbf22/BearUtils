package jar_maker;


import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;



/**
 * Class used for creating a jar file in a project.
 */
public class JarMaker {
    public static void main(String[] args) {

        Map<String, String> shortArgs = Map.of(
            "-m", 
            """
            The name of main class of the Java project. If you main class is 
            'Main.java' in package 'com.example' then the main class is 
            'com.example.Main'
            """,
            "-n", 
            """
            The name of the JAR file to be created. Eg 'MyJar.jar'
            """,
            "-s", 
            """
            Path to source files. This would probably be the main directory of 
            your project, or it also could be the directory where dependencies
            for your project are stored. Seperated by ','
            """
        );

        ArgParser parser = new ArgParser(
            args, 
            shortArgs, 
            null, 
            null, 
            Map.of("-h", "View help", "--help", "View help"), 
            "******** JarMaker ********",
            """
            A command line util for binding up a java project into a runnable 
            jar with a single command.     
            """,
            """
            By default, the main class is the first class in the first source 
            directory, the name of the jar will assume the same name as that class.

            We recommend making a bash script or alias to make creating a jar easier.
            On linux and mac you can make a file such as 'make-jar.sh' and stick a command 
            like this in the file: 
            
                'java -jar path/to/JarMaker.jar -m com.example.MyMainClass -n MyJar -s /path/to/src,/path/to/dependency'

            By default, the main class is the first class in the first source directory, the name of the jar will assume the same name as that class.
            If no directories are specified as the source directory, the current directory is used.
            """,
            true
        );
        Map<String, String> parsedArgs = parser.parseArgs();

        try {
            String main = parsedArgs.get("-m");
            String name = parsedArgs.get("-n");
            String sourcesString = parsedArgs.get("-s");
            if (sourcesString == null) sourcesString = System.getProperty("user.dir");
            if (sourcesString.startsWith(".")) sourcesString = System.getProperty("user.dir") + sourcesString.substring(1);
            List<String> sources = Arrays.asList(sourcesString.split(","));



            deleteDirectory(new File("bin"));

            // Compile Java files
            List<String> files = sources.stream()
                .flatMap(sourceDir -> getFilesInDirectoryRecursively(sourceDir, null).stream())
                .filter(file -> !file.contains(".git"))
                .toList();
            List<String> javaFiles = files.stream()
                .filter(file -> file.endsWith(".java"))
                .toList();
            

            // determine main class and jar name (if not provided)
            if (main == null) {
                List<String> firstSourceJavaFiles = getFilesInDirectoryRecursively(sources.get(0), ".java");
                Collections.sort(firstSourceJavaFiles, (a, b) -> Integer.compare(a.split("/").length, b.split("/").length));
                Path mainFile = Path.of(
                    firstSourceJavaFiles.stream().findFirst().get()
                );
                main = mainFile.toString().replace(".java", "");
                if (main.contains("/")) {
                    main = main.replace("/", ".");
                    main = main.substring(main.indexOf(".") + 1);
                }
            }
            System.out.println("Using main class: " + main);
            if (name == null) {
                name = main.contains(".") ? main.substring(main.lastIndexOf(".") + 1) : main;
                name += ".jar";
            }
            System.out.println("Creating jar with name: " + name);

            // don't include itself again in new jar
            String jarName = name;
            List<String> jarFiles = files.stream()
                .filter(file -> file.endsWith(".jar") && !file.endsWith(jarName))
                .toList();
            
            // compile classes
            StringBuilder compileCommand = new StringBuilder("javac -Xlint:deprecation -d bin ");
            for (String javaFile : javaFiles)  compileCommand.append(javaFile).append(" ");
            runProcess(compileCommand.toString()); // javac -d bin src/jar_maker/JarMaker.java

            // copy resources to bin
            files.stream()
                .filter(file -> !file.endsWith(".java") && ! file.endsWith(".jar"))
                .forEach(file -> {
                    try {
                        Path fullPath = Paths.get(file);
                        if (!fullPath.getFileName().toString().equals(jarName)) {
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
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            
            // make fat jar with dependencies
            if (!jarFiles.isEmpty()) {
                for (String jar : jarFiles) {

                    // copy dependencies to bin
                    Path jarFile = Paths.get(jar);
                    Path copyPath =  Path.of("bin/" + jarFile.getFileName());
                    Files.copy(jarFile, copyPath);

                    // extract dependencies
                    runProcess("jar xf " + jarFile.getFileName(), "bin");

                    // remove jar
                    Files.deleteIfExists(copyPath);

                    System.out.println("Found jar in src folder: " + jar);
                    System.out.println("Combining with jar as a dependency");

                }
            }


            // Create JAR file
            runProcess("jar cfe " + name + " " + main + " -C bin ."); // jar cfm JarMaker.jar MANIFEST.MF -C bin .
          
            // delete bin
            deleteDirectory(new File("bin"));

            System.out.println("Java project compiled and JAR file created successfully.");
            
        }
        catch (Exception e) {
            e.printStackTrace();
            System.out.println();
            parser.printMan();
        }
        
    }



    private static void runProcess(String command) throws IOException, InterruptedException {
        runProcess(command, null);
    }

    private static void runProcess(String command, String directory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("bash", "-c", command);
        if (directory != null) processBuilder.directory(new File(directory));
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        String error = new String(process.getErrorStream().readAllBytes());
        if (exitCode != 0) throw new RuntimeException(error);
        if (!error.isBlank()) System.out.println(error);
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
                .collect(Collectors.toList());
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



    public static class ArgParser {

        public static class HelpException extends RuntimeException {}

        public String[] args;
        public Map<String, String> shortFlags;
        public Map<String, String> longFlags;
        public Map<String, String> booleanFlags;
        public Map<String, String> helpFlags;
        public String appName;
        public String appDescription;
        public String notes;
        public boolean showManOnNoArgs;
    
        public ArgParser(
            String[] args, 
            Map<String, String> shortFlags, 
            Map<String, String> longFlags, 
            Map<String, String> booleanFlags, 
            Map<String, String> helpFlags, 
            String appName, 
            String appDescription, 
            String notes,
            boolean showManOnNoArgs
        )
        {
            this.args = args;
            this.shortFlags = shortFlags;
            this.longFlags = longFlags;
            this.booleanFlags = booleanFlags;
            this.helpFlags = helpFlags;
            this.appName = appName;
            this.appDescription = appDescription;
            this.notes = notes;
            this.showManOnNoArgs = showManOnNoArgs;
        }

        private Map<String, String> parseArgs() {
            
            try {

                if (showManOnNoArgs && args.length == 0) throw new HelpException();
    
                Map<String, String> parsedArgs = new HashMap<>();
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    
                    // short flags
                    if (shortFlags.containsKey(arg)) {
                        i++;
                        String nextValue = args[i];
                        parsedArgs.put(arg, nextValue);
                    }
                    // boolean flags
                    else if (booleanFlags.containsKey(arg)) {
                        parsedArgs.put(arg, "");
                    }
                    // long flags
                    else if(arg.contains("=")) {
                        String[] longArg = arg.split("=");
                        parsedArgs.put(longArg[0], longArg[1]);
                    }
                    else {
    
                        if (!helpFlags.containsKey(arg)) {
                            throw new IllegalArgumentException(arg + " is not a recognized argument");
                        }
                        else {
                            throw new HelpException();
                        }
    
                    }
                }
    
                return parsedArgs;
            }
            catch(Exception e) {
                printMan();
    
                if (e instanceof HelpException) {
                    throw e;
                }
                else {
                    if (e instanceof IllegalArgumentException) throw e;
        
                    throw new IllegalArgumentException("Error parsing args!");

                }
            }
            
    
        }
        
        private void printMan() {
            System.out.println(appName);
            System.out.println();
            System.out.println(appDescription);
            System.out.println();
            System.out.println("Args");
            Map<String, String> allFlags = new LinkedHashMap<>();
            if (booleanFlags != null) allFlags.putAll(booleanFlags);
            if (shortFlags != null) allFlags.putAll(shortFlags);
            if (longFlags != null) allFlags.putAll(longFlags);
            if (helpFlags != null) allFlags.putAll(helpFlags);
            int longestFlagName = allFlags.entrySet().stream()
                .mapToInt(entry -> entry.getKey().length())
                .max()
                .orElse(0);
            for (Entry<String, String> entry : allFlags.entrySet()) {
                String description = "   " + entry.getKey() + " ".repeat(longestFlagName - entry.getKey().length()) + " " + entry.getValue();
                System.out.println(description);
                System.out.println();
            }

            System.out.println(notes);
        }
    }

}