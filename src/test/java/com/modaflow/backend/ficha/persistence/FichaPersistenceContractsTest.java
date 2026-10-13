package com.modaflow.backend.ficha.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.*;
import static org.junit.jupiter.api.Assertions.*;

/** Contratos puros: não carrega Spring, banco, armazenamento ou servidor HTTP. */
class FichaPersistenceContractsTest {
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final Identity ID = new Identity("peca-uuid", "marca-opaca", "linx-colecao-025");
    private static final Actor AUTHOR = new Actor("usuario-servidor", "Autor do servidor");
    private static final Instant NOW = Instant.parse("2026-10-07T15:00:00Z");

    private DraftWrite draft(Operation operation, String expected, JsonNode content,
                             List<AttachmentReference> attachments) {
        return new DraftWrite(ID, 1, operation, expected, content, attachments);
    }

    private Snapshot snapshot(Identity identity, String revision, JsonNode content,
                              List<AttachmentReference> attachments) {
        return new Snapshot(identity, 1, revision, content, attachments, NOW, AUTHOR, NOW, AUTHOR);
    }

    private AttachmentReference attachment(Identity identity) {
        return new AttachmentReference("anexo-servidor", identity, "referencias", "imagem.png", "image/png", 10);
    }

    @Test void aceitaRascunhoParcialSemObrigatoriedadeDeNegocioNemConversaoMonetaria() {
        var content = JSON.objectNode().put("nomeProduto", "").put("precoManual", "");
        content.putArray("tamanhos");
        var request = draft(Operation.CRIAR, null, content, List.of());
        assertEquals("", request.conteudo().get("precoManual").asText());
        assertEquals(0, request.conteudo().get("tamanhos").size());
        assertDoesNotThrow(() -> draft(Operation.CRIAR, null, JSON.objectNode(), List.of()));
    }

    @Test void preservaPrecosQuantidadesEAutorLegadoSemInferirCadastroFiscalOuAutoriaAutenticada() {
        var content = JSON.objectNode()
                .put("precoManual", "0,12345678")
                .put("consumo", "1.12345678")
                .put("peso", "0,00000001");
        content.putObject("historicoLocal").put("responsavel", "Autor do navegador legado");
        var request = draft(Operation.CRIAR, null, content, List.of());
        var command = new ServerWrite(request, "revisao-servidor", NOW, AUTHOR);
        var saved = snapshot(ID, command.novaRevisao(), command.draft().conteudo(), List.of());
        assertEquals("0,12345678", saved.conteudo().get("precoManual").asText());
        assertTrue(saved.conteudo().get("precoManual").isTextual());
        assertEquals("1.12345678", saved.conteudo().get("consumo").asText());
        assertEquals("0,00000001", saved.conteudo().get("peso").asText());
        assertEquals("Autor do navegador legado", saved.conteudo().get("historicoLocal").get("responsavel").asText());
        assertEquals(AUTHOR, saved.atualizadoPor());
        assertFalse(saved.conteudo().has("NCM"));
        assertFalse(saved.conteudo().has("empresa"));
    }

    @Test void preservaIdentidadeOpacaSemDeduzirGriffeOuConverterUuidParaLong() {
        var identity = new Identity("  id-opaco  ", "marca-A", "linx-KING&JOE-025");
        assertEquals("  id-opaco  ", identity.pecaId());
        assertEquals("linx-KING&JOE-025", identity.colecaoId());
        assertNotEquals(identity, new Identity("  id-opaco  ", "marca-B", "linx-KING&JOE-025"));
        assertThrows(IllegalArgumentException.class, () -> new Identity("", "m", "c"));
    }

