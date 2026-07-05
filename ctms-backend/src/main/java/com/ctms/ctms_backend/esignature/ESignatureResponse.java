package com.ctms.ctms_backend.esignature;

import java.time.Instant;

public record ESignatureResponse(
        Long id, String signedByUsername, String entityName, String entityId, String reason, Instant signedAt) {

    static ESignatureResponse from(ESignature signature) {
        return new ESignatureResponse(
                signature.getId(),
                signature.getUser().getUsername(),
                signature.getEntityName(),
                signature.getEntityId(),
                signature.getReason(),
                signature.getSignedAt());
    }
}
