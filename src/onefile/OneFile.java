package onefile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.DelayQueue;

/**
 * Converts a repository into one large file that can be easily copied into 
 * a project
 */
public class OneFile {

    private OneFile() {}

    /**
     * Converts a repository to one large file for ease of copying into a project.
     * 
     * The following must be true for a repository to be able to do the conversion
     * - no duplicate class names
     * - no files containing the '.java' in their name that aren't java classes/enums etc..
     * - all of your repo is under one package
     * - your main package name doesn't match any of your dependencies names (rare)
     * - your class doesn't have a java doc followed by import statements at the top of the class (weird)
     * - (the code actually compiles)
     * 
     * @param repositoryName name of your repository, and resulting name of one file output
     * @param rootDirectory root directory that contains all classes you want to include in the one file
     */
    public static void buildOneFile(String repositoryName, String rootDirectory) {
        if (repositoryName == null)
            throw new OneFileException("Please provide the resporitory name", null);
        if (rootDirectory == null)
            throw new OneFileException("Please provide the root directory", null);

        // collect all the files
        Set<File> allFiles = getAllJavaFiles(rootDirectory);

        // prepare strings for each java file
        Set<String> imports = new HashSet<>();
        List<String> classes = new ArrayList<>();
        String packageName = "";
        parseFiles(allFiles, imports, classes, packageName);

        // build oneFile
        StringBuilder oneFile = new StringBuilder();
        oneFile.append("package ").append(packageName).append(";\n\n");
        

        // write out
        
    }

    private static Set<File> getAllJavaFiles(String rootDir) {
        Set<File> allFiles = new HashSet<>();
        Set<String> fileNames = new HashSet<>();
        File root = new File(rootDir);

        Deque<File> filesToExplore = new ArrayDeque<>();
        filesToExplore.addAll(
            Arrays.stream(root.listFiles()).toList()
        );
        while (!filesToExplore.isEmpty()) {
            File file = filesToExplore.pop();
            if (file.isFile()) {
                if (file.getName().contains(".java")) {
                    allFiles.add(file);
                    if (fileNames.contains(file.getName())) {
                        throw new OneFileException(
                            "Found duplicate file with name " + file.getName() + ". You'll have to rename it for one file To work.", null
                        );
                    }
                    fileNames.add(file.getName());
                } 
            }
            else {
                filesToExplore.addAll(
                    Arrays.stream(file.listFiles()).toList()
                );
            }
        }

        return allFiles;
    }
    
    private static boolean containsJavaDeclaration(String line) {
        return line.contains("class") || line.contains("interface") || line.contains("enum");
    }

    private static void parseFiles(Set<File> allFiles, Set<String> imports, List<String> classes, String packageName) {
        for (File file : allFiles) {
            try ( BufferedReader fileReader = new BufferedReader(new FileReader(file)) ) {
                
                // package line
                String packageLine = fileReader.readLine();
                while (!packageLine.contains("package"))
                    packageLine = fileReader.readLine();

                if (packageName.isEmpty()) {
                    packageLine = packageLine.replaceFirst("package ", "");
                    packageName = packageLine.split(".")[0];
                }

                // import statements
                String line = fileReader.readLine();
                while (
                    !containsJavaDeclaration(line) && 
                    !line.contains("/*")
                ) {
                    if (line.contains("import ")) {
                        String importPackage = line.replace("import ", "").split(".")[0];
                        if (!importPackage.equals(packageName)) imports.add(line);
                    }
                }

                // class, enum, interface
                StringBuilder classStringBuilder = new StringBuilder();
                while (line != null) {
                    classStringBuilder.append(line);
                    line = fileReader.readLine();
                }

                // make all classes static (since they'll be inner classes)
                String classString = classStringBuilder.toString();
                classString = classString.replace("class", "static class");
                classString = classString.replace("interface", "static interface");

                classString = classString.replace("static static", "static");


                classes.add(classString);

            } catch (IOException e) {
                throw new OneFileException("Error reading file: " + file.getPath(), e);
            }
            
        }
    }



    public enum Bob {}
    public abstract static class Bill {}
    public static class Jane {}
    public static interface Sarah {}

    public static class OneFileException extends RuntimeException {

        public OneFileException(String message, Exception cause) {
            super(message, cause);
        }
    }
}
