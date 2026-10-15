# Preparado para a prova de persistencia autorizada: somente SELECT, nunca gravacao.
# A identidade e restrita a fixtures mf-homolog-; nao consulta catalogos/tabelas ERP.
# Nao imprime conteudo_json/antes_json/depois_json completos, credenciais ou connection string.
# A contagem de midia local identifica URIs data:/blob:, nao base64 arbitrario sem marcador.
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateScript({
        $taskParsedMarcaId = [long]0
        $_ -cmatch '\A[1-9][0-9]{0,18}\z' -and
            [long]::TryParse($_, [ref]$taskParsedMarcaId) -and $taskParsedMarcaId -gt 0
    })]
    [string]$MarcaId,
    [Parameter(Mandatory = $true)]
    [ValidateScript({ $_.Length -le 128 -and $_ -cmatch '\Amf-homolog-[a-z0-9][a-z0-9._-]*\z' })]
    [string]$ColecaoId,
    [Parameter(Mandatory = $true)]
    [ValidateScript({ $_.Length -le 128 -and $_ -cmatch '\Amf-homolog-[a-z0-9][a-z0-9._-]*\z' })]
    [string]$PecaId,
    [ValidateRange(1, 100)]
    [int]$LimiteEventos = 100,
    [string]$CredentialPath = 'C:\Users\nathan henrique\Documents\Codex\2026-10-02\eu-x20\work\sqlserver-credential.clixml'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Data
$taskConnection = $null
$taskPassword = $null
$taskCommand = $null
$taskReader = $null
$taskStage = 'credential-import'

function Measure-LocalMediaReference {
    param([AllowNull()][string]$JsonText)
    if ([string]::IsNullOrEmpty($JsonText)) { return $null }
    $taskParsedJson = ConvertFrom-Json -InputObject $JsonText -ErrorAction Stop
    $taskNodes = [System.Collections.Generic.Stack[object]]::new()
    $taskNodes.Push($taskParsedJson)
    $taskLocalMediaCount = 0
    while ($taskNodes.Count -gt 0) {
        $taskNode = $taskNodes.Pop()
        if ($null -eq $taskNode) { continue }
        if ($taskNode -is [string]) {
            $taskStart = 0
            while ($taskStart -lt $taskNode.Length -and
                ([char]::IsWhiteSpace($taskNode[$taskStart]) -or [int]$taskNode[$taskStart] -eq 0xFEFF)) {
                $taskStart++
            }
            $taskPrefix = $taskNode.Substring($taskStart)
            if ($taskPrefix.StartsWith('data:', [StringComparison]::OrdinalIgnoreCase) -or
                $taskPrefix.StartsWith('blob:', [StringComparison]::OrdinalIgnoreCase)) {
                $taskLocalMediaCount++
            }
        } elseif ($taskNode -is [System.Collections.IDictionary]) {
            foreach ($taskChild in $taskNode.Values) { $taskNodes.Push($taskChild) }
        } elseif ($taskNode -is [System.Collections.IEnumerable]) {
            foreach ($taskChild in $taskNode) { $taskNodes.Push($taskChild) }
        } elseif ($taskNode -is [pscustomobject]) {
            foreach ($taskProperty in $taskNode.PSObject.Properties) { $taskNodes.Push($taskProperty.Value) }
        }
    }
    return $taskLocalMediaCount
}

