package com.renomad.inmra.utils;

import java.io.IOException;
import java.nio.file.Path;

/**
 * This is a wrapper for {@link java.nio.file.Files#delete;} to enable better testing
 */
public interface IFileDeleteWrapper {

    void delete(Path path) throws IOException;
}
