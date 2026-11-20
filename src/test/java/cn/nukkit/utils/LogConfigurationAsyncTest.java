package cn.nukkit.utils;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped log4j2.xml must keep disk and terminal writes off the thread that logs.
 *
 * <p>A direct file appender makes every log line a write() on the calling thread. On a busy
 * spinning disk such a write blocked the main thread for 1.5 s, which is a TPS drop for everyone
 * online. The test loads the real configuration, parks its file target and checks that a log
 * call still returns at once and that the line is written as soon as the disk answers.
 */
class LogConfigurationAsyncTest {

    @Test
    void theLoggingThreadDoesNotWaitForABusyFile() throws Exception {
        LoggerContext context = new LoggerContext("nukkit-log-config-test");
        XmlConfiguration configuration = new XmlConfiguration(context,
                ConfigurationSource.fromResource("log4j2.xml", LogConfigurationAsyncTest.class.getClassLoader()));
        configuration.initialize();

        assertEquals(Set.of("Async"), configuration.getRootLogger().getAppenders().keySet(),
                "The root logger must reach the console and the file only through the queue");
        AsyncAppender queue = assertInstanceOf(AsyncAppender.class, configuration.getAppender("Async"));
        assertTrue(queue.isBlocking(), "A full queue must wait rather than drop lines");
        assertEquals(List.of("Console", "File"), List.of(queue.getAppenderRefStrings()));

        CountDownLatch diskAnswers = new CountDownLatch(1);
        RecordingAppender file = new RecordingAppender("File", diskAnswers);
        configuration.removeAppender("File");
        configuration.addAppender(file);
        configuration.removeAppender("Console");
        configuration.addAppender(new RecordingAppender("Console", null));
        context.start(configuration);
        try {
            Logger logger = context.getLogger("nukkit.test");
            long started = System.nanoTime();
            logger.info("line while the disk is busy");
            long waitedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

            assertTrue(file.entered.await(5, TimeUnit.SECONDS), "The queue must hand the line to the file");
            assertTrue(waitedMillis < 1_000, "The logging thread waited " + waitedMillis + " ms for the file");
            diskAnswers.countDown();
            assertTrue(file.written.await(5, TimeUnit.SECONDS), "The line must reach the file once the disk answers");
            assertEquals(List.of("line while the disk is busy"), file.lines);
            assertEquals(List.of(Thread.currentThread().getName()), file.threads,
                    "[%t] must still name the thread that logged, not the queue thread");
        } finally {
            diskAnswers.countDown();
            context.stop();
        }
    }

    private static final class RecordingAppender extends AbstractAppender {
        private final CountDownLatch release;
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch written = new CountDownLatch(1);
        private final List<String> lines = new CopyOnWriteArrayList<>();
        private final List<String> threads = new CopyOnWriteArrayList<>();

        private RecordingAppender(String name, CountDownLatch release) {
            super(name, null, null, true, Property.EMPTY_ARRAY);
            this.release = release;
        }

        @Override
        public void append(LogEvent event) {
            entered.countDown();
            if (release != null) {
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            lines.add(event.getMessage().getFormattedMessage());
            threads.add(event.getThreadName());
            written.countDown();
        }
    }
}
