package com.firstclub.membership.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.hamcrest.Matchers;

@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void listsSeededPlans() throws Exception {
        mvc.perform(get("/api/plans"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.planType=='MONTHLY')]").exists())
           .andExpect(jsonPath("$[?(@.planType=='YEARLY')]").exists());
    }

    @Test
    void listsSeededTiersByRank() throws Exception {
        mvc.perform(get("/api/tiers"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[0].name").value("SILVER"))
           .andExpect(jsonPath("$[2].name").value("PLATINUM"));
    }

    @Test
    void platinumTierExposesDiscountFifteen() throws Exception {
        mvc.perform(get("/api/tiers"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[2].name").value("PLATINUM"))
           .andExpect(jsonPath("$[2].benefits[?(@.type=='PERCENT_DISCOUNT')].params.discountPercent").value(Matchers.hasItem("15")));
    }
}
