# Migração dos rascunhos da ficha técnica — homologação .6

**Decisão posterior do usuário:** rascunho e histórico somente no navegador. A migração abaixo é um artefato histórico, não deve ser aplicada novamente. O helper recusa `-ApplyMigration` antes de conectar, e o recurso central do aplicativo foi desativado. As duas tabelas já criadas no `.6` ainda existem; nenhum DROP/DELETE foi executado nesta correção. A exclusão depende de confirmação específica. Veja [armazenamento local](C:/King-Joe-Projects/modaflow-frontend/docs/ficha-rascunho-local.md).

Estado em **07/10/2026**: migração aplicada e reaplicada, após aprovação do usuário, exclusivamente em **192.168.1.6:1433 / HOMOLOG_COBRANCA**. Persistência e auditoria foram conferidas por SQL e HTTP, inclusive com atualizações concorrentes e releitura após reiniciar o backend isolado. O ambiente `.3` não foi acessado e não houve gravação em tabelas do ERP. Consulte o [relatório de resultados](C:/King-Joe-Projects/modaflow-frontend/docs/ficha-rascunho-homologacao-resultado.md) para as evidências e os limites desta homologação.

O script [001-modaflow-ficha-rascunhos.sql](C:/King-Joe-Projects/modaflow-backend/db/migrations/001-modaflow-ficha-rascunhos.sql) criou exclusivamente o schema próprio `modaflow` e suas duas tabelas; a reaplicação confirmou estruturas compatíveis sem recriar os dados. Não modifica tabelas do ERP, cadastros de produtos/materiais/cores, custos ou versões integradas. Não prepara gravação no ERP nem confirma integrações reais.

SHA-256 da versão aplicada: `B3F15F9BB175189A037D52BD47D3B30BC73B5ADAB7E4EEA141C3BF14BD66B715`.

O cabeçalho “PREPARACAO SOMENTE” no arquivo SQL registra seu estado original. O arquivo aplicado foi preservado byte a byte para manter o hash da evidência; o estado posterior à aprovação é o desta página e do relatório de resultados.

## Estruturas e contrato

| Estrutura | Finalidade | Identidade |
|---|---|---|
| `modaflow.ficha_documento` | Rascunho atual, revisão para controle de concorrência e autoria/tempos do servidor | PK `(marca_id, colecao_id, peca_id)` |
| `modaflow.ficha_auditoria` | Evento imutável por operação com snapshots completos antes/depois | PK `evento_id`; FK própria para a identidade do documento |

Todos os IDs de marca, coleção, peça, revisão e usuário usam `nvarchar(128)` com `Latin1_General_100_BIN2`. Os três IDs da PK ocupam no máximo 768 bytes, dentro do limite de chave clustered de 900 bytes. A comparação binária evita misturar IDs apenas pela diferença de maiúsculas/minúsculas. Nomes de usuário usam `nvarchar(256)`; `evento_id` usa `nvarchar(36)` e operação `nvarchar(16)`.

O documento contém `schema_version` inteiro positivo, `revisao`, `conteudo_json`, `anexos_json`, `criado_em`, `atualizado_em`, `criado_por_id`, `atualizado_por_id`, `criado_por_nome` e `atualizado_por_nome`. A versão HTTP inicialmente aceita é 1; a constraint de banco admite versões positivas para evolução controlada. Campos de preço permanecem no JSON exatamente como enviados/validados pelo contrato, sem conversão automática ou atualização de cadastros globais.

**Anexos continuam somente no navegador.** O adapter exige `anexos=[]` e grava `anexos_json` como `[]`; não envia bytes, base64, URLs locais ou identificadores de IndexedDB como arquivos integrados. A constraint SQL valida JSON sintático, não substitui a validação dessa regra pelo adapter.

A auditoria tem `CRIAR`/`ATUALIZAR`, `registrado_em`, `antes_json` e `depois_json`. `antes_json` é nulo somente em `CRIAR`; em `ATUALIZAR` deve existir e ser JSON válido. `depois_json` sempre existe e é JSON válido. Os snapshots completos incluem identidade, schemaVersion, revisão, conteúdo, anexos e autoria/tempos. A FK é `NO ACTION`: nada é removido automaticamente em cascata. O índice por identidade + `registrado_em, evento_id` apoia a ordem determinística do histórico.

