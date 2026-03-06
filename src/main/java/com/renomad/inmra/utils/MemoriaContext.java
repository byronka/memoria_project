package com.renomad.inmra.utils;


import com.renomad.inmra.security.ISecurityUtils;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.state.Context;
import com.renomad.minum.utils.CryptoUtils;


/**
 * Sort of a kitchen drawer of data we might need anywhere in the Memoria application
 */
public class MemoriaContext {

    private final Constants constants;
    private final IFileUtils fileUtils;
    private final Auditor auditor;
    private final CachedData cachedData;
    private ISecurityUtils securityUtils;

    /**
     * An object we pass around throughout Memoria, holds instances of some
     * values we need throughout.
     *
     * @param constants constant values that are set in memoria.config
     * @param fileUtils a wrapper around Minum's fileutils, custom for our needs
     * @param auditor a tool for improved audits
     */
    public MemoriaContext(Constants constants, IFileUtils fileUtils, Auditor auditor, CachedData cachedData) {
        this.constants = constants;
        this.fileUtils = fileUtils;
        this.auditor = auditor;
        this.cachedData = cachedData;
    }

    /**
     * These are for context particular to our business needs here, not
     * as generalistic as what Minum provides.  Instead, it's specific
     * stuff like the root directory of our templates directory or
     * wrappers for FileUtils.
     */
    public static MemoriaContext buildMemoriaContext(Context context) {
        Constants constants = new Constants();
        ILogger logger = context.getLogger();
        var fileUtils = new FileUtils(new com.renomad.minum.utils.FileUtils(logger, context.getConstants()), constants);
        var auditor = new Auditor(context);
        var cachedData = new CachedData();
        return new MemoriaContext(constants, fileUtils, auditor, cachedData);
    }

    public String getHashedPrivacyPassword() {
        return CryptoUtils.createPasswordHash(constants.PRIVACY_PASSWORD, "this_is_my_salt");
    }


    public void setSecurityUtils(ISecurityUtils securityUtils) {
        this.securityUtils = securityUtils;
    }

    public Constants getConstants() {
        return constants;
    }

    public IFileUtils getFileUtils() {
        return fileUtils;
    }

    public Auditor getAuditor() {
        return auditor;
    }

    public CachedData getCachedData() {
        return cachedData;
    }
}
