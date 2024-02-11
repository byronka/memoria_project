package com.renomad.inmra.featurelogic.persons;

public enum Gender {
    MALE,
    FEMALE,
    UNKNOWN;

    public static Gender deserialize(String genderInput) {
        return switch (genderInput.toUpperCase()) {
            case "MALE" -> MALE;
            case "FEMALE" -> FEMALE;
            default -> UNKNOWN;
        };
    }

    public String serialize() {
        return switch (this) {
            case MALE -> "male";
            case FEMALE -> "female";
            default -> "";
        };
    }
}
