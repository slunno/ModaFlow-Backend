package com.modaflow.backend.ficha.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Metadados de confirmação são sempre do servidor; histórico importado é apenas conteúdo. */
public final class FichaDraftService {
    private final FichaRepositoryPort repository;
    private final FichaAuditPort audit;
    private final FichaDraftValidator validator;
    private final Clock clock;

    public FichaDraftService(FichaRepositoryPort repository, FichaAuditPort audit,
                             FichaDraftValidator validator, Clock clock) {
        this.repository = repository; this.audit = audit; this.validator = validator; this.clock = clock;
    }

    public Snapshot find(Identity identity) {
        validator.validateIdentity(identity);
        return repository.find(identity).orElseThrow(() -> new ResourceNotFoundException("Rascunho não encontrado nesta marca e coleção."));
    }

    public Snapshot save(Identity identity, int schemaVersion, String expectedRevision,
                         JsonNode content, List<AttachmentReference> attachments, Actor serverActor) {
        if (attachments == null) throw new IllegalArgumentException("anexos precisa ser uma lista vazia.");
        var operation = expectedRevision == null ? Operation.CRIAR : Operation.ATUALIZAR;
        var draft = new DraftWrite(identity, schemaVersion, operation, expectedRevision, content, attachments);
        validator.validate(draft);
        validator.validateActor(serverActor);
        // Nenhum campo de integração Linx é criado/confirmado por esta operação.
        return repository.persistWithAudit(new ServerWrite(draft, UUID.randomUUID().toString(),
                clock.instant().truncatedTo(ChronoUnit.MICROS), serverActor));
    }

    public List<AuditEntry> history(Identity identity, String afterEventoId, int limit) {
        validator.validateIdentity(identity);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit precisa estar entre 1 e 100.");
        if (afterEventoId != null) {
            validator.validateCursor(afterEventoId);
            try { UUID.fromString(afterEventoId); }
            catch (IllegalArgumentException ex) { throw new IllegalArgumentException("afterEventoId inválido."); }
        }
        return audit.list(identity, afterEventoId, limit);
    }
}
