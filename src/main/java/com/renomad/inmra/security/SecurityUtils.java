package com.renomad.inmra.security;

import com.renomad.minum.logging.ILogger;
import com.renomad.minum.utils.MyThread;
import com.renomad.minum.utils.TimeUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * Looking for bad actors in our system
 */
public class SecurityUtils implements ISecurityUtils {

    /**
     * How long we'll sleep between clearing out old keys
     */
    private final int investigationLifespan;

    private final ExecutorService es;

    /**
     * How long our inner thread will sleep before waking up to scan
     * for old keys
     */
    private final int sleepTime;
    private final ILogger logger;
    private final KeyProcessor keyProcessor;

    /**
     * This is the thread that runs throughout the lifetime
     * of the application.
     */
    private Thread myThread;

    /**
     * These are all the keys that have been added
     */
    private final Map<String, Long> keys;

    private final Map<String, List<Long>> clientsAndTimes = new HashMap<>();

    private final ReentrantLock scriptedLoginLock = new ReentrantLock();

    /**
     * This constructor lets us set the length of time that
     * we'll keep a particular key in the data.
     * @param investigationLifespan lifetime in millis we'll keep a key under investigation
     */
    public SecurityUtils(int investigationLifespan, int sleepTime, ExecutorService es, ILogger logger) {
        this.investigationLifespan = investigationLifespan;
        this.es = es;
        this.keys = new HashMap<>();
        this.sleepTime = sleepTime;
        this.logger = logger;
        this.keyProcessor = new KeyProcessor(logger);
    }

    /**
     * In this class we create a thread that runs throughout the lifetime
     * of the application, in an infinite loop removing keys from the list
     * under consideration.
     */
    public SecurityUtils(ExecutorService es, ILogger logger) {
        this(10 * 1_000, 10 * 1_000, es, logger);
    }

    // Regarding the BusyWait - indeed, we expect that the while loop
    // below is an infinite loop unless there's an exception thrown, that's what it is.
    @Override
    @SuppressWarnings({"BusyWait"})
    public ISecurityUtils initialize() {
        logger.logDebug(() -> "Initializing SecurityUtils main loop");
        Callable<Object> innerLoopThread = () -> {
            Thread.currentThread().setName("SecurityUtilsLoop");
            myThread = Thread.currentThread();
            while (true) {
                try {
                    var now = System.currentTimeMillis();
                    keyProcessor.processKeysUnderConsideration(keys, investigationLifespan, clientsAndTimes, now);
                    Thread.sleep(sleepTime);
                } catch (InterruptedException ex) {

                    /*
                    this is what we expect to happen.
                    once this happens, we just continue on.
                    this only gets called when we are trying to shut everything
                    down cleanly
                     */

                    System.out.printf(TimeUtils.getTimestampIsoInstant() + " SecurityUtils is stopped.%n");
                    return null;
                } catch (Exception ex) {
                    System.out.printf(TimeUtils.getTimestampIsoInstant() + " ERROR: SecurityUtils has stopped unexpectedly. error: %s%n", ex);
                    throw ex;
                }
            }
        };
        es.submit(innerLoopThread);
        return this;
    }

    /**
     * Kills the infinite loop running inside this class.
     */
    @Override
    public void stop() {
        logger.logDebug(() -> "SecurityUtils has been told to stop");
        for (int i = 0; i < 10; i++) {
            if (myThread != null) {
                logger.logDebug(() -> "SecurityUtils: Sending interrupt to thread");
                myThread.interrupt();
                return;
            } else {
                MyThread.sleep(20);
            }
        }
        throw new RuntimeException("SecurityUtils: Leaving without successfully stopping thread");
    }


    /**
     * Check if the client seems to be trying to login too quickly,
     * suggesting a scripted brute-force attack.
     */
    @Override
    public boolean isScriptedLogin(String clientAddress) {
        logger.logTrace(() -> "SecurityUtils: Reviewing " + clientAddress + " for scripted brute-force logins");

        long currentTime = System.currentTimeMillis();
        keys.putIfAbsent(clientAddress, currentTime);

        scriptedLoginLock.lock();
        try {
            clientsAndTimes.merge(
                    clientAddress,
                    List.of(currentTime),
                    (prev, current) -> Stream.concat(prev.stream(), current.stream()).toList());
        } finally {
            scriptedLoginLock.unlock();
        }
        var logins = clientsAndTimes.get(clientAddress);
        if (logins == null) return false;
        var sortedLogins = logins.stream().sorted().toList();
        if (sortedLogins.size() <= 1) return false;
        boolean result = false;
        for (int i = 1; i < sortedLogins.size(); i++) {
            if (sortedLogins.get(i) - sortedLogins.get(i - 1) < 1_000) {
                result = true;
                break;
            }
        }
        boolean finalResult = result;
        logger.logTrace(() -> "SecurityUtils: " + clientAddress + " authenticating too frequently? " + finalResult);
        return result;
    }

}
