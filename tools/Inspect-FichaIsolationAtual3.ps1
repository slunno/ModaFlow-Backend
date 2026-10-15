# SELECTs fixos. Nunca imprime credenciais, altera permissões, executa módulos ou escreve no SQL.
[CmdletBinding()]
param([switch]$UseReaderCredential)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Data
$isolationConnection=$null
$isolationSecure=$null
try {
    if ($UseReaderCredential) {
        $isolationValues=@{}
        foreach($isolationLine in (Get-Content -LiteralPath (Join-Path $PSScriptRoot '..\.env.local'))) {
            if($isolationLine -match '^(MODAFLOW_LINX_DATASOURCE_USERNAME|MODAFLOW_LINX_DATASOURCE_PASSWORD)=(.*)$') {
                $isolationValues[$Matches[1]]=$Matches[2]
            }
        }
        $isolationSecure=ConvertTo-SecureString $isolationValues['MODAFLOW_LINX_DATASOURCE_PASSWORD'] -AsPlainText -Force
        $isolationLogin=$isolationValues['MODAFLOW_LINX_DATASOURCE_USERNAME']
        $isolationValues.Clear()
    } else {
        $isolationCredential=Import-Clixml -LiteralPath 'C:\Users\nathan henrique\Documents\Codex\2026-10-02\eu-x20\work\sqlserver-credential.clixml'
        $isolationLogin=$isolationCredential.UserName
        $isolationSecure=$isolationCredential.Password.Copy()
    }
    $isolationSecure.MakeReadOnly()
    $isolationBuilder=[System.Data.SqlClient.SqlConnectionStringBuilder]::new()
    $isolationBuilder['Data Source']='tcp:192.168.1.3,1433'
    $isolationBuilder['Initial Catalog']='HOMOLOGACAO_ATUAL'
    $isolationBuilder['Encrypt']=$true
    $isolationBuilder['TrustServerCertificate']=$true
    $isolationBuilder['Connect Timeout']=10
    $isolationBuilder['Application Name']='ModaFlow - SELECT isolamento fichas'
    $isolationConnection=[System.Data.SqlClient.SqlConnection]::new($isolationBuilder.ConnectionString,
        [System.Data.SqlClient.SqlCredential]::new($isolationLogin,$isolationSecure))
    $isolationConnection.Open()
    $isolationCommand=$isolationConnection.CreateCommand()
    $isolationCommand.CommandTimeout=30
    $isolationCommand.CommandText=@'
SELECT DB_NAME() AS banco,CONVERT(varchar(48),CONNECTIONPROPERTY('local_net_address')) AS ip,
 CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')) AS porta,
 (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id=@@SPID) AS tls,
 IS_SRVROLEMEMBER('sysadmin') AS sysadmin;
'@
    $isolationReader=$isolationCommand.ExecuteReader()
    try {
        if(-not $isolationReader.Read() -or $isolationReader['banco'] -cne 'HOMOLOGACAO_ATUAL' -or $isolationReader['ip'] -cne '192.168.1.3' -or $isolationReader['porta'] -ne 1433 -or $isolationReader['tls'] -cne 'TRUE') { throw 'Destination mismatch' }
        $isolationAdmin=$isolationReader['sysadmin']
    } finally { $isolationReader.Dispose() }
    $isolationCommand.CommandText=@'
SELECT T.name AS tabela,I.name AS chave,C.name AS coluna,IC.key_ordinal AS ordem
FROM sys.tables T JOIN sys.indexes I ON I.object_id=T.object_id AND I.is_primary_key=1
JOIN sys.index_columns IC ON IC.object_id=I.object_id AND IC.index_id=I.index_id
JOIN sys.columns C ON C.object_id=IC.object_id AND C.column_id=IC.column_id
WHERE T.name IN ('PRODUTOS','PRODUTO_CORES','PRODUTO_FICHA_VERSAO','PRODUTO_VERSAO_MATERIAL','PRODUTO_VERSAO_MATERIAL_COR') ORDER BY T.name,IC.key_ordinal;
SELECT OBJECT_NAME(F.parent_object_id) AS tabela,F.name AS fk,OBJECT_NAME(F.referenced_object_id) AS referencia,
 F.is_disabled,F.is_not_trusted,F.update_referential_action_desc,F.delete_referential_action_desc
FROM sys.foreign_keys F WHERE F.parent_object_id IN (OBJECT_ID('dbo.PRODUTO_FICHA_VERSAO'),OBJECT_ID('dbo.PRODUTO_VERSAO_MATERIAL'),OBJECT_ID('dbo.PRODUTO_VERSAO_MATERIAL_COR'),OBJECT_ID('dbo.KING_PRODUTOS_VERSAO_MATERIAL_LOG'));
SELECT T.name AS gatilho,T.is_disabled,M.definition AS definicao
FROM sys.triggers T LEFT JOIN sys.sql_modules M ON M.object_id=T.object_id
WHERE T.parent_id=OBJECT_ID('dbo.KING_PRODUTOS_VERSAO_MATERIAL_LOG');
SELECT RTRIM(P.PRODUTO) AS produto,RTRIM(P.GRIFFE) AS griffe,RTRIM(P.COLECAO) AS colecao_global,RTRIM(P.VERSAO_FICHA) AS versao_global,
 RTRIM(H.VERSAO_FICHA) AS versao,RTRIM(H.U_COLECAO) AS colecao_ficha,RTRIM(C.DESC_COLECAO) AS descricao,
 (SELECT COUNT(*) FROM dbo.PRODUTO_VERSAO_MATERIAL V WHERE V.PRODUTO=H.PRODUTO AND V.VERSAO_FICHA=H.VERSAO_FICHA) AS materiais
FROM dbo.PRODUTOS P JOIN dbo.PRODUTO_FICHA_VERSAO H ON H.PRODUTO=P.PRODUTO
LEFT JOIN dbo.COLECOES C ON C.COLECAO=COALESCE(NULLIF(RTRIM(H.U_COLECAO),''),P.COLECAO)
WHERE P.PRODUTO='BM00301' AND P.GRIFFE='KING&JOE' ORDER BY H.VERSAO_FICHA;
SELECT V.objeto,HAS_PERMS_BY_NAME(V.objeto,'OBJECT','INSERT') AS pode_inserir,HAS_PERMS_BY_NAME(V.objeto,'OBJECT','UPDATE') AS pode_atualizar
FROM (VALUES ('dbo.PRODUTO_FICHA_VERSAO'),('dbo.PRODUTO_VERSAO_MATERIAL'),('dbo.PRODUTO_VERSAO_MATERIAL_COR')) V(objeto);
'@
    $isolationSets=[Collections.Generic.List[object]]::new()
    $isolationReader=$isolationCommand.ExecuteReader()
    try {
        do {
            $isolationRows=[Collections.Generic.List[object]]::new()
            while($isolationReader.Read()) {
                $isolationRow=[ordered]@{}
                for($isolationIndex=0;$isolationIndex -lt $isolationReader.FieldCount;$isolationIndex++) {
                    $isolationValue=$isolationReader.GetValue($isolationIndex)
                    if($isolationValue -is [DBNull]) { $isolationValue=$null }
                    $isolationRow[$isolationReader.GetName($isolationIndex)]=$isolationValue
                }
                $isolationRows.Add($isolationRow)
            }
            $isolationSets.Add($isolationRows)
        } while($isolationReader.NextResult())
    } finally { $isolationReader.Dispose() }
    [ordered]@{banco='HOMOLOGACAO_ATUAL';ip='192.168.1.3';somente_select=$true;sysadmin=$isolationAdmin;chaves=$isolationSets[0];fks=$isolationSets[1];gatilhos_log=$isolationSets[2];referencia=$isolationSets[3];permissoes=$isolationSets[4]} | ConvertTo-Json -Depth 10
} catch {
    Write-Output 'Inspeção SELECT interrompida. Nenhuma gravação SQL executada; detalhes de conexão/credenciais omitidos.'
    exit 1
} finally {
    if($isolationConnection) { $isolationConnection.Dispose() }
    if($isolationSecure) { $isolationSecure.Dispose() }
}
