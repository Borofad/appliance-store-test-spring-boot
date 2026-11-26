package com.epam.rd.autocode.assessment.appliances.controller;

import com.epam.rd.autocode.assessment.appliances.security.AppUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndexControllerTest {

    @Test
    void index_showsTheHomePageWithTheUsersName() {
        Model model = new ConcurrentModel();
        AppUserDetails user = new AppUserDetails(1L, "Mercury", "mercury@gmail.com", "HASH", "CLIENT");

        String view = new IndexController().index(user, model);

        assertEquals("index", view);
        assertEquals("Mercury", model.getAttribute("userName"));
    }
}
