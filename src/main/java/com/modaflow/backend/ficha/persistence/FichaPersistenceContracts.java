package com.modaflow.backend.ficha.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Contratos de rascunho, independentes de banco, schema, transporte e catálogo ERP.
 * Não são entidades JPA nem registram serviços/controllers. Nenhum campo de negócio
 * é obrigatório para salvar um rascunho; obrigatoriedade de entrega é outra regra.
 */
public final class FichaPersistenceContracts {
    private FichaPersistenceContracts() { }

    public enum Operation { CRIAR, ATUALIZAR }

    /** IDs opacos do portal, não conversões de UUID/referência Linx para Long. */
    public record Identity(String pecaId, String marcaId, String colecaoId) {
        public Identity {
            required(pecaId, "pecaId");
            required(marcaId, "marcaId");
            required(colecaoId, "colecaoId");
        }
    }

    /** Preenchido pelo servidor a partir do usuário autenticado, nunca pelo rascunho. */
    public record Actor(String usuarioId, String nome) {
        public Actor {
            required(usuarioId, "usuarioId");
            required(nome, "nome");
        }
    }

    /**
     * Referência de arquivo já armazenado e pertencente à mesma ficha. Não contém
     * URL, bytes ou localização física. O ID não pode ser uma URL local do navegador.
     * Tipo, limites, categorias e integridade serão confirmados no futuro adaptador.
     */
    public record AttachmentReference(
            String anexoId, Identity identity, String categoria, String nome,
            String mediaType, long tamanho) {
        public AttachmentReference {
            required(anexoId, "anexoId");
            var normalized = anexoId.strip().toLowerCase(java.util.Locale.ROOT);
            if (normalized.startsWith("blob:") || normalized.startsWith("data:"))
                throw new IllegalArgumentException("Uma URL local não é um ID de anexo persistido.");
            Objects.requireNonNull(identity, "identity");
            required(categoria, "categoria");
            required(nome, "nome");
            if (tamanho < 0) throw new IllegalArgumentException("Tamanho do anexo não pode ser negativo.");
        }
    }

    /**
     * Conteúdo editável. CRIAR exige ausência; ATUALIZAR exige revisão anterior.
     * Autor, nova revisão e horário não são enviados pelo cliente neste contrato.
     * Identidade e referências ainda exigem autorização/releitura no servidor.
     */
    public record DraftWrite(
            Identity identity, int schemaVersion, Operation operation,
            String revisaoEsperada, JsonNode conteudo, List<AttachmentReference> anexos) {
        public DraftWrite {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(operation, "operation");
            requireSchemaVersion(schemaVersion);
            if (operation == Operation.CRIAR && revisaoEsperada != null)
                throw new IllegalArgumentException("Criação exige ausência de revisão anterior.");
            if (operation == Operation.ATUALIZAR) required(revisaoEsperada, "revisaoEsperada");
            conteudo = copyContent(conteudo);
            anexos = copyReferences(identity, anexos);
        }

        @Override public JsonNode conteudo() { return conteudo.deepCopy(); }
    }

    /** Comando interno: metadados atribuídos pelo servidor, não DTO HTTP público. */
    public record ServerWrite(DraftWrite draft, String novaRevisao, Instant registradoEm, Actor actor) {
        public ServerWrite {
            Objects.requireNonNull(draft, "draft");
            required(novaRevisao, "novaRevisao");
            if (novaRevisao.equals(draft.revisaoEsperada()))
                throw new IllegalArgumentException("A revisão confirmada precisa ser nova.");
            Objects.requireNonNull(registradoEm, "registradoEm");
            Objects.requireNonNull(actor, "actor");
        }
    }

    /** Retorno somente após persistência durável confirmada pelo futuro adaptador. */
    public record Snapshot(
            Identity identity, int schemaVersion, String revisao, JsonNode conteudo,
            List<AttachmentReference> anexos, Instant criadoEm, Actor criadoPor,
            Instant atualizadoEm, Actor atualizadoPor) {
        public Snapshot {
            Objects.requireNonNull(identity, "identity");
            requireSchemaVersion(schemaVersion);
            required(revisao, "revisao");
            conteudo = copyContent(conteudo);
            anexos = copyReferences(identity, anexos);
            Objects.requireNonNull(criadoEm, "criadoEm");
            Objects.requireNonNull(criadoPor, "criadoPor");
            Objects.requireNonNull(atualizadoEm, "atualizadoEm");
            Objects.requireNonNull(atualizadoPor, "atualizadoPor");
            if (atualizadoEm.isBefore(criadoEm))
                throw new IllegalArgumentException("Atualização não pode anteceder criação.");
        }

        @Override public JsonNode conteudo() { return conteudo.deepCopy(); }
    }

    /** Antes/depois da mesma identidade. Autor/data são os da confirmação no servidor. */
    public record AuditEntry(String eventoId, Operation operation, Snapshot antes, Snapshot depois) {
        public AuditEntry {
            required(eventoId, "eventoId");
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(depois, "depois");
            if (operation == Operation.CRIAR && antes != null)
                throw new IllegalArgumentException("Auditoria de criação não possui estado anterior.");
            if (operation == Operation.ATUALIZAR) {
                Objects.requireNonNull(antes, "antes");
                if (!antes.identity().equals(depois.identity()))
                    throw new IllegalArgumentException("Auditoria não pode misturar peças, marcas ou coleções.");
                if (antes.revisao().equals(depois.revisao()))
                    throw new IllegalArgumentException("Auditoria de atualização exige nova revisão.");
            }
        }
    }

    static String required(String value, String field) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(field + " precisa ser informado.");
        return value;
    }

    private static void requireSchemaVersion(int schemaVersion) {
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion precisa ser positivo.");
    }

    private static List<AttachmentReference> copyReferences(
            Identity identity, List<AttachmentReference> references) {
        Objects.requireNonNull(references, "anexos");
        var copied = List.copyOf(references);
        var ids = new java.util.HashSet<String>();
        for (var reference : copied) {
            if (!identity.equals(reference.identity()))
                throw new IllegalArgumentException("Anexo pertence a outra peça, marca ou coleção.");
            if (!ids.add(reference.anexoId()))
                throw new IllegalArgumentException("Referências de anexo não podem ser repetidas.");
        }
        return copied;
    }

    private static JsonNode copyContent(JsonNode content) {
        if (content == null || !content.isObject())
            throw new IllegalArgumentException("Conteúdo do rascunho precisa ser um objeto JSON.");
        rejectBinaryNodes(content);
        return content.deepCopy();
    }

    private static void rejectBinaryNodes(JsonNode node) {
        if (node.isBinary() || node.isPojo())
            throw new IllegalArgumentException("Binários/objetos Java não pertencem ao JSON da ficha; use anexos separados.");
        node.elements().forEachRemaining(FichaPersistenceContracts::rejectBinaryNodes);
    }
}
