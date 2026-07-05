package com.ctms.ctms_backend.rules;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only "no-code" rule configuration surface (Phase 0 skeleton; Phase 10 adds the visual
 * builder on top of this same backend). Domain modules (visit windows, document requirements,
 * workflow triggers) will call {@link RuleSetService#evaluate} directly rather than through HTTP.
 */
@RestController
@RequestMapping("/api/rule-sets")
@PreAuthorize("hasRole('ADMIN')")
public class RuleSetController {

    private final RuleSetService ruleSetService;

    public RuleSetController(RuleSetService ruleSetService) {
        this.ruleSetService = ruleSetService;
    }

    @PostMapping
    public RuleSetResponse create(@Valid @RequestBody CreateRuleSetRequest request) {
        return RuleSetResponse.from(
                ruleSetService.createRuleSet(request.name(), request.category(), request.description()));
    }

    @PostMapping("/{name}/definitions")
    public RuleDefinitionResponse addDefinition(
            Principal principal, @PathVariable String name, @Valid @RequestBody AddRuleDefinitionRequest request) {
        return RuleDefinitionResponse.from(
                ruleSetService.addDefinition(name, request.drlContent(), principal.getName()));
    }

    @GetMapping("/{name}/evaluate-smoke-test")
    public List<Object> evaluateSmokeTest(@PathVariable String name) {
        return ruleSetService.evaluate(name, List.of());
    }
}
