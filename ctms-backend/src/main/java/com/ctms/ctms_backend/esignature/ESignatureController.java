package com.ctms.ctms_backend.esignature;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generic e-signature capture, reused by any feature needing a 21 CFR Part 11 signature (study
 * lifecycle transitions, document approvals, etc. in later phases) -- those features call
 * {@link ESignatureService} directly for entity-specific side effects (e.g. locking a record) and
 * use this endpoint only for the reusable UI "Sign" modal described in the Implementation Plan.
 */
@RestController
@RequestMapping("/api/e-signatures")
public class ESignatureController {

    private final ESignatureService eSignatureService;

    public ESignatureController(ESignatureService eSignatureService) {
        this.eSignatureService = eSignatureService;
    }

    @PostMapping
    public ESignatureResponse sign(Principal principal, @Valid @RequestBody SignRequest request) {
        ESignature signature = eSignatureService.sign(
                principal.getName(), request.password(), request.entityName(), request.entityId(), request.reason());
        return ESignatureResponse.from(signature);
    }

    @GetMapping
    public List<ESignatureResponse> history(
            @RequestParam String entityName, @RequestParam String entityId) {
        return eSignatureService.history(entityName, entityId);
    }
}
