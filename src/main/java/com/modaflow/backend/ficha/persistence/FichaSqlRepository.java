package com.modaflow.backend.ficha.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistência exclusiva em modaflow.* no homolog .6. Não lê/grava entidades do ERP.
 * Todos os SQLs são fixos/parametrizados e a confirmação engloba documento + auditoria.
 */
public final class FichaSqlRepository implements FichaRepositoryPort, FichaAuditPort {
    static final String CONNECTION_CHECK = """
        SELECT DB_NAME() AS database_name,
               CONVERT(varchar(64), CONNECTIONPROPERTY('local_net_address')) AS server_address,
               CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')) AS server_port
        """;
    private static final String KEY = "marca_id=:marca AND colecao_id=:colecao AND peca_id=:peca";
    private static final String COLUMNS = """
        marca_id, colecao_id, peca_id, schema_version, revisao, conteudo_json, anexos_json,
        criado_em, criado_por_id, criado_por_nome, atualizado_em, atualizado_por_id, atualizado_por_nome
        """;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final FichaDraftValidator validator;

    public FichaSqlRepository(NamedParameterJdbcTemplate jdbc, TransactionTemplate transaction, ObjectMapper mapper,
                              FichaDraftValidator validator) {
        this.jdbc = jdbc; this.transaction = transaction; this.mapper = mapper;
        this.validator = validator;
    }

    @Override public Optional<Snapshot> find(Identity identity) {
        validator.validateIdentity(identity);
        return Objects.requireNonNull(transaction.execute(status -> {
            requireHomologConnection();
            return findInsideTransaction(identity, false);
        }));
    }

    @Override public Snapshot persistWithAudit(ServerWrite command) {
        validator.validate(command.draft());
        validator.validateActor(command.actor());
        return Objects.requireNonNull(transaction.execute(status -> {
            requireHomologConnection();
            var draft = command.draft();
            // HOLDLOCK protege também a ausência da chave durante CRIAR, evitando create/create.
            var before = findInsideTransaction(draft.identity(), true).orElse(null);
            FichaRevisionConflictException.requireMatch(draft.identity(), draft.revisaoEsperada(),
                    before == null ? null : before.revisao());
            var after = new Snapshot(draft.identity(), draft.schemaVersion(), command.novaRevisao(),
                    draft.conteudo(), List.of(), before == null ? command.registradoEm() : before.criadoEm(),
                    before == null ? command.actor() : before.criadoPor(), command.registradoEm(), command.actor());
            var parameters = identityParameters(after.identity())
                    .addValue("schema", after.schemaVersion()).addValue("revision", after.revisao())
                    .addValue("content", json(after.conteudo()), Types.NVARCHAR)
                    .addValue("created", utcValue(after.criadoEm()), Types.TIMESTAMP)
                    .addValue("createdById", after.criadoPor().usuarioId())
                    .addValue("createdByName", after.criadoPor().nome())
                    .addValue("updated", utcValue(after.atualizadoEm()), Types.TIMESTAMP)
                    .addValue("updatedById", after.atualizadoPor().usuarioId())
                    .addValue("updatedByName", after.atualizadoPor().nome());
            int changed;
            if (before == null) {
                changed = jdbc.update("""
                    INSERT INTO modaflow.ficha_documento
                        (marca_id,colecao_id,peca_id,schema_version,revisao,conteudo_json,anexos_json,
                         criado_em,criado_por_id,criado_por_nome,atualizado_em,atualizado_por_id,atualizado_por_nome)
                    VALUES (:marca,:colecao,:peca,:schema,:revision,:content,N'[]',
                            :created,:createdById,:createdByName,:updated,:updatedById,:updatedByName)
                    """, parameters);
            } else {
                parameters.addValue("expected", draft.revisaoEsperada());
                changed = jdbc.update("""
                    UPDATE modaflow.ficha_documento
                       SET schema_version=:schema,revisao=:revision,conteudo_json=:content,anexos_json=N'[]',
                           atualizado_em=:updated,atualizado_por_id=:updatedById,atualizado_por_nome=:updatedByName
                     WHERE
                    """ + KEY + " AND revisao=:expected", parameters);
            }
            if (changed != 1) throw new FichaRevisionConflictException(draft.identity(), draft.revisaoEsperada(),
                    before == null ? null : before.revisao());
            var event = new AuditEntry(UUID.randomUUID().toString(), draft.operation(), before, after);
            int audited = jdbc.update("""
                INSERT INTO modaflow.ficha_auditoria
                    (evento_id,marca_id,colecao_id,peca_id,operacao,registrado_em,antes_json,depois_json)
                VALUES (:event,:marca,:colecao,:peca,:operation,:registered,:before,:after)
                """, identityParameters(after.identity()).addValue("event", event.eventoId())
                    .addValue("operation", event.operation().name())
                    .addValue("registered", utcValue(command.registradoEm()), Types.TIMESTAMP)
                    .addValue("before", before == null ? null : json(before), Types.NVARCHAR)
                    .addValue("after", json(after), Types.NVARCHAR));
            if (audited != 1) throw new DataRetrievalFailureException("Auditoria não foi confirmada.");
            // TransactionTemplate só devolve este resultado após commit bem-sucedido.
            return after;
        }));
    }

