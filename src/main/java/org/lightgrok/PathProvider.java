package org.lightgrok;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;



public class PathProvider {
    static boolean sStripRootLead = false;

    public static Path rootIndexDirectory() {
        return Paths.get("/tmp/lightgrok/index");
    }

    public static Boolean getStripRootLead() {
        return sStripRootLead;
    }

    public static Boolean setStripRootLead(boolean enabled) {
        return sStripRootLead = enabled;
    }

    public static String hashSourcePath(String sourcePath) {
        HashFunction sha256 = Hashing.sha256();
        return sha256.hashString(sourcePath, StandardCharsets.UTF_8).toString();
    }
}
