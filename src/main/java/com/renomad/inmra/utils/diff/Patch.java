package com.renomad.inmra.utils.diff;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.renomad.inmra.utils.diff.DiffMatchPatch.unescapeForEncodeUriCompatability;

/**
 * Class representing one patch operation.
 */
public class Patch {
    public List<Diff> diffs;
    public int start1;
    public int start2;
    public int length1;
    public int length2;

    /**
     * Constructor.  Initializes with an empty list of diffs.
     */
    public Patch() {
        this.diffs = new ArrayList<>();
    }

    /**
     * Emulate GNU diff's format.
     * Header: @@ -382,8 +481,9 @@
     * Indices are printed as 1-based, not 0-based.
     * @return The GNU diff string.
     */
    public String toString() {
        String coords1, coords2;
        if (this.length1 == 0) {
            coords1 = this.start1 + ",0";
        } else if (this.length1 == 1) {
            coords1 = Integer.toString(this.start1 + 1);
        } else {
            coords1 = (this.start1 + 1) + "," + this.length1;
        }
        if (this.length2 == 0) {
            coords2 = this.start2 + ",0";
        } else if (this.length2 == 1) {
            coords2 = Integer.toString(this.start2 + 1);
        } else {
            coords2 = (this.start2 + 1) + "," + this.length2;
        }
        StringBuilder text = new StringBuilder();
        text.append("@@ -").append(coords1).append(" +").append(coords2)
                .append(" @@\n");
        // Escape the body of the patch with %xx notation.
        for (Diff aDiff : this.diffs) {
            switch (aDiff.operation) {
                case INSERT:
                    text.append('+');
                    break;
                case DELETE:
                    text.append('-');
                    break;
                case EQUAL:
                    text.append(' ');
                    break;
            }
            text.append(URLEncoder.encode(aDiff.text, StandardCharsets.UTF_8).replace('+', ' '))
                    .append("\n");
        }
        return unescapeForEncodeUriCompatability(text.toString());
    }
}
