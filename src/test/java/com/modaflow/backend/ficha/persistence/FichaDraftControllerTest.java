package com.modaflow.backend.ficha.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.common.exception.TenantAccessDeniedException;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.*;
import com.modaflow.backend.security.TenantAccessValidator;
import com.modaflow.backend.security.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FichaDraftControllerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final FichaRepositoryPort repository = mock(FichaRepositoryPort.class);
    private final FichaAuditPort audit = mock(FichaAuditPort.class);
    private final TenantAccessValidator access = mock(TenantAccessValidator.class);
    private final Identity identity = new Identity("peca-1", "1", "colecao-1");
    private final Instant now = Instant.parse("2026-10-07T16:00:00Z");
    private MockMvc mvc;
    private final String path = "/fichas/rascunhos/colecao-1/pecas/peca-1";
    private String request() {
        return """
            {"schemaVersion":1,"expectedRevision":null,"anexos":[],
             "conteudo":{"id":"peca-1","marcaId":"1","colecaoId":"colecao-1", "preco":"14.97991234",
                         "historico":[{"usuario":"Autor legado"}],"nome":""},
             "actor":{"usuarioId":"forjado","nome":"Forjado"},"criadoPor":{"usuarioId":"forjado"}}
            """;
    }

    @BeforeEach void setup() {
        var user = new UserPrincipal(7L, "Usuário autenticado", "test@example.test", "not-a-password", List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        when(access.requireTenantForCurrentUser()).thenReturn(1L);
        var service = new FichaDraftService(repository, audit, new FichaDraftValidator(mapper, 100_000), Clock.fixed(now, ZoneOffset.UTC));
        mvc = MockMvcBuilders.standaloneSetup(new FichaDraftController(service, access)).build();
        when(repository.persistWithAudit(any())).thenAnswer(invocation -> {
            ServerWrite command = invocation.getArgument(0);
            return new Snapshot(command.draft().identity(), 1, command.novaRevisao(), command.draft().conteudo(), List.of(),
                    command.registradoEm(), command.actor(), command.registradoEm(), command.actor());
        });
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void saveUsesServerActorAndDateNotForgedOrImportedActor() throws Exception {
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isOk()).andExpect(jsonPath("criadoPor.usuarioId").value("7"))
                .andExpect(jsonPath("criadoPor.nome").value("Usuário autenticado"))
                .andExpect(jsonPath("conteudo.preco").value("14.97991234"));
        var capture = ArgumentCaptor.forClass(ServerWrite.class);
        verify(repository).persistWithAudit(capture.capture());
        assertThat(capture.getValue().actor()).isEqualTo(new Actor("7", "Usuário autenticado"));
        assertThat(capture.getValue().registradoEm()).isEqualTo(now);
        assertThat(capture.getValue().novaRevisao()).hasSize(36);
    }

    @Test void rejectsWrongBrandBeforePersistAndDeniedTenantBeforeReadOrHistory() throws Exception {
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(request().replace("\"marcaId\":\"1\"", "\"marcaId\":\"2\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_DRAFT"));
        verify(repository, never()).persistWithAudit(any());
        when(access.requireTenantForCurrentUser()).thenThrow(new TenantAccessDeniedException("denied"));
        mvc.perform(get(path)).andExpect(status().isForbidden());
        mvc.perform(get(path + "/historico")).andExpect(status().isForbidden());
        verify(repository, never()).find(any());
        verifyNoInteractions(audit);
    }

    @Test void rejectsUnauthenticatedCallsBeforeTenantOrRepository() throws Exception {
        SecurityContextHolder.clearContext();
        mvc.perform(get(path)).andExpect(status().isForbidden());
        verifyNoInteractions(access, repository);
    }

    @Test void reportsNotFoundAndRevisionConflictWithoutSuccessFallback() throws Exception {
        when(repository.find(identity)).thenReturn(Optional.empty());
        mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(jsonPath("code").value("DRAFT_NOT_FOUND"));
        doThrow(new FichaRevisionConflictException(identity, null, "server-revision"))
                .when(repository).persistWithAudit(any());
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(request()))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("REVISION_CONFLICT"))
                .andExpect(jsonPath("currentRevision").value("server-revision"));
    }

    @Test void refusesEmbeddedBrowserMediaAndMissingAttachmentsArray() throws Exception {
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(request().replace("\"preco\":\"14.97991234\"", "\"imagemUrl\":\"data:image/png;base64,AAA\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(request().replace("\"anexos\":[],", "")))
                .andExpect(status().isBadRequest());
        verify(repository, never()).persistWithAudit(any());
    }

    @Test void historyIsScopedAndReturnsDirectListWithDefaultLimit() throws Exception {
        when(audit.list(identity, null, 50)).thenReturn(List.of());
        mvc.perform(get(path + "/historico")).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(audit).list(identity, null, 50);
        mvc.perform(get(path + "/historico").param("limit", "101")).andExpect(status().isBadRequest());
        mvc.perform(get(path + "/historico").param("limit", "not-a-number")).andExpect(status().isBadRequest());
    }

    @Test void browserWhitespaceCannotBypassLocalMediaRejectionBeforePersisting() throws Exception {
        for (var space : List.of("\u00a0", "\ufeff", "\u2007", "\u202f")) {
            for (var media : List.of("data:image/png;base64,AAA", "blob:https://site/uuid")) {
                var body = request().replace("\"preco\":\"14.97991234\"", "\"imagemUrl\":\"" + space + media + "\"");
                mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("INVALID_DRAFT"));
            }
        }
        verify(repository, never()).persistWithAudit(any());
    }
}
