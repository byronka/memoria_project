package com.renomad.inmra.utils.diff;


import java.util.List;

/**
 * Internal class for returning results from diff_linesToChars().
 * Other less paranoid languages just use a three-element array.
 */
class LinesToCharsResult {
    String chars1;
    String chars2;
    List<String> lineArray;

    LinesToCharsResult(String chars1, String chars2,
                       List<String> lineArray) {
        this.chars1 = chars1;
        this.chars2 = chars2;
        this.lineArray = lineArray;
    }
}