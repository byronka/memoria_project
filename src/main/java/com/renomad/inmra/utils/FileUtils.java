package com.renomad.inmra.utils;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class FileUtils implements IFileUtils {

    private final com.renomad.minum.utils.FileUtils fileUtils;
    private final Constants constants;
    public static final Pattern badFilePathPatterns = Pattern.compile("//|\\.\\.|:|^/|^\\\\");

    public FileUtils(com.renomad.minum.utils.FileUtils fileUtils, Constants constants) {
        this.fileUtils = fileUtils;
        this.constants = constants;
    }

    /**
     * See {@link com.renomad.minum.utils.FileUtils#makeDirectory(Path)}
     */
    @Override
    public void makeDirectory(Path path) {
        fileUtils.makeDirectory(path);
    }

    /**
     * Read a template file, expected to use this with {@link com.renomad.minum.templating.TemplateProcessor}
     */
    @Override
    public String readTemplate(String path) {
        return fileUtils.readTextFile(constants.TEMPLATE_DIRECTORY + path);
    }

}
