package dev.justine.proxyvote.policy;

import dev.justine.proxyvote.policy.PolicyDtos.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {
    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    public PolicyResponse get() {
        return service.current();
    }

    @PutMapping
    public PolicyResponse update(@Valid @RequestBody UpdatePolicyRequest req) {
        return service.update(req);
    }

    @PostMapping("/recalculate")
    public Map<String, Integer> recalculate() {
        return Map.of("recommendations", service.recalculate());
    }
}