try {
    $taskCredential = Import-Clixml -LiteralPath $CredentialPath
    if ($taskCredential -isnot [System.Management.Automation.PSCredential]) {
        throw 'O arquivo precisa conter PSCredential protegida pelo Windows.'
    }
    $taskStage = 'secure-credential'
    $taskPassword = $taskCredential.Password.Copy()
    $taskPassword.MakeReadOnly()
    $taskSqlCredential = [System.Data.SqlClient.SqlCredential]::new($taskCredential.UserName, $taskPassword)

    # Palavras-chave SQL canonicas para o adaptador IDictionary do PowerShell.
    # O destino nao e um parametro e nao possui alias, redirecionamento ou fallback.
    $taskBuilder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $taskBuilder['Data Source'] = 'tcp:192.168.1.6,1433'
    $taskBuilder['Initial Catalog'] = 'HOMOLOG_COBRANCA'
    $taskBuilder['Encrypt'] = $true
    $taskBuilder['TrustServerCertificate'] = $true
    $taskBuilder['Connect Timeout'] = 10
    $taskBuilder['Application Name'] = 'ModaFlow - evidencia SELECT homolog6'
    $taskStage = 'connection-open'
    $taskConnection = [System.Data.SqlClient.SqlConnection]::new($taskBuilder.ConnectionString, $taskSqlCredential)
    $taskConnection.Open()

    $taskStage = 'target-select'
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
    OBJECT_ID(N'modaflow.ficha_documento', N'U') AS documento_id,
    OBJECT_ID(N'modaflow.ficha_auditoria', N'U') AS auditoria_id,
    HAS_PERMS_BY_NAME(N'modaflow.ficha_documento', 'OBJECT', 'SELECT') AS pode_ler_documento,
    HAS_PERMS_BY_NAME(N'modaflow.ficha_auditoria', 'OBJECT', 'SELECT') AS pode_ler_auditoria,
    (SELECT COUNT(*) FROM sys.columns
        WHERE object_id IN (OBJECT_ID(N'modaflow.ficha_documento', N'U'),
            OBJECT_ID(N'modaflow.ficha_auditoria', N'U')) AND is_computed = 1) AS colunas_computadas;
'@
    $taskReader = $taskCommand.ExecuteReader()
    if (-not $taskReader.Read()) { throw 'Identificacao do servidor ausente.' }
    $taskTarget = [ordered]@{ tipo = 'DESTINO' }
    for ($taskColumn = 0; $taskColumn -lt $taskReader.FieldCount; $taskColumn++) {
        $taskValue = $taskReader.GetValue($taskColumn)
        if ($taskValue -is [DBNull]) { $taskValue = $null }
        $taskTarget[$taskReader.GetName($taskColumn)] = $taskValue
    }
    $taskReader.Dispose(); $taskReader = $null
    $taskCommand.Dispose(); $taskCommand = $null
    if ($taskTarget.banco -cne 'HOMOLOG_COBRANCA' -or $taskTarget.endereco -cne '192.168.1.6' -or
        $taskTarget.porta -ne 1433 -or $taskTarget.transporte -cne 'TCP' -or
        $taskTarget.criptografia -cne 'TRUE' -or $null -eq $taskTarget.documento_id -or
        $null -eq $taskTarget.auditoria_id -or $taskTarget.pode_ler_documento -ne 1 -or
        $taskTarget.pode_ler_auditoria -ne 1 -or $taskTarget.colunas_computadas -ne 0) {
        throw 'Alvo, TLS, tabelas fisicas proprias ou permissoes incompativeis. Leitura bloqueada.'
    }
    [pscustomobject]$taskTarget | ConvertTo-Json -Compress -Depth 4

    $taskStage = 'fixture-select'
    $taskCommand = $taskConnection.CreateCommand()
    $taskCommand.CommandTimeout = 15
    $taskCommand.CommandText = @'
SELECT TOP (1)
    N'DOCUMENTO' AS tipo, d.marca_id, d.colecao_id, d.peca_id, d.schema_version, d.revisao,
    CONVERT(varchar(27), d.criado_em, 126) + 'Z' AS criado_em_utc,
    d.criado_por_id, d.criado_por_nome,
    CONVERT(varchar(27), d.atualizado_em, 126) + 'Z' AS atualizado_em_utc,
    d.atualizado_por_id, d.atualizado_por_nome,
    ISJSON(d.conteudo_json) AS conteudo_json_valido,
    CASE WHEN d.anexos_json = N'[]' COLLATE Latin1_General_100_BIN2 THEN 0 ELSE -1 END AS anexos_persistidos,
    CASE WHEN LOWER(d.conteudo_json) LIKE N'%data:%' OR LOWER(d.conteudo_json) LIKE N'%blob:%'
        THEN 1 ELSE 0 END AS texto_contem_marcador_midia,
    JSON_VALUE(d.conteudo_json, '$.nome') AS nome_peca,
    JSON_VALUE(d.conteudo_json, '$.fichaTecnica.variantes[0].tecidos[0].custo') AS tecido_preco_manual,
    JSON_VALUE(d.conteudo_json, '$.fichaTecnica.variantes[0].tecidos[0].consumo') AS tecido_consumo,
    JSON_VALUE(d.conteudo_json, '$.fichaTecnica.variantes[0].tecidos[0].peso') AS tecido_peso,
    JSON_VALUE(d.conteudo_json, '$.fichaTecnica.variantes[0].aviamentos[0].custo') AS aviamento_preco_manual,
    JSON_VALUE(d.conteudo_json, '$.fichaTecnica.variantes[0].aviamentos[0].quantidade') AS aviamento_quantidade,
    (SELECT COUNT_BIG(*) FROM modaflow.ficha_auditoria AS a
        WHERE a.marca_id = @Marca AND a.colecao_id = @Colecao AND a.peca_id = @Peca) AS total_eventos_auditoria,
    d.conteudo_json AS _conteudo_interno
FROM modaflow.ficha_documento AS d
WHERE d.marca_id = @Marca AND d.colecao_id = @Colecao AND d.peca_id = @Peca;

SELECT TOP (@Limite)
    N'AUDITORIA' AS tipo, a.evento_id, a.marca_id, a.colecao_id, a.peca_id, a.operacao,
    CONVERT(varchar(27), a.registrado_em, 126) + 'Z' AS registrado_em_utc,
    JSON_VALUE(a.antes_json, '$.revisao') AS antes_revisao,
    JSON_VALUE(a.depois_json, '$.revisao') AS depois_revisao,
    JSON_VALUE(a.depois_json, '$.atualizadoPor.usuarioId') AS responsavel_id,
    JSON_VALUE(a.depois_json, '$.atualizadoPor.nome') AS responsavel_nome,
    JSON_VALUE(a.antes_json, '$.conteudo.nome') AS antes_nome_peca,
    JSON_VALUE(a.depois_json, '$.conteudo.nome') AS depois_nome_peca,
    JSON_VALUE(a.antes_json, '$.conteudo.fichaTecnica.variantes[0].tecidos[0].custo') AS antes_tecido_preco_manual,
    JSON_VALUE(a.depois_json, '$.conteudo.fichaTecnica.variantes[0].tecidos[0].custo') AS depois_tecido_preco_manual,
    JSON_VALUE(a.antes_json, '$.conteudo.fichaTecnica.variantes[0].tecidos[0].consumo') AS antes_tecido_consumo,
    JSON_VALUE(a.depois_json, '$.conteudo.fichaTecnica.variantes[0].tecidos[0].consumo') AS depois_tecido_consumo,
    JSON_VALUE(a.antes_json, '$.conteudo.fichaTecnica.variantes[0].aviamentos[0].custo') AS antes_aviamento_preco_manual,
    JSON_VALUE(a.depois_json, '$.conteudo.fichaTecnica.variantes[0].aviamentos[0].custo') AS depois_aviamento_preco_manual,
    ISJSON(a.antes_json) AS antes_json_valido, ISJSON(a.depois_json) AS depois_json_valido,
    CASE WHEN a.antes_json IS NULL THEN NULL
        WHEN JSON_QUERY(a.antes_json, '$.anexos') = N'[]' COLLATE Latin1_General_100_BIN2 THEN 0 ELSE -1 END AS antes_anexos_persistidos,
    CASE WHEN JSON_QUERY(a.depois_json, '$.anexos') = N'[]' COLLATE Latin1_General_100_BIN2 THEN 0 ELSE -1 END AS depois_anexos_persistidos,
    CASE WHEN a.antes_json IS NULL THEN NULL
        WHEN LOWER(a.antes_json) LIKE N'%data:%' OR LOWER(a.antes_json) LIKE N'%blob:%'
        THEN 1 ELSE 0 END AS antes_texto_contem_marcador_midia,
    CASE WHEN LOWER(a.depois_json) LIKE N'%data:%' OR LOWER(a.depois_json) LIKE N'%blob:%'
        THEN 1 ELSE 0 END AS depois_texto_contem_marcador_midia,
    a.antes_json AS _antes_interno, a.depois_json AS _depois_interno
FROM modaflow.ficha_auditoria AS a
WHERE a.marca_id = @Marca AND a.colecao_id = @Colecao AND a.peca_id = @Peca
ORDER BY a.registrado_em ASC, a.evento_id ASC;
'@
    [void]$taskCommand.Parameters.Add('@Marca', [System.Data.SqlDbType]::NVarChar, 128)
    $taskCommand.Parameters['@Marca'].Value = $MarcaId
    [void]$taskCommand.Parameters.Add('@Colecao', [System.Data.SqlDbType]::NVarChar, 128)
    $taskCommand.Parameters['@Colecao'].Value = $ColecaoId
    [void]$taskCommand.Parameters.Add('@Peca', [System.Data.SqlDbType]::NVarChar, 128)
    $taskCommand.Parameters['@Peca'].Value = $PecaId
    [void]$taskCommand.Parameters.Add('@Limite', [System.Data.SqlDbType]::Int)
    $taskCommand.Parameters['@Limite'].Value = $LimiteEventos
    $taskReader = $taskCommand.ExecuteReader()
    $taskDocumentoCount = 0
    $taskAuditoriaCount = 0
    $taskTotalAuditoria = 0
    do {
        while ($taskReader.Read()) {
            $taskRow = [ordered]@{}
            for ($taskColumn = 0; $taskColumn -lt $taskReader.FieldCount; $taskColumn++) {
                $taskName = $taskReader.GetName($taskColumn)
                $taskValue = $taskReader.GetValue($taskColumn)
                if ($taskValue -is [DBNull]) { $taskValue = $null }
                if ($taskName.StartsWith('_', [StringComparison]::Ordinal)) {
                    $taskRow[$taskName.Substring(1) + '_uris_locais'] = Measure-LocalMediaReference -JsonText $taskValue
                } else {
                    $taskRow[$taskName] = $taskValue
                }
            }
            if ($taskRow.tipo -ceq 'DOCUMENTO') {
                $taskDocumentoCount++
                $taskTotalAuditoria = $taskRow.total_eventos_auditoria
            } else { $taskAuditoriaCount++ }
            [pscustomobject]$taskRow | ConvertTo-Json -Compress -Depth 4
        }
    } while ($taskReader.NextResult())
    [pscustomobject]@{
        tipo = 'RESUMO_LEITURA'
        documentos = $taskDocumentoCount
        eventos_exibidos = $taskAuditoriaCount
        eventos_totais = $taskTotalAuditoria
        historico_truncado = $taskTotalAuditoria -gt $taskAuditoriaCount
        somente_select = $true
    } | ConvertTo-Json -Compress -Depth 4
} catch {
    # Nunca imprimir excecao original: ela pode trazer SQL/conteudo/connection string.
    $taskFailure = $_.Exception
    while ($null -ne $taskFailure.InnerException -and $taskFailure -isnot [System.Data.SqlClient.SqlException]) {
        $taskFailure = $taskFailure.InnerException
    }
    if ($taskFailure -is [System.Data.SqlClient.SqlException]) {
        foreach ($taskSqlError in $taskFailure.Errors) {
            Write-Warning ('Diagnostico SQL sem conteudo: Number={0}; State={1}; Class={2}; Line={3}' -f
                $taskSqlError.Number, $taskSqlError.State, $taskSqlError.Class, $taskSqlError.LineNumber)
        }
    }
    Write-Warning ('Etapa={0}; Tipo={1}; Linha={2}' -f
        $taskStage, $taskFailure.GetType().FullName, $_.InvocationInfo.ScriptLineNumber)
    throw 'Leitura de evidencias bloqueada. Nenhuma gravacao ou fallback para outro servidor foi executado.'
} finally {
    if ($null -ne $taskReader) { $taskReader.Dispose() }
    if ($null -ne $taskCommand) { $taskCommand.Dispose() }
    if ($null -ne $taskConnection) { $taskConnection.Dispose() }
    if ($null -ne $taskPassword) { $taskPassword.Dispose() }
}
