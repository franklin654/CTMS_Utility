package com.ctms.ctms_backend.esignature;

import com.ctms.ctms_backend.audit.AuditAction;
import com.ctms.ctms_backend.audit.AuditService;
import com.ctms.ctms_backend.security.exception.InvalidCredentialsException;
import com.ctms.ctms_backend.user.User;
import com.ctms.ctms_backend.user.UserRepository;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ESignatureService {

    private final ESignatureRepository eSignatureRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public ESignatureService(
            ESignatureRepository eSignatureRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.eSignatureRepository = eSignatureRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * Re-authenticates the signer by password (single factor, per org constraint) and records an
     * immutable e-signature against the given entity. Throws {@link InvalidCredentialsException}
     * if the password doesn't match -- callers should treat that as "signature not captured."
     */
    @Transactional
    public ESignature sign(String username, String password, String entityName, String entityId, String reason) {
        User user = userRepository.findByUsername(username).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        ESignature signature = eSignatureRepository.save(new ESignature(user, entityName, entityId, reason));
        auditService.record(entityName, entityId, AuditAction.STATE_CHANGE, null, "e-signed: " + reason, reason);
        return signature;
    }

    @Transactional(readOnly = true)
    public List<ESignatureResponse> history(String entityName, String entityId) {
        return eSignatureRepository.findByEntityNameAndEntityIdOrderBySignedAtDesc(entityName, entityId).stream()
                .map(ESignatureResponse::from)
                .toList();
    }
}
