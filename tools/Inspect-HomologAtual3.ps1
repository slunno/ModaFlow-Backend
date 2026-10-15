# Apenas SELECT, servidor e banco fixos autorizados em 15/10/2026. Não executa módulos retornados.
[CmdletBinding()]
param([string]$CredentialPath='C:\Users\nathan henrique\Documents\Codex\2026-10-02\eu-x20\work\sqlserver-credential.clixml',
      [switch]$IncludeDefinitions)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$atualConnection=$null
$atualPassword=$null
$atualStage='credential'
try {
    $atualCredential=Import-Clixml -LiteralPath $CredentialPath
    if ($atualCredential -isnot [System.Management.Automation.PSCredential]) { throw 'Invalid credential' }
    $atualPassword=$atualCredential.Password.Copy()
    $atualPassword.MakeReadOnly()
    $atualSqlCredential=[System.Data.SqlClient.SqlCredential]::new($atualCredential.UserName,$atualPassword)
    $atualBuilder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $atualBuilder['Data Source']='tcp:192.168.1.3,1433'
    $atualBuilder['Initial Catalog']='HOMOLOGACAO_ATUAL'
    $atualBuilder['Encrypt']=$true
    $atualBuilder['TrustServerCertificate']=$true
    $atualBuilder['Connect Timeout']=10
    $atualBuilder['Application Name']='ModaFlow - SELECT homologacao atual autorizada'
    $atualStage='connect'
    $atualConnection=[System.Data.SqlClient.SqlConnection]::new($atualBuilder.ConnectionString,$atualSqlCredential)
    $atualConnection.Open()
    $atualCommand=$atualConnection.CreateCommand()
    $atualCommand.CommandTimeout=20
    $atualCommand.CommandText=@'
SELECT DB_NAME() AS banco,CONVERT(varchar(128),SERVERPROPERTY('ServerName')) AS servidor,
 CONVERT(varchar(48),CONNECTIONPROPERTY('local_net_address')) AS endereco,
 CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')) AS porta,
 (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id=@@SPID) AS criptografia;
'@
    $atualReader=$atualCommand.ExecuteReader()
    try {
        if (-not $atualReader.Read()) { throw 'No identity' }
        $atualIdentity=[ordered]@{}
        for($atualIndex=0;$atualIndex -lt $atualReader.FieldCount;$atualIndex++) {
            $atualIdentity[$atualReader.GetName($atualIndex)]=$atualReader.GetValue($atualIndex)
        }
    } finally { $atualReader.Dispose() }
    if ($atualIdentity.banco -cne 'HOMOLOGACAO_ATUAL' -or $atualIdentity.endereco -cne '192.168.1.3' -or $atualIdentity.porta -ne 1433 -or $atualIdentity.criptografia -cne 'TRUE') { throw 'Unexpected target' }
    $atualStage='local-metadata-select'
    $atualCommand.CommandText=@'
SELECT T.name AS tabela,C.column_id AS ordem,C.name AS coluna,TY.name AS tipo,C.max_length AS bytes,
 C.is_nullable AS aceita_null,C.is_identity AS identidade,C.is_computed AS calculada,
 COALESCE(DC.definition,SM.definition) AS valor_padrao
FROM sys.tables T JOIN sys.columns C ON C.object_id=T.object_id
JOIN sys.types TY ON TY.user_type_id=C.user_type_id
LEFT JOIN sys.default_constraints DC ON DC.object_id=C.default_object_id
LEFT JOIN sys.sql_modules SM ON SM.object_id=C.default_object_id
WHERE T.schema_id=SCHEMA_ID('dbo') AND T.name IN ('PRODUTOS','PRODUTO_CORES','PRODUTO_FICHA_VERSAO','PRODUTO_VERSAO_MATERIAL','PRODUTO_VERSAO_MATERIAL_COR')
ORDER BY T.name,C.column_id;
SELECT OBJECT_NAME(T.parent_id) AS tabela,T.name AS gatilho,T.is_disabled AS desabilitado,T.is_instead_of_trigger AS instead_of,
 M.execute_as_principal_id AS executar_como,M.definition AS definicao
FROM sys.triggers T LEFT JOIN sys.sql_modules M ON M.object_id=T.object_id
WHERE T.parent_id IN (OBJECT_ID('dbo.PRODUTOS'),OBJECT_ID('dbo.PRODUTO_CORES'),OBJECT_ID('dbo.PRODUTO_FICHA_VERSAO'),OBJECT_ID('dbo.PRODUTO_VERSAO_MATERIAL'),OBJECT_ID('dbo.PRODUTO_VERSAO_MATERIAL_COR'))
ORDER BY tabela,gatilho;
SELECT OBJECT_NAME(D.referencing_id) AS objeto,D.referenced_server_name AS servidor_referenciado,
 D.referenced_database_name AS banco_referenciado,D.referenced_schema_name AS esquema_referenciado,D.referenced_entity_name AS entidade_referenciada
FROM sys.sql_expression_dependencies D
WHERE (D.referenced_database_name IS NOT NULL OR D.referenced_server_name IS NOT NULL)
 AND D.referencing_id IN (SELECT object_id FROM sys.triggers WHERE parent_id IN
 (OBJECT_ID('dbo.PRODUTOS'),OBJECT_ID('dbo.PRODUTO_CORES'),OBJECT_ID('dbo.PRODUTO_FICHA_VERSAO'),OBJECT_ID('dbo.PRODUTO_VERSAO_MATERIAL'),OBJECT_ID('dbo.PRODUTO_VERSAO_MATERIAL_COR')))
ORDER BY objeto,banco_referenciado,entidade_referenciada;
'@
    $atualSets=[Collections.Generic.List[object]]::new()
    $atualReader=$atualCommand.ExecuteReader()
    try {
        do {
            $atualRows=[Collections.Generic.List[object]]::new()
            while($atualReader.Read()) {
                $atualRow=[ordered]@{}
                for($atualIndex=0;$atualIndex -lt $atualReader.FieldCount;$atualIndex++) {
                    $atualValue=$atualReader.GetValue($atualIndex)
                    if($atualValue -is [DBNull]) { $atualValue=$null }
                    $atualRow[$atualReader.GetName($atualIndex)]=$atualValue
                }
                $atualRows.Add($atualRow)
            }
            $atualSets.Add($atualRows)
        } while($atualReader.NextResult())
    } finally { $atualReader.Dispose() }
    $atualColumns=$atualSets[0]
    $atualTriggers=$atualSets[1]
    if(-not $IncludeDefinitions) {
        $atualColumns=@($atualSets[0] | Group-Object tabela | Select-Object Name,Count)
        $atualTriggers=@($atualSets[1] | Select-Object tabela,gatilho,desabilitado,instead_of,executar_como)
    }
    [ordered]@{identidade=$atualIdentity;somente_select=$true;colunas=$atualColumns;gatilhos=$atualTriggers;referencias_externas=$atualSets[2]} | ConvertTo-Json -Depth 10
} catch {
    # Não imprime conexão, credencial ou stack do driver.
    Write-Output ('Inspeção interrompida na etapa '+$atualStage+'. Nenhum comando de escrita foi executado.')
    exit 1
} finally {
    if($atualConnection) { $atualConnection.Dispose() }
    if($atualPassword) { $atualPassword.Dispose() }
}
