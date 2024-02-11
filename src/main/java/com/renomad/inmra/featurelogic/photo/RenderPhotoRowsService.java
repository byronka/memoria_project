package com.renomad.inmra.featurelogic.photo;

import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.utils.Cleaners;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.database.Db;
import com.renomad.minum.templating.TemplateProcessor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.renomad.minum.utils.StringUtils.safeAttr;
import static com.renomad.minum.utils.StringUtils.safeHtml;

public class RenderPhotoRowsService {

    private final Db<PhotoToPerson> photoToPersonDb;
    private final Db<Photograph> photographDb;
    private final TemplateProcessor listPhotosItemTemplateProcessor;
    private final TemplateProcessor listPhotosItemPreviewTemplateProcessor;

    public RenderPhotoRowsService(
            Db<PhotoToPerson> photoToPersonDb,
            Db<Photograph> photographDb,
            IFileUtils fileUtils
            ){
        this.photoToPersonDb = photoToPersonDb;
        this.photographDb = photographDb;
        listPhotosItemTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_photos_item_template.html"));
        listPhotosItemPreviewTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("listphotos/list_photos_item_preview_template.html"));
    }

    /**
     * Given a {@link Person}, extract the information we need to render all the rows
     * of photos - including links to the photo, descriptions, etc.
     */
    public String renderPhotoRows(Person foundPerson) {
        List<Long> photoIdsForPerson = getPhotoIdsForPerson(foundPerson);

        return photographDb.values().stream().filter(x -> photoIdsForPerson.contains(x.getIndex())).map(x -> {
            String figcaption = String.format("<p>%s</p><div>%s</div>", safeHtml(x.getShortDescription()), Cleaners.cleanScript(x.getDescription()));
            return listPhotosItemTemplateProcessor.renderTemplate(Map.of(
                    "photo_id", String.valueOf(x.getIndex()),
                    "photo_url", safeAttr(x.getPhotoUrl()),
                    "photo_url_html", safeHtml(x.getPhotoUrl()),
                    "short_description", safeHtml(x.getShortDescription()),
                    "short_description_attr", safeAttr(x.getShortDescription()),
                    "description", safeHtml(x.getDescription()),
                    "description_for_figcaption", figcaption
            ));
        }).collect(Collectors.joining ("\n"));
    }

    /**
     * Similar to {@link #renderPhotoRows(Person)} except we don't show input fields
     * for descriptions, nor do we allow deleting here.  This is meant for a speed
     * aid when modifying data in the biography input field when editing a person.
     */
    public String renderPreviewPhotoRows(Person foundPerson) {
        List<Long> photoIdsForPerson = getPhotoIdsForPerson(foundPerson);

        return photographDb.values().stream().filter(x -> photoIdsForPerson.contains(x.getIndex())).map(x -> {
            String figcaption = String.format("<p>%s</p><div>%s</div>", safeHtml(x.getShortDescription()), Cleaners.cleanScript(x.getDescription()));
            return listPhotosItemPreviewTemplateProcessor.renderTemplate(Map.of(
                    "photo_url", safeAttr(x.getPhotoUrl()),
                    "photo_url_html", safeHtml(x.getPhotoUrl()),
                    "short_description", safeHtml(x.getShortDescription()),
                    "short_description_attr", safeAttr(x.getShortDescription()),
                    "description", safeHtml(x.getDescription()),
                    "description_for_figcaption", figcaption
            ));
        }).collect(Collectors.joining ("\n"));
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
}
