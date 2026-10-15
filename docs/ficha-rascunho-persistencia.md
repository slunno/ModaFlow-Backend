# Rascunho central ModaFlow (opt-in homolog .6)

**Experimento encerrado por decisão posterior do usuário em 07/10/2026:** rascunho e histórico somente no navegador. A configuração e o controller centrais não são mais registrados, mesmo com `enabled=true`; o launcher foi bloqueado e o helper recusa aplicar a migração. Este documento preserva o desenho anterior. As tabelas do `.6` permanecem fisicamente no banco até aprovação separada da exclusão. Consulte [armazenamento local](C:/King-Joe-Projects/modaflow-frontend/docs/ficha-rascunho-local.md).

O recurso está desativado por padrão. Esta implementação não envia dados ao Linx,
não altera materiais/cores globais e não confirma integração nem aprovação de peça.
Revisão de rascunho (`revisao`) não equivale a `VERSAO_FICHA` do ERP.

Em **07/10/2026**, após aprovação específica, as estruturas próprias foram aplicadas
e reaplicadas em `.6/HOMOLOG_COBRANCA`. O rascunho e sua auditoria foram homologados
por SQL e HTTP real, com concorrência e persistência após reinício. Isso não ativa
o recurso no portal normal. As evidências e limitações estão no
[relatório de homologação](C:/King-Joe-Projects/modaflow-frontend/docs/ficha-rascunho-homologacao-resultado.md).

## Configuração e isolamento

O prefixo é `modaflow.ficha.persistence`; veja o exemplo inativo em
`src/main/resources/examples/ficha-rascunho-homolog6.yml.example`. Ao habilitar, é
obrigatório informar `maximum-document-bytes` positivo e credenciais fora da URL.
Esse limite mede o JSON UTF-8 de conteúdo e referências, não uma regra de negócio.
Não houve alteração do DataSource primário, dos flags ou dos `.env`/YAML da
configuração normal. Foi acrescentado apenas o YAML
`application-ficha-homolog6-test.yml`, selecionado explicitamente pelo launcher
isolado descrito abaixo; ele não redireciona o backend normal.

A guarda local ocorre antes da criação do pool e permite somente endereço literal
`.6:1433`, banco `HOMOLOG_COBRANCA`, driver Microsoft e `encrypt=true`. A conexão
usada em cada operação é novamente verificada por `DB_NAME()` e `CONNECTIONPROPERTY`
(`local_net_address`/`local_tcp_port`) dentro da transação. Divergência bloqueia a
operação, sem procurar o primário ou outro banco. O holder dedicado não é bean
`DataSource`, impedindo que o Spring Boot o use no lugar do JPA primário.

O adaptador só usa `modaflow.ficha_documento` e `modaflow.ficha_auditoria`.
Migração manual e habilitação operacional são passos separados. Nesta homologação,
a criação do schema `modaflow`, das duas tabelas e a gravação sintética no `.6`
foram confirmadas; a habilitação operacional normal permanece desativada.
`.3`/`KINGEJOE` continuam fora do acesso desta implementação.

SHA-256 da migração aplicada e reaplicada:
`B3F15F9BB175189A037D52BD47D3B30BC73B5ADAB7E4EEA141C3BF14BD66B715`.
O helper de migração usa `-ApplyMigration` para aplicar DDL após aprovação e
`-InspectSchema` para consulta de metadata; sem o switch de aplicação, não aplica
DDL. A migração não criou grants ou usuário SQL definitivo.

## API

Caminhos relativos ao context path existente `/api/v1`:

- `GET /fichas/rascunhos/{colecaoId}/pecas/{pecaId}`: Snapshot direto ou 404.
- `PUT` no mesmo caminho: criação ou atualização CAS explícita.
- `GET .../historico?afterEventoId={uuid}&limit=50`: lista direta de AuditEntry,
  ordem crescente de horário + ID; limite entre 1 e 100.

JWT e `X-Marca-Id` existentes são exigidos. O servidor verifica o vínculo do usuário
à marca antes de ler/gravar/listar. A marca da identidade vem do tenant validado,
nunca de um `actor` enviado pelo cliente. A política de cargos é a existente;
nenhuma matriz nova de edição/aprovação foi presumida neste módulo.

Envelope PUT:

```json
{
  "schemaVersion": 1,
  "expectedRevision": null,
  "conteudo": {"id": "peca-id", "marcaId": "marca-id", "colecaoId": "colecao-id"},
  "anexos": []
}
```

O conteúdo é o objeto completo da peça, projetado pelo frontend sem mídias locais.
Pode ter campos de negócio ainda vazios: salvar rascunho não exige aprovação de
entrega nem cadastro fiscal completo. `id`, `marcaId` e `colecaoId` precisam ser
strings consistentes com a identidade; IDs têm até 128 caracteres e não aceitam
espaços nas extremidades, evitando aliases de comparação do `nvarchar` no SQL.
Preço/consumo enviados como strings são mantidos sem conversão ou arredondamento.

