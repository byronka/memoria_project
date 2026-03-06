package com.renomad.inmra.utils;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

public class GeneralUtilities {


    /**
     * Deletes a directory, deleting everything inside it
     * recursively afterwards.  A more dangerous method than
     * many others, take care.
     */
    public static void deleteDirectoryRecursivelyIfExists(Path myPath) {
        if (!myPath.toFile().exists()) return;
        try (Stream<Path> walk = Files.walk(myPath)) {

            final var files = walk.sorted(Comparator.reverseOrder())
                    .map(Path::toFile).toList();

            for(var file: files) {
                System.out.println("deleting " + file);
                Files.delete(file.toPath());
            }
        } catch (IOException ex) {
            throw new RuntimeException("Error during deleteDirectoryRecursivelyIfExists: " + ex);
        }
    }


    public static void copyDir(String src, String dest) {
        try {
            Path path = Paths.get(src);
            try (Stream<Path> walk = Files.walk(path)) {
                walk.forEach(a -> {
                    Path b = Paths.get(dest, a.toString().substring(src.length()));
                    try {
                        Files.copy(a, b);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        } catch (IOException e) {
            //permission issue
            throw new RuntimeException(e);
        }
    }
}
