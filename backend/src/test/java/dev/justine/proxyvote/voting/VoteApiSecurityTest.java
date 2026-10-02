package dev.justine.proxyvote.voting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.justine.proxyvote.config.AppProperties;
import dev.justine.proxyvote.config.SecurityConfig;
import dev.justine.proxyvote.meeting.VoteDecision;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VoteController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = "app.cors-allowed-origins=http://localhost:5174")
class VoteApiSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean VoteService votes;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void analystWithoutVoterRoleCannotVote() throws Exception {
        mvc.perform(put("/api/proposals/1/vote").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ANALYST")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"FOR\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void voterCanVote() throws Exception {
        when(votes.cast(eq(1L), eq(VoteDecision.FOR), any()))
            .thenReturn(new VoteService.VoteResponse(1L, VoteDecision.FOR, "vic", Instant.now(), 0));
        mvc.perform(put("/api/proposals/1/vote").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VOTER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"FOR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("FOR"));
    }

    @Test
    void unknownDecisionIsRejected() throws Exception {
        mvc.perform(put("/api/proposals/1/vote").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_VOTER")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"MAYBE\"}"))
            .andExpect(status().isBadRequest());
    }
}
