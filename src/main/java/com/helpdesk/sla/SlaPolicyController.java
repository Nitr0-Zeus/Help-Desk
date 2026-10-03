package com.helpdesk.sla;

import com.helpdesk.sla.dto.SlaDtos.SlaPolicyView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sla-policies")
public class SlaPolicyController {

    private final SlaPolicyRepository policies;

    public SlaPolicyController(SlaPolicyRepository policies) {
        this.policies = policies;
    }

    @GetMapping
    public List<SlaPolicyView> list() {
        return policies.findAll().stream()
                .map(p -> new SlaPolicyView(p.getPriority(), p.getFirstResponseHours(),
                        p.getResolutionHours(), p.isActive()))
                .toList();
    }
}
