package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.auth.GettingOlderLoop;
import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.VideoToPerson;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.queue.ActionQueueKiller;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Path;
import java.util.*;

import static com.renomad.inmra.featurelogic.persons.services.InterestingScore.getInterestingScore;
import static com.renomad.minum.testing.TestFramework.*;

public class EnhancedPersonListTests {


    private static IFileUtils fileUtils;
    private static EnhancedPersonList epl;
    private static Context context;
    private static TestLogger logger;
    private static Map<UUID, PersonMetrics> personMetricsMap;

    @BeforeClass
    public static void init() {
        Context context = buildTestingContext("DetailedViewRendererTests");
        MemoriaContext memoriaContext = MemoriaContext.buildMemoriaContext(context);
        fileUtils = memoriaContext.getFileUtils();
        buildNodesForLargeScaleAnalysis();
    }


    /**
     * This helper method pulls data from a database specifically provided
     * for more extensive graph testing (see sample_db/simple_db_no_media).
     * This will end up building a large graph, making it available in the
     * fgb FamilyGraphBuilder field value.
     */
    private static void buildNodesForLargeScaleAnalysis() {
        Properties properties = Constants.getConfiguredProperties();
        properties.setProperty("DB_DIRECTORY", "target/simple_db_no_media");
        context = buildTestingContext("EnhancedPersonListTests", properties);
        logger = (TestLogger) context.getLogger();
        Constants constants = context.getConstants();
        var dbDir = Path.of(constants.dbDirectory);
        Path personDirectory = dbDir.resolve("person_files");
        PersonLruCache personLruCacheLarge = new PersonLruCache(personDirectory, logger);

        AbstractDb<Person> personDb = context.getDb("persons", Person.EMPTY);
        var fgb = new FamilyGraphBuilder(personDb, personLruCacheLarge, logger);
        fgb.buildFamilyGraph();
        var photoToPersonDb = context.getDb("photo_to_person", PhotoToPerson.EMPTY);
        var videoToPersonDb = context.getDb("video_to_person", VideoToPerson.EMPTY);
        var personMetricsDb = context.getDb("person_metrics", PersonMetrics.EMPTY);
        personMetricsDb.registerIndex("id", x -> x.getPersonUuid().toString());
        personMetricsMap = new HashMap<>();
        var stats = new Stats(logger, fileUtils, personMetricsDb, personMetricsMap, photoToPersonDb, videoToPersonDb);
        epl = new EnhancedPersonList(logger, fileUtils, personDb, personLruCacheLarge, photoToPersonDb, personMetricsMap, stats);
        MemoriaContext memoriaContext = MemoriaContext.buildMemoriaContext(context);
        var loopingPersonMetricsReview = new GettingOlderLoop(context, memoriaContext, personMetricsDb, fgb, personLruCacheLarge, personDb, photoToPersonDb, videoToPersonDb, personMetricsMap);
        loopingPersonMetricsReview.processMetrics();
    }

    @AfterClass
    public static void cleanup() {
        new ActionQueueKiller(context).killAllQueues();
    }


    /**
     * This is primarily to test performance
     */
    @Test
    public void testRenderInnerPaginatedList() {
        var personComparator = Comparator.comparing((Person x) -> getInterestingScore(personMetricsMap.get(x.getId())), Comparator.reverseOrder());
        var currentSortValue = "Interesting (combination of biography, pictures, videos...), descending";
        var sortResult = new EnhancedPersonList.SortResult(personComparator, currentSortValue);
        var filterResult = new EnhancedPersonList.FilterResult("eap", (Person person) -> this.personMetricsMap.get(person.getId()).getAgeYears() > 105);

        // run a hundred times with timing
        StopwatchUtils stopwatchUtils = new StopwatchUtils().startTimer();
        for (int i = 0; i < 100; i++) {
            int page = Math.floorMod(i,10) + 1;
            String result = epl.renderInnerPaginatedList("", page, sortResult, "intd", filterResult, "eap");
            assertFalse(result.isEmpty());
        }
        long l = stopwatchUtils.stopTimer();
        logger.logDebug(() -> "Time taken was " + l);

        assertTrue(l < 1000, "The time taken to process this data should be less than one second");

    }

}
