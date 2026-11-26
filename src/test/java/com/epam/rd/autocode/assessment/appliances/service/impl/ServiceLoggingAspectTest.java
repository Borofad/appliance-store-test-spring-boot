package com.epam.rd.autocode.assessment.appliances.service.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.epam.rd.autocode.assessment.appliances.aspect.LoggingServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.*;

interface SampleService {
    String find(Long id);
}

/** Lives in the service.impl package on purpose: the aspect logs every public method there at DEBUG. */
class SampleServiceImpl implements SampleService {
    public String find(Long id) {
        return "item-" + id;
    }
}

class ServiceLoggingAspectTest {

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> appender;
    private Level originalLevel;
    private SampleService service;

    @BeforeEach
    void setUp() {
        aspectLogger = (Logger) LoggerFactory.getLogger(LoggingServices.class);
        originalLevel = aspectLogger.getLevel();
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);

        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleServiceImpl());
        factory.addAspect(new LoggingServices());
        service = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
        aspectLogger.setLevel(originalLevel);
    }

    @Test
    void serviceMethod_isLoggedAtDebug() {
        aspectLogger.setLevel(Level.DEBUG);

        assertEquals("item-5", service.find(5L));

        assertEquals(2, appender.list.size());
        assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
        assertTrue(appender.list.get(0).getFormattedMessage().contains("find"));
        assertTrue(appender.list.get(1).getFormattedMessage().contains("item-5"));
    }

    @Test
    void serviceMethod_isSilentAtInfoLevel() {
        aspectLogger.setLevel(Level.INFO);

        assertEquals("item-5", service.find(5L));

        assertTrue(appender.list.isEmpty());
    }
}
