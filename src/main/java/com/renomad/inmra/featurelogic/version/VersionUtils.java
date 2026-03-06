package com.renomad.inmra.featurelogic.version;

import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.utils.diff.Diff;
import com.renomad.inmra.utils.diff.DiffMatchPatch;
import com.renomad.inmra.utils.diff.Patch;

import java.time.ZonedDateTime;
import java.util.List;

public class VersionUtils {

    private final DiffMatchPatch diffMatchPatch;

    public VersionUtils() {
        this.diffMatchPatch = new DiffMatchPatch();
    }

    /**
     * This creates a {@link PersonFile} which has content sufficient
     * to represent the difference between itself and one newer
     * @return a package of information that provides patches for those entries which are warranted.
     */
    public PersonFileVersionEntry createPersonFilePatch(PersonFile older, PersonFile newer, String userName, long userId, ZonedDateTime dateTimeStamp) {
        var personFilePatch = new PersonFile(
                older.getIndex(),
                older.getId(),
                makePatch(older.getImageUrl(), newer.getImageUrl()),
                makePatch(older.getName(), newer.getName()),
                older.getBorn(),
                older.getDied(),
                makePatch(older.getSiblings(),newer.getSiblings()),
                makePatch(older.getSpouses(),newer.getSpouses()),
                makePatch(older.getParents(),newer.getParents()),
                makePatch(older.getChildren(),newer.getChildren()),
                makePatch(older.getBiography(),newer.getBiography()),
                makePatch(older.getNotes(),newer.getNotes()),
                makePatch(older.getExtraFields(),newer.getExtraFields()),
                older.getGender(),
                older.getLastModified(),
                older.getLastModifiedBy(),
                makePatch(older.getAuthBio(), newer.getAuthBio())
        );

        return new PersonFileVersionEntry(personFilePatch, userName, userId, dateTimeStamp);
    }



    /**
     * Creates a patch for the diff going from the newer to the older string
     */
    private String makePatch(String older, String newer) {
        List<Diff> diffs = diffMatchPatch.diff_main(newer, older);
        diffMatchPatch.diff_cleanupEfficiency(diffs);
        List<Patch> patches = diffMatchPatch.patch_make(newer, diffs);
        return diffMatchPatch.patch_toText(patches);
    }

    /**
     * Applies a patch to the newer string to result in the older version
     */
    private String applyPatch(String newer, String patch) {
        List<Patch> patches = diffMatchPatch.patch_fromText(patch);
        return (String) diffMatchPatch.patch_apply(patches, newer)[0];
    }

    public PersonFile patchPersonFile(PersonFile newer, PersonFile personFilePatch) {
        return new PersonFile(
                personFilePatch.getIndex(),
                personFilePatch.getId(),
                applyPatch(newer.getImageUrl(), personFilePatch.getImageUrl()),
                applyPatch(newer.getName(), personFilePatch.getName()),
                personFilePatch.getBorn(),
                personFilePatch.getDied(),
                applyPatch(newer.getSiblings(),personFilePatch.getSiblings()),
                applyPatch(newer.getSpouses(),personFilePatch.getSpouses()),
                applyPatch(newer.getParents(),personFilePatch.getParents()),
                applyPatch(newer.getChildren(),personFilePatch.getChildren()),
                applyPatch(newer.getBiography(),personFilePatch.getBiography()),
                applyPatch(newer.getNotes(),personFilePatch.getNotes()),
                applyPatch(newer.getExtraFields(),personFilePatch.getExtraFields()),
                personFilePatch.getGender(),
                personFilePatch.getLastModified(),
                personFilePatch.getLastModifiedBy(),
                applyPatch(newer.getAuthBio(), personFilePatch.getAuthBio()));
    }


    /**
     * Create a PersonFile with each String spot filled with a pretty diff
     */
    public PersonFile createDiffPersonFile(PersonFile newer, PersonFile personFilePatch) {
        return new PersonFile(
                personFilePatch.getIndex(),
                personFilePatch.getId(),
                showDiff(newer.getImageUrl(), personFilePatch.getImageUrl()),
                showDiff(newer.getName(), personFilePatch.getName()),
                personFilePatch.getBorn(),
                personFilePatch.getDied(),
                showDiff(newer.getSiblings(),personFilePatch.getSiblings()),
                showDiff(newer.getSpouses(),personFilePatch.getSpouses()),
                showDiff(newer.getParents(),personFilePatch.getParents()),
                showDiff(newer.getChildren(),personFilePatch.getChildren()),
                showDiff(newer.getBiography(),personFilePatch.getBiography()),
                showDiff(newer.getNotes(),personFilePatch.getNotes()),
                showDiff(newer.getExtraFields(),personFilePatch.getExtraFields()),
                personFilePatch.getGender(),
                personFilePatch.getLastModified(),
                personFilePatch.getLastModifiedBy(),
                showDiff(newer.getAuthBio(), personFilePatch.getAuthBio()));
    }

    /**
     * Given a "from" text and "to" text, show the diff between them
     */
    public String showDiff(String from, String to) {
        List<Diff> diffs = diffMatchPatch.diff_main(from, to);
        return diffMatchPatch.diff_prettyHtml(diffs, 100);
    }

    /**
     * Given a patch in string format, and a newer text content to
     * apply it to, generate a nice HTML diff view
     */
    public String showDiffFromPatch(String patchText, String newer) {
        String olderVersion = applyPatch(newer, patchText);
        return showDiff(newer, olderVersion);
    }
}
