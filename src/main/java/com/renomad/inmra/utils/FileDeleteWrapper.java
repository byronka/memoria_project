package com.renomad.inmra.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileDeleteWrapper implements IFileDeleteWrapper{
    @Override
    public void delete(Path path) throws IOException {
        Files.delete(path);
    }
}