    @Override public List<AuditEntry> list(Identity identity, String afterEventoId, int limit) {
        validator.validateIdentity(identity);
        if (afterEventoId != null) validator.validateCursor(afterEventoId);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit precisa estar entre 1 e 100.");
        return Objects.requireNonNull(transaction.execute(status -> {
            requireHomologConnection();
            var parameters = identityParameters(identity).addValue("limit", limit);
            String cursorPredicate = "";
            if (afterEventoId != null) {
                // O cursor só pode ser resolvido na mesma identidade; UUID não é cronológico.
                var cursor = jdbc.query("SELECT registrado_em FROM modaflow.ficha_auditoria WHERE " + KEY
                                + " AND evento_id=:cursor", parameters.addValue("cursor", afterEventoId),
                        (row, number) -> utcInstant(row, "registrado_em"));
                if (cursor.size() != 1) throw new IllegalArgumentException("Cursor não pertence ao histórico desta ficha.");
                parameters.addValue("cursorTime", utcValue(cursor.getFirst()), Types.TIMESTAMP);
                cursorPredicate = " AND (registrado_em>:cursorTime OR (registrado_em=:cursorTime AND evento_id>:cursor))";
            }
            return jdbc.query("""
                SELECT TOP (:limit) evento_id,operacao,antes_json,depois_json
                  FROM modaflow.ficha_auditoria WHERE
                """ + KEY + cursorPredicate
                    + " ORDER BY registrado_em ASC,evento_id ASC", parameters,
                    (row, number) -> mapAudit(row, identity));
        }));
    }

    private Optional<Snapshot> findInsideTransaction(Identity identity, boolean lock) {
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM modaflow.ficha_documento"
                + (lock ? " WITH (UPDLOCK,HOLDLOCK)" : "") + " WHERE " + KEY,
                identityParameters(identity), (row, number) -> mapSnapshot(row));
        if (rows.size() > 1) throw new DataRetrievalFailureException("Identidade da ficha não é única.");
        return rows.stream().findFirst();
    }

    private void requireHomologConnection() {
        // Todos os acessos ocorrem dentro do TransactionTemplate com a conexão thread-bound.
        var actual = jdbc.queryForMap(CONNECTION_CHECK, new MapSqlParameterSource());
        var port = actual.get("server_port");
        if (!"HOMOLOG_COBRANCA".equals(actual.get("database_name"))
                || !"192.168.1.6".equals(actual.get("server_address"))
                || !(port instanceof Number number) || number.intValue() != 1433)
            throw new FichaDraftConnectionException();
    }

    private Snapshot mapSnapshot(ResultSet row) throws SQLException {
        try {
            var snapshot = new Snapshot(new Identity(row.getString("peca_id"), row.getString("marca_id"), row.getString("colecao_id")),
                    row.getInt("schema_version"), row.getString("revisao"), mapper.readTree(row.getString("conteudo_json")),
                    mapper.readValue(row.getString("anexos_json"), new TypeReference<List<AttachmentReference>>() {}),
                    utcInstant(row, "criado_em"), new Actor(row.getString("criado_por_id"), row.getString("criado_por_nome")),
                    utcInstant(row, "atualizado_em"), new Actor(row.getString("atualizado_por_id"), row.getString("atualizado_por_nome")));
            validator.validateSnapshot(snapshot);
            return snapshot;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new DataRetrievalFailureException("Documento persistido incompatível; não será sobrescrito.");
        }
    }

    private Snapshot readSnapshot(String json) {
        if (json == null) return null;
        try {
            var snapshot = mapper.readValue(json, Snapshot.class);
            validator.validateSnapshot(snapshot);
            return snapshot;
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new DataRetrievalFailureException("Auditoria persistida incompatível.");
        }
    }

    private AuditEntry mapAudit(ResultSet row, Identity identity) throws SQLException {
        try {
            var event = new AuditEntry(row.getString("evento_id"), Operation.valueOf(row.getString("operacao")),
                    readSnapshot(row.getString("antes_json")), readSnapshot(row.getString("depois_json")));
            if (!identity.equals(event.depois().identity()))
                throw new DataRetrievalFailureException("Auditoria pertence a outra identidade.");
            return event;
        } catch (IllegalArgumentException ex) {
            throw new DataRetrievalFailureException("Auditoria persistida incompatível.");
        }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("Documento não pode ser serializado como JSON."); }
    }

    private static MapSqlParameterSource identityParameters(Identity identity) {
        return new MapSqlParameterSource().addValue("marca", identity.marcaId(), Types.NVARCHAR)
                .addValue("colecao", identity.colecaoId(), Types.NVARCHAR).addValue("peca", identity.pecaId(), Types.NVARCHAR);
    }

    private static SqlTypeValue utcValue(Instant instant) {
        return (statement, index, sqlType, typeName) -> statement.setTimestamp(index, Timestamp.from(instant), utcCalendar());
    }

    private static Instant utcInstant(ResultSet row, String column) throws SQLException {
        var timestamp = row.getTimestamp(column, utcCalendar());
        if (timestamp == null) throw new DataRetrievalFailureException("Horário persistido ausente.");
        return timestamp.toInstant();
    }

    private static Calendar utcCalendar() { return Calendar.getInstance(TimeZone.getTimeZone("UTC")); }
}
