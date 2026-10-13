-- PREPARACAO SOMENTE: nao foi executado. Exige aprovacao especifica antes da aplicacao.
-- Banco/servidor permitidos: TCP 192.168.1.6:1433 / HOMOLOG_COBRANCA.
-- Sem USE, GO, gravacao de fichas ou alteracao de tabelas do ERP.
-- Lote unico, aditivo, transacional; nao apaga nem corrige estruturas existentes.
SET NOCOUNT ON;
SET XACT_ABORT ON;

SELECT
    CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) AS servidor,
    DB_NAME() AS banco,
    CONVERT(nvarchar(128), CONNECTIONPROPERTY('local_net_address')) AS endereco_local,
    CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')) AS porta_local,
    CONVERT(nvarchar(32), CONNECTIONPROPERTY('net_transport')) AS transporte,
    (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID) AS criptografia,
    ORIGINAL_LOGIN() AS login_original,
    HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION') AS pode_ver_definicoes,
    HAS_PERMS_BY_NAME(NULL, NULL, 'VIEW ANY DEFINITION') AS pode_ver_definicoes_servidor;

-- NULL tambem bloqueia: execucao por socket/local sem identificar o TCP nao e aceita.
IF COALESCE(DB_NAME(), N'') COLLATE Latin1_General_100_BIN2 <> N'HOMOLOG_COBRANCA'
    OR COALESCE(CONVERT(nvarchar(128), CONNECTIONPROPERTY('local_net_address')), N'')
        COLLATE Latin1_General_100_BIN2 <> N'192.168.1.6'
    OR COALESCE(CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')), -1) <> 1433
    OR COALESCE(CONVERT(nvarchar(32), CONNECTIONPROPERTY('net_transport')), N'') <> N'TCP'
    OR NOT EXISTS (SELECT 1 FROM sys.dm_exec_connections
        WHERE session_id = @@SPID AND encrypt_option = N'TRUE')
    THROW 51001, N'Migracao bloqueada: confirme TCP 192.168.1.6:1433, TLS e HOMOLOG_COBRANCA.', 1;

IF @@TRANCOUNT <> 0
    THROW 51002, N'Use uma conexao dedicada, sem transacao externa aberta.', 1;

-- Sem visibilidade suficiente, nao se assume que triggers/regras ausentes sejam seguros.
IF COALESCE(HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION'), 0) <> 1
    OR COALESCE(HAS_PERMS_BY_NAME(NULL, NULL, 'VIEW ANY DEFINITION'), 0) <> 1
    THROW 51003, N'Metadata insuficiente para conferir estruturas e triggers antes do DDL.', 1;

SELECT
    (SELECT COUNT(*) FROM sys.triggers WHERE parent_class = 0 AND is_disabled = 0)
        AS triggers_ddl_banco_habilitados,
    (SELECT COUNT(*) FROM sys.server_triggers WHERE is_disabled = 0)
        AS triggers_ddl_servidor_habilitados,
    OBJECT_ID(N'modaflow.ficha_documento', N'U') AS documento_existente,
    OBJECT_ID(N'modaflow.ficha_auditoria', N'U') AS auditoria_existente;

-- DDL pode disparar triggers; nao executar modulos desconhecidos nesta preparacao.
IF EXISTS (SELECT 1 FROM sys.triggers WHERE parent_class = 0 AND is_disabled = 0)
    OR EXISTS (SELECT 1 FROM sys.server_triggers WHERE is_disabled = 0)
    THROW 51004, N'Existem triggers DDL habilitados. Revisao e aprovacao especificas sao necessarias.', 1;

IF (OBJECT_ID(N'modaflow.ficha_documento') IS NOT NULL
        AND OBJECT_ID(N'modaflow.ficha_documento', N'U') IS NULL)
    OR (OBJECT_ID(N'modaflow.ficha_auditoria') IS NOT NULL
        AND OBJECT_ID(N'modaflow.ficha_auditoria', N'U') IS NULL)
    THROW 51005, N'Nome reservado do ModaFlow ja e usado por um objeto que nao e tabela.', 1;

BEGIN TRY
    BEGIN TRANSACTION;

    IF SCHEMA_ID(N'modaflow') IS NULL
        EXEC(N'CREATE SCHEMA modaflow AUTHORIZATION dbo;');

    IF OBJECT_ID(N'modaflow.ficha_documento', N'U') IS NULL
    BEGIN
        CREATE TABLE modaflow.ficha_documento (
            marca_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            colecao_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            peca_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            schema_version int NOT NULL,
            revisao nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            conteudo_json nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
            anexos_json nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
            criado_em datetime2(7) NOT NULL
                CONSTRAINT DF_modaflow_documento_criado_em DEFAULT SYSUTCDATETIME(),
            atualizado_em datetime2(7) NOT NULL
                CONSTRAINT DF_modaflow_documento_atualizado_em DEFAULT SYSUTCDATETIME(),
            criado_por_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            atualizado_por_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            criado_por_nome nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
            atualizado_por_nome nvarchar(256) COLLATE Latin1_General_100_BIN2 NOT NULL,
            CONSTRAINT PK_modaflow_ficha_documento PRIMARY KEY CLUSTERED
                (marca_id, colecao_id, peca_id),
            CONSTRAINT CK_modaflow_documento_schema_version CHECK (schema_version > 0),
            CONSTRAINT CK_modaflow_documento_conteudo_json CHECK (ISJSON(conteudo_json) = 1),
            CONSTRAINT CK_modaflow_documento_anexos_json CHECK (ISJSON(anexos_json) = 1)
        );
    END;

    IF OBJECT_ID(N'modaflow.ficha_auditoria', N'U') IS NULL
    BEGIN
        CREATE TABLE modaflow.ficha_auditoria (
            evento_id nvarchar(36) COLLATE Latin1_General_100_BIN2 NOT NULL,
            marca_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            colecao_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            peca_id nvarchar(128) COLLATE Latin1_General_100_BIN2 NOT NULL,
            operacao nvarchar(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
            registrado_em datetime2(7) NOT NULL
                CONSTRAINT DF_modaflow_auditoria_registrado_em DEFAULT SYSUTCDATETIME(),
            antes_json nvarchar(max) COLLATE Latin1_General_100_BIN2 NULL,
            depois_json nvarchar(max) COLLATE Latin1_General_100_BIN2 NOT NULL,
            CONSTRAINT PK_modaflow_ficha_auditoria PRIMARY KEY CLUSTERED (evento_id),
            CONSTRAINT FK_modaflow_auditoria_documento FOREIGN KEY (marca_id, colecao_id, peca_id)
                REFERENCES modaflow.ficha_documento (marca_id, colecao_id, peca_id),
            CONSTRAINT CK_modaflow_auditoria_operacao CHECK
                (operacao = N'CRIAR' OR operacao = N'ATUALIZAR'),
            CONSTRAINT CK_modaflow_auditoria_antes_json CHECK
                (antes_json IS NULL OR ISJSON(antes_json) = 1),
            CONSTRAINT CK_modaflow_auditoria_depois_json CHECK (ISJSON(depois_json) = 1),
            CONSTRAINT CK_modaflow_auditoria_antes_operacao CHECK
                ((operacao = N'CRIAR' AND antes_json IS NULL)
                    OR (operacao = N'ATUALIZAR' AND antes_json IS NOT NULL))
        );
    END;

    DECLARE @DocumentoId int = OBJECT_ID(N'modaflow.ficha_documento', N'U');
    DECLARE @AuditoriaId int = OBJECT_ID(N'modaflow.ficha_auditoria', N'U');

    -- Reaplicacao so aceita exatamente o contrato desta migracao, sem alterar dados.
    IF (SELECT COUNT(*) FROM sys.columns WHERE object_id = @DocumentoId) <> 13
        OR (SELECT COUNT(*) FROM sys.columns WHERE object_id = @AuditoriaId) <> 8
        THROW 51006, N'Tabelas ModaFlow existentes tem quantidade de colunas incompativel.', 1;

    IF EXISTS (
        SELECT 1
        FROM (VALUES
            (@DocumentoId, N'marca_id', N'nvarchar', 256, 0, 0),
            (@DocumentoId, N'colecao_id', N'nvarchar', 256, 0, 0),
            (@DocumentoId, N'peca_id', N'nvarchar', 256, 0, 0),
            (@DocumentoId, N'schema_version', N'int', 4, 0, 0),
            (@DocumentoId, N'revisao', N'nvarchar', 256, 0, 0),
            (@DocumentoId, N'conteudo_json', N'nvarchar', -1, 0, 0),
            (@DocumentoId, N'anexos_json', N'nvarchar', -1, 0, 0),
            (@DocumentoId, N'criado_em', N'datetime2', 8, 7, 0),
            (@DocumentoId, N'atualizado_em', N'datetime2', 8, 7, 0),
            (@DocumentoId, N'criado_por_id', N'nvarchar', 256, 0, 0),
            (@DocumentoId, N'atualizado_por_id', N'nvarchar', 256, 0, 0),
            (@DocumentoId, N'criado_por_nome', N'nvarchar', 512, 0, 0),
            (@DocumentoId, N'atualizado_por_nome', N'nvarchar', 512, 0, 0),
            (@AuditoriaId, N'evento_id', N'nvarchar', 72, 0, 0),
            (@AuditoriaId, N'marca_id', N'nvarchar', 256, 0, 0),
            (@AuditoriaId, N'colecao_id', N'nvarchar', 256, 0, 0),
            (@AuditoriaId, N'peca_id', N'nvarchar', 256, 0, 0),
            (@AuditoriaId, N'operacao', N'nvarchar', 32, 0, 0),
            (@AuditoriaId, N'registrado_em', N'datetime2', 8, 7, 0),
            (@AuditoriaId, N'antes_json', N'nvarchar', -1, 0, 1),
            (@AuditoriaId, N'depois_json', N'nvarchar', -1, 0, 0)
        ) AS esperado(objeto_id, coluna, tipo, tamanho_bytes, escala, anulavel)
        LEFT JOIN sys.columns AS c
            ON c.object_id = esperado.objeto_id AND c.name = esperado.coluna
        LEFT JOIN sys.types AS t ON t.user_type_id = c.user_type_id
        WHERE c.column_id IS NULL OR t.name <> esperado.tipo
            OR c.user_type_id <> c.system_type_id
            OR c.max_length <> esperado.tamanho_bytes OR c.scale <> esperado.escala
            OR c.is_nullable <> esperado.anulavel OR c.is_identity <> 0 OR c.is_computed <> 0
            OR c.generated_always_type <> 0 OR c.is_sparse <> 0 OR c.is_column_set <> 0
            OR c.rule_object_id <> 0 OR COALESCE(c.encryption_type, 0) <> 0
            OR (esperado.tipo = N'nvarchar'
                AND COALESCE(c.collation_name, N'') <> N'Latin1_General_100_BIN2')
    )
        THROW 51007, N'Tipos, tamanhos, nulabilidade ou collation ModaFlow existentes sao incompativeis.', 1;

    IF EXISTS (SELECT 1 FROM sys.triggers
        WHERE parent_id IN (@DocumentoId, @AuditoriaId))
        THROW 51008, N'Tabelas ModaFlow existentes possuem triggers nao previstos; nao serao executados.', 1;

    IF (SELECT COUNT(*) FROM sys.key_constraints
        WHERE parent_object_id IN (@DocumentoId, @AuditoriaId) AND type = 'PK') <> 2
        OR EXISTS (
            SELECT 1 FROM sys.key_constraints AS pk
            INNER JOIN sys.indexes AS i
                ON i.object_id = pk.parent_object_id AND i.index_id = pk.unique_index_id
            WHERE pk.parent_object_id IN (@DocumentoId, @AuditoriaId) AND pk.type = 'PK'
                AND (i.type <> 1 OR i.is_unique <> 1 OR i.is_disabled <> 0 OR i.ignore_dup_key <> 0)
        )
        OR (SELECT COUNT(*) FROM sys.key_constraints AS pk
            INNER JOIN sys.index_columns AS ic
                ON ic.object_id = pk.parent_object_id AND ic.index_id = pk.unique_index_id
            WHERE pk.parent_object_id IN (@DocumentoId, @AuditoriaId) AND pk.type = 'PK'
                AND ic.key_ordinal > 0) <> 4
        OR EXISTS (
            SELECT 1
            FROM (VALUES (@DocumentoId, 1, N'marca_id'), (@DocumentoId, 2, N'colecao_id'),
                (@DocumentoId, 3, N'peca_id'), (@AuditoriaId, 1, N'evento_id'))
                AS esperado(objeto_id, ordinal, coluna)
            LEFT JOIN sys.key_constraints AS pk
                ON pk.parent_object_id = esperado.objeto_id AND pk.type = 'PK'
            LEFT JOIN sys.index_columns AS ic ON ic.object_id = pk.parent_object_id
                AND ic.index_id = pk.unique_index_id AND ic.key_ordinal = esperado.ordinal
            LEFT JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE c.column_id IS NULL OR c.name <> esperado.coluna OR ic.is_descending_key <> 0
        )
        THROW 51009, N'Chaves primarias ModaFlow existentes nao correspondem ao contrato.', 1;

    IF EXISTS (SELECT 1 FROM sys.indexes
        WHERE object_id IN (@DocumentoId, @AuditoriaId) AND is_unique = 1 AND is_primary_key = 0)
        THROW 51014, N'Indices unicos adicionais em tabelas ModaFlow exigem revisao especifica.', 1;

    -- Normaliza tokens fora de literais; nunca modifica case, espacos ou escapes
    -- dentro de strings SQL. Operacao e BIN2: 'CRIAR' nao equivale a 'criar'.
    -- Esta tabela variavel guarda somente metadata local, nao dados de fichas.
    DECLARE @ChecksNormalizados TABLE (
        objeto_id int PRIMARY KEY,
        expressao_simples nvarchar(max) NOT NULL,
        expressao_com_grupos nvarchar(max) NOT NULL
    );
    DECLARE @CheckId int, @Definicao nvarchar(max), @Posicao int, @Tamanho int;
    DECLARE @Literal bit, @Caractere nchar(1), @Simples nvarchar(max), @ComGrupos nvarchar(max);
    DECLARE check_definicoes CURSOR LOCAL FAST_FORWARD FOR
        SELECT object_id, definition FROM sys.check_constraints
        WHERE parent_object_id IN (@DocumentoId, @AuditoriaId);
    OPEN check_definicoes;
    FETCH NEXT FROM check_definicoes INTO @CheckId, @Definicao;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        -- Definicao inesperadamente longa exige revisao; evita normalizacao sem limite.
        IF @Definicao IS NULL OR DATALENGTH(@Definicao) > 8192
            THROW 51010, N'Definicao CHECK ausente ou fora do limite de revisao do contrato.', 1;
        SELECT @Posicao = 1, @Tamanho = DATALENGTH(@Definicao) / 2,
            @Literal = 0, @Simples = N'', @ComGrupos = N'';
        WHILE @Posicao <= @Tamanho
        BEGIN
            SET @Caractere = SUBSTRING(@Definicao, @Posicao, 1);
            IF @Literal = 1
            BEGIN
                SET @Simples += @Caractere;
                SET @ComGrupos += @Caractere;
                IF @Caractere = N''''
                BEGIN
                    IF SUBSTRING(@Definicao, @Posicao + 1, 1) = N''''
                    BEGIN
                        -- Duas aspas dentro do literal sao uma aspa escapada, nao fim de string.
                        SET @Simples += N'''';
                        SET @ComGrupos += N'''';
                        SET @Posicao += 1;
                    END
                    ELSE SET @Literal = 0;
                END;
            END
            ELSE IF @Caractere = N''''
            BEGIN
                SET @Literal = 1;
                SET @Simples += @Caractere;
                SET @ComGrupos += @Caractere;
            END
            ELSE IF @Caractere NOT IN (N' ', NCHAR(9), NCHAR(10), NCHAR(13), N'[', N']')
            BEGIN
                SET @Caractere = LOWER(@Caractere COLLATE Latin1_General_100_BIN2);
                SET @ComGrupos += @Caractere;
                IF @Caractere NOT IN (N'(', N')') SET @Simples += @Caractere;
            END;
            SET @Posicao += 1;
        END;
        IF @Literal <> 0
            THROW 51010, N'Definicao CHECK com literal SQL ambiguo exige revisao.', 1;
        INSERT INTO @ChecksNormalizados (objeto_id, expressao_simples, expressao_com_grupos)
            VALUES (@CheckId, @Simples, @ComGrupos);
        FETCH NEXT FROM check_definicoes INTO @CheckId, @Definicao;
    END;
    CLOSE check_definicoes;
    DEALLOCATE check_definicoes;

    IF (SELECT COUNT(*) FROM sys.check_constraints
        WHERE parent_object_id IN (@DocumentoId, @AuditoriaId)) <> 7
        OR EXISTS (
            SELECT 1
            FROM (VALUES
                (@DocumentoId, N'CK_modaflow_documento_schema_version', N'schema_version>0'),
                (@DocumentoId, N'CK_modaflow_documento_conteudo_json', N'isjsonconteudo_json=1'),
                (@DocumentoId, N'CK_modaflow_documento_anexos_json', N'isjsonanexos_json=1'),
                (@AuditoriaId, N'CK_modaflow_auditoria_operacao', N'operacao=n''CRIAR''oroperacao=n''ATUALIZAR'''),
                (@AuditoriaId, N'CK_modaflow_auditoria_antes_json', N'antes_jsonisnullorisjsonantes_json=1'),
                (@AuditoriaId, N'CK_modaflow_auditoria_depois_json', N'isjsondepois_json=1'),
                (@AuditoriaId, N'CK_modaflow_auditoria_antes_operacao',
                    N'GRUPOS_BOOLEANOS_VERIFICADOS_SEPARADAMENTE')
            ) AS esperado(objeto_id, nome, expressao)
            LEFT JOIN sys.check_constraints AS cc
                ON cc.parent_object_id = esperado.objeto_id AND cc.name = esperado.nome
            LEFT JOIN @ChecksNormalizados AS normalizado ON normalizado.objeto_id = cc.object_id
            WHERE cc.object_id IS NULL OR cc.is_disabled <> 0 OR cc.is_not_trusted <> 0
                OR cc.is_not_for_replication <> 0 OR cc.definition IS NULL
                OR (esperado.nome <> N'CK_modaflow_auditoria_antes_operacao'
                    AND normalizado.expressao_simples COLLATE Latin1_General_100_BIN2 <> esperado.expressao)
                OR (esperado.nome = N'CK_modaflow_auditoria_antes_operacao'
                    AND normalizado.expressao_com_grupos COLLATE Latin1_General_100_BIN2 NOT IN (
                        -- Representacao observada no SQL Server 2019: AND precede OR.
                        N'(operacao=n''CRIAR''andantes_jsonisnulloroperacao=n''ATUALIZAR''andantes_jsonisnotnull)',
                        N'((operacao=n''CRIAR''andantes_jsonisnull)or(operacao=n''ATUALIZAR''andantes_jsonisnotnull))',
                        N'(operacao=n''CRIAR''andantes_jsonisnull)or(operacao=n''ATUALIZAR''andantes_jsonisnotnull)',
                        N'(((operacao=n''CRIAR'')andantes_jsonisnull)or((operacao=n''ATUALIZAR'')andantes_jsonisnotnull))',
                        N'(((operacao=n''CRIAR'')and(antes_jsonisnull))or((operacao=n''ATUALIZAR'')and(antes_jsonisnotnull)))'
                    ))
        )
    BEGIN
        -- Diagnostico somente de metadata propria, retornado apenas ao bloquear.
        -- Nenhum conteudo de ficha, credencial ou definicao de outro schema e exposto.
        SELECT OBJECT_NAME(cc.parent_object_id) AS tabela_modaflow,
            cc.name AS check_nome, cc.definition AS check_definicao,
            normalizado.expressao_simples AS normalizado_simples,
            normalizado.expressao_com_grupos AS normalizado_com_grupos,
            cc.is_disabled AS desabilitado, cc.is_not_trusted AS nao_confiavel,
            cc.is_not_for_replication AS nao_para_replicacao
        FROM sys.check_constraints AS cc
        LEFT JOIN @ChecksNormalizados AS normalizado ON normalizado.objeto_id = cc.object_id
        WHERE cc.parent_object_id IN (@DocumentoId, @AuditoriaId)
        ORDER BY cc.parent_object_id, cc.name;
        THROW 51010, N'CHECKs ModaFlow ausentes, desabilitados, nao confiaveis ou diferentes do contrato.', 1;
    END;

    IF (SELECT COUNT(*) FROM sys.default_constraints
        WHERE parent_object_id IN (@DocumentoId, @AuditoriaId)) <> 3
        OR EXISTS (
            SELECT 1
            FROM (VALUES (@DocumentoId, N'criado_em'), (@DocumentoId, N'atualizado_em'),
                (@AuditoriaId, N'registrado_em')) AS esperado(objeto_id, coluna)
            LEFT JOIN sys.columns AS c ON c.object_id = esperado.objeto_id AND c.name = esperado.coluna
            LEFT JOIN sys.default_constraints AS dc ON dc.object_id = c.default_object_id
            WHERE dc.object_id IS NULL OR dc.definition IS NULL
                OR LOWER(REPLACE(REPLACE(REPLACE(dc.definition, N'(', N''), N')', N''), N' ', N''))
                    <> N'sysutcdatetime'
        )
        THROW 51011, N'Defaults UTC ModaFlow existentes nao correspondem ao contrato.', 1;

    IF (SELECT COUNT(*) FROM sys.foreign_keys
        WHERE parent_object_id IN (@DocumentoId, @AuditoriaId)) <> 1
        OR NOT EXISTS (SELECT 1 FROM sys.foreign_keys
            WHERE parent_object_id = @AuditoriaId AND referenced_object_id = @DocumentoId
                AND name = N'FK_modaflow_auditoria_documento' AND is_disabled = 0 AND is_not_trusted = 0
                AND is_not_for_replication = 0 AND delete_referential_action = 0 AND update_referential_action = 0)
        OR (SELECT COUNT(*) FROM sys.foreign_key_columns AS fkc
            INNER JOIN sys.foreign_keys AS fk ON fk.object_id = fkc.constraint_object_id
            WHERE fk.parent_object_id = @AuditoriaId) <> 3
        OR EXISTS (
            SELECT 1
            FROM (VALUES (1, N'marca_id'), (2, N'colecao_id'), (3, N'peca_id')) AS esperado(ordinal, coluna)
            LEFT JOIN sys.foreign_keys AS fk ON fk.parent_object_id = @AuditoriaId
                AND fk.name = N'FK_modaflow_auditoria_documento'
            LEFT JOIN sys.foreign_key_columns AS fkc
                ON fkc.constraint_object_id = fk.object_id AND fkc.constraint_column_id = esperado.ordinal
            LEFT JOIN sys.columns AS origem
                ON origem.object_id = fkc.parent_object_id AND origem.column_id = fkc.parent_column_id
            LEFT JOIN sys.columns AS destino
                ON destino.object_id = fkc.referenced_object_id AND destino.column_id = fkc.referenced_column_id
            WHERE origem.column_id IS NULL OR destino.column_id IS NULL
                OR origem.name <> esperado.coluna OR destino.name <> esperado.coluna
        )
        THROW 51012, N'FK propria ModaFlow ausente, nao confiavel ou com vinculo diferente do contrato.', 1;

    IF EXISTS (SELECT 1 FROM sys.foreign_keys
        WHERE referenced_object_id IN (@DocumentoId, @AuditoriaId)
            AND NOT (parent_object_id = @AuditoriaId AND referenced_object_id = @DocumentoId
                AND name = N'FK_modaflow_auditoria_documento'))
        THROW 51015, N'FKs entrantes nao previstas nas tabelas ModaFlow exigem revisao especifica.', 1;

    IF NOT EXISTS (SELECT 1 FROM sys.indexes
        WHERE object_id = @AuditoriaId AND name = N'IX_modaflow_auditoria_identidade_tempo')
        CREATE NONCLUSTERED INDEX IX_modaflow_auditoria_identidade_tempo
            ON modaflow.ficha_auditoria (marca_id, colecao_id, peca_id, registrado_em, evento_id);

    DECLARE @IndiceAuditoriaId int = (SELECT index_id FROM sys.indexes
        WHERE object_id = @AuditoriaId AND name = N'IX_modaflow_auditoria_identidade_tempo');
    IF NOT EXISTS (SELECT 1 FROM sys.indexes
            WHERE object_id = @AuditoriaId AND index_id = @IndiceAuditoriaId AND type = 2
                AND is_unique = 0 AND is_disabled = 0 AND is_hypothetical = 0 AND has_filter = 0)
        OR (SELECT COUNT(*) FROM sys.index_columns
            WHERE object_id = @AuditoriaId AND index_id = @IndiceAuditoriaId AND key_ordinal > 0) <> 5
        OR EXISTS (
            SELECT 1
            FROM (VALUES (1, N'marca_id'), (2, N'colecao_id'), (3, N'peca_id'),
                (4, N'registrado_em'), (5, N'evento_id')) AS esperado(ordinal, coluna)
            LEFT JOIN sys.index_columns AS ic ON ic.object_id = @AuditoriaId
                AND ic.index_id = @IndiceAuditoriaId AND ic.key_ordinal = esperado.ordinal
            LEFT JOIN sys.columns AS c ON c.object_id = ic.object_id AND c.column_id = ic.column_id
            WHERE c.column_id IS NULL OR c.name <> esperado.coluna OR ic.is_descending_key <> 0
        )
        THROW 51013, N'Indice de historico ModaFlow existente e incompativel.', 1;

    COMMIT TRANSACTION;

    SELECT N'ESTRUTURAS_CONFERIDAS' AS resultado, DB_NAME() AS banco,
        N'modaflow.ficha_documento' AS documento, N'modaflow.ficha_auditoria' AS auditoria;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
