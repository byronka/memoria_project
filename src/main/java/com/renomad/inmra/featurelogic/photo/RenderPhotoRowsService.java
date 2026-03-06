package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.featurelogic.persons.PersonLruCache;
import com.renomad.inmra.utils.Cleaners;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.AbstractDb;
import com.renomad.minum.templating.TemplateProcessor;
import com.renomad.minum.utils.SearchUtils;
import com.renomad.minum.utils.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.StringUtils.safeAttr;
import static com.renomad.minum.utils.StringUtils.safeHtml;

public class RenderPhotoRowsService {

    private final AbstractDb<PhotoToPerson> photoToPersonDb;
    private final AbstractDb<Photograph> photographDb;
    private final AbstractDb<VideoToPerson> videoToPersonDb;
    private final AbstractDb<Video> videoDb;
    private final TemplateProcessor listPhotosItemTemplateProcessor;
    private final AbstractDb<Person> personDb;
    private final TemplateProcessor listVideosItemTemplateProcessor;
    private final TemplateProcessor listPhotosItemPreviewTemplateProcessor;
    private final TemplateProcessor listVideosItemPreviewTemplateProcessor;
    private final PersonLruCache personLruCache;

    // these regex's are used to find the images and videos in the biographies so we can
    // mark them in the table as being used.
    private final static Pattern imageRegex = Pattern.compile("photo\\?name=(?<photo>.*?\\..+?)\"");
    private final static Pattern videoRegex = Pattern.compile("video\\?name=(?<video>.*?\\..+?)\"");

    public RenderPhotoRowsService(
            AbstractDb<PhotoToPerson> photoToPersonDb,
            AbstractDb<Photograph> photographDb,
            AbstractDb<VideoToPerson> videoToPersonDb,
            AbstractDb<Video> videoDb,
            AbstractDb<Person> personDb,
            IFileUtils fileUtils,
            PersonLruCache personLruCache){
        this.photoToPersonDb = photoToPersonDb;
        this.photographDb = photographDb;
        this.videoToPersonDb = videoToPersonDb;
        this.videoDb = videoDb;
        this.personDb = personDb;
        listVideosItemTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_videos_item_template.html"));
        listPhotosItemTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_photos_item_template.html"));
        listPhotosItemPreviewTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_photos_item_preview_template.html"));
        listVideosItemPreviewTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_videos_item_preview_template.html"));
        this.personLruCache = personLruCache;
    }

    /**
     * Given a {@link Person}, extract the information we need to render all the rows
     * of photos - including links to the photo, descriptions, etc.
     */
    public String renderPhotoRows(Person foundPerson) {
        Set<Long> publishedPhotos = determinePublishedPhotos(foundPerson);
        Set<Long> publishedVideos = determinePublishedVideos(foundPerson);
        List<Long> photoIdsForPerson = getPhotoIdsForPerson(foundPerson);
        String photos = renderPhotoRowStrings(foundPerson, photoIdsForPerson, publishedPhotos);
        List<Long> videoIdsForPerson = getVideoIdsForPerson(foundPerson);
        String videos = renderVideoRowStrings(foundPerson, videoIdsForPerson, publishedVideos);
        return photos + videos;
    }

    /**
     * This method looks at the biography and authenticated biography of a person
     * and finds all the img tags, and returns a set of their UUID's.  It is useful
     * for knowing which photos are already being shown.
     */
    public Set<Long> determinePublishedPhotos(Person foundPerson) {
        PersonFile personFile = this.personLruCache.getCachedPersonFile(foundPerson);
        String bio = personFile.getBiography();
        String authBio = personFile.getAuthBio();

        Set<String> bioPhotos = imageRegex.matcher(bio).results().map(x -> x.group("photo")).collect(Collectors.toSet());
        Set<String> authBioPhotos = imageRegex.matcher(authBio).results().map(x -> x.group("photo")).collect(Collectors.toSet());

        bioPhotos.addAll(authBioPhotos);

        // now we have all the photos, we have to find the ones that Memoria owns for this person.

        // get all the photos for this person
        Collection<PhotoToPerson> photoToPersonDataForThisPerson = this.photoToPersonDb.getIndexedData("persons", String.valueOf(foundPerson.getIndex()));

        // get the indexes of photos that match against what we found in the bios
        Set<Long> result = new HashSet<>();
        for (PhotoToPerson photoToPerson : photoToPersonDataForThisPerson) {
            if (bioPhotos.contains(photoToPerson.getPhotoUrl())) {
                result.add(photoToPerson.getPhotoIndex());
            }
        }

        return result;
    }

