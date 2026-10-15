# Preparado para homologação. Não executar -ApplyMigration sem aprovação dos testes.
# Sem esse switch, executa apenas SELECT de identificação no .6.
[CmdletBinding()]
param(
    [string]$CredentialPath = 'C:\Users\nathan henrique\Documents\Codex\2026-10-02\eu-x20\work\sqlserver-credential.clixml',
    [switch]$InspectSchema,
    [switch]$ApplyMigration
)

$ErrorActionPreference = 'Stop'
if ($ApplyMigration) {
    throw 'Migração central desativada: rascunho e histórico ficam somente no navegador. Nenhuma conexão ou DDL foi executado.'
}
Add-Type -AssemblyName System.Data

$taskConnection = $null
$taskPassword = $null
$taskCommand = $null
$taskReader = $null
$taskStage = 'credential-import'

try {
    $taskCredential = Import-Clixml -LiteralPath $CredentialPath
    if ($taskCredential -isnot [System.Management.Automation.PSCredential]) {
        throw 'O arquivo deve conter uma PSCredential protegida pelo Windows.'
    }
    $taskStage = 'secure-credential'
    $taskPassword = $taskCredential.Password.Copy()
    $taskPassword.MakeReadOnly()
    $taskSqlCredential = [System.Data.SqlClient.SqlCredential]::new(
        $taskCredential.UserName, $taskPassword
    )

    # Não existem parâmetros de servidor/banco, aliases ou fallback para o .3.
    $taskStage = 'connection-builder'
    $taskBuilder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    # O adaptador IDictionary do PowerShell exige as palavras-chave SQL canônicas.
    $taskBuilder['Data Source'] = 'tcp:192.168.1.6,1433'
    $taskBuilder['Initial Catalog'] = 'HOMOLOG_COBRANCA'
    $taskBuilder['Encrypt'] = $true
    $taskBuilder['TrustServerCertificate'] = $true
    $taskBuilder['Connect Timeout'] = 10
    $taskBuilder['Application Name'] = 'ModaFlow - migracao homolog6 aprovada'
    $taskStage = 'connection-create'
    $taskConnection = [System.Data.SqlClient.SqlConnection]::new(
        $taskBuilder.ConnectionString, $taskSqlCredential
    )
    $taskStage = 'connection-open'
    $taskConnection.Open()

    $taskStage = 'identity-select'
    $taskCommand = $taskConnection.CreateCommand()
    $taskCommand.CommandTimeout = 15
    $taskCommand.CommandText = @'
SELECT
    CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) AS servidor,
    DB_NAME() AS banco,
    CONVERT(varchar(48), CONNECTIONPROPERTY('local_net_address')) AS endereco,
    CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')) AS porta,
    CONVERT(nvarchar(40), CONNECTIONPROPERTY('net_transport')) AS transporte,
    CONVERT(nvarchar(40), CONNECTIONPROPERTY('auth_scheme')) AS autenticacao,
    (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID) AS criptografia,
    HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE TABLE') AS pode_criar_tabela,
    HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE SCHEMA') AS pode_criar_schema;
