package com.modaflow.backend.ficha.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** SQL/commit são simulados; nenhum driver, socket, schema ou banco é aberto. */
class FichaSqlRepositoryTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final TrackingTransactions manager = new TrackingTransactions();
    private final Identity identity = new Identity("peca-1", "1", "colecao-1");
    private final Instant now = Instant.parse("2026-10-07T16:00:00.123456Z");
    private final Actor actor = new Actor("7", "Usuário atual");
    private List<Snapshot> rows = List.of();
    private FichaSqlRepository repository;

    @BeforeEach void setup() {
        repository = new FichaSqlRepository(jdbc, new TransactionTemplate(manager), mapper, new FichaDraftValidator(mapper, 100_000));
        when(jdbc.queryForMap(eq(FichaSqlRepository.CONNECTION_CHECK), any(SqlParameterSource.class)))
                .thenReturn(Map.of("database_name", "HOMOLOG_COBRANCA", "server_address", "192.168.1.6", "server_port", 1433));
        doAnswer(invocation -> rows).when(jdbc).query(anyString(), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<Object>>any());
        when(jdbc.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
    }

    private ServerWrite command(String expectedRevision) {
        var content = mapper.createObjectNode().put("id", identity.pecaId()).put("marcaId", identity.marcaId())
                .put("colecaoId", identity.colecaoId()).put("preco", "14.97991234").put("consumo", "0.00000001");
        return new ServerWrite(new DraftWrite(identity, 1, expectedRevision == null ? Operation.CRIAR : Operation.ATUALIZAR,
                expectedRevision, content, List.of()), "new-revision", now, actor);
    }

    private Snapshot existing(String revision) {
        return new Snapshot(identity, 1, revision, command(null).draft().conteudo(), List.of(), now.minusSeconds(60),
                new Actor("3", "Autor original"), now.minusSeconds(30), new Actor("4", "Editor anterior"));
    }

    @Test void createCommitsDocumentAndAuditTogetherWithLockAndNoErpStatements() throws Exception {
        var after = repository.persistWithAudit(command(null));
        assertThat(after.criadoPor()).isEqualTo(actor);
        assertThat(manager.commits).isEqualTo(1);
        assertThat(manager.rollbacks).isZero();
        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, times(2)).update(sql.capture(), parameters.capture());
        assertThat(sql.getAllValues().getFirst()).contains("INSERT INTO modaflow.ficha_documento", "N'[]'");
        assertThat(sql.getAllValues().getLast()).contains("INSERT INTO modaflow.ficha_auditoria");
        assertThat(parameters.getAllValues().getFirst().getValue("content").toString()).contains("14.97991234", "0.00000001");
        assertThat(parameters.getAllValues().getLast().getValue("before")).isNull();
        var auditSnapshot = mapper.readValue(parameters.getAllValues().getLast().getValue("after").toString(), Snapshot.class);
        assertThat(auditSnapshot).isEqualTo(after);
        verify(jdbc).query(contains("WITH (UPDLOCK,HOLDLOCK)"), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Snapshot>>any());
    }

    @Test void existingCreateMissingUpdateAndStaleUpdateAllConflictWithoutWrites() {
        rows = List.of(existing("current-revision"));
        assertThatThrownBy(() -> repository.persistWithAudit(command(null))).isInstanceOf(FichaRevisionConflictException.class);
        assertThatThrownBy(() -> repository.persistWithAudit(command("stale-revision"))).isInstanceOf(FichaRevisionConflictException.class);
        rows = List.of();
        assertThatThrownBy(() -> repository.persistWithAudit(command("current-revision"))).isInstanceOf(FichaRevisionConflictException.class);
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
        assertThat(manager.commits).isZero();
        assertThat(manager.rollbacks).isEqualTo(3);
    }

    @Test void updateUsesExpectedRevisionAndIndependentBeforeSnapshot() throws Exception {
        rows = List.of(existing("current-revision"));
        var before = rows.getFirst();
        var after = repository.persistWithAudit(command("current-revision"));
        assertThat(after.criadoPor()).isEqualTo(before.criadoPor());
        assertThat(after.criadoEm()).isEqualTo(before.criadoEm());
        assertThat(after.atualizadoPor()).isEqualTo(actor);
        var sql = ArgumentCaptor.forClass(String.class);
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, times(2)).update(sql.capture(), parameters.capture());
        assertThat(sql.getAllValues().getFirst()).contains("UPDATE modaflow.ficha_documento", "AND revisao=:expected");
        assertThat(parameters.getAllValues().getFirst().getValue("expected")).isEqualTo("current-revision");
        assertThat(mapper.readValue(parameters.getAllValues().getLast().getValue("before").toString(), Snapshot.class)).isEqualTo(before);
    }

    @Test void auditFailureRollsBackDocumentInsteadOfReturningSuccess() {
        when(jdbc.update(contains("INSERT INTO modaflow.ficha_auditoria"), any(SqlParameterSource.class)))
                .thenThrow(new DataIntegrityViolationException("simulated audit failure"));
        assertThatThrownBy(() -> repository.persistWithAudit(command(null))).isInstanceOf(DataIntegrityViolationException.class);
        verify(jdbc).update(contains("INSERT INTO modaflow.ficha_documento"), any(SqlParameterSource.class));
        assertThat(manager.commits).isZero();
        assertThat(manager.rollbacks).isEqualTo(1);
    }

    @Test void conditionalUpdateWithNoAffectedRowConflictsAndDoesNotAudit() {
        rows = List.of(existing("current-revision"));
        when(jdbc.update(contains("UPDATE modaflow.ficha_documento"), any(SqlParameterSource.class))).thenReturn(0);
        assertThatThrownBy(() -> repository.persistWithAudit(command("current-revision"))).isInstanceOf(FichaRevisionConflictException.class);
        verify(jdbc, never()).update(contains("INSERT INTO modaflow.ficha_auditoria"), any(SqlParameterSource.class));
        assertThat(manager.rollbacks).isEqualTo(1);
    }

    @Test void guardRejectsWrongDatabaseAddressOrPortBeforeDocumentReadOrWrite() {
        for (var actual : List.<Map<String, Object>>of(
                Map.of("database_name", "KINGEJOE", "server_address", "192.168.1.6", "server_port", 1433),
                Map.of("database_name", "HOMOLOG_COBRANCA", "server_address", "192.168.1.3", "server_port", 1433),
                Map.of("database_name", "HOMOLOG_COBRANCA", "server_address", "192.168.1.6", "server_port", 1400))) {
            when(jdbc.queryForMap(eq(FichaSqlRepository.CONNECTION_CHECK), any(SqlParameterSource.class))).thenReturn(actual);
            assertThatThrownBy(() -> repository.find(identity)).isInstanceOf(FichaDraftConnectionException.class);
            assertThatThrownBy(() -> repository.persistWithAudit(command(null))).isInstanceOf(FichaDraftConnectionException.class);
        }
        verify(jdbc, never()).query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any());
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
    }

    @Test void utcCalendarIsExplicitWhenBindingServerTimestamp() throws Exception {
        repository.persistWithAudit(command(null));
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).update(contains("INSERT INTO modaflow.ficha_documento"), parameters.capture());
        var statement = mock(PreparedStatement.class);
        ((SqlTypeValue) parameters.getValue().getValue("updated")).setTypeValue(statement, 3, Types.TIMESTAMP, null);
        var timestamp = ArgumentCaptor.forClass(Timestamp.class);
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(statement).setTimestamp(eq(3), timestamp.capture(), calendar.capture());
        assertThat(timestamp.getValue().toInstant()).isEqualTo(now);
        assertThat(calendar.getValue().getTimeZone().getID()).isEqualTo("UTC");
    }

    @Test void historyCursorIsResolvedInTheSameIdentityAndOrderedByTimestampThenId() {
        var cursor = "f7f702b2-0032-46ed-a9a5-f05b1b7aad84";
        doAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.contains("SELECT registrado_em") ? List.of(now) : List.of();
        }).when(jdbc).query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Object>>any());
        assertThat(repository.list(identity, cursor, 50)).isEmpty();
        var parameters = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(contains("SELECT registrado_em"), parameters.capture(), ArgumentMatchers.<RowMapper<Instant>>any());
        assertThat(parameters.getValue().getValue("marca")).isEqualTo("1");
        assertThat(parameters.getValue().getValue("colecao")).isEqualTo("colecao-1");
        assertThat(parameters.getValue().getValue("peca")).isEqualTo("peca-1");
        verify(jdbc).query(contains("ORDER BY registrado_em ASC,evento_id ASC"), any(SqlParameterSource.class),
                ArgumentMatchers.<RowMapper<AuditEntry>>any());
    }

    @Test void storedSnapshotWithDifferentContentIdentityFailsClosed() throws Exception {
        var result = mock(ResultSet.class);
        when(result.getString("peca_id")).thenReturn("peca-1");
        when(result.getString("marca_id")).thenReturn("1");
        when(result.getString("colecao_id")).thenReturn("colecao-1");
        when(result.getInt("schema_version")).thenReturn(1);
        when(result.getString("revisao")).thenReturn("current-revision");
        when(result.getString("conteudo_json")).thenReturn("{\"id\":\"wrong\",\"marcaId\":\"1\",\"colecaoId\":\"colecao-1\"}");
        when(result.getString("anexos_json")).thenReturn("[]");
        when(result.getString("criado_por_id")).thenReturn("7");
        when(result.getString("atualizado_por_id")).thenReturn("7");
        when(result.getString("criado_por_nome")).thenReturn("Usuário");
        when(result.getString("atualizado_por_nome")).thenReturn("Usuário");
        when(result.getTimestamp(anyString(), any(Calendar.class))).thenReturn(Timestamp.from(now));
        doAnswer(invocation -> {
            RowMapper<Snapshot> rowMapper = invocation.getArgument(2);
            return List.of(rowMapper.mapRow(result, 0));
        }).when(jdbc).query(anyString(), any(SqlParameterSource.class), ArgumentMatchers.<RowMapper<Snapshot>>any());
        assertThatThrownBy(() -> repository.find(identity)).isInstanceOf(org.springframework.dao.DataRetrievalFailureException.class);
        verify(jdbc, never()).update(anyString(), any(SqlParameterSource.class));
        var calendar = ArgumentCaptor.forClass(Calendar.class);
        verify(result).getTimestamp(eq("criado_em"), calendar.capture());
        assertThat(calendar.getValue().getTimeZone().getID()).isEqualTo("UTC");
    }

    private static final class TrackingTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
