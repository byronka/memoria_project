package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.*;
import com.renomad.inmra.featurelogic.photo.PhotoToPerson;
import com.renomad.inmra.featurelogic.photo.VideoToPerson;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.logging.ILogger;
import com.renomad.minum.templating.TemplateProcessor;

import java.text.DecimalFormat;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.renomad.inmra.featurelogic.persons.services.InterestingScore.getInterestingScore;

public class Stats {

    private final ILogger logger;
    private final TemplateProcessor statsTemplateProcessor;
    private final AbstractDb<PersonMetrics> personMetricsDb;
    private final Map<UUID, PersonMetrics> personMetricsMap;
    private final AbstractDb<PhotoToPerson> photoToPersonDb;
    private final AbstractDb<VideoToPerson> videoToPersonDb;

    public Stats(
            ILogger logger,
            IFileUtils fileUtils,
            AbstractDb<PersonMetrics> personMetricsDb,
            Map<UUID, PersonMetrics> personMetricsMap,
            AbstractDb<PhotoToPerson> photoToPersonDb,
            AbstractDb<VideoToPerson> videoToPersonDb
    ) {
        this.logger = logger;
        this.statsTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/stats.html"));
        this.personMetricsDb = personMetricsDb;
        this.personMetricsMap = personMetricsMap;
        this.photoToPersonDb = photoToPersonDb;
        this.videoToPersonDb = videoToPersonDb;
    }

    public void rebuildMetricsForPerson(PersonNode personNode, IPersonLruCache personLruCache) {
        PersonMetrics personMetrics = FamilyGraph.getPersonMetrics(personNode, personLruCache, photoToPersonDb, videoToPersonDb);
        // Replace any existing metric entry for this person
        PersonMetrics existingPersonMetric = personMetricsDb.findExactlyOne("id", personNode.getId().toString());
        if (existingPersonMetric != null) {
            personMetrics.setIndex(existingPersonMetric.getIndex());
            personMetricsDb.write(personMetrics);
            logger.logDebug(() -> "Refreshing metric: " + personMetrics);
        } else {
            // add a new person metrics entry
            logger.logDebug(() -> "Adding new metric: " + personMetrics);
            personMetricsDb.write(personMetrics);
        }
        personMetricsMap.put(personNode.getId(), personMetrics);
    }

    private PersonMetrics getMetricsForPerson(Person x) {
        return personMetricsDb.findExactlyOne("id", x.getId().toString(), () -> PersonMetrics.EMPTY);
    }

    /**
     * Returns a list of identifiers for photos that are associated
     * with a person
     * @param person the {@link Person} we are inspecting for photos
     */
    private List<Long> getPhotoIdsForPerson(Person person) {
        return photoToPersonDb.values().stream()
                .filter(x -> x.getPersonIndex() == person.getIndex())
                .map(PhotoToPerson::getPhotoIndex)
                .toList();
    }

    public String prepareStatsTemplate(Person p, PersonFile deserializedPersonFile) {
        PersonMetrics metricsForPerson = getMetricsForPerson(p);
        float cousinsRatio = metricsForPerson.getCousinsCount() == 0 ? 0 : (float)metricsForPerson.getCountFirstCousins() / metricsForPerson.getCousinsCount();

        Map<String, String> statsMap = new HashMap<>();
        statsMap.put("photoCount", String.valueOf(getPhotoIdsForPerson(p).size()));
        statsMap.put("lastModified", deserializedPersonFile.getLastModified().truncatedTo(ChronoUnit.SECONDS).toString());
        statsMap.put("bioImageCount", String.valueOf(metricsForPerson.getBioImageCount()));
        statsMap.put("bioVideoCount", String.valueOf(metricsForPerson.getBioVideoCount()));
        statsMap.put("videoCount", String.valueOf(metricsForPerson.getVideoCount()));
        statsMap.put("spouseCount", String.valueOf(metricsForPerson.getSpouseCount()));
        statsMap.put("siblingCount", String.valueOf(metricsForPerson.getSiblingCount()));
        statsMap.put("childCount", String.valueOf(metricsForPerson.getChildCount()));
        statsMap.put("ageYears", metricsForPerson.getAgeYears() < 0 ? "unknown" : String.valueOf(metricsForPerson.getAgeYears()));
        statsMap.put("bioCharCount", String.valueOf(metricsForPerson.getBioCharCount()));
        statsMap.put("summaryCharCount", String.valueOf(metricsForPerson.getSummarySize()));
        statsMap.put("notesCharCount", String.valueOf(metricsForPerson.getNotesCharCount()));
        statsMap.put("fullTreeSize", String.valueOf(metricsForPerson.getFamilyTreeSize()));
        statsMap.put("hasHeadshot", String.valueOf(metricsForPerson.hasHeadshot()));
        statsMap.put("closeRelativeCount", String.valueOf(metricsForPerson.getCountCloseRelatives()));
        statsMap.put("firstCousinCount", String.valueOf(metricsForPerson.getCountFirstCousins()));
        statsMap.put("ancestorCount", String.valueOf(metricsForPerson.getCountAncestors()));
        statsMap.put("descendantCount", String.valueOf(metricsForPerson.getCountDescendants()));
        statsMap.put("interestingScore", String.valueOf(getInterestingScore(metricsForPerson)));
        statsMap.put("cousinsCount", String.valueOf(metricsForPerson.getCousinsCount()));
        statsMap.put("nephewsNiecesCount", String.valueOf(metricsForPerson.getCountNephewsNieces()));
        statsMap.put("unclesAuntsCount", String.valueOf(metricsForPerson.getCountUnclesAunts()));
        statsMap.put("cousinsRatio", new DecimalFormat("#.##").format(cousinsRatio));
        return statsTemplateProcessor.renderTemplate(statsMap);
    }
}
