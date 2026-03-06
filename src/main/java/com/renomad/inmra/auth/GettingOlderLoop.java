package com.renomad.inmra.auth;

import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.featurelogic.persons.services.BirthDeathDays;
import com.renomad.inmra.featurelogic.persons.services.FamilyGraphBuilder;
import com.renomad.inmra.featurelogic.persons.services.PersonSearch;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.VideoToPerson;
import com.renomad.inmra.utils.MemoriaContext;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.logging.LoggingLevel;
import com.renomad.minum.state.Constants;
import com.renomad.minum.state.Context;
import com.renomad.minum.testing.StopwatchUtils;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.StacktraceUtils;
import com.renomad.minum.utils.TimeUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

/**
 * This class starts an infinite loop when the application begins,
 * handling activities that ought to be done several times in a day:
 * 1) Recalculating every person's age (every day they get older)
 * 2) Rebuild the cache for which birthdays and deathdays are today
 * 3) Caching which are those persons most interesting, to show on the homepage
 * analyzing each person and setting their metrics.
 */
public class GettingOlderLoop {

    private final ExecutorService es;
    private final ILogger logger;
    private final int sleepTime;
    private final Constants constants;
    private final MemoriaContext memoriaContext;
    private final AbstractDb<PersonMetrics> pm;
    private final FamilyGraphBuilder familyGraphBuilder;
    private final IPersonLruCache personLruCache;
    private final AbstractDb<Person> personDb;
    private final AbstractDb<PhotoToPerson> photoToPersonDb;
    private final AbstractDb<VideoToPerson> videoToPersonDb;
    private final Map<UUID, PersonMetrics> personMetricsMap;
    private final PersonSearch personSearch;

    public GettingOlderLoop(Context context,
                            MemoriaContext memoriaContext,
                            AbstractDb<PersonMetrics> pm,
                            FamilyGraphBuilder familyGraphBuilder,
                            IPersonLruCache personLruCache,
                            AbstractDb<Person> personDb,
                            AbstractDb<PhotoToPerson> photoToPersonDb,
                            AbstractDb<VideoToPerson> videoToPersonDb,
                            Map<UUID, PersonMetrics> personMetricsMap
                            ) {
        this.es = context.getExecutorService();
        this.logger = context.getLogger();
        this.constants = context.getConstants();
        this.memoriaContext = memoriaContext;
        this.pm = pm;
        this.familyGraphBuilder = familyGraphBuilder;
        this.personLruCache = personLruCache;
        this.personDb = personDb;
        this.photoToPersonDb = photoToPersonDb;
        this.videoToPersonDb = videoToPersonDb;
        this.personMetricsMap = personMetricsMap;
        this.personSearch = new PersonSearch(personDb, personLruCache, personMetricsMap);
        // wake up once every 6 hours
        this.sleepTime = 6 * 60 * 60 * 1000;
    }

    /**
     * This kicks off the infinite loop reviewing each person in the database to
     * calculate some valuable metrics - see PersonMetrics
     */
    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @SuppressWarnings({"BusyWait"})
    public GettingOlderLoop initialize() {
        logger.logDebug(() -> "Initializing GettingOlderLoop main loop");

        initialDataLoad();

        Callable<Object> innerLoopThread = () -> {
            Thread.currentThread().setName("GettingOlderLoop");
            while (true) {
                try {
                    Thread.sleep(sleepTime);

                    var stopwatch = new StopwatchUtils().startTimer();
                    processMetrics();
                    logger.logDebug(() -> "Took %d milliseconds to process the metrics".formatted(stopwatch.stopTimer()));

                    LocalDate now = LocalDate.now();

                    var stopwatch2 = new StopwatchUtils().startTimer();
                    cacheBirthDeathDays(now);
                    logger.logDebug(() -> "Took %d milliseconds to build a cache of birth and death days".formatted(stopwatch2.stopTimer()));

                    var stopwatch3 = new StopwatchUtils().startTimer();
                    cacheInterestingPeople();
                    logger.logDebug(() -> "Took %d milliseconds to build a cache of interesting people".formatted(stopwatch3.stopTimer()));
                } catch (InterruptedException ex) {

                    /*
                    this is what we expect to happen.
                    once this happens, we just continue on.
                    this only gets called when we are trying to shut everything
                    down cleanly
                     */

                    if (constants.logLevels.contains(LoggingLevel.DEBUG)) System.out.printf(TimeUtils.getTimestampIsoInstant() + " GettingOlderLoop is stopped.%n");
                    Thread.currentThread().interrupt();
                    return null;
                } catch (Throwable ex) {
                    logger.logAsyncError(() -> "Error while processing GettingOlderLoop: " + StacktraceUtils.stackTraceToString(ex));
                }
            }
        };
        es.submit(innerLoopThread);
        return this;
    }

