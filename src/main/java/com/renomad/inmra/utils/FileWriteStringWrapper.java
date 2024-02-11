package com.renomad.inmra.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public class FileWriteStringWrapper implements IFileWriteStringWrapper{
    @Override
    public Path writeString(Path path, CharSequence csq, OpenOption... options) throws IOException {
        return Files.writeString(path, csq, options);
    }
}
