package com.helpdesk.metrics;

import com.helpdesk.auth.domain.Role;
import com.helpdesk.auth.repo.UserRepository;
import com.helpdesk.metrics.dto.MetricsDtos.AgentMetrics;
import com.helpdesk.metrics.dto.MetricsDtos.GlobalMetrics;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricsController {

    private final MetricsService metricsService;
    private final UserRepository users;

    public MetricsController(MetricsService metricsService, UserRepository users) {
        this.metricsService = metricsService;
        this.users = users;
    }

    @GetMapping
    public GlobalMetrics global() {
        return metricsService.global();
    }

    @GetMapping("/agents")
    public List<AgentMetrics> agents() {
        return metricsService.perAgent(users.findByRole(Role.AGENTE));
    }

    @GetMapping("/agents/{id}")
    public AgentMetrics agent(@PathVariable Long id) {
        return metricsService.forAgent(id);
    }
}
