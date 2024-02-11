package com.renomad.inmra.utils;

import java.io.IOException;
import java.nio.file.Path;

public interface IFileUtils {
    /**
     * See {@link com.renomad.minum.utils.FileUtils#makeDirectory(Path)}
     */
    void makeDirectory(Path path) throws IOException;

    /**
     * Read a template file, expected to use this with {@link com.renomad.minum.templating.TemplateProcessor}
     */
    String readTemplate(String path);
}
