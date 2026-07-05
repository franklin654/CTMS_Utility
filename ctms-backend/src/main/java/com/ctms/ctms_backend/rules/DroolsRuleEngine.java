package com.ctms.ctms_backend.rules;

import java.util.ArrayList;
import java.util.List;
import org.kie.api.KieBase;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper around Drools/KIE: compiles a DRL string into a session, inserts facts, fires all
 * rules, and returns whatever the rules pushed onto the "results" global. This is the mechanism
 * Phases 3/5/6/10 build on for workflow/visit/document rule evaluation -- it deliberately has no
 * domain knowledge of its own.
 */
@Component
public class DroolsRuleEngine {

    public List<Object> fireRules(String drlContent, List<Object> facts) {
        KieBase kieBase = new KieHelper().addContent(drlContent, ResourceType.DRL).build();
        KieSession session = kieBase.newKieSession();
        List<Object> results = new ArrayList<>();
        try {
            session.setGlobal("results", results);
            for (Object fact : facts) {
                session.insert(fact);
            }
            session.fireAllRules();
        } finally {
            session.dispose();
        }
        return results;
    }
}