    /**
     * This method looks at the biography and authenticated biography of a person
     * and finds all the video tags, and returns a set of their UUID's.  It is useful
     * for knowing which photos are already being shown.
     */
    Set<Long> determinePublishedVideos(Person foundPerson) {
        PersonFile personFile = this.personLruCache.getCachedPersonFile(foundPerson);
        String bio = personFile.getBiography();
        String authBio = personFile.getAuthBio();

        Set<String> bioVideos = videoRegex.matcher(bio).results().map(x -> x.group("video")).collect(Collectors.toSet());
        Set<String> authBioVideos = videoRegex.matcher(authBio).results().map(x -> x.group("video")).collect(Collectors.toSet());

        bioVideos.addAll(authBioVideos);

        // now we have all the videos, we have to find the ones that Memoria owns for this person.

        // get all the videos for this person
        Collection<VideoToPerson> videoToPersonDataForThisPerson = this.videoToPersonDb.getIndexedData("persons", String.valueOf(foundPerson.getIndex()));

        // get the indexes of videos that match against what we found in the bios
        Set<Long> result = new HashSet<>();
        for (VideoToPerson videoToPerson : videoToPersonDataForThisPerson) {
            if (bioVideos.contains(videoToPerson.getVideoUrl())) {
                result.add(videoToPerson.getVideoIndex());
            }
        }

        return result;
    }

    public String renderVideoRowStrings(Person person, List<Long> videoIdsForPerson, Set<Long> publishedVideos) {
        return videoDb.values().stream()
                .filter(video -> videoIdsForPerson.contains(video.getIndex()))
                .map(video -> "<tr data-videoid=\""+video.getIndex()+"\">\n" + renderRowForVideo(video, person, publishedVideos.contains(video.getIndex())) + "</tr>\n").collect(Collectors.joining("\n"));
    }

    public String renderPhotoRowStrings(Person person, List<Long> photoIdsForPerson, Set<Long> publishedPhotos) {
        return photographDb.values().stream()
                .filter(photograph -> photoIdsForPerson.contains(photograph.getIndex()))
                .map(photograph -> "<tr data-photoid=\""+photograph.getIndex()+"\">\n" + renderRowForPhotograph(photograph, person, publishedPhotos.contains(photograph.getIndex())) + "</tr>\n").collect(Collectors.joining("\n"));
    }

    public String renderInnerVideoRowString(Person person, List<Long> videoIdsForPerson, Set<Long> publishedVideoIds) {
        Video video = SearchUtils.findExactlyOne(videoDb.values().stream(), x -> videoIdsForPerson.contains(x.getIndex()));
        return renderRowForVideo(video, person, publishedVideoIds.contains(video.getIndex()));
    }

    public String renderInnerPhotoRowString(Person person, List<Long> photoIdsForPerson, Set<Long> publishedPhotos) {
        Photograph photo = SearchUtils.findExactlyOne(photographDb.values().stream(), x -> photoIdsForPerson.contains(x.getIndex()));
        return renderRowForPhotograph(photo, person, publishedPhotos.contains(photo.getIndex()));
    }

    /**
     * On the page for viewing photo and video data for a person, there is a variety of information and
     * actions in a table format.  This method calculates that data and renders a template
     * for a single row (that is, a single video) in the table.
     *
     * @param video       the {@link Video} in question
     * @param person the original person who owns this photograph
     */
    private String renderRowForVideo(Video video, Person person, boolean isPublished) {
        // obtain a list of anchor elements for persons who also own this media, which
        // we will convert to anchor tags.
        String copiedToPersons = videoToPersonDb.values().stream()
                // get all the videos that connect to this video file
                .filter(x -> x.getVideoUrl().equals(video.getVideoUrl()))
                // convert to get the persons associated with all the videos for this video file
                .map(x -> SearchUtils.findExactlyOne(personDb.values().stream(), y -> y.getIndex() == x.getPersonIndex()))
                .filter(Objects::nonNull)
                // exclude the original person - we only want to see other persons who have this
                .filter(x -> ! x.getId().equals(person.getId()))
                .map(x -> String.format("<a class=\"other_persons\" href=\"photos?personid=%s\" title=\"%s\" >%s</a>", x.getId(), StringUtils.safeAttr(x.getName()), StringUtils.safeHtml(x.getName()))).collect(Collectors.joining("\n"));
        String figcaption = String.format("<p>%s</p><p>%s</p>", safeHtml(video.getShortDescription()), Cleaners.cleanScript(video.getDescription()));
        return listVideosItemTemplateProcessor.renderTemplate(Map.of(
                "video_id", String.valueOf(video.getIndex()),
                "video_url", safeAttr(video.getVideoUrl()),
                "copied_to_persons", copiedToPersons,
                "short_description", safeHtml(video.getShortDescription()),
                "description", safeHtml(video.getDescription()),
                "description_for_figcaption", figcaption,
                "video_poster", StringUtils.safeAttr(video.getPoster()),
                "is_published", isPublished ? "is-indeed-published" : ""
        ));
    }

    /**
     * On the page for viewing photo data for a person, there is a variety of information and
     * actions in a table format.  This method calculates that data and renders a template
     * for a single row (that is, a single photograph) in the table.
     *
     * @param photograph  the {@link Photograph} in question
     * @param person the original person who owns this photograph
     * @param isPublished whether this photo shows up in a biography or auth biography
     */
    private String renderRowForPhotograph(Photograph photograph, Person person, boolean isPublished) {
        // obtain a list of anchor elements for persons who also own this media, which
        // we will convert to anchor tags.
        String copiedToPersons = photoToPersonDb.values().stream()
                // get all the photos that connect to this photo file
                .filter(x -> x.getPhotoUrl().equals(photograph.getPhotoUrl()))
                // convert to get the persons associated with all the photos for this photo file
                .map(x -> SearchUtils.findExactlyOne(personDb.values().stream(), y -> y.getIndex() == x.getPersonIndex()))
                .filter(Objects::nonNull)
                // exclude the original person - we only want to see other persons who have this
                .filter(x -> ! x.getId().equals(person.getId()))
                .map(x -> String.format("<a class=\"other_persons\" href=\"photos?personid=%s\" title=\"%s\" >%s</a>", x.getId(), StringUtils.safeAttr(x.getName()), StringUtils.safeHtml(x.getName()))).collect(Collectors.joining("\n"));
        String figcaption = String.format("<p>%s</p>", Cleaners.cleanScript(photograph.getDescription()));
        return listPhotosItemTemplateProcessor.renderTemplate(Map.of(
                "photo_id", String.valueOf(photograph.getIndex()),
                "photo_url", safeAttr(photograph.getPhotoUrl()),
                "copied_to_persons", copiedToPersons,
                "photo_url_html", safeHtml(photograph.getPhotoUrl()),
                "short_description", safeHtml(photograph.getShortDescription()),
                "short_description_attr", safeAttr(photograph.getShortDescription()),
                "description", safeHtml(photograph.getDescription()),
                "description_for_figcaption", figcaption,
                "is_published", isPublished ? "is-indeed-published" : ""
        ));
    }

    /**
     * Similar to {@link #renderPhotoRows(Person)} except we don't show input fields
     * for descriptions, nor do we allow deleting here.  This is meant for a speed
     * aid when modifying data in the biography input field when editing a person.
     */
    public String renderPreviewPhotoRows(Person foundPerson) {
        List<Long> photoIdsForPerson = getPhotoIdsForPerson(foundPerson);
        List<Long> videoIdsForPerson = getVideoIdsForPerson(foundPerson);
        Set<Long> publishedPhotos = determinePublishedPhotos(foundPerson);
        Set<Long> publishedVideos = determinePublishedVideos(foundPerson);

        String photoRows = photographDb.values().stream().filter(x -> photoIdsForPerson.contains(x.getIndex())).map(photograph -> {
            String figcaption = String.format("<p>%s</p>", Cleaners.cleanScript(photograph.getDescription()));
            return listPhotosItemPreviewTemplateProcessor.renderTemplate(Map.of(
                    "photo_url", safeAttr(photograph.getPhotoUrl()),
                    "photo_url_html", safeHtml(photograph.getPhotoUrl()),
                    "short_description", safeHtml(photograph.getShortDescription()),
                    "short_description_attr", safeAttr(photograph.getShortDescription()),
                    "description", safeHtml(photograph.getDescription()),
                    "description_for_figcaption", figcaption,
                    "is_published", publishedPhotos.contains(photograph.getIndex()) ? "is-indeed-published" : ""
            ));
        }).collect(Collectors.joining("\n"));

        String videoRows = videoDb.values().stream().filter(x -> videoIdsForPerson.contains(x.getIndex())).map(video -> {
            String figcaption = String.format("<p>%s</p><p>%s</p>", safeHtml(video.getShortDescription()), Cleaners.cleanScript(video.getDescription()));
            return listVideosItemPreviewTemplateProcessor.renderTemplate(Map.of(
                    "video_url", safeAttr(video.getVideoUrl()),
                    "video_poster",  StringUtils.safeAttr(video.getPoster()),
                    "short_description", safeHtml(video.getShortDescription()),
                    "description", safeHtml(video.getDescription()),
                    "description_for_figcaption", figcaption,
                    "is_published", publishedVideos.contains(video.getIndex()) ? "is-indeed-published" : ""
            ));
        }).collect(Collectors.joining("\n"));

        return photoRows + videoRows;
    }


    /**
     * Returns a list of identifiers for photos that are associated
     * with a person
     * @param person the {@link Person} we are inspecting for photos
     */
    private List<Long> getPhotoIdsForPerson(Person person) {
        return photoToPersonDb.getIndexedData("persons", String.valueOf(person.getIndex())).stream()
                .map(PhotoToPerson::getPhotoIndex)
                .toList();
    }

    /**
     * Returns a list of identifiers for videos that are associated
     * with a person
     * @param person the {@link Person} we are inspecting for videos
     */
    private List<Long> getVideoIdsForPerson(Person person) {
        return videoToPersonDb.getIndexedData("persons", String.valueOf(person.getIndex())).stream()
                .map(VideoToPerson::getVideoIndex)
                .toList();
    }
}
