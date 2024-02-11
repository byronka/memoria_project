package com.renomad.inmra.utils;

import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This wrapper exists solely to let us mock calls for better testing.
 */
public interface ISearchUtilsWrapper {

    <T> T findExactlyOne(Stream<T> streamOfSomething, Predicate<? super T> searchPredicate);

    <T> T findExactlyOne(Stream<T> streamOfSomething, Predicate<? super T> searchPredicate, Callable<T> alternate);
}
