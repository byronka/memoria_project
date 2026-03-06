package com.renomad.inmra.featurelogic.version;

import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.utils.diff.Diff;
import com.renomad.inmra.utils.diff.DiffMatchPatch;
import com.renomad.inmra.utils.diff.Patch;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.TestFramework;
import org.junit.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.renomad.minum.testing.TestFramework.assertEquals;
import static com.renomad.minum.testing.TestFramework.assertTrue;


/**
 * These are tests to investigate storing old versions of biographies
 * as patches, so that users can see previous versions as diffs
 * and the patches can be small.
 */
public class VersionUtilsTests {

    private static final DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
    private static Context context;
    private VersionUtils versionUtils;

    @BeforeClass
    public static void init() {
        context = TestFramework.buildTestingContext("VersioningTests");
    }

    @AfterClass
    public static void cleanup() {
        TestFramework.shutdownTestingContext(context);
    }

    @Before
    public void start() {
        versionUtils = new VersionUtils();
    }

    /**
     * Experimenting with the new tools for creating diffs and patches
     */
    @Test
    public void testBasicDiffAndPatchConcepts() {
        String bio = "this is a foo";
        String bio2 = "this is not a foo";

        // generate diffs and show them
        List<Diff> diffs = diffMatchPatch.diff_main(bio, bio2);
        diffMatchPatch.diff_cleanupEfficiency(diffs);
        String prettyHtml = diffMatchPatch.diff_prettyHtml(diffs);
        assertEquals(prettyHtml, "<span class=\"equal\">this is </span><ins style=\"background:#e6ffe6;\">not </ins><span class=\"equal\">a foo</span>");

        // create a patch that will be stored for later
        List<Patch> patches = diffMatchPatch.patch_make(bio, diffs);
        String patch = diffMatchPatch.patch_toText(patches);
        assertEquals(patch, "@@ -1,13 +1,17 @@\n this is \n+not \n a foo\n");
        
        // test out applying the patch
        List<Patch> recoveredPatches = diffMatchPatch.patch_fromText(patch);
        String result = (String)diffMatchPatch.patch_apply(recoveredPatches, bio)[0];
        assertEquals(result, bio2);
    }

    private record DatedPersonAudit(ZonedDateTime date, PersonFile personFile) {}

