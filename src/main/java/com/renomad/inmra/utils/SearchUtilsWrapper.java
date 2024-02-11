package com.renomad.inmra.utils;

import com.renomad.minum.utils.SearchUtils;

import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SearchUtilsWrapper implements ISearchUtilsWrapper{
    @Override
    public <T> T findExactlyOne(Stream<T> streamOfSomething, Predicate<? super T> searchPredicate) {
        return SearchUtils.findExactlyOne(streamOfSomething, searchPredicate);
    }

    @Override
    public <T> T findExactlyOne(Stream<T> streamOfSomething, Predicate<? super T> searchPredicate, Callable<T> alternate) {
        return SearchUtils.findExactlyOne(streamOfSomething, searchPredicate, alternate);
    }
}