'@
    $taskReader = $taskCommand.ExecuteReader()
    if (-not $taskReader.Read()) { throw 'O servidor não retornou sua identificação.' }
    $taskTarget = [ordered]@{}
    for ($taskColumn = 0; $taskColumn -lt $taskReader.FieldCount; $taskColumn++) {
        $taskValue = $taskReader.GetValue($taskColumn)
        if ($taskValue -is [DBNull]) { $taskValue = $null }
        $taskTarget[$taskReader.GetName($taskColumn)] = $taskValue
    }
    $taskReader.Dispose()
    $taskReader = $null
    $taskCommand.Dispose()
    $taskCommand = $null

    if ($taskTarget.banco -cne 'HOMOLOG_COBRANCA' -or
        $taskTarget.endereco -cne '192.168.1.6' -or
        $taskTarget.porta -ne 1433 -or
        $taskTarget.transporte -cne 'TCP' -or
        $taskTarget.criptografia -cne 'TRUE') {
        throw 'Identificação inesperada. Operação bloqueada; somente .6:1433/HOMOLOG_COBRANCA com TLS é permitido.'
    }

    [pscustomobject]$taskTarget
    if ($InspectSchema) {
        $taskStage = 'schema-inspection-select'
        $taskCommand = $taskConnection.CreateCommand()
        $taskCommand.CommandTimeout = 15
        $taskCommand.CommandText = @'
SELECT
    HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION') AS pode_ver_definicoes,
    HAS_PERMS_BY_NAME(NULL, NULL, 'VIEW ANY DEFINITION') AS pode_ver_definicoes_servidor,
    IS_SRVROLEMEMBER('sysadmin') AS sysadmin,
    SCHEMA_ID(N'modaflow') AS schema_modaflow_id,
    (SELECT COUNT(*) FROM sys.triggers WHERE parent_class = 0 AND is_disabled = 0) AS triggers_ddl_banco,
    (SELECT COUNT(*) FROM sys.server_triggers WHERE is_disabled = 0) AS triggers_ddl_servidor;
SELECT s.name AS esquema, o.name AS objeto, o.type_desc AS tipo,
    o.create_date AS criado_em, o.modify_date AS alterado_em,
    (SELECT COUNT(*) FROM sys.triggers t WHERE t.parent_id = o.object_id) AS triggers,
    (SELECT COUNT(*) FROM sys.columns c WHERE c.object_id = o.object_id) AS colunas
FROM sys.objects o JOIN sys.schemas s ON s.schema_id = o.schema_id
WHERE s.name = N'modaflow'
ORDER BY o.type_desc, o.name;
'@
        $taskReader = $taskCommand.ExecuteReader()
        do {
            while ($taskReader.Read()) {
                $taskMetadata = [ordered]@{}
                for ($taskColumn = 0; $taskColumn -lt $taskReader.FieldCount; $taskColumn++) {
                    $taskValue = $taskReader.GetValue($taskColumn)
                    if ($taskValue -is [DBNull]) { $taskValue = $null }
                    $taskMetadata[$taskReader.GetName($taskColumn)] = $taskValue
                }
                [pscustomobject]$taskMetadata
            }
        } while ($taskReader.NextResult())
        $taskReader.Dispose()
        $taskReader = $null
        $taskCommand.Dispose()
        $taskCommand = $null
    }
    if (-not $ApplyMigration) {
        Write-Output 'Somente leitura concluída. Nenhuma migração ou gravação foi executada.'
        return
    }

    $taskStage = 'migration-read'
    $taskMigration = Join-Path $PSScriptRoot '..\db\migrations\001-modaflow-ficha-rascunhos.sql'
    $taskMigration = (Resolve-Path -LiteralPath $taskMigration).Path
    $taskSql = Get-Content -LiteralPath $taskMigration -Raw -Encoding UTF8
    if ($taskSql -match '(?im)^\s*GO\s*(?:--.*)?$') {
        throw 'A migração deve ser um batch único; GO não é aceito neste executor.'
    }
    $taskHash = (Get-FileHash -LiteralPath $taskMigration -Algorithm SHA256).Hash
    Write-Output "Aplicando exclusivamente a migração própria do portal, SHA256: $taskHash"
    $taskCommand = $taskConnection.CreateCommand()
    $taskCommand.CommandTimeout = 60
    $taskCommand.CommandText = $taskSql
    $taskStage = 'migration-execute'
    # Consumir somente os resultsets fixos do batch de migração. Em erro, os
    # diagnósticos de metadata própria anteriores ao THROW ficam observáveis.
    $taskReader = $taskCommand.ExecuteReader()
    do {
        while ($taskReader.Read()) {
            $taskMigrationRow = [ordered]@{}
            for ($taskColumn = 0; $taskColumn -lt $taskReader.FieldCount; $taskColumn++) {
                $taskValue = $taskReader.GetValue($taskColumn)
                if ($taskValue -is [DBNull]) { $taskValue = $null }
                $taskMigrationRow[$taskReader.GetName($taskColumn)] = $taskValue
            }
            [pscustomobject]$taskMigrationRow
        }
    } while ($taskReader.NextResult())
    $taskReader.Dispose()
    $taskReader = $null
    Write-Output 'Migração concluída no .6/HOMOLOG_COBRANCA. Não foram gravadas fichas nem catálogos Linx.'
} catch {
    # Não imprimir a exceção original/credencial/connection string. A senha nunca é desembrulhada.
    $taskFailure = $_.Exception
    while ($null -ne $taskFailure.InnerException -and
        $taskFailure -isnot [System.Data.SqlClient.SqlException]) {
        $taskFailure = $taskFailure.InnerException
    }
    if ($taskFailure -is [System.Data.SqlClient.SqlException]) {
        foreach ($taskSqlError in $taskFailure.Errors) {
            Write-Warning ("Diagnóstico SQL sem conteúdo: Number={0}; State={1}; Class={2}; Line={3}" -f
                $taskSqlError.Number, $taskSqlError.State, $taskSqlError.Class, $taskSqlError.LineNumber)
        }
    }
    Write-Warning ("Etapa={0}; Tipo={1}; Linha={2}" -f
        $taskStage, $taskFailure.GetType().FullName, $_.InvocationInfo.ScriptLineNumber)
    throw 'Falha na verificação ou migração de homologação. Nenhum fallback para outro servidor foi tentado. Revise credencial protegida, alvo e script antes de repetir.'
} finally {
    if ($null -ne $taskReader) { $taskReader.Dispose() }
    if ($null -ne $taskCommand) { $taskCommand.Dispose() }
    if ($null -ne $taskConnection) { $taskConnection.Dispose() }
    if ($null -ne $taskPassword) { $taskPassword.Dispose() }
}
