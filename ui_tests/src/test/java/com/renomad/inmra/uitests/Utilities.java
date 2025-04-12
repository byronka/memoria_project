package com.renomad.inmra.uitests;

import com.renomad.minum.utils.MyThread;

public class Utilities {

    /**
     * Many actions in the browser are on radio buttons, clicking links,
     * that sort of thing.  Those are tied in deeply to the browser and
     * end up being synchronous C++ code.  On the other side, there are
     * many actions that cause JavaScript to run, which often run asynchronously,
     * with timers and network calls and so forth, and which tools like
     * Selenium have a harder time predicting when they'll be done.
     * <br>
     * Furthermore, if a following action (like clicking a link) is somehow
     * impacted by a previous JavaScript action or CSS animation, it may be that Selenium
     * won't find something.
     * <br>
     * For example, if I hover over an element and it shows a preview window,
     * and I immediately afterwards try to click a link that might be obscured
     * by that window, Selenium will fail to find it and the test will fail.
     * <br>
     * This method is meant to be used around all those actions where JavaScript,
     * timers, CSS animations cause us to need a bit extra delay, so our UI
     * tests will be more dependable.
     */
    public static void waitForUi() {
        MyThread.sleep(20);
    }
}
