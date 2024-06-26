package id;

import java.util.UUID;


/**
 * Class for making ids with the following options
 * <ul>
 * <li>with a prefix</li>
 * <li>using secure random</li>
 * <li>with or without a seed (deterministic vs random)</li>
 * <li>a certain length</li>
 * <li>upper case or lower case</li>
 * </ul>
 * 
 * <p> Examples
 * <ul>
 * <li>user_c20ad4</li>
 * <li>product_C51CE410</li>
 * <li>service_aab3238922bc325a</li>
 * <li>teacher_9bf31c7ff062336a_96d3c8bd1f8f2ff3_400de3fb81df39c6_ac51c73a848549a9_e918dbbbd91331e4</li>
 * </ul>
 * 
 * <p> This class uses the java uuid internally
 * 
 */
public class BearId {

    private static final int DIV_LENGTH = 8;


    private BearId(){}
    
    
    public static String randomId(String prefix, String seed) {
        return randomId(prefix, seed, 8, DIV_LENGTH, false);
    }

    /**
     * 
     * Creates a random id using secure random.
     * Prefixes with the provided prefix.
     * 
     * @param prefix
     * @param seed leave null if you want an id generated by secure random
     * @param length length of the id excluding the prefix and underscores
     * @param underscoreSpacing the spacing between underscores
     * @param upperCase weather id characters should be upper case
     * @return
     */
    public static String randomId(String prefix, String seed, Integer length, Integer underscoreSpacing, boolean upperCase) {
        
        // append uuids together until we get too length
        StringBuilder uuidMash = new StringBuilder();
        while (uuidMash.length() < length) {
            String uuid = (seed == null)? UUID.randomUUID().toString() : UUID.nameUUIDFromBytes(seed.getBytes()).toString();
            seed = uuid;
            uuid = uuid.replace("-", "");
            uuidMash.append(uuid);
        }
        String uuid = uuidMash.substring(0, length);


        // split up into divlength chunks and kebob
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < uuid.length(); i+=underscoreSpacing) {
            id.append("_");

            int end = i+underscoreSpacing;
            if (end > uuid.length()) end = uuid.length();
            id.append(uuid.substring(i, end));
        }

        // convert to uppercase if needed
        String idAsString = id.toString();
        if (upperCase) idAsString = idAsString.toUpperCase();


        return prefix + idAsString;
    }
}
