# Inventário e nomes de quatro referências já autorizadas, somente SELECT e alvo fixo.
[CmdletBinding()]
param([string]$CredentialPath = 'C:\Users\nathan henrique\Documents\Codex\2026-10-02\eu-x20\work\sqlserver-credential.clixml')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Data
$fichaConnection = $null
$fichaPassword = $null
$fichaStage = 'credential'
try {
    $fichaCredential = Import-Clixml -LiteralPath $CredentialPath
    if ($fichaCredential -isnot [System.Management.Automation.PSCredential]) { throw 'Credencial inválida.' }
    $fichaPassword = $fichaCredential.Password.Copy()
    $fichaPassword.MakeReadOnly()
    $fichaSqlCredential = [System.Data.SqlClient.SqlCredential]::new($fichaCredential.UserName, $fichaPassword)
    $fichaBuilder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $fichaBuilder['Data Source'] = 'tcp:192.168.1.6,1433'
    $fichaBuilder['Initial Catalog'] = 'HOMOLOG_COBRANCA'
    $fichaBuilder['Encrypt'] = $true
    $fichaBuilder['TrustServerCertificate'] = $true
    $fichaBuilder['Connect Timeout'] = 10
    $fichaBuilder['Application Name'] = 'ModaFlow - SELECT inventario ficha 20261015'
    $fichaStage = 'connect'
    $fichaConnection = [System.Data.SqlClient.SqlConnection]::new($fichaBuilder.ConnectionString, $fichaSqlCredential)
    $fichaConnection.Open()
    $fichaCommand = $fichaConnection.CreateCommand()
    try {
        $fichaCommand.CommandTimeout = 30
        $fichaCommand.CommandText = @'
SELECT DB_NAME() AS banco, CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) AS servidor,
 CONVERT(varchar(48), CONNECTIONPROPERTY('local_net_address')) AS endereco,
 CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')) AS porta,
 CONVERT(nvarchar(40), CONNECTIONPROPERTY('net_transport')) AS transporte,
 (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID) AS criptografia;
'@
        $fichaReader = $fichaCommand.ExecuteReader()
        try {
            if (-not $fichaReader.Read()) { throw 'Sem identificação.' }
            $fichaIdentity = [ordered]@{}
            for ($fichaIndex = 0; $fichaIndex -lt $fichaReader.FieldCount; $fichaIndex++) {
                $fichaIdentity[$fichaReader.GetName($fichaIndex)] = $fichaReader.GetValue($fichaIndex)
            }
        } finally { $fichaReader.Dispose() }
        if ($fichaIdentity.banco -cne 'HOMOLOG_COBRANCA' -or $fichaIdentity.endereco -cne '192.168.1.6' -or $fichaIdentity.porta -ne 1433 -or $fichaIdentity.transporte -cne 'TCP' -or $fichaIdentity.criptografia -cne 'TRUE') { throw 'Alvo não autorizado.' }
        $fichaStage = 'metadata-select'
        $fichaCommand.CommandText = @'
SELECT S.name AS esquema, T.name AS tabela, C.column_id AS ordem, C.name AS coluna,
 TY.name AS tipo, C.max_length AS bytes, C.precision AS precisao, C.scale AS escala,
 C.is_nullable AS aceita_null, C.is_identity AS identidade, C.is_computed AS calculada,
 C.collation_name AS collation,
 COALESCE(D.definition, SM.definition) AS valor_padrao,
 CASE WHEN D.object_id IS NOT NULL THEN 'DEFAULT_CONSTRAINT'
      WHEN O.object_id IS NOT NULL THEN 'DEFAULT_LEGADO_VINCULADO' ELSE 'SEM_DEFAULT' END AS tipo_padrao,
 CONVERT(nvarchar(4000), EP.value) AS descricao_coluna
FROM sys.tables T
JOIN sys.schemas S ON S.schema_id = T.schema_id
JOIN sys.columns C ON C.object_id = T.object_id
JOIN sys.types TY ON TY.user_type_id = C.user_type_id
LEFT JOIN sys.default_constraints D ON D.object_id = C.default_object_id
LEFT JOIN sys.objects O ON O.object_id = C.default_object_id
LEFT JOIN sys.sql_modules SM ON SM.object_id = C.default_object_id
LEFT JOIN sys.extended_properties EP ON EP.major_id = T.object_id AND EP.minor_id = C.column_id
 AND EP.class = 1 AND EP.name = N'MS_Description'
WHERE S.name = 'dbo' AND T.name IN ('PRODUTOS', 'PRODUTO_CORES', 'PRODUTO_FICHA_VERSAO',
 'PRODUTO_VERSAO_MATERIAL', 'PRODUTO_VERSAO_MATERIAL_COR', 'CORES_BASICAS', 'PRODUTOS_TAMANHOS',
 'MATERIAIS', 'MATERIAIS_CORES', 'MATERIAIS_FORNECEDOR', 'PRODUTOS_MODELO', 'COLECOES')
ORDER BY T.name, C.column_id;
SELECT T.name AS tabela, I.name AS chave, C.name AS coluna, IC.key_ordinal AS ordem
FROM sys.tables T JOIN sys.schemas S ON S.schema_id = T.schema_id
JOIN sys.indexes I ON I.object_id = T.object_id
JOIN sys.index_columns IC ON IC.object_id = I.object_id AND IC.index_id = I.index_id
JOIN sys.columns C ON C.object_id = IC.object_id AND C.column_id = IC.column_id
WHERE S.name = 'dbo' AND T.name IN ('PRODUTOS', 'PRODUTO_CORES', 'PRODUTO_FICHA_VERSAO',
 'PRODUTO_VERSAO_MATERIAL', 'PRODUTO_VERSAO_MATERIAL_COR') AND I.is_primary_key = 1
ORDER BY T.name, IC.key_ordinal;
SELECT OBJECT_NAME(F.parent_object_id) AS tabela, FK.name AS chave, F.constraint_column_id AS ordem,
 C.name AS coluna, OBJECT_SCHEMA_NAME(F.referenced_object_id) AS esquema_referenciado,
 OBJECT_NAME(F.referenced_object_id) AS tabela_referenciada, R.name AS coluna_referenciada,
 FK.is_disabled AS desabilitada, FK.is_not_trusted AS nao_confiavel,
 FK.delete_referential_action_desc AS exclusao, FK.update_referential_action_desc AS atualizacao
FROM sys.foreign_key_columns F JOIN sys.foreign_keys FK ON FK.object_id = F.constraint_object_id
JOIN sys.columns C ON C.object_id = F.parent_object_id AND C.column_id = F.parent_column_id
JOIN sys.columns R ON R.object_id = F.referenced_object_id AND R.column_id = F.referenced_column_id
WHERE F.parent_object_id IN (OBJECT_ID(N'dbo.PRODUTOS'), OBJECT_ID(N'dbo.PRODUTO_CORES'),
 OBJECT_ID(N'dbo.PRODUTO_FICHA_VERSAO'), OBJECT_ID(N'dbo.PRODUTO_VERSAO_MATERIAL'),
 OBJECT_ID(N'dbo.PRODUTO_VERSAO_MATERIAL_COR'))
ORDER BY tabela, chave, F.constraint_column_id;
SELECT OBJECT_NAME(TR.parent_id) AS tabela, TR.name AS gatilho, TR.is_disabled AS desabilitado,
 TR.is_instead_of_trigger AS instead_of
FROM sys.triggers TR
WHERE TR.parent_id IN (OBJECT_ID(N'dbo.PRODUTOS'), OBJECT_ID(N'dbo.PRODUTO_CORES'),
 OBJECT_ID(N'dbo.PRODUTO_FICHA_VERSAO'), OBJECT_ID(N'dbo.PRODUTO_VERSAO_MATERIAL'),
 OBJECT_ID(N'dbo.PRODUTO_VERSAO_MATERIAL_COR'))
ORDER BY tabela, gatilho;
'@
        $fichaSets = [System.Collections.Generic.List[object]]::new()
        $fichaReader = $fichaCommand.ExecuteReader()
        try {
            do {
                $fichaRows = [System.Collections.Generic.List[object]]::new()
                while ($fichaReader.Read()) {
                    $fichaRow = [ordered]@{}
                    for ($fichaIndex = 0; $fichaIndex -lt $fichaReader.FieldCount; $fichaIndex++) {
                        $fichaValue = $fichaReader.GetValue($fichaIndex)
                        if ($fichaValue -is [DBNull]) { $fichaValue = $null }
                        $fichaRow[$fichaReader.GetName($fichaIndex)] = $fichaValue
                    }
                    $fichaRows.Add($fichaRow)
                }
                $fichaSets.Add($fichaRows)
            } while ($fichaReader.NextResult())
        } finally { $fichaReader.Dispose() }
        $fichaStage = 'authorized-name-samples-select'
        $fichaCommand.CommandText = @'
SELECT RTRIM(PRODUTO) AS produto, DESC_PRODUTO AS descricao_cadastral,
 U_NOME_PRODUTO AS nome_personalizado, RTRIM(GRIFFE) AS griffe,
 RTRIM(COLECAO) AS colecao, RTRIM(VERSAO_FICHA) AS versao_atual
FROM dbo.PRODUTOS
WHERE PRODUTO IN ('BM00301', 'BM00002', 'MO14205K', 'PO14006K')
ORDER BY PRODUTO;
'@
        $fichaSamples = [System.Collections.Generic.List[object]]::new()
        $fichaReader = $fichaCommand.ExecuteReader()
        try {
            while ($fichaReader.Read()) {
                $fichaSample = [ordered]@{}
                for ($fichaIndex = 0; $fichaIndex -lt $fichaReader.FieldCount; $fichaIndex++) {
                    $fichaValue = $fichaReader.GetValue($fichaIndex)
                    if ($fichaValue -is [DBNull]) { $fichaValue = $null }
                    $fichaSample[$fichaReader.GetName($fichaIndex)] = $fichaValue
                }
                $fichaSamples.Add($fichaSample)
            }
        } finally { $fichaReader.Dispose() }
        $fichaReport = [ordered]@{
            coletado_em = [DateTimeOffset]::Now.ToString('o')
            identidade = $fichaIdentity
            somente_select = $true
            colunas = $fichaSets[0]
            chaves_primarias = $fichaSets[1]
            chaves_estrangeiras = $fichaSets[2]
            gatilhos = $fichaSets[3]
            amostras_nomes = $fichaSamples
        }
        $fichaReportPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../outputs/linx-ficha-colunas-20261015.json'))
        [IO.File]::WriteAllText($fichaReportPath, ($fichaReport | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
        $fichaIdentity | ConvertTo-Json -Compress
        "Inventario: $fichaReportPath"
        "Amostras de nome: $(($fichaSamples | ConvertTo-Json -Compress -Depth 4))"
        foreach ($fichaTable in @('PRODUTOS', 'PRODUTO_CORES', 'PRODUTO_FICHA_VERSAO', 'PRODUTO_VERSAO_MATERIAL', 'PRODUTO_VERSAO_MATERIAL_COR')) {
            $fichaColumns = @($fichaSets[0] | Where-Object { $_.tabela -ceq $fichaTable })
            "Tabela $fichaTable - $($fichaColumns.Count) colunas"
            ($fichaColumns | ForEach-Object { "$($_.coluna):$($_.tipo)" }) -join ', '
        }
        "PK: $(($fichaSets[1] | ConvertTo-Json -Compress -Depth 5))"
        "FK: $(($fichaSets[2] | ConvertTo-Json -Compress -Depth 5))"
        "Gatilhos: $(($fichaSets[3] | ConvertTo-Json -Compress -Depth 5))"
    } finally { $fichaCommand.Dispose() }
} catch {
    # Não exibe senha, credencial ou mensagem de conexão que possa conter informação sensível.
    throw "Inspeção de colunas interrompida na etapa $fichaStage. Nenhuma escrita SQL foi executada."
} finally {
    if ($fichaConnection) { $fichaConnection.Dispose() }
    if ($fichaPassword) { $fichaPassword.Dispose() }
}