`datetime2(7)` não contém fuso horário. Defaults usam `SYSUTCDATETIME()`; o adapter também deve gerar os instantes em UTC, obter IDs/nomes do usuário autenticado e nunca confiar em autoria fornecida pelo cliente. A revisão é gerada pelo servidor, não pelo banco. Salvar documento e adicionar auditoria são uma única transação; `UPDLOCK,HOLDLOCK` por PK e revisão anterior no `WHERE` do UPDATE evitam sobrescrita silenciosa.

## Segurança e procedimento de aplicação

A configuração normal, seus flags e os arquivos `.env` não foram alterados. Foi iniciado somente um backend de teste explicitamente isolado, com autenticação em H2 local e persistência de rascunhos no `.6`; o leitor Linx ficou desabilitado. Aplicar a migração e testar uma ficha sintética não equivale a ativar persistência central no portal normal ou integrar uma peça.

O procedimento abaixo permanece necessário para qualquer nova aplicação autorizada; não é autorização para executar novamente em outro ambiente.

1. Revisar o script e obter aprovação específica para o alvo da homologação.
2. Usar conexão dedicada, criptografada, explicitamente fixada no TCP `192.168.1.6:1433` / `HOMOLOG_COBRANCA`; credencial protegida, nunca embutida no SQL. Manter cópia/backup conforme política da TI.
3. Executar os SELECTs de preflight abaixo e conferir banco, endereço local do servidor, porta, transporte e visibilidade de metadata. Não presumir destino por nome de instância ou por seleção visual de banco.
4. Conferir triggers DDL habilitados no banco/servidor. A migração falha se houver algum, sem desabilitá-lo; sua análise e eventual execução exigem autorização própria. Tabelas ModaFlow preexistentes com triggers também são recusadas.
5. Somente depois da aprovação aplicar o script como **um único batch, sem `GO` e sem `USE`**. O [helper de migração](C:/King-Joe-Projects/modaflow-backend/tools/Invoke-ModaflowFichaMigration.ps1) mantém preflight como padrão; seu switch real é `-ApplyMigration`, que permite mutação e não constitui autorização implícita. `-InspectSchema` permite conferir metadata sem aplicar DDL.
6. Conferir o resultado e as estruturas. Testar o adapter com registros sintéticos autorizados, verificando identidade, conflitos, auditoria e anexos vazios. Essas verificações foram executadas nesta homologação; a falha provocada da auditoria/rollback foi coberta somente por teste unitário, não por alteração de permissões ou triggers no SQL.

A migração checa banco/IP/porta/TCP e `encrypt_option=TRUE` da conexão atual, com falha fechada para valores nulos **antes de qualquer DDL**. Precisa de visibilidade de definições no banco e no servidor, além da consulta da conexão em `sys.dm_exec_connections`, para não interpretar objetos invisíveis como inexistentes; aplicação deve usar usuário de migração autorizado, não conceder automaticamente privilégios ao usuário do portal. O SQL não concede permissões. TLS deve ser configurado no helper/conexão de execução; o SQL apenas verifica a conexão já estabelecida, não ativa a criptografia.

O script usa uma transação aditiva. Reaplicação não altera registros; aceita estruturas compatíveis e cria apenas objetos ausentes previstos. Se encontrar quantidade/tipo/tamanho/nulabilidade/collation de colunas, PK, CHECKs, defaults UTC, FK ou índice incompatíveis, interrompe e desfaz o DDL desta execução. CHECKs/FK devem estar habilitados e trusted; não são revalidados, recriados ou corrigidos automaticamente. Índices únicos extras, FKs entrantes não previstas, regras antigas vinculadas a coluna e colunas criptografadas também exigem revisão, sem alteração automática.

A comparação das expressões CHECK é deliberadamente conservadora. Checks simples removem apenas diferenças de formatação/enclosures da representação; a regra de operação/antes combina AND/OR e mantém seus grupos de parênteses, aceitando somente formas canônicas explicitamente equivalentes. Não se alega um verificador universal de equivalência SQL: uma representação não reconhecida, mesmo equivalente, interrompe a migração para revisão humana em vez de liberar uma regra potencialmente diferente.

## Preflight somente SELECT

