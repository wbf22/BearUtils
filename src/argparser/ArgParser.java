package argparser;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class ArgParser {

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
        if (shortFlags == null) shortFlags = new HashMap<>();
        if (booleanFlags == null) booleanFlags = new HashMap<>();
        if (longFlags == null) longFlags = new HashMap<>();
        
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