    /**
     * This tests the scenario where we have data in a "personfile" file, and
     * one or more entries (lines - they are separated by newlines) in a
     * personfile audit file. In this case the person is Louis Goodman.
     * <p>
     *     The way this ought to work: There are 16 components to a PersonFile.
     *     Most of them are strings, and if we store old versions as patches
     *     backwards from the current version, it should help us save a lot
     *     of space, and we can use those patches to show what has changed
     *     each step backwards.
     * </p>
     * <p>
     *     Also, some of the stored old versions are for obsolete schemas. For
     *     that reason, it is necessary to scan each file and skip over the old
     *     non-parseable schemas, and convert the rest to a chain of patches
     *     backwards from the current version:
     *     <ol>
     *     <li>current personfile -> most recent change in audit file</li>
     *     <li>most recent change in audit file -> second most recent change</li>
     *     <li>and so on</li>
     *     </ol>
     * </p>
     */
    @Test
    public void testMoreRealistic() throws IOException {
        Path testFilesDirectory = Path.of("src/test/resources/personfile_diffs");
        String personFileRaw = Files.readString(testFilesDirectory.resolve("b3cd0fe1-0322-4e71-8010-8eaf6d7d9333"));
        PersonFile currentVersion = PersonFile.EMPTY.deserialize(personFileRaw);
        String personFileAuditRaw = Files.readString(testFilesDirectory.resolve("b3cd0fe1-0322-4e71-8010-8eaf6d7d9333.audit"));
        // each item is a previous version.  The first entry is the oldest
        String[] previousVersions = personFileAuditRaw.split("\n");
        assertEquals(previousVersions.length, 27);

        context.getLogger().logDebug(() -> "WE ARE ABOUT TO SEE A LOT OF COMPLAINTS ABOUT PARSING - IGNORE, IT IS FOR THE TEST");

        ArrayList<DatedPersonAudit> datedPersonAudits = new ArrayList<>();
        for (String previousVersion : previousVersions) {
            String[] split = previousVersion.split("\t");
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(split[0]);
            try {
                PersonFile personFile = PersonFile.EMPTY.deserialize(split[1]);
                datedPersonAudits.add(new DatedPersonAudit(zonedDateTime, personFile));
            } catch (Exception ex) {
                // parsing errors will exist for trying to parse old schemas.  In that case, just move on
                context.getLogger().logDebug(() -> "Unable to parse: " + ex.getMessage());
            }
        }

        // so of the original audits, we were able to parse 10 of them.
        assertEquals(datedPersonAudits.size(), 10);

        // build efficient diffs for the bio and short bio (auth bio) from the current to one older
        int versionToCompare = 3;
        DatedPersonAudit versionThreePersonFile = datedPersonAudits.get(versionToCompare);
        List<Diff> bioDiff = diffMatchPatch.diff_main(currentVersion.getBiography(), versionThreePersonFile.personFile().getBiography());
        diffMatchPatch.diff_cleanupEfficiency(bioDiff);
        List<Diff> shortBioDiff = diffMatchPatch.diff_main(currentVersion.getAuthBio(), versionThreePersonFile.personFile().getAuthBio());
        diffMatchPatch.diff_cleanupEfficiency(shortBioDiff);
        List<Diff> extraFieldsDiff = diffMatchPatch.diff_main(currentVersion.getExtraFields(), versionThreePersonFile.personFile().getExtraFields());
        diffMatchPatch.diff_cleanupEfficiency(extraFieldsDiff);

        // show those diffs in pretty format:

        String prettyDiffBio = diffMatchPatch.diff_prettyHtml(bioDiff);
        String prettyDiffShortBio = diffMatchPatch.diff_prettyHtml(shortBioDiff);
        String prettyExtraFieldsDiff = diffMatchPatch.diff_prettyHtml(extraFieldsDiff);

        assertTrue(prettyDiffBio.length() > 1000);
        assertTrue(prettyDiffShortBio.length() > 100);
        assertTrue(prettyExtraFieldsDiff.length() > 50);

        // create patches from those diffs
        List<Patch> bioPatch = diffMatchPatch.patch_make(currentVersion.getBiography(), bioDiff);
        List<Patch> shortBioPatch = diffMatchPatch.patch_make(currentVersion.getAuthBio(), shortBioDiff);
        List<Patch> extraFieldsPatch = diffMatchPatch.patch_make(currentVersion.getExtraFields(), extraFieldsDiff);

        String bioPatchString = diffMatchPatch.patch_toText(bioPatch);
        String shortBioPatchString = diffMatchPatch.patch_toText(shortBioPatch);
        String extraFieldsPatchString = diffMatchPatch.patch_toText(extraFieldsPatch);

        assertEquals(bioPatchString, "@@ -1027,39 +1027,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -1090,32 +1090,32 @@\n a5269473.jpg%22%3E%0D%0A\n+\n     %3Cfigcaption%3E\n@@ -1351,39 +1351,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -2723,39 +2723,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -3063,39 +3063,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -4020,39 +4020,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -4215,39 +4215,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -5086,39 +5086,24 @@\n figure%3E%3Cimg \n-loading=%22lazy%22 \n src=%22photo?n\n@@ -6296,39 +6296,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -7387,39 +7387,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -7610,39 +7610,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -8813,138 +8813,8 @@\n ly.)\n-%0D%0A%3C/p%3E%0D%0A%3Cimg loading=%22lazy%22 src=%22photo?name=d4cc0073-efc8-4118-a38c-c8405f50ce21.jpg%22 alt=%22Louis Goodman&apos;s Model A car%22%3E%0D%0A%3Cp%3E\n  Onc\n@@ -9591,39 +9591,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -9780,39 +9780,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -11315,39 +11315,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22Louis a\n@@ -12470,39 +12470,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -12713,39 +12713,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -14194,39 +14194,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -15994,39 +15994,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -16975,39 +16975,24 @@\n figure%3E%3Cimg \n-loading=%22lazy%22 \n src=%22photo?n\n@@ -18363,39 +18363,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -18943,39 +18943,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -19470,39 +19470,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -20589,39 +20589,24 @@\n %3E%0D%0A    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 src=%22\n@@ -21491,31 +21491,16 @@\n    %3Cimg \n-loading=%22lazy%22 \n alt=%22%22 s\n@@ -21738,308 +21738,8 @@\n d.%0D%0A\n+\n %3C/p%3E\n-%0D%0A %3Cfigure%3E%3Cimg loading=%22lazy%22 src=%22photo?name=eb963d4a-c7e3-4a66-a908-5c5421021219.jpg%22 alt=%22dapper-looking family on the sidewalk%22%3E%3Cfigcaption%3E%3Cp%3EThe whole Goodman family - from left, Margie, Susan, Louis, and Gary, wearing their best clothes.  Probably taken around 1961.%3C/p%3E%3C/figcaption%3E%3C/figure%3E\n");
        assertEquals(shortBioPatchString, "@@ -1,1544 +1,21 @@\n-%3Cp%3E%0D%0Aa letter from Louis to a veterans group:%0D%0A%3C/p%3E%0D%0A%3Ch3%3E%0D%0ALouis H. Goodman%0D%0A%3C/h3%3E%0D%0A%3Cp%3E%0D%0AResidence 2217 Otterburn Lane, Germantown, TN 38138 (Outskirts of Memphis). Phone (901) 756-1441. Business 2018 Exeter Road, Suite 1, Germantown, TN 38138, Phone (901) 754-5301%0D%0A%3C/p%3E%0D%0A%3Cp%3E%0D%0ABirthdate: 1/31/19%0D%0A%3C/p%3E%0D%0A%3Cp%3E%0D%0AAfter graduation, worked at family business in Lorain, Goodman Beverage Company, until April 2, 1941 when I entered the Army.%0D%0A%3C/p%3E%0D%0A%3Cp%3E%0D%0AServed at Fort Bragg Camp Forrest, then transferred to Second Army Headquarters, Memphis, Tennessee.  Thence to New Guinea, Leyte, Mindoro, and Mindanao. Have seven Battle Stars and one Bronze Star.%0D%0A%3C/p%3E%0D%0A%3Cp%3E%0D%0AMarried Margie Evensky 1/31/43 Memphis, Tennessee.  \n W\n-e h\n a\n-ve two children, and two lovely grandchildren.  Our \n s\n-on\n  \n-is a lawyer in Chi\n c\n-ago, and our daughter is a h\n o\n-usewife.%0D%0A%3C/p%3E%0D%0A%3Cp%3E%0D%0AI have been self-emp\n lo\n-yed since discharge from the Army.  From 1946 to 1951, I had a liquo\n r \n-store.  From 1951 to 1956, I operated a lum\n b\n-er yard.  From 1956 to 1977, I operated a sawmil\n l\n- \n in\n- Waldron, Arkansas.  In 1966, I starte\n d\n- processing and selling pine bark in bags to the plant nursery and mass merchandise trade.  We have had terrific success, and we are still operating this business.  We don't need the money, but we do need to keep busy!\n %0D%0A\n-%3C/p%3E%0D%0A%3Cp%3E%0D%0AWe have traveled extensively in Europe, the Carribean, South America, China, and the Orient.%0D%0A%3C/p%3E%0D%0A%3Cp%3E%0D%0ABoth my wife and I are enjoying good health, and we hope to be at the next reunion!%0D%0A%3C/p%3E%0D%0A%0D%0A%3Chr%3E%0D%0A%3Cp%3E%0D%0ALouis was color blind.%0D%0A%3C/p%3E%0D%0A%3Cp%3E\n %0D%0ALo\n@@ -176,10 +176,4 @@\n ere%22\n-%0D%0A%3C/p%3E\n");
        assertEquals(extraFieldsPatchString, "@@ -26,52 +26,8 @@\n ate%7C\n-Occupation%7CNursery+supply+wholesaler%7Cstring%7C\n Birt\n@@ -191,126 +191,4 @@\n ring\n-%7COccupation%7CLiquor+store+proprietor%7Cstring%7COccupation%7CSoldier+in+World+War+II%7Cstring%7COccupation%7CLumbermill+operator%7Cstring\n");

        // Create a personfile that consists of the data we'll store as an audit record.
        // Each string field will be a patch
        // we can just use the PersonFile class to store the patch data.

        PersonFileVersionEntry patchEntry = versionUtils.createPersonFilePatch(
                versionThreePersonFile.personFile(),
                currentVersion,
                "byron",
                1,
                ZonedDateTime.ofInstant(Instant.ofEpochSecond(1111111111L), ZoneId.of("UTC")));
        assertEquals(patchEntry.dateTimeStamp(), ZonedDateTime.ofInstant(Instant.ofEpochSecond(1111111111L), ZoneId.of("UTC")));

        PersonFile p1 = versionUtils.patchPersonFile(currentVersion, patchEntry.personFile());
        PersonFile p2 = versionThreePersonFile.personFile();

        // these assertions check that by applying our stored patch to the current version of the PersonFile,
        // we are able to successfully convert it to the older version
        assertEquals(p1.getIndex(), p2.getIndex());
        assertEquals(p1.getId(), p2.getId());
        assertEquals(p1.getImageUrl(), p2.getImageUrl());
        assertEquals(p1.getName(), p2.getName());
        assertEquals(p1.getBorn(), p2.getBorn());
        assertEquals(p1.getDied(), p2.getDied());
        assertEquals(p1.getSiblings(), p2.getSiblings());
        assertEquals(p1.getSpouses(), p2.getSpouses());
        assertEquals(p1.getParents(), p2.getParents());
        assertEquals(p1.getChildren(), p2.getChildren());
        assertEquals(p1.getBiography(), p2.getBiography());
        assertEquals(p1.getNotes(), p2.getNotes());
        assertEquals(p1.getExtraFields(), p2.getExtraFields());
        assertEquals(p1.getGender(), p2.getGender());
        assertEquals(p1.getLastModified(), p2.getLastModified());
        assertEquals(p1.getLastModifiedBy(), p2.getLastModifiedBy());
        assertEquals(p1.getAuthBio(), p2.getAuthBio());
    }

    /**
     * A test to play with data from a patch file
     */
    @Test
    public void testProcessFiles() throws IOException {
        Path testFilesDirectory = Path.of("src/test/resources/personfile_diffs");
        String personFileRaw = Files.readString(testFilesDirectory.resolve("28fb6b27-5df2-48ed-b7ca-e58d096e7b61"));
        PersonFile myPerson = PersonFile.EMPTY.deserialize(personFileRaw);
        String personFileAuditRaw = Files.readString(testFilesDirectory.resolve("28fb6b27-5df2-48ed-b7ca-e58d096e7b61.audit"));

        // each item is a previous version.  The first entry is the oldest
        String[] previousVersions = personFileAuditRaw.split("\n");

        ArrayList<PersonFileVersionEntry> datedPersonAudits = new ArrayList<>();
        for (String previousVersion : previousVersions) {
            // this will split into 4 parts - the date, the username who made that audit, their id, and finally the patch data
            String[] split = previousVersion.split("\t");
            ZonedDateTime zonedDateTime = ZonedDateTime.parse(split[0]);
            long userId = Long.parseLong(split[2]);
            PersonFile personFile = PersonFile.EMPTY.deserialize(split[3]);
            datedPersonAudits.add(new PersonFileVersionEntry( personFile, split[1], userId, zonedDateTime));
        }

        PersonFile p1 = versionUtils.patchPersonFile(myPerson, datedPersonAudits.getLast().personFile());

        PersonFile diffPersonFile = versionUtils.createDiffPersonFile(p1, myPerson);
        System.out.println(diffPersonFile);

    }

}