    @Test void criacaoExigeAusenciaEAtualizacaoExigeRevisaoEsperada() {
        assertThrows(IllegalArgumentException.class,
                () -> draft(Operation.CRIAR, "r1", JSON.objectNode(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> draft(Operation.ATUALIZAR, null, JSON.objectNode(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> draft(Operation.ATUALIZAR, " ", JSON.objectNode(), List.of()));
        assertEquals("r1", draft(Operation.ATUALIZAR, "r1", JSON.objectNode(), List.of()).revisaoEsperada());
    }

    @Test void contratoNaoAceitaVersaoDeSchemaInvalidaOuConteudoNaoObjeto() {
        assertThrows(IllegalArgumentException.class,
                () -> new DraftWrite(ID, 0, Operation.CRIAR, null, JSON.objectNode(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> draft(Operation.CRIAR, null, JSON.arrayNode(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> draft(Operation.CRIAR, null, null, List.of()));
    }

    @Test void copiaConteudoNaEntradaENaSaidaSemMutarOOriginal() {
        var content = JSON.objectNode();
        content.putObject("geral").put("nome", "original");
        var request = draft(Operation.CRIAR, null, content, List.of());
        ((ObjectNode) content.get("geral")).put("nome", "alterado fora");
        ((ObjectNode) request.conteudo().get("geral")).put("nome", "alterado no retorno");
        assertEquals("original", request.conteudo().get("geral").get("nome").asText());

        var saved = snapshot(ID, "r1", content, List.of());
        ((ObjectNode) saved.conteudo().get("geral")).put("nome", "outro");
        assertEquals("alterado fora", saved.conteudo().get("geral").get("nome").asText());
    }

    @Test void copiaReferenciasERejeitaAnexosDeOutraColecaoMarcaOuPeca() {
        var input = new ArrayList<>(List.of(attachment(ID)));
        var request = draft(Operation.CRIAR, null, JSON.objectNode(), input);
        input.clear();
        assertEquals(1, request.anexos().size());
        assertThrows(UnsupportedOperationException.class, () -> request.anexos().clear());
        for (var other : List.of(new Identity("outra", ID.marcaId(), ID.colecaoId()),
                new Identity(ID.pecaId(), "outra", ID.colecaoId()),
                new Identity(ID.pecaId(), ID.marcaId(), "outra"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> draft(Operation.CRIAR, null, JSON.objectNode(), List.of(attachment(other))));
        }
        assertThrows(IllegalArgumentException.class,
                () -> draft(Operation.CRIAR, null, JSON.objectNode(), List.of(attachment(ID), attachment(ID))));
    }

    @Test void naoConverteBlobUrlEmReferenciaNemIncluiBinariosNoJson() {
        assertThrows(IllegalArgumentException.class,
                () -> new AttachmentReference("blob:http://localhost/id", ID, "referencias", "foto", "image/png", 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AttachmentReference("data:image/png;base64,aA==", ID, "referencias", "foto", "image/png", 1));
        var binaryContent = JSON.objectNode();
        binaryContent.putArray("arquivos").add(JSON.binaryNode(new byte[] { 1, 2 }));
        assertThrows(IllegalArgumentException.class,
                () -> draft(Operation.CRIAR, null, binaryContent, List.of()));
        var legacy = draft(Operation.CRIAR, null,
                JSON.objectNode().put("imagemLegada", "blob:http://localhost/pendente"), List.of());
        assertEquals("blob:http://localhost/pendente", legacy.conteudo().get("imagemLegada").asText());
        assertTrue(legacy.anexos().isEmpty()); // A prévia futura deve sinalizar a URL; não houve transporte.
    }

    @Test void comandoInternoExigeNovaRevisaoHorarioEAutorDoServidor() {
        var request = draft(Operation.ATUALIZAR, "r1", JSON.objectNode(), List.of());
        var command = new ServerWrite(request, "r2", NOW, AUTHOR);
        assertEquals("r2", command.novaRevisao());
        assertEquals(AUTHOR, command.actor());
        assertThrows(IllegalArgumentException.class, () -> new ServerWrite(request, "r1", NOW, AUTHOR));
        assertThrows(NullPointerException.class, () -> new ServerWrite(request, "r2", null, AUTHOR));
        assertThrows(NullPointerException.class, () -> new ServerWrite(request, "r2", NOW, null));
    }

    @Test void snapshotPreservaCriacaoENaoPermiteDataDeAtualizacaoAnterior() {
        var saved = snapshot(ID, "r1", JSON.objectNode(), List.of());
        assertEquals(AUTHOR, saved.criadoPor());
        assertEquals(NOW, saved.criadoEm());
        assertThrows(IllegalArgumentException.class,
                () -> new Snapshot(ID, 1, "r1", JSON.objectNode(), List.of(), NOW, AUTHOR, NOW.minusSeconds(1), AUTHOR));
    }

    @Test void auditoriaPreservaAntesDepoisDaMesmaIdentidadeESemReescreverAnterior() {
        var before = snapshot(ID, "r1", JSON.objectNode().put("nome", ""), List.of());
        var after = snapshot(ID, "r2", JSON.objectNode().put("nome", "Produto informado"), List.of());
        var update = new AuditEntry("evento-servidor", Operation.ATUALIZAR, before, after);
        assertEquals("", update.antes().conteudo().get("nome").asText());
        assertEquals("Produto informado", update.depois().conteudo().get("nome").asText());
        assertDoesNotThrow(() -> new AuditEntry("criada", Operation.CRIAR, null, before));
        assertThrows(IllegalArgumentException.class, () -> new AuditEntry("erro", Operation.CRIAR, before, after));
        assertThrows(IllegalArgumentException.class, () -> new AuditEntry("erro", Operation.ATUALIZAR, before, before));
        var wrong = snapshot(new Identity(ID.pecaId(), ID.marcaId(), "outra"), "r2", JSON.objectNode(), List.of());
        assertThrows(IllegalArgumentException.class, () -> new AuditEntry("erro", Operation.ATUALIZAR, before, wrong));
    }

    @Test void conflitoTestavelDistingueCriacaoExistenteAtualizacaoAusenteERevisaoAntiga() {
        assertDoesNotThrow(() -> FichaRevisionConflictException.requireMatch(ID, null, null));
        assertDoesNotThrow(() -> FichaRevisionConflictException.requireMatch(ID, "r1", "r1"));
        assertThrows(FichaRevisionConflictException.class,
                () -> FichaRevisionConflictException.requireMatch(ID, null, "r1"));
        assertThrows(FichaRevisionConflictException.class,
                () -> FichaRevisionConflictException.requireMatch(ID, "r1", null));
        var conflict = assertThrows(FichaRevisionConflictException.class,
                () -> FichaRevisionConflictException.requireMatch(ID, "r1", "r2"));
        assertEquals(ID, conflict.identity());
        assertEquals("r1", conflict.revisaoEsperada());
        assertEquals("r2", conflict.revisaoAtual());
    }
}
