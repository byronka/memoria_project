package com.renomad.inmra.utils;

import com.renomad.minum.logging.Logger;
import com.renomad.minum.logging.ThrowingSupplier;
import com.renomad.minum.queue.AbstractActionQueue;

import java.util.HashMap;
import java.util.Map;

import static com.renomad.minum.utils.TimeUtils.getTimestampIsoInstant;

public class MemoriaLogger extends Logger {

    private final Map<MemoriaLoggingLevel, Boolean> logLevels;

    public MemoriaLogger(Logger logger) {
        super(logger);
        logLevels = new HashMap<>();
        logLevels.put(MemoriaLoggingLevel.REQUEST, true);
    }

    /**
     * Allow users to get the mutable map of logging levels, which will allow
     * them to adjust it as they wish.
     */
    public Map<MemoriaLoggingLevel, Boolean> getLogLevels() {
        return logLevels;
    }

    public void logRequests(ThrowingSupplier<String, Exception> msg) {
        MemoriaLogger.logHelper(msg, MemoriaLoggingLevel.REQUEST, logLevels, loggingActionQueue);
    }

    static void logHelper(
            ThrowingSupplier<String, Exception> msg,
            MemoriaLoggingLevel loggingLevel,
            Map<MemoriaLoggingLevel, Boolean> logLevels,
            AbstractActionQueue loggingActionQueue
    ) {
        if (Boolean.TRUE.equals(logLevels.get(loggingLevel))) {
            String receivedMessage;
            try {
                receivedMessage = msg.get();
            } catch (Exception ex) {
                receivedMessage = "EXCEPTION DURING GET: " + ex;
            }
            String finalReceivedMessage = receivedMessage;
            if (loggingActionQueue == null || loggingActionQueue.isStopped()) {
                Object[] args = new Object[]{getTimestampIsoInstant(), loggingLevel.name(), showWhiteSpace(finalReceivedMessage)};
                System.out.printf("%s\t%s\t%s%n", args);
            } else {
                loggingActionQueue.enqueue("Logger#logHelper(" + receivedMessage + ")", () -> {
                    Object[] args = new Object[]{getTimestampIsoInstant(), loggingLevel.name(), showWhiteSpace(finalReceivedMessage)};
                    System.out.printf("%s\t%s\t%s%n", args);
                });
            }
        }
    }

    /**
     * Given a string that may have whitespace chars, render it in a way we can see
     */
    static String showWhiteSpace(String msg) {
        if (msg == null) return "(NULL)";
        if (msg.isEmpty()) return "(EMPTY)";

        // if we have tabs, returns, newlines in the text, show them
        String text = msg
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");

        if (text.isBlank()) return "(BLANK)";
        return text;
    }

}
