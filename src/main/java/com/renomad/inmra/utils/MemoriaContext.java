package com.renomad.inmra.utils;

import com.renomad.minum.state.Context;

/**
 * An object we pass around throughout Memoria, holds instances of some
 * values we need throughout.
 * @param constants constant values that are set in memoria.config
 * @param fileUtils a wrapper around Minum's fileutils, custom for our needs
 */
public record MemoriaContext(Constants constants, IFileUtils fileUtils) {

    /**
     * These are for context particular to our business needs here, not
     * as generalistic as what Minum provides.  Instead, it's specific
     * stuff like the root directory of our templates directory or
     * wrappers for FileUtils.
     */
    public static MemoriaContext buildMemoriaContext(Context context) {
        Constants constants = new Constants();
        var fileUtils = new FileUtils(new com.renomad.minum.utils.FileUtils(context.getLogger(), context.getConstants()), constants);
        return new MemoriaContext(constants, fileUtils);
    }

}
