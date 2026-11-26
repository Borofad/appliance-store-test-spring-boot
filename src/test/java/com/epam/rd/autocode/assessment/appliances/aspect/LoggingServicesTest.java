package com.epam.rd.autocode.assessment.appliances.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

interface Greeter {
    String hello(String name);

    String secret(String password);

    void boom();

    String plain(String text);
}

class GreeterImpl implements Greeter {

    @Loggable
    public String hello(String name) {
        return "Hello " + name;
    }

    @Loggable(logArguments = false)
    public String secret(String password) {
        return "stored";
    }

    @Loggable
    public void boom() {
        throw new IllegalStateException("kaboom");
    }

    public String plain(String text) {
        return text;
    }
}

class LoggingServicesTest {

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;
    private Greeter greeter;

    @BeforeEach
    void setUp() {
        aspectLogger = (Logger) LoggerFactory.getLogger(LoggingServices.class);
        originalLevel = aspectLogger.getLevel();
        aspectLogger.setLevel(Level.DEBUG);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);

        AspectJProxyFactory factory = new AspectJProxyFactory(new GreeterImpl());
        factory.addAspect(new LoggingServices());
        greeter = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
        aspectLogger.setLevel(originalLevel);
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void loggableMethod_returnsResultUnchangedAndLogsEntryAndExitAtInfo() {
        String result = greeter.hello("Anna");

        assertEquals("Hello Anna", result);
        assertEquals(2, appender.list.size());
        assertEquals(Level.INFO, appender.list.get(0).getLevel());
        assertTrue(messages().get(0).contains("hello"));
        assertTrue(messages().get(0).contains("Anna"));
        assertTrue(messages().get(1).contains("Hello Anna"));
        assertTrue(messages().get(1).contains("ms"));
    }

    @Test
    void hiddenArguments_neverAppearInTheLog() {
        greeter.secret("TopSecret1!");

        assertFalse(appender.list.isEmpty());
        String all = String.join(" | ", messages());
        assertFalse(all.contains("TopSecret1!"));
        assertTrue(all.contains("hidden"));
    }

    @Test
    void exception_isRethrownAndTraced() {
        assertThrows(IllegalStateException.class, () -> greeter.boom());

        String all = String.join(" | ", messages());
        assertTrue(all.contains("IllegalStateException"));
        assertTrue(all.contains("kaboom"));
    }

    @Test
    void methodWithoutAnnotation_isNotLogged() {
        assertEquals("x", greeter.plain("x"));

        assertTrue(appender.list.isEmpty());
    }

    @Test
    void veryLongArgument_isShortened() {
        greeter.hello("x".repeat(1000));

        String entry = messages().get(0);
        assertTrue(entry.length() < 400);
        assertTrue(entry.contains("..."));
    }

    @Test
    void lineBreaksInArguments_areRemoved() {
        greeter.hello("Anna\nFAKE LOG LINE");

        assertFalse(messages().get(0).contains("\n"));
    }

    @Test
    void infoLevelOff_doesNotLogButStillReturnsResult() {
        aspectLogger.setLevel(Level.WARN);

        assertEquals("Hello Anna", greeter.hello("Anna"));
        assertTrue(appender.list.isEmpty());
    }
}