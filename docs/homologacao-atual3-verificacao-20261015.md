# Troca de destino e verificação — 15/10/2026

## Autorização e limites

Destino confirmado pelo usuário: `192.168.1.3:1433 / HOMOLOGACAO_ATUAL`.
O usuário autorizou teste de gravação apenas **criando nova versão**, sem alterar a ficha existente, usando qualquer referência/coleção.
Essa autorização não foi tratada como permissão para usar o gravador de atualização da versão atual, alterar o banco original, mudar permissões ou renomear banco SQL.

## Alterações feitas no projeto

- `.env.local`: URL de leitura apontada a `HOMOLOGACAO_ATUAL`, com criptografia e certificado conforme a configuração existente; demais credenciais preservadas e não exibidas.
- `MODAFLOW_LINX_EXPECTED_DATABASE=HOMOLOGACAO_ATUAL` configurado explicitamente.
- Default `LinxDatabaseClient.REQUIRED_DATABASE` e exemplo/documentação atualizados.
- Validação antes do pool do conector normal exige endereço literal `.3:1433`, banco confirmado e `encrypt=true`, rejeitando `KINGEJOE`, `HOMOLOGACAO_COBRANCA`, `HOMOLOG_ATUAL`, outro IP e roteamento/aliases.
- Perfil opt-in `.6/HOMOLOG_COBRANCA` preservado isolado para compatibilidade; relatórios antigos não foram reescritos como se fossem evidência do novo ambiente.
- Gravador de atualização existente permaneceu restrito ao `.6` e desativado. Não foi redirecionado à `.3` contrariando a restrição de criar somente uma versão nova.
- Ferramenta `tools/Inspect-HomologAtual3.ps1`: alvo literal, credencial Windows protegida em memória e somente SELECT de identidade/metadados. Não executa os textos de módulos retornados. O resumo padrão omite definições SQL extensas.

## Evidência do banco

Consulta SELECT com `SqlCredential` e TLS confirmou:

| Propriedade | Resultado |
| --- | --- |
| Banco atual | HOMOLOGACAO_ATUAL |
| Servidor | SRV-LINX |
| Endereço | 192.168.1.3 |
| Porta | 1433 |
| Criptografia efetiva | TRUE |

Foram lidos metadados das cinco tabelas já relacionadas à ficha. Isso **não** substitui auditoria transitiva dos efeitos de INSERT/triggers nem aprova gravação. Não houve DML, execução de procedure, DDL ou alteração de permissão.

## Verificação pelo site

Usada uma aba separada do Edge no portal local, preservando a coleção/ficha que o usuário já tinha aberta.

1. Sessão autenticada de Administrador AKR permaneceu disponível.
2. Relatórios → Peças carregou a fonte oficial.
3. O indicador do banco apresentou **HOMOLOGACAO_ATUAL**.
4. Listagem apresentou **11.669 produtos**, página **1 de 234**, sinalizada como somente leitura.
5. Nenhum erro capturado no console da aba de verificação.

Evidência: `C:/King-Joe-Projects/outputs/homologacao-atual3-portal-20261015.png`.

## Resultado do teste solicitado

- **Site → API → leitura do novo banco → listagem: verificado.**
- **Criação de nova versão: não executada.** O backend atual possui `/linx/integracao/preparar` e `/confirmar` para deltas na versão atual, mas não possui uma operação de criação de versão. Seus contratos não incluem essa operação e o serviço rejeita versão diferente da atual.
- Não foi emitido recibo, simulada integração ou criada versão local como se estivesse no ERP. Nenhuma ficha existente foi alterada.

Aplicando a orientação de verificação ponta a ponta, o teste de gravação parou na primeira fronteira sem suporte: a criação da versão na API. Não foi substituído por uma atualização da ficha original nem por INSERT avulso fora do site.

## Próximo passo necessário para o teste de escrita

Implementar e validar uma operação separada de criação de nova versão, com:

1. Identidade explícita de produto/griffe/coleção e versão de referência.
2. Regra comprovada de reserva da nova versão, sem colisão e sem assumir o default `00001`.
3. Inserção do cabeçalho e itens/vínculos na nova versão, respeitando chaves, defaults e efeitos dos triggers.
4. Política explícita para o ponteiro `PRODUTOS.VERSAO_FICHA`: criar uma versão não autoriza substituir silenciosamente a referência atual nem alterar o cadastro compartilhado.
5. Transação e comprovação, antes/depois, de que as linhas da versão original foram preservadas.
6. Recibo específico de criação e releitura dessa nova versão no portal, sem confundir com recibo parcial de atualização.

As orientações de configuração segura mantiveram segredos fora dos artefatos e preservaram as variáveis locais. As orientações de backend mantiveram Spring/SQL Server e separação de leitura/escrita. A orientação de verificação impediu avançar por uma operação que não atende à autorização do usuário.
