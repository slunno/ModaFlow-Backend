package com.modaflow.backend.ficha.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.DraftWrite;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Identity;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Actor;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Snapshot;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Valida apenas o envelope/segurança de armazenamento. Rascunhos podem ser parciais. */
public final class FichaDraftValidator {
    private final ObjectMapper mapper;
    private final long maximumDocumentBytes;

    public FichaDraftValidator(ObjectMapper mapper, long maximumDocumentBytes) {
        if (maximumDocumentBytes <= 0) throw new IllegalArgumentException("Limite do documento precisa ser positivo.");
        this.mapper = mapper;
        this.maximumDocumentBytes = maximumDocumentBytes;
    }

    public void validateIdentity(Identity identity) {
        bounded(identity.pecaId(), "pecaId", 128);
        bounded(identity.marcaId(), "marcaId", 128);
        bounded(identity.colecaoId(), "colecaoId", 128);
    }

    public void validate(DraftWrite draft) {
        validateIdentity(draft.identity());
        if (draft.schemaVersion() != 1) throw new IllegalArgumentException("schemaVersion suportado é 1.");
        if (!draft.anexos().isEmpty())
            throw new IllegalArgumentException("Anexos permanecem no navegador nesta etapa; envie anexos vazio.");
        if (draft.revisaoEsperada() != null) bounded(draft.revisaoEsperada(), "expectedRevision", 128);
        var content = draft.conteudo();
        matching(content, "id", draft.identity().pecaId());
        matching(content, "marcaId", draft.identity().marcaId());
        matching(content, "colecaoId", draft.identity().colecaoId());
        rejectLocalMedia(content);
        for (var reference : draft.anexos()) {
            bounded(reference.anexoId(), "anexoId", 128);
            bounded(reference.categoria(), "categoria do anexo", 64);
            bounded(reference.nome(), "nome do anexo", 512);
            if (reference.mediaType() != null) bounded(reference.mediaType(), "mediaType", 256);
        }
        try {
            // Mede JSON UTF-8 sem coerção decimal ou reinterpretação dos campos de negócio.
            long bytes = mapper.writeValueAsString(content).getBytes(StandardCharsets.UTF_8).length;
            bytes += mapper.writeValueAsString(draft.anexos()).getBytes(StandardCharsets.UTF_8).length;
            if (bytes > maximumDocumentBytes)
                throw new FichaDocumentTooLargeException();
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Documento não pode ser serializado como JSON.");
        }
    }

    public void validateActor(Actor actor) {
        bounded(actor.usuarioId(), "usuarioId do servidor", 128);
        bounded(actor.nome(), "nome do usuário do servidor", 256);
    }

    public void validateSnapshot(Snapshot snapshot) {
        // Documento incompatível não vira uma ficha aprovada nem é substituído silenciosamente.
        validate(new DraftWrite(snapshot.identity(), snapshot.schemaVersion(),
                FichaPersistenceContracts.Operation.ATUALIZAR, snapshot.revisao(), snapshot.conteudo(), snapshot.anexos()));
        validateActor(snapshot.criadoPor());
        validateActor(snapshot.atualizadoPor());
    }

    public void validateCursor(String cursor) { bounded(cursor, "afterEventoId", 36); }

    private static void matching(JsonNode content, String field, String expected) {
        var value = content.get(field);
        if (value == null || !value.isTextual() || !value.textValue().equals(expected))
            throw new IllegalArgumentException("conteudo." + field + " precisa coincidir com a identidade da ficha.");
    }

    private static void bounded(String value, String field, int length) {
        if (value == null || value.isBlank() || value.length() > length
                || edgeSpace(value)
                || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(field + " inválido; informe texto de até " + length + " caracteres.");
    }

    private static boolean edgeSpace(String value) {
        if (value.isEmpty()) return false;
        return checkingSpace(value.codePointAt(0)) || checkingSpace(value.codePointBefore(value.length()));
    }

    private static boolean checkingSpace(int codePoint) {
        // Java strip não inclui NBSP/BOM. Alinha a checagem ao trim do navegador,
        // sem normalizar IDs ou modificar o conteúdo enviado.
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == 0xFEFF;
    }

    private static String mediaPrefixForChecking(String value) {
        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (!checkingSpace(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        return value.substring(start).toLowerCase(Locale.ROOT);
    }

    private static void rejectLocalMedia(JsonNode node) {
        if (node.isBinary() || node.isPojo()) throw new IllegalArgumentException("Use referências de anexos persistidos, não binários.");
        if (node.isTextual()) {
            var value = mediaPrefixForChecking(node.textValue());
            if (value.startsWith("blob:") || value.startsWith("data:"))
                throw new IllegalArgumentException("Anexo local não foi transferido; o rascunho não foi salvo no servidor.");
        }
        node.elements().forEachRemaining(FichaDraftValidator::rejectLocalMedia);
    }
}
