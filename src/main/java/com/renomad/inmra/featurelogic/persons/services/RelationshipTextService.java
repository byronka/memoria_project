package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.featurelogic.persons.Gender;
import com.renomad.inmra.featurelogic.persons.PersonNode;

import java.util.Map;

/**
 * Here we handle some of the programs dealing with relationship
 * text.  For example, "father of father of mother" is equal to
 * "parent of father of parent" is equal to "great-grandparent"
 */
public class RelationshipTextService {

    /**
     * Converter to a gendered version of relationship. For
     * example, a "father" instead of a "parent"
     * @param relationshipString a string of the relationship - for example, "parent"
     */
    public static String getGenderedRelationshipString(String relationshipString, Gender gender) {
        String convertedValue = relationshipString;
        if (gender.equals(Gender.MALE)) {
            convertedValue = switch (relationshipString) {
                case "child" -> "son";
                case "spouse" -> "husband";
                case "sibling" -> "brother";
                case "parent" -> "father";
                default -> relationshipString;
            };
        } else if (gender.equals(Gender.FEMALE)) {
            convertedValue = switch (relationshipString) {
                case "child" -> "daughter";
                case "spouse" -> "wife";
                case "sibling" -> "sister";
                case "parent" -> "mother";
                default -> relationshipString;
            };
        }
        return convertedValue;
    }

}
