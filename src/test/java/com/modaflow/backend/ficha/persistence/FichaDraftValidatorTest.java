package com.modaflow.backend.ficha.persistence;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class FichaDraftValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Identity identity = new Identity("peca-1", "1", "colecao-1");
    private final FichaDraftValidator validator = new FichaDraftValidator(mapper, 100_000);

    private ObjectNode content() {
        return mapper.createObjectNode().put("id", "peca-1").put("marcaId", "1").put("colecaoId", "colecao-1");
    }
    private DraftWrite draft(ObjectNode content) { return new DraftWrite(identity, 1, Operation.CRIAR, null, content, List.of()); }

    @Test void acceptsPartialBusinessDataAndPreservesEightDecimalStrings() {
        var content = content().put("nome", "").put("preco", "14.97991234").put("consumo", "0.00000001");
        var draft = draft(content);
        assertThatCode(() -> validator.validate(draft)).doesNotThrowAnyException();
        assertThat(draft.conteudo().path("preco").textValue()).isEqualTo("14.97991234");
        assertThat(draft.conteudo().path("consumo").textValue()).isEqualTo("0.00000001");
    }

    @Test void rejectsIdentityMismatchOrMissingStringIdentity() {
        assertThatThrownBy(() -> validator.validate(draft(content().put("colecaoId", "outra"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("colecaoId");
        assertThatThrownBy(() -> validator.validate(draft(content().put("marcaId", 1))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("marcaId");
        assertThatThrownBy(() -> validator.validate(draft(mapper.createObjectNode())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("id");
    }

    @Test void rejectsSpaceAliasesAndOversizedIdsWithoutTrimmingThem() {
        for (var pecaId : List.of("peca-1 ", " peca-1", "peca-1\u00a0", "x".repeat(129)))
            assertThatThrownBy(() -> validator.validateIdentity(new Identity(pecaId, "1", "colecao-1")))
                    .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(new DraftWrite(identity, 1, Operation.ATUALIZAR,
                "revision ", content(), List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateCursor(" f7f702b2-0032-46ed-a9a5-f05b1b7aad84"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsBrowserWhitespaceOnIdentityRevisionAndCursorBoundariesWithoutNormalizing() {
        for (var space : List.of("\u00a0", "\ufeff", "\u2007", "\u202f")) {
            for (var id : List.of(space + "peca-1", "peca-1" + space)) {
                var identity = new Identity(id, "1", "colecao-1");
                assertThatThrownBy(() -> validator.validateIdentity(identity)).isInstanceOf(IllegalArgumentException.class);
                assertThat(identity.pecaId()).isEqualTo(id);
            }
            for (var revision : List.of(space + "revision", "revision" + space))
                assertThatThrownBy(() -> validator.validate(new DraftWrite(identity, 1, Operation.ATUALIZAR,
                        revision, content(), List.of()))).isInstanceOf(IllegalArgumentException.class);
            for (var cursor : List.of(space + "f7f702b2-0032-46ed-a9a5-f05b1b7aad84", "f7f702b2-0032-46ed-a9a5-f05b1b7aad84" + space))
                assertThatThrownBy(() -> validator.validateCursor(cursor)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void browserWhitespaceCannotConcealMediaAndNormalBusinessTextIsNotTrimmed() {
        for (var space : List.of("\u00a0", "\ufeff", "\u2007", "\u202f")) {
            for (var media : List.of("data:image/png;base64,AAA", "blob:https://site/uuid")) {
                var content = content();
                content.putArray("variantes").addObject().put("imagemUrl", space + media);
                var draft = draft(content);
                assertThatThrownBy(() -> validator.validate(draft)).isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Anexo local");
                assertThat(draft.conteudo().path("variantes").get(0).path("imagemUrl").textValue()).isEqualTo(space + media);
            }
            var draft = draft(content().put("descricao", space + "Texto manual" + space));
            assertThatCode(() -> validator.validate(draft)).doesNotThrowAnyException();
            assertThat(draft.conteudo().path("descricao").textValue()).isEqualTo(space + "Texto manual" + space);
        }
    }

    @Test void rejectsInlineMediaAtAnyDepthAndNonemptyAttachmentReferences() {
        for (var media : List.of("blob:https://site/uuid", "data:image/png;base64,AAA", " DATA:image/jpeg;base64,ABC")) {
            var content = content();
            content.putArray("variantes").addObject().put("imagemUrl", media);
            assertThatThrownBy(() -> validator.validate(draft(content)))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Anexo local");
        }
        var ref = new AttachmentReference("anexo-1", identity, "referencia", "foto.png", "image/png", 10);
        assertThatThrownBy(() -> validator.validate(new DraftWrite(identity, 1, Operation.CRIAR, null, content(), List.of(ref))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Anexos permanecem");
    }

    @Test void rejectsUnsupportedSchemaAndConfiguredSizeOverflow() {
        assertThatThrownBy(() -> validator.validate(new DraftWrite(identity, 2, Operation.CRIAR, null, content(), List.of())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> new FichaDraftValidator(mapper, 5).validate(draft(content())))
                .isInstanceOf(FichaDocumentTooLargeException.class);
        assertThatThrownBy(() -> new FichaDraftValidator(mapper, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void serverActorIsBoundedBeforeSql() {
        assertThatThrownBy(() -> validator.validateActor(new Actor("x".repeat(129), "Usuário")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validateActor(new Actor("1", "x".repeat(257))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
