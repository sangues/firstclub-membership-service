package com.firstclub.membership;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RootControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void rootReturnsApiIndex() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("FirstClub Membership Program"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.endpoints['GET /api/plans']").exists())
                .andExpect(jsonPath("$.endpoints['POST /api/order-events']").exists());
    }
}