    /**
     * Pull data off the disk and create caches
     */
    private void initialDataLoad() {
        logger.logDebug(() -> "Putting data from person metrics data on disk into personMetricsMap");
        var pmDataLoadTimer = new StopwatchUtils().startTimer();
        loadPersonMetricsData();
        logger.logDebug(() -> "Took %d milliseconds to load the metrics".formatted(pmDataLoadTimer.stopTimer()));

        LocalDate now = LocalDate.now();

        var stopwatch2 = new StopwatchUtils().startTimer();
        cacheBirthDeathDays(now);
        logger.logDebug(() -> "Took %d milliseconds to build a cache of birth and death days".formatted(stopwatch2.stopTimer()));

        var stopwatch3 = new StopwatchUtils().startTimer();
        cacheInterestingPeople();
        logger.logDebug(() -> "Took %d milliseconds to build a cache of interesting people".formatted(stopwatch3.stopTimer()));
    }

    private void loadPersonMetricsData() {
        for (PersonMetrics personMetrics : pm.values()) {
            personMetricsMap.put(personMetrics.getPersonUuid(), personMetrics);
        }
    }

    /**
     * cache current lists of the most interesting people
     */
    public void cacheInterestingPeople() {
        List<Person> interestingLivingPeople = personSearch.getAllInterestingPeople(true);
        List<Person> interestingDeadPeople = personSearch.getAllInterestingPeople(false);
        memoriaContext.getCachedData().setMostInterestingPeopleIncludingLiving(interestingLivingPeople);
        memoriaContext.getCachedData().setMostInterestingPeopleNoLiving(interestingDeadPeople);
    }

    /**
     * cache a current rendering of the birthdays and deathdays
     */
    public void cacheBirthDeathDays(LocalDate now) {
        String birthDeathHtmlWithLiving = BirthDeathDays.renderAnniversaryString(personDb.values(), now, true);
        String birthDeathHtml = BirthDeathDays.renderAnniversaryString(personDb.values(), now, false);
        memoriaContext.getCachedData().setBirthDeathDaysRendered(birthDeathHtml);
        memoriaContext.getCachedData().setBirthDeathDaysWithLivingRendered(birthDeathHtmlWithLiving);
    }

    public void processMetrics() {
        logger.logDebug(() -> "Waking up to calculate metrics for all persons");
        for (Map.Entry<UUID, PersonNode> entry : familyGraphBuilder.getPersonNodes().entrySet()) {
            UUID personId = entry.getKey();
            PersonNode personNode = entry.getValue();
            PersonMetrics personMetrics = FamilyGraph.getPersonMetrics(personNode, personLruCache, photoToPersonDb, videoToPersonDb);
            // Replace any existing metric entry for this person
            PersonMetrics existingPersonMetric = pm.findExactlyOne("id", personId.toString());
            if (existingPersonMetric != null) {
                personMetrics.setIndex(existingPersonMetric.getIndex());
                pm.write(personMetrics);
            } else {
                // add a new person metrics entry
                logger.logTrace(() -> "Adding new metric: " + personMetrics);
                pm.write(personMetrics);
            }
            personMetricsMap.put(personId, personMetrics);
        }
    }

    public void updatePersonMetricsMap(UUID personId) {
        var personNode = SearchUtils.findExactlyOne(familyGraphBuilder.getPersonNodes().values().stream(), x -> x.getId().equals(personId));
        PersonMetrics personMetrics = FamilyGraph.getPersonMetrics(personNode, personLruCache, photoToPersonDb, videoToPersonDb);
        personMetricsMap.put(personNode.getId(), personMetrics);
    }

}
