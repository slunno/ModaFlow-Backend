# Inspeção fixa de metadados e quatro amostras autorizadas. Não aceita SQL, alvo alternativo ou escrita.
[CmdletBinding()]
param([string]$CredentialPath = 'C:\Users\nathan henrique\Documents\Codex\2026-10-02\eu-x20\work\sqlserver-credential.clixml')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Data
$mappingConnection = $null
$mappingPassword = $null
$mappingStage = 'credential'
try {
    $mappingCredential = Import-Clixml -LiteralPath $CredentialPath
    if ($mappingCredential -isnot [System.Management.Automation.PSCredential]) { throw 'Credencial inválida.' }
    $mappingPassword = $mappingCredential.Password.Copy()
    $mappingPassword.MakeReadOnly()
    $mappingSqlCredential = [System.Data.SqlClient.SqlCredential]::new($mappingCredential.UserName, $mappingPassword)
    $mappingBuilder = [System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $mappingBuilder['Data Source'] = 'tcp:192.168.1.6,1433'
    $mappingBuilder['Initial Catalog'] = 'HOMOLOG_COBRANCA'
    $mappingBuilder['Encrypt'] = $true
    $mappingBuilder['TrustServerCertificate'] = $true
    $mappingBuilder['Connect Timeout'] = 10
    $mappingBuilder['Application Name'] = 'ModaFlow - SELECT metadados grade e cor'
    $mappingStage = 'connect'
    $mappingConnection = [System.Data.SqlClient.SqlConnection]::new($mappingBuilder.ConnectionString, $mappingSqlCredential)
    $mappingConnection.Open()
    $mappingCommand = $mappingConnection.CreateCommand()
    try {
        $mappingCommand.CommandTimeout = 15
        $mappingCommand.CommandText = @'
SELECT DB_NAME() AS banco,
 CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) AS servidor,
 CONVERT(varchar(48), CONNECTIONPROPERTY('local_net_address')) AS endereco,
 CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')) AS porta,
 CONVERT(nvarchar(40), CONNECTIONPROPERTY('net_transport')) AS transporte,
 (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id = @@SPID) AS criptografia;
'@
        $mappingReader = $mappingCommand.ExecuteReader()
        try {
            if (-not $mappingReader.Read()) { throw 'Sem identificação.' }
            $mappingIdentity = [ordered]@{}
            for ($mappingIndex = 0; $mappingIndex -lt $mappingReader.FieldCount; $mappingIndex++) {
                $mappingIdentity[$mappingReader.GetName($mappingIndex)] = $mappingReader.GetValue($mappingIndex)
            }
        } finally { $mappingReader.Dispose() }
        if ($mappingIdentity.banco -cne 'HOMOLOG_COBRANCA' -or $mappingIdentity.endereco -cne '192.168.1.6' -or $mappingIdentity.porta -ne 1433 -or $mappingIdentity.transporte -cne 'TCP' -or $mappingIdentity.criptografia -cne 'TRUE') { throw 'Alvo não autorizado.' }
        $mappingIdentity | ConvertTo-Json -Compress
        $mappingStage = 'metadata-select'
        $mappingCommand.CommandText = @'
SELECT O.name AS tabela, O.type_desc AS tipo_objeto, C.name AS coluna,
 T.name AS tipo, C.max_length, C.is_nullable
FROM sys.objects O JOIN sys.columns C ON C.object_id = O.object_id
JOIN sys.types T ON T.user_type_id = C.user_type_id
WHERE O.object_id IN (OBJECT_ID(N'dbo.CORES_BASICAS'), OBJECT_ID(N'dbo.PRODUTOS_TAMANHOS'),
 OBJECT_ID(N'dbo.PRODUTO_CORES'), OBJECT_ID(N'dbo.PRODUTO_VERSAO_MATERIAL_COR'))
ORDER BY O.name, C.column_id;
SELECT OBJECT_NAME(F.parent_object_id) AS tabela, C.name AS coluna,
 OBJECT_NAME(F.referenced_object_id) AS tabela_referenciada, R.name AS coluna_referenciada
FROM sys.foreign_key_columns F
JOIN sys.columns C ON C.object_id = F.parent_object_id AND C.column_id = F.parent_column_id
JOIN sys.columns R ON R.object_id = F.referenced_object_id AND R.column_id = F.referenced_column_id
WHERE F.parent_object_id IN (OBJECT_ID(N'dbo.PRODUTO_CORES'), OBJECT_ID(N'dbo.PRODUTO_VERSAO_MATERIAL_COR'));
SELECT O.name AS tabela, I.name AS chave, C.name AS coluna, IC.key_ordinal AS ordem
FROM sys.objects O JOIN sys.indexes I ON I.object_id = O.object_id
JOIN sys.index_columns IC ON IC.object_id = I.object_id AND IC.index_id = I.index_id
JOIN sys.columns C ON C.object_id = IC.object_id AND C.column_id = IC.column_id
WHERE O.object_id IN (OBJECT_ID(N'dbo.PRODUTO_CORES'), OBJECT_ID(N'dbo.PRODUTO_VERSAO_MATERIAL_COR'))
 AND I.is_primary_key = 1 ORDER BY O.name, IC.key_ordinal;
SELECT RTRIM(PC.PRODUTO) AS produto, RTRIM(PC.COR_PRODUTO) AS cor_produto,
 RTRIM(PC.DESC_COR_PRODUTO) AS descricao_produto, RTRIM(PC.COR) AS cor_basica,
 RTRIM(CB.DESC_COR) AS descricao_basica, RTRIM(PC.U_COLECAO) AS colecao_cor,
 PC.U_INATIVO AS inativa
FROM dbo.PRODUTO_CORES PC
LEFT JOIN dbo.CORES_BASICAS CB ON CB.COR = PC.COR
WHERE PC.PRODUTO IN ('BM00301', 'BM00002', 'MO14205K', 'PO14006K')
ORDER BY PC.PRODUTO, PC.COR_PRODUTO;
SELECT DISTINCT RTRIM(P.PRODUTO) AS produto, RTRIM(P.GRADE) AS grade,
 T.NUMERO_TAMANHOS, T.TAMANHO_1, T.TAMANHO_2, T.TAMANHO_3, T.TAMANHO_4,
 T.TAMANHO_5, T.TAMANHO_6, T.TAMANHO_7, T.TAMANHO_8, T.TAMANHO_9, T.TAMANHO_10
FROM dbo.PRODUTOS P LEFT JOIN dbo.PRODUTOS_TAMANHOS T ON T.GRADE = P.GRADE
WHERE P.PRODUTO IN ('BM00301', 'BM00002', 'MO14205K', 'PO14006K');
'@
        # Os identificadores abaixo são gerados somente do intervalo físico fixo confirmado (1..48).
        $mappingCountSql = (1..48 | ForEach-Object { "CASE WHEN NULLIF(LTRIM(RTRIM(T.TAMANHO_$_)), '') IS NULL THEN 0 ELSE 1 END" }) -join ' + '
        $mappingCommand.CommandText += @"
SELECT RTRIM(P.PRODUTO) AS produto, RTRIM(T.GRADE) AS grade,
 T.NUMERO_TAMANHOS, ($mappingCountSql) AS posicoes_nomeadas
FROM dbo.PRODUTOS P INNER JOIN dbo.PRODUTOS_TAMANHOS T ON T.GRADE = P.GRADE
WHERE P.PRODUTO IN ('BM00301', 'BM00002', 'MO14205K', 'PO14006K');
"@
        $mappingReader = $mappingCommand.ExecuteReader()
        try {
            do {
                while ($mappingReader.Read()) {
                    $mappingRow = [ordered]@{}
                    for ($mappingIndex = 0; $mappingIndex -lt $mappingReader.FieldCount; $mappingIndex++) {
                        $mappingValue = $mappingReader.GetValue($mappingIndex)
                        if ($mappingValue -is [DBNull]) { $mappingValue = $null }
                        $mappingRow[$mappingReader.GetName($mappingIndex)] = $mappingValue
                    }
                    $mappingRow | ConvertTo-Json -Compress
                }
            } while ($mappingReader.NextResult())
        } finally { $mappingReader.Dispose() }
    } finally { $mappingCommand.Dispose() }
} catch {
    # Não imprime a credencial ou detalhes da exceção de conexão.
    throw "Inspeção SELECT interrompida na etapa $mappingStage. Nenhuma escrita foi executada."
} finally {
    if ($mappingConnection) { $mappingConnection.Dispose() }
    if ($mappingPassword) { $mappingPassword.Dispose() }
}
