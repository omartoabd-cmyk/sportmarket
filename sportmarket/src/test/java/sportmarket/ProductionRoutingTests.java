package sportmarket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"production", "test"})
class ProductionRoutingTests {
    @Autowired MockMvc mvc;

    @Test void frontendDeepLinksForwardToReact() throws Exception {
        for (String route : new String[]{"/", "/login", "/register", "/my-listings",
                "/listings/new", "/listings/42", "/listings/42/edit"}) {
            mvc.perform(get(route)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test void publicApiStillWorks() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test void protectedApiStillRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me/listings")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/listings").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void unspecifiedPathsRemainDenied() throws Exception {
        mvc.perform(get("/api/not-an-endpoint")).andExpect(status().isUnauthorized());
        mvc.perform(post("/login")).andExpect(status().isUnauthorized());
    }
}
