package com.helpdesk;

import com.helpdesk.common.config.SpaForwardFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;


import static org.assertj.core.api.Assertions.assertThat;

class SpaForwardFilterTest {

    private final SpaForwardFilter filter = new SpaForwardFilter();

    @Test
    void forwardsSpaClientRoutesToIndexHtml() throws Exception {
        for (String path : new String[]{"/", "/login", "/tickets/5", "/metrics", "/profile"}) {
            var request = new MockHttpServletRequest("GET", path);
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(response.getForwardedUrl())
                    .as("forward for %s", path).isEqualTo("/index.html");
            assertThat(chain.getRequest()).as("chain not called for %s", path).isNull();
        }
    }

    @Test
    void passesApiDocsAndAssetsThrough() throws Exception {
        for (String path : new String[]{"/api/tickets", "/v3/api-docs",
                "/swagger-ui/index.html", "/actuator/health", "/assets/app.js", "/favicon.svg"}) {
            var request = new MockHttpServletRequest("GET", path);
            var response = new MockHttpServletResponse();
            var chain = new MockFilterChain();
            filter.doFilter(request, response, chain);
            assertThat(chain.getRequest()).as("chain called for %s", path).isNotNull();
            assertThat(response.getForwardedUrl()).isNull();
        }
    }

    @Test
    void nonGetRequestsPassThrough() throws Exception {
        var request = new MockHttpServletRequest("POST", "/tickets");
        var chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