Executar somente em uma conexão previamente configurada para o destino de homologação autorizado. Estes SELECTs não aplicam a migração.

```sql
SELECT
    CONVERT(nvarchar(128), SERVERPROPERTY('ServerName')) AS servidor,
    DB_NAME() AS banco,
    CONVERT(nvarchar(128), CONNECTIONPROPERTY('local_net_address')) AS endereco_local,
    CONVERT(int, CONNECTIONPROPERTY('local_tcp_port')) AS porta_local,
    CONVERT(nvarchar(32), CONNECTIONPROPERTY('net_transport')) AS transporte,
    ORIGINAL_LOGIN() AS login_original;

SELECT
    HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'VIEW DEFINITION') AS pode_ver_definicoes,
    HAS_PERMS_BY_NAME(NULL, NULL, 'VIEW ANY DEFINITION') AS pode_ver_definicoes_servidor,
    HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE TABLE') AS pode_criar_tabela,
    HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE SCHEMA') AS pode_criar_schema;

SELECT name, is_disabled, parent_class_desc
FROM sys.triggers WHERE parent_class = 0;

SELECT name, is_disabled
FROM sys.server_triggers;

SELECT s.name AS schema_nome, o.name AS objeto, o.type_desc
FROM sys.objects AS o
JOIN sys.schemas AS s ON s.schema_id = o.schema_id
WHERE s.name = N'modaflow'
ORDER BY o.type_desc, o.name;
```

## Rollback e preservação

Não há rollback destrutivo automático nem script de DROP. Em erro durante a migração, a transação desfaz somente a execução ainda não confirmada. Depois do COMMIT, os rascunhos e a auditoria podem conter dados importantes: reversão precisa de inventário/backup e decisão explícita da TI. Não excluir tabela ou schema para resolver incompatibilidade, especialmente se já houver documentos ou eventos.

Para suspender o recurso, desabilitar sua utilização/configuração com autorização e preservar as estruturas/dados. Se um schema/tabela já existia antes desta migração, sua propriedade e uso devem ser confirmados; o script não assume que pode substituí-los.

## Resultados comprovados e limites

O preflight real confirmou `srv-sql-dev`, banco `HOMOLOG_COBRANCA`, endereço `.6`, porta 1433, transporte TCP e criptografia ativa. O schema estava ausente antes da aplicação e não havia triggers DDL habilitados no banco/servidor. Depois da aplicação e reaplicação, foram conferidas as duas tabelas próprias, suas colunas, chaves, defaults UTC, índice de auditoria e sete CHECKs habilitados e confiáveis.

O teste real criou um documento sintético e dois eventos (`CRIAR` e `ATUALIZAR`). Duas atualizações com a mesma revisão produziram exatamente um sucesso e um conflito HTTP 409. Rejeições de identidade, autorização, anexos e conteúdo inválido não sobrescreveram o documento nem adicionaram eventos. A releitura HTTP e SQL após reinício retornou o mesmo conteúdo, a mesma revisão e os mesmos eventos. Preços/consumos textuais com oito casas decimais foram preservados; não se comprovou um cálculo de custo oficial do Linx.

Foram aprovados 135 testes de backend e 263 de frontend, além da execução real descrita no relatório. A cobertura unitária de falha da auditoria/rollback não deve ser apresentada como injeção de falha no banco real. Também não foram homologados escrita ERP, anexos compartilhados, matriz definitiva de permissões, produção ou todas as fases de negócio. A conexão de homologação tinha `sysadmin`; uma credencial definitiva de menor privilégio ainda precisa ser definida. O limite técnico de 1 MiB usado no teste não define o limite de produção. Os registros sintéticos foram preservados para conferência, sem exclusão automática.

Referências técnicas oficiais: [ISJSON, com assinatura compatível com SQL Server 2019](https://learn.microsoft.com/en-us/sql/t-sql/functions/isjson-transact-sql), [CONNECTIONPROPERTY](https://learn.microsoft.com/en-us/sql/t-sql/functions/connectionproperty-transact-sql), [limites de capacidade do SQL Server](https://learn.microsoft.com/en-us/sql/sql-server/maximum-capacity-specifications-for-sql-server), [sys.check_constraints](https://learn.microsoft.com/en-us/sql/relational-databases/system-catalog-views/sys-check-constraints-transact-sql).
