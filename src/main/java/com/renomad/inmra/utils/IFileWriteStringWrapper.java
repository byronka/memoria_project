package com.renomad.inmra.utils;

import java.io.IOException;
import java.nio.file.OpenOption;
import java.nio.file.Path;

public interface IFileWriteStringWrapper {

    Path writeString(Path path, CharSequence csq, OpenOption... options)
            throws IOException;
}