`expectedRevision=null` exige que o rascunho não exista. Para atualizar, informar a
revisão atual lida; existente ao criar, ausente ao atualizar ou revisão divergente
resultam em 409 `REVISION_CONFLICT`, com `expectedRevision` e `currentRevision`.
O cliente deve consultar/conferir o estado atual; nunca reaplicar automaticamente
a revisão retornada pelo erro. Documento + auditoria antes/depois são confirmados
na mesma transação com bloqueio da chave e CAS no UPDATE. Falha de auditoria ou
commit não é tratada como sucesso nem como fallback local de persistência central.

Snapshot: `identity`, `schemaVersion`, `revisao`, `conteudo`, `anexos`, `criadoEm`,
`criadoPor`, `atualizadoEm`, `atualizadoPor`. Atores são `{usuarioId,nome}` autenticados;
datas são UTC com precisão de micros e binding/leitura JDBC com Calendar UTC.
AuditEntry: `eventoId`, `operation` (`CRIAR`/`ATUALIZAR`), `antes` (null na criação),
`depois` (Snapshot). Autores/data dentro de histórico legado no conteúdo não são
tratados como autoria autenticada do servidor.

## Anexos e pendências

Anexos físicos continuam apenas no navegador, conforme a decisão do usuário.
`anexos` deve ser uma lista vazia. Não há upload, tabela de metadados, implementação
da porta de arquivos, conversão de blob URLs, descarte de arquivos ou migração de
anexos. Texto `data:`/`blob:` e binários embutidos são rejeitados no documento central.
O frontend deve projetar sem modificar/excluir a cópia local e explicar que os
arquivos não estarão em outros computadores. Links comuns não viram arquivos
duráveis por esta gravação.

A suíte unitária usa mocks JDBC/controle transacional e MockMvc isolado para
contratos, autorização, conflitos, auditoria/rollback e guards. Nesta execução,
135 testes de backend e 263 de frontend foram aprovados. Adicionalmente, o teste
real autorizado confirmou schema, round-trip do JSON, conflito de revisão entre
duas requisições simultâneas, autoria autenticada, anexos vazios e persistência
após reinício do backend isolado. Falha provocada da auditoria e rollback continuam
com cobertura unitária apenas; não se alteraram permissões ou triggers no SQL
para provocar a falha. O teste não comprovou regras de custo Linx ou integração.

Não iniciar o backend normal para testar enquanto seu DataSource primário puder
apontar `.3`. O launcher isolado abaixo exclui essa configuração e não tem
fallback para ela. A credencial de homologação observada tinha `sysadmin`; os
guards de aplicação não substituem uma credencial definitiva de menor privilégio.

## Launcher isolado para o teste real autorizado

`tools/Start-FichaHomolog6Backend.ps1` é uma inicialização explicitamente separada,
não uma mudança do backend normal. Exige PowerShell 7, JAR previamente empacotado
com o YAML isolado e a credencial PSCredential DPAPI já aprovada. O launcher não
executa build, migração ou envio ERP e não habilita o frontend. Seu único SQL
DataSource é o de rascunhos `.6/HOMOLOG_COBRANCA`; Linx fica desativado.

O primário usa exclusivamente H2 local `target/homolog6-test/auth`, com fixtures
do perfil dev para autenticação/roles. A pasta precisa estar dentro do workspace,
sem links, e ter marker próprio; arquivos preexistentes sem marker não são usados.
Esse H2 não é o banco próprio de produção. É preservado no restart para não perder
as identidades/vínculos de marca do teste e pode desaparecer com `mvn clean`.

A JVM é iniciada com `ProcessStartInfo` sem shell/janela, ambiente allowlisted,
somente `application-ficha-homolog6-test.yml` e profiles `dev,ficha-homolog6-test`.
Overrides críticos são argumentos fixos, não credenciais: loopback `127.0.0.1`,
porta 18081 por padrão, H2 file local, driver/dialect H2, Linx false e URL de
rascunhos `.6` criptografada. A senha DPAPI e o segredo JWT aleatório vivem só no
ambiente/memória do processo filho; não entram na command line ou arquivos.
O limite 1 MiB e o TTL JWT de uma hora são configurações técnicas deste teste.

O launcher permanece em foreground para drenar stdout/stderr para `logs/`
(gitignored), emitindo primeiro PID, starttime, alvo, URL e caminhos seguros. O
status `LaunchedNotReady` não prova prontidão da API nem acesso ao banco. Outra
chamada com `-Restart` só pode encerrar o processo que coincidir com PID, starttime,
caminho java e command line completos registrados. Não mata serviços por porta,
nome ou PID sem conferência. Após restart, autenticar novamente: o JWT é renovado,
enquanto as fixtures/vínculos H2 e os rascunhos SQL permanecem.

O perfil isolado usa o conector Tomcat NIO2 para contornar a falha AF_UNIX/pipe do
JDK Windows instalado, sem mudar firewall/segurança do sistema. A auto-configuração
do usuário de segurança padrão do Spring Boot é excluída só nesse teste: login,
BCrypt e JWT continuam nos componentes próprios existentes. O backend normal
não recebe essas mudanças de protocolo/auto-configuração.

Referências oficiais para a configuração de teste:
[conectores HTTP do Tomcat](https://tomcat.apache.org/tomcat-10.1-doc/config/http.html)
e [TomcatServletWebServerFactory do Spring Boot 3.2.3](https://docs.spring.io/spring-boot/docs/3.2.3/api/org/springframework/boot/web/embedded/tomcat/TomcatServletWebServerFactory.html).
