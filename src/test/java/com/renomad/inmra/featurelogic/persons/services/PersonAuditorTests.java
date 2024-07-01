package com.renomad.inmra.featurelogic.persons.services;

import com.renomad.inmra.utils.IFileWriteStringWrapper;
import com.renomad.minum.state.Context;
import com.renomad.minum.logging.TestLogger;
import com.renomad.minum.queue.ActionQueueKiller;
import com.renomad.minum.utils.MyThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static com.renomad.minum.testing.TestFramework.assertTrue;
import static com.renomad.minum.testing.TestFramework.buildTestingContext;

public class PersonAuditorTests {

    private Context context;
    private TestLogger logger;

    @Before
    public void init() throws IOException {
        context = buildTestingContext("person_auditor_tests");
        logger = (TestLogger)context.getLogger();
    }

    @After
    public void cleanup() {
        new ActionQueueKiller(context).killAllQueues();
    }

    @Test
    public void testPersonAuditor_EdgeCase_IOExceptionThrown() {
        // this mock Files.writeString will always throw an exception
        IFileWriteStringWrapper fileWriteStringWrapper = (path, csq, options) -> {
            throw new IOException("foo foo did a foo");
        };
        var personAuditor = new PersonAuditor(context, fileWriteStringWrapper);
        UUID uuid = UUID.fromString("46c99d19-d2e0-4924-b255-c4489c27bce8");
        personAuditor.storePersonToAudit(
                uuid,
                "some content here",
                Path.of("DOESNOTMATTER"), // does not matter - the code for writing to disk is being mocked
                "Theodore the test");

        MyThread.sleep(100);
        assertTrue(logger.doesMessageExist("exception thrown while writing audit"));
    }
}
