package com.renomad.inmra.featurelogic.version;

import com.renomad.inmra.featurelogic.persons.PersonFile;

import java.time.ZonedDateTime;

public record PersonFileVersionEntry(PersonFile personFile, String userName, long userId, ZonedDateTime dateTimeStamp){}