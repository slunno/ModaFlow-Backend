# Gravador direto Linx — primeira entrega, 15/10/2026

## Estado real

Código de gravação direta por JDBC implementado, **desativado por padrão e não homologado em SQL real**. Não depende de uma API/procedure de gravação do Linx. Não foram executados SELECTs nem DML em SQL Server nesta entrega. Nenhum acesso ao .3, criação de tabela, migration, alteração de permissão ou inicialização do backend conectado a banco real.

O custo e a precificação do portal não equivalem a NCM/tributação. Como o usuário confirmou que os demais dados fiscais não virão do site, o primeiro escopo preserva o cadastro fiscal de um produto **já existente**, sem inventar valores ou copiar uma peça modelo.

## O que o gravador executa, se aprovado e habilitado

| Operação explícita | Destino |
| --- | --- |
| Alterar nome, com consentimento para o efeito compartilhado | `PRODUTOS.DESC_PRODUTO` |
| Alterar uso e consumos por tamanho na grade real | `PRODUTO_VERSAO_MATERIAL.DESC_USO_MATERIAL`, `C1…C48` |
| Alterar cor básica de uma cor do produto já identificada | `PRODUTO_CORES.COR`, com `COR_PRODUTO` existente e coleção explícita |
| Alterar cor de material de um vínculo já identificado | `PRODUTO_VERSAO_MATERIAL_COR.COR_MATERIAL`, com chave completa |
| Descartar material removido, mediante confirmação | Remove seus vínculos de cor nesta versão e depois o item, com produto/versão/material/item |
| Descartar vínculo individual de cor, mediante confirmação via API | Remove apenas a chave completa do vínculo |

Não troca código, griffe, coleção, grade ou versão. Não cria produto novo, versão nova, material novo, cor global, preço global ou cadastro fiscal. O cabeçalho `PRODUTO_FICHA_VERSAO` é relido para conferir a versão e a coleção, não alterado.

## Dados ainda fora da gravação

- Produto inexistente: bloqueado até existir um perfil fiscal/operacional aprovado fora do portal, ou cadastro efetuado no ERP.
- Nova ficha de um código reutilizado em outra coleção: bloqueada nesta entrega. Não assume que filtrar coleção cria identidade nova.
- Material incluído ou substituído: faltam regra de item e valores explícitos de fase/setor; bloqueia o envio, sem copiar de outro material.
- Cor: falta de vínculo explícito `COR_PRODUTO` não é resolvida por nome, hex, ordem do array ou pela cor do material. Vínculos antigos válidos podem ser usados; mudanças ambíguas são bloqueadas.
- Preço/custo/fornecedor por item, totais de custos e precificação: não são escritos em colunas fiscais nem no catálogo. O destino desses valores segue pendente.
- Categoria descritiva não vira `COD_CATEGORIA`. Descrição detalhada, Técnica e demais observações sem destino aprovado não são escritas.
- Variantes adicionais, cores personalizadas, anexos e histórico continuam no portal.

O recibo é **parcial**, identificado por `escopo=CAMPOS_MAPEADOS`. Não cria histórico de versão nem limpa a pendência da ficha inteira. O portal guarda a última confirmação em `linxUltimaGravacao` e atualiza somente a base dos campos confirmados.

## Fluxo HTTP autenticado

1. `GET /api/v1/linx/integracao/capacidade`: informa habilitação sem consultar o SQL.
2. `GET /api/v1/linx/integracao/revisao`: opcional e exclusivamente SELECT; exige `review-enabled`, usuário autorizado e pool restrito. Retorna hash e nomes dos objetos bloqueados, não suas definições. O hash não aprova nada automaticamente.
3. `POST /api/v1/linx/integracao/preparar`: conferência da Principal, autorização, identidade efetiva da conexão, revisão de dependências e releitura da base. Não executa DML. Retorna antes/depois e token de cinco minutos, vinculado a usuário e marca.
4. Usuário confere os campos, confirma descarte quando necessário e confirma separadamente alteração de nome compartilhado.
5. `POST /api/v1/linx/integracao/confirmar`: recebe somente token e consentimentos. Revalida tudo em transação SERIALIZABLE com locks, rejeita concorrência, grava deltas e relê o resultado. Falha na releitura provoca rollback. Recibo só retorna após commit.

Não há tentativa automática após falha na confirmação: uma resposta perdida pode ocorrer depois de commit. Consulte o ERP e prepare outra prévia antes de repetir. O token é de uso único, em memória; reiniciar o backend invalida prévias antigas. Não há promessa de idempotência persistente entre reinícios.

## Isolamento e ativação futura

Exemplo **não carregado**: `src/main/resources/examples/linx-write-homolog6.yml.example`.

- Configuração independente da leitura e do DataSource JPA; nunca herda a URL/credencial normal.
- URL literal permitida: `jdbc:sqlserver://192.168.1.6:1433;databaseName=HOMOLOG_COBRANCA;encrypt=true;trustServerCertificate=true`.
- `.3`, aliases, outras bases, opções de roteamento e URL sem criptografia são rejeitados **antes da criação do pool**.
- Não aceita `sa`; a sessão efetiva também não pode ser sysadmin. Criar/conceder acesso a esse usuário não faz parte desta entrega.
- Configurar IDs reais de usuários permitidos e mapa explícito `tenant-griffes`, além da associação autenticada do usuário à marca.
- O usuário SQL deve ter os direitos locais mínimos para DML aprovado e leitura de metadados/identidade da sessão. Sem visibilidade de metadados, a guarda falha fechada. Não conceder acesso a bancos originais nem usar EXECUTE AS/ownership chaining para contornar o isolamento.
- Iniciar com `enabled=false`, `review-enabled=true` permite levantar a revisão somente leitura. Revisar definições/dependências e FKs/defaults com a TI. Dependências externas, sinônimos, módulos criptografados/dinâmicos ou travessia não resolvida bloqueiam escrita. O hash do relatório deve ser aprovado por uma pessoa, não copiado automaticamente para habilitar o gravador.
- Somente depois da revisão e aprovação dos testes, configurar `approved-schema-sha256` e habilitar escrita. Mudança no retrato invalida a aprovação.
- Não desabilita, remove nem altera triggers. Referências ao original precisam ser resolvidas por um trabalho separado aprovado; esta entrega não faz a correção automaticamente.

## Roteiro proposto para a primeira homologação (não executado)

1. Escolher uma ficha descartável existente na cópia, com vínculo explícito de coleção e versão atual; identificar o estado anterior e os efeitos esperados dos triggers.
2. Aprovar por escrito produto/versão, campos, valores e eventual descarte, além do plano de recuperação. Não começar usando cadastro real de referência como alvo descartável.
3. Levantar a revisão somente leitura e aprovar o retrato com usuário SQL restrito ao .6.
4. Testar uso/consumo de um item e alteração de nome separadamente; conferir releitura no ERP e efeitos nas dependências.
5. Testar conflito concorrente, falha/rollback, descarte de filhos/pai, repetição de token e resposta perdida. Não desabilitar constraints/triggers para fazer passar.
6. Reverter o cadastro de teste de acordo com plano aprovado e só então decidir a próxima ampliação do gravador. Esta etapa não autoriza produção.

## Verificação de código

Testes unitários de SQL com JDBC simulado e teste do TransactionTemplate com Connection simulado: sem driver/socket real. Testes React verificam DTO, exclusão de dados locais, grade com lacunas, antes/depois, consentimentos, gravador desligado, mudança após prévia e resposta perdida. Esses testes **não substituem a homologação da escrita no SQL Server real**.

As orientações de arquitetura mantiveram Spring/SQL Server, separando conector de leitura e gravador. A revisão React preservou labels, controle de carregamento e descarte de respostas tardias; não introduziu framework ou dependência nova.
