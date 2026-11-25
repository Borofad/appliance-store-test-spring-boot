package com.epam.rd.autocode.assessment.appliances.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private static final Locale EN = Locale.ENGLISH;

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.setUseCodeAsDefaultMessage(true);
        messages.addMessage("error.entity.notFound", EN, "{0} with id {1} was not found");
        messages.addMessage("entity.appliance", EN, "Appliance");
        messages.addMessage("error.manufacturer.exists", EN, "A manufacturer with this name already exists");
        messages.addMessage("error.order.empty", EN, "An order must contain at least one item");
        messages.addMessage("error.403.message", EN, "You do not have permission to view this page.");
        messages.addMessage("error.404.title", EN, "Page not found");
        messages.addMessage("error.500.message", EN, "The server encountered an error. Please try again later.");
        handler = new GlobalExceptionHandler(messages);

        request = new MockHttpServletRequest("GET", "/appliances/5");
    }

    private void assertPage(ModelAndView mav, HttpStatus status) {
        assertEquals("error/error", mav.getViewName());
        assertEquals(status, mav.getStatus());
        assertEquals(status.value(), mav.getModel().get("status"));
        assertNotNull(mav.getModel().get("title"));
        assertNotNull(mav.getModel().get("message"));
    }

    @Test
    void notFound_returns404WithTranslatedEntityName() {
        ModelAndView mav = handler.handleNotFound(ResourceNotFoundException.appliance(5L), request, EN);

        assertPage(mav, HttpStatus.NOT_FOUND);
        assertEquals("Appliance with id 5 was not found", mav.getModel().get("message"));
        assertEquals("Page not found", mav.getModel().get("title"));
    }

    @Test
    void emptyOrder_returns400() {
        ModelAndView mav = handler.handleEmptyOrder(new EmptyOrderException(), request, EN);

        assertPage(mav, HttpStatus.BAD_REQUEST);
        assertEquals("An order must contain at least one item", mav.getModel().get("message"));
    }

    @Test
    void businessRule_returns409WithItsMessage() {
        ModelAndView mav = handler.handleBusiness(new ManufacturerAlreadyExistsException("Sony"), request, EN);

        assertPage(mav, HttpStatus.CONFLICT);
        assertEquals("A manufacturer with this name already exists", mav.getModel().get("message"));
    }

    @Test
    void accessDenied_returns403() {
        request.setRemoteUser("bob");

        ModelAndView mav = handler.handleAccessDenied(new AccessDeniedException("nope"), request, EN);

        assertPage(mav, HttpStatus.FORBIDDEN);
        assertEquals("You do not have permission to view this page.", mav.getModel().get("message"));
    }

    @Test
    void dataIntegrityViolation_returns409() {
        ModelAndView mav = handler.handleDataIntegrity(
                new DataIntegrityViolationException("unique index violated"), request, EN);

        assertPage(mav, HttpStatus.CONFLICT);
    }

    @Test
    void illegalArgument_returns400() {
        assertPage(handler.handleBadRequest(new IllegalArgumentException("bad"), request, EN),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void typeMismatch_returns400() {
        assertPage(handler.handleBadRequest(mock(MethodArgumentTypeMismatchException.class), request, EN),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    void springWebException_keepsItsStatus() {
        ModelAndView mav = handler.handleAny(new ResponseStatusException(HttpStatus.NOT_FOUND), request, EN);

        assertPage(mav, HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownStatus_usesGenericMessages() {
        ModelAndView mav = handler.handleAny(new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED), request, EN);

        assertPage(mav, HttpStatus.METHOD_NOT_ALLOWED);
        assertEquals("error.title", mav.getModel().get("title"));
        assertEquals("error.generic", mav.getModel().get("message"));
    }

    @Test
    void unexpectedException_returns500AndHidesDetails() {
        ModelAndView mav = handler.handleAny(new IllegalStateException("secret database password"), request, EN);

        assertPage(mav, HttpStatus.INTERNAL_SERVER_ERROR);
        assertEquals("The server encountered an error. Please try again later.", mav.getModel().get("message"));
        assertFalse(mav.getModel().values().toString().contains("secret"));
    }
}