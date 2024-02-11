package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.IPersonLruCache;
import com.renomad.inmra.featurelogic.persons.Person;
import com.renomad.inmra.featurelogic.persons.PersonFile;
import com.renomad.inmra.utils.IFileUtils;
import com.renomad.minum.templating.TemplateProcessor;

import java.util.Map;

import static com.renomad.minum.utils.StringUtils.safeAttr;
import static com.renomad.minum.utils.StringUtils.safeHtml;

/**
 * Responsible for rendering a person for the homepage
 */
public class HomepagePersonRenderer {

    private final IPersonLruCache personLruCache;
    private final TemplateProcessor personListItemShortTemplateProcessor;

    public HomepagePersonRenderer(IFileUtils fileUtils, IPersonLruCache personLruCache) {
        personListItemShortTemplateProcessor = TemplateProcessor.buildProcessor(fileUtils.readTemplate("person/person_list_item_short.html"));
        this.personLruCache = personLruCache;
    }

    /**
     * Given the detail in a {@link Person} object,
     * obtain all the information that is needed to
     * fill in a preview of a person in HTML.
     */
    public String renderPersonTemplate(Person p) {
        PersonFile personFile;
        personFile = personLruCache.getCachedPersonFile(p);
        String fullImageUrl = determineFullImageUrl(personFile);
        String birthdayString = "(unknown birthdate)";
        if (! p.getBirthday().equals(com.renomad.inmra.featurelogic.persons.Date.EMPTY) && ! p.getBirthday().equals(com.renomad.inmra.featurelogic.persons.Date.EXISTS_BUT_UNKNOWN)) {
            birthdayString = "born " + p.getBirthday().getPrettyString();
        }
        Map<String, String> myMap = Map.of(
                "id", safeAttr(personFile.getId().toString()),
                "person_image", fullImageUrl,
                "name", safeHtml(personFile.getName()),
                "born_date", birthdayString
        );
        return personListItemShortTemplateProcessor.renderTemplate(myMap);
    }


    /**
     * Given a {@link PersonFile}, we will have enough information to
     * set the full image URL - that is, the URL that is used for the
     * main view of a person.
     */
    private static String determineFullImageUrl(PersonFile deserializedPersonFile) {
        String fullImageUrl;
        String imageUrl = deserializedPersonFile.getImageUrl();
        if (imageUrl != null && ! imageUrl.isBlank()){
            fullImageUrl = safeAttr(imageUrl) + "&amp;size=small";
        } else {
            fullImageUrl = setDefaultImageByGender(deserializedPersonFile);
        }
        return fullImageUrl;
    }


    /**
     * If we do not have a main image for a person, we'll fill in a default
     * value depending on the known gender.
     * <br>
     * Specifically, gender unknown will get you a hot air balloon.  The male
     * and female genders get you an expected cartoonish gender image.
     */
    private static String setDefaultImageByGender(PersonFile deserializedPersonFile) {
        return switch (deserializedPersonFile.getGender()) {
            case MALE -> "general/man.svg";
            case FEMALE -> "general/woman.svg";
            default -> "general/hot-air-balloon.svg";
        };
    }

}
