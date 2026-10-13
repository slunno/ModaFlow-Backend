package com.modaflow.backend.ficha.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.modaflow.backend.common.exception.ResourceNotFoundException;
import com.modaflow.backend.common.exception.TenantAccessDeniedException;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.*;
import com.modaflow.backend.security.TenantAccessValidator;
import com.modaflow.backend.security.UserPrincipal;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Experimento central desativado: rascunho e histórico permanecem somente no navegador. */
@RestController
@RequestMapping("/fichas/rascunhos/{colecaoId}/pecas/{pecaId}")
@ConditionalOnExpression("false")
public class FichaDraftController {
    private final FichaDraftService service;
    private final TenantAccessValidator tenantAccess;

    public FichaDraftController(FichaDraftService service, TenantAccessValidator tenantAccess) {
        this.service = service; this.tenantAccess = tenantAccess;
    }

    public record PutRequest(int schemaVersion, String expectedRevision, JsonNode conteudo,
                             List<AttachmentReference> anexos) { }
    public record Error(int status, String code, String message, Instant timestamp) { }
    public record RevisionError(int status, String code, String message, Instant timestamp,
                                String expectedRevision, String currentRevision) { }

    @GetMapping public Snapshot find(@PathVariable String colecaoId, @PathVariable String pecaId) {
        return service.find(authorizedIdentity(colecaoId, pecaId));
    }

    @PutMapping public Snapshot save(@PathVariable String colecaoId, @PathVariable String pecaId,
                                     @RequestBody PutRequest request) {
        var identity = authorizedIdentity(colecaoId, pecaId);
        var user = authenticatedUser();
        // Autor/horário/revisão nova não são extraídos de request.conteudo/histórico legado.
        return service.save(identity, request.schemaVersion(), request.expectedRevision(), request.conteudo(),
                request.anexos(), new Actor(user.getId().toString(), user.getNome()));
    }

    @GetMapping("/historico") public List<AuditEntry> history(@PathVariable String colecaoId,
            @PathVariable String pecaId, @RequestParam(required = false) String afterEventoId,
            @RequestParam(defaultValue = "50") int limit) {
        return service.history(authorizedIdentity(colecaoId, pecaId), afterEventoId, limit);
    }

    private Identity authorizedIdentity(String colecaoId, String pecaId) {
        authenticatedUser();
        var marcaId = tenantAccess.requireTenantForCurrentUser();
        return new Identity(pecaId, marcaId.toString(), colecaoId);
    }

    private static UserPrincipal authenticatedUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal user) || user.getId() == null)
            throw new TenantAccessDeniedException("Usuário autenticado precisa ter acesso à marca informada.");
        return user;
    }

    @ExceptionHandler(FichaRevisionConflictException.class)
    public ResponseEntity<RevisionError> conflict(FichaRevisionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new RevisionError(409, "REVISION_CONFLICT",
                ex.getMessage(), Instant.now(), ex.revisaoEsperada(), ex.revisaoAtual()));
    }

    @ExceptionHandler(FichaDocumentTooLargeException.class)
    public ResponseEntity<Error> tooLarge(FichaDocumentTooLargeException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "DOCUMENT_TOO_LARGE", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Error> invalid(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DRAFT", ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Error> malformed(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DRAFT", "Envelope do rascunho inválido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Error> invalidQuery(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DRAFT", "Parâmetros de consulta inválidos.");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Error> notFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<Error> denied(TenantAccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "Usuário não possui acesso à marca informada.");
    }

    @ExceptionHandler({DataAccessException.class, FichaDraftConnectionException.class})
    public ResponseEntity<Error> unavailable(Exception ex) {
        // Nunca expor SQL/URL/credenciais do driver nem prometer fallback local como gravação central.
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DRAFT_STORAGE_UNAVAILABLE",
                "O rascunho não foi confirmado no servidor. Preserve a cópia local e tente novamente.");
    }

    private static ResponseEntity<Error> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new Error(status.value(), code, message, Instant.now()));
    }
}
