package com.modaflow.backend.integracao.linx.write;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.ficha.FichaConferenciaService;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import static com.modaflow.backend.integracao.linx.write.LinxWriteContracts.*;
import static com.modaflow.backend.integracao.linx.write.LinxWriteRules.*;

/** Escrita versionada por coleção. Nunca modifica o cadastro global do produto. */
@Service
public class LinxWriteService {
    static final Set<String> GRIFFES = Set.of("KING&JOE", "K&J BLACK", "KING&JOE PLAY");
    static final List<String> WARNINGS = List.of(
        "Escopo parcial: somente os campos discriminados serão gravados na versão atual existente.",
        "Custo, precificação, preço/fornecedor por item, categoria descritiva, observações sem destino confirmado, variantes e anexos permanecem no portal.",
        "PRODUTOS e PRODUTO_CORES são compartilhados e não são alterados por este fluxo.");
    private final LinxWriteProperties properties;
    private final Optional<LinxWriteDatabase> database;
    private final FichaConferenciaService conference;
    private final ConcurrentHashMap<UUID, Plan> plans = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, VersionPlan> versionPlans = new ConcurrentHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private record Plan(long tenant, long user, Instant expires, Request request, String fingerprint,
                        List<Change> changes, boolean global) {}
    private record Snapshot(Map<String,Object> product, Map<String,Object> header,
                            Map<String,Object> collection, Map<String,Object> grade,
                            List<Map<String,Object>> materials, List<Map<String,Object>> colors,
                            List<Map<String,Object>> materialColors) {}
    private record VersionPlan(long tenant, long user, Instant expires, VersionRequest request,
                               Identity target, String fingerprint, List<Change> changes) {}
    private static final List<String> VERSION_TABLES = List.of("PRODUTO_FICHA_VERSAO", MATERIAL, MATERIAL_COLOR);
    private static final List<String> VERSION_WARNINGS = List.of(
        "Nova versão vinculada exclusivamente à coleção de destino. A referência e as versões existentes serão preservadas.",
        "PRODUTOS, seu ponteiro VERSAO_FICHA e PRODUTO_CORES não serão alterados. Nome global e cores globais não são versionados.",
        "Cópia da ficha selecionada e dos deltas listados, não cadastro de produto novo nem envio integral. Preços manuais, anexos e variantes adicionais continuam no portal.");

    public LinxWriteService(LinxWriteProperties properties, Optional<LinxWriteDatabase> database,
                            FichaConferenciaService conference) {
        this.properties = properties; this.database = database; this.conference = conference;
    }
    public Capability capability() {
        boolean enabled = properties.isEnabled() && database.isPresent();
        return new Capability(enabled, properties.server()+":1433", properties.database(), "FICHA_POR_COLECAO",
            enabled ? "Gravador direto de homologação habilitado; exige prévia e confirmação."
                    : "Gravação desativada. Configure o gravador exclusivo de homologação, com usuário SQL restrito, marcas/usuários autorizados e esquema revisado.");
    }
    private LinxWriteDatabase db() {
        if (!capability().habilitada()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, capability().mensagem());
        return database.orElseThrow();
    }
    private void scope(long tenant, Identity identity) {
        if (identity == null || !GRIFFES.contains(identity.griffe())
                || !identity.griffe().equals(properties.getTenantGriffes().get(tenant)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "A griffe não corresponde à marca autorizada do portal.");
        text(identity.produto(),12,"Produto",false); text(identity.versao(),5,"Versão",false);
        text(identity.colecao(),6,"Coleção",false); text(identity.descColecao(),200,"Descrição da coleção",false);
    }
    private void writer(long user) {
        if (!properties.getAllowedUserIds().contains(user))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Usuário sem autorização explícita para o gravador de homologação.");
    }
    /** Levanta o hash para revisão sem exigir aprovação prévia e sem habilitar DML. */
    public SchemaReview review(long user) {
        writer(user);
        if (!properties.isReviewEnabled() || database.isEmpty())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Revisão SQL desativada; configure review-enabled separadamente da escrita.");
        return database.orElseThrow().transaction(true,jdbc -> {
            guardConnection(jdbc);
            var modules=jdbc.queryForList(SCHEMA_SQL,Map.of());
            if (modules.isEmpty()) throw conflict("Não foi possível ler os metadados de dependências.");
            var blocked=modules.stream().filter(row -> Boolean.TRUE.equals(row.get("BLOQUEADO")))
                .map(row -> Objects.toString(row.get("name"),"Objeto não identificado")).toList();
            return new SchemaReview(properties.server()+":1433",properties.database(),hash(modules),modules.size(),blocked,
                "Somente SELECT. O hash não é aprovação; revise triggers/dependências e permissões antes de configurar approved-schema-sha256. Objetos bloqueados impedem escrita mesmo com hash aprovado.");
        });
    }
    public synchronized Preview prepare(long tenant, long user, Request request) {
        db(); writer(user); scope(tenant, request.identidade());
        if (!request.conferencia().paraIntegracao()) throw invalid("Envie a conferência da Principal para integração.");
        conference.exigirValida(request.conferencia());
        var patches = normalizeRequest(request);
        plans.entrySet().removeIf(entry -> entry.getValue().expires().isBefore(Instant.now()));
        if (plans.size() >= 200) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Há muitas prévias pendentes. Aguarde sua expiração.");
        return db().transaction(true, jdbc -> {
            guard(jdbc);
            var snapshot = snapshot(jdbc, request.identidade(), false);
            var normalized = new Request(request.identidade(), request.conferencia(), patches);
            validateConference(jdbc, normalized, snapshot);
            var changes = changes(jdbc, request.identidade(), patches, snapshot);
            if (changes.isEmpty()) throw invalid("Nenhuma alteração mapeada difere dos dados atuais do Linx.");
            var id = UUID.randomUUID(); var expires = Instant.now().plusSeconds(300);
            boolean global = changes.stream().anyMatch(change -> PRODUCT.equals(change.tabela()));
            var immutable = new Request(request.identidade(), request.conferencia(), List.copyOf(patches));
            plans.put(id, new Plan(tenant,user,expires,immutable,hash(snapshot),List.copyOf(changes),global));
            return new Preview(id,expires,request.identidade(),List.copyOf(changes),WARNINGS,global);
        });
    }
    /** O token é de uso único e atrelado ao usuário/marca. Nenhum campo SQL é recebido na confirmação. */
    public synchronized Receipt confirm(long tenant, long user, Confirm confirmation) {
        db(); writer(user);
        var plan = plans.get(confirmation.planoId());
        if (plan == null || plan.tenant()!=tenant || plan.user()!=user)
            throw conflict("Prévia inexistente, expirada ou de outra sessão. Confira novamente.");
        scope(tenant, plan.request().identidade());
        if (!confirmation.confirmar() || (plan.global() && !confirmation.confirmarCadastroCompartilhado()))
            throw invalid("Confirme a gravação e, quando aplicável, o efeito no cadastro compartilhado.");
        plans.remove(confirmation.planoId());
        if (plan.expires().isBefore(Instant.now())) throw conflict("A prévia expirou. Confira novamente.");
        return db().transaction(false, jdbc -> {
            guard(jdbc);
            var identity = plan.request().identidade();
            var current = snapshot(jdbc, identity, true);
            if (!plan.fingerprint().equals(hash(current)))
                throw conflict("A ficha foi alterada no Linx após a prévia. Nada foi gravado; confira novamente.");
            conference.exigirValida(plan.request().conferencia());
            validateConference(jdbc, plan.request(), current);
            // Revalida FKs/catálogos dentro da transação, não apenas na preparação.
            changes(jdbc, identity, plan.request().alteracoes(), current);
            var ordered = plan.request().alteracoes().stream()
                .sorted(Comparator.comparing(patch -> MATERIAL_COLOR.equals(patch.tabela()) ? 0 : 1)).toList();
            for (var patch : ordered) {
                if (patch.excluir()) apply(jdbc, identity, patch);
                else {
                    var existing = row(current,patch.tabela(),patch.chave());
                    var actual = new TreeMap<String,Object>();
                    patch.valores().forEach((field,value) -> { if (!equal(existing.get(field),value)) actual.put(field,value); });
                    if (!actual.isEmpty()) apply(jdbc,identity,new Patch(patch.tabela(),patch.chave(),actual,false,false));
                }
            }
            var after = snapshot(jdbc, identity, true);
            verifyApplied(plan.changes(), after);
            // O recibo só sai do método depois de o TransactionTemplate confirmar COMMIT.
            return new Receipt("CONFIRMADA","CAMPOS_MAPEADOS",UUID.randomUUID(),Instant.now(),identity,
                plan.changes().size(),WARNINGS);
        });
    }
    /** Prévia SELECT-only. O número é proposto, não reservado fora da transação de confirmação. */
    public synchronized Preview prepareVersion(long tenant, long user, VersionRequest request) {
        db(); writer(user); scope(tenant,request.origem());
        if (request.pecaId()==null || request.destino()==null) throw invalid("Peça e coleção de destino obrigatórias.");
        text(request.descricaoVersao(),40,"Descrição da versão",false);
        if (!request.conferencia().paraIntegracao()) throw invalid("Envie a conferência da Principal para integração.");
        conference.exigirValida(request.conferencia());
        var patches = request.alteracoes().isEmpty() ? List.<Patch>of() : normalizeRequest(
            new Request(request.origem(),request.conferencia(),request.alteracoes()));
        var normalized = new VersionRequest(request.pecaId(),request.origem(),request.destino(),
            request.descricaoVersao(),request.conferencia(),patches);
        versionPlans.entrySet().removeIf(entry -> entry.getValue().expires().isBefore(Instant.now()));
        if (versionPlans.size()>=200) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Aguarde a expiração das prévias pendentes.");
        return db().transaction(true,jdbc -> {
            guard(jdbc);
            var source=snapshot(jdbc,request.origem(),false,true);
            var target=versionTarget(jdbc,normalized,source,false);
            scope(tenant,target);
            var patchRequest=new Request(request.origem(),request.conferencia(),patches);
            validateConference(jdbc,patchRequest,source);
            var deltas=changes(jdbc,request.origem(),patches,source);
            var listed=new ArrayList<Change>();
            listed.add(new Change("PRODUTO_FICHA_VERSAO",Map.of(),"VERSAO_FICHA",null,target.versao(),false));
            listed.add(new Change("PRODUTO_FICHA_VERSAO",Map.of(),"U_COLECAO",null,target.colecao(),false));
            listed.add(new Change("PRODUTO_FICHA_VERSAO",Map.of(),"DESC_VERSAO_FICHA",null,request.descricaoVersao(),false));
            listed.add(new Change(MATERIAL,Map.of(),"ITENS_COPIADOS",null,source.materials().size(),false));
            listed.add(new Change(MATERIAL_COLOR,Map.of(),"VINCULOS_COPIADOS",null,source.materialColors().size(),false));
            listed.addAll(deltas);
            var id=UUID.randomUUID(); var expires=Instant.now().plusSeconds(300);
            versionPlans.put(id,new VersionPlan(tenant,user,expires,normalized,target,
                hash(protectedRows(jdbc,target,false)),List.copyOf(listed)));
            return new Preview(id,expires,target,List.copyOf(listed),VERSION_WARNINGS,false);
        });
    }

    public synchronized Receipt confirmVersion(long tenant,long user,Confirm confirmation) {
        db(); writer(user);
        var plan=versionPlans.get(confirmation.planoId());
        if (plan==null || plan.tenant()!=tenant || plan.user()!=user) throw conflict("Prévia inexistente, expirada ou de outra sessão.");
        scope(tenant,plan.target());
        if (!confirmation.confirmar()) throw invalid("Confirme a criação de uma nova versão.");
        versionPlans.remove(confirmation.planoId());
        if (!plan.expires().isAfter(Instant.now())) throw conflict("A prévia expirou. Confira novamente.");
        return db().transaction(false,jdbc -> {
            guard(jdbc);
            var before=protectedRows(jdbc,plan.target(),true);
            if (!plan.fingerprint().equals(hash(before))) throw conflict("O produto ou suas fichas mudaram após a prévia. Nada foi gravado.");
            var request=plan.request();
            var source=snapshot(jdbc,request.origem(),true,true);
            var target=versionTarget(jdbc,request,source,true);
            if (!target.equals(plan.target())) throw conflict("Outra integração reservou a versão. Confira novamente.");
            conference.exigirValida(request.conferencia());
            validateConference(jdbc,new Request(request.origem(),request.conferencia(),request.alteracoes()),source);
            var deltas=changes(jdbc,request.origem(),request.alteracoes(),source);
            var parameters=new HashMap<String,Object>(params(request.origem()));
            parameters.put("novaVersao",target.versao());
            parameters.put("novaColecao",target.colecao());
            parameters.put("novaDescricao",request.descricaoVersao());
            parameters.put("novoResponsavel","MODAFLOW USER "+user);
            var observation="MODAFLOW:"+request.pecaId()+" "+Objects.toString(source.header().get("OBS_VERSAO_FICHA"),"").trim();
            parameters.put("novaObservacao",text(observation.trim(),250,"Observação da nova versão",false));
            for (var table:VERSION_TABLES) {
                var columns=copyColumns(jdbc,table);
                var overrides=new HashMap<String,String>(); overrides.put("VERSAO_FICHA",":novaVersao");
                if (table.equals("PRODUTO_FICHA_VERSAO")) overrides.putAll(Map.of(
                    "U_COLECAO",":novaColecao","DESC_VERSAO_FICHA",":novaDescricao",
                    "RESPONSAVEL",":novoResponsavel","DATA_CRIACAO","SYSDATETIME()","OBS_VERSAO_FICHA",":novaObservacao"));
                var select=columns.stream().map(column -> overrides.getOrDefault(column,"["+column+"]")).collect(Collectors.joining(","));
                int inserted=jdbc.update("INSERT INTO dbo.["+table+"] ("+columns.stream().map(c -> "["+c+"]").collect(Collectors.joining(","))+") SELECT "+select+
                    " FROM dbo.["+table+"] WHERE PRODUTO=:produto AND VERSAO_FICHA=:versao",parameters);
                int expected=table.equals("PRODUTO_FICHA_VERSAO")?1:table.equals(MATERIAL)?source.materials().size():source.materialColors().size();
                if (inserted!=expected) throw conflict("A cópia da ficha não confirmou todas as linhas. Transação desfeita.");
            }
            var copied=snapshot(jdbc,target,true);
            if (!hash(reversion(source.materials(),target.versao())).equals(hash(copied.materials()))
                || !hash(reversion(source.materialColors(),target.versao())).equals(hash(copied.materialColors())))
                throw conflict("A cópia dos materiais/cores diverge da referência. Transação desfeita.");
            // Descarta filhos primeiro; todos os predicados incluem a NOVA versão.
            request.alteracoes().stream().sorted(Comparator.comparing(p -> MATERIAL_COLOR.equals(p.tabela())?0:1))
                .forEach(patch -> {
                    if (patch.excluir()) apply(jdbc,target,patch);
                    else {
                        var actual=new TreeMap<String,Object>(); var existing=row(copied,patch.tabela(),patch.chave());
                        patch.valores().forEach((field,value) -> { if (!equal(existing.get(field),value)) actual.put(field,value); });
                        if (!actual.isEmpty()) apply(jdbc,target,new Patch(patch.tabela(),patch.chave(),actual,false,false));
                    }
                });
            verifyApplied(deltas,snapshot(jdbc,target,true));
            if (!hash(before).equals(hash(protectedRows(jdbc,target,true))))
                throw conflict("Uma ficha anterior ou cadastro compartilhado foi alterado. Transação desfeita.");
            return new Receipt("CONFIRMADA","NOVA_VERSAO_COLECAO",request.pecaId(),Instant.now(),target,
                1+source.materials().size()+source.materialColors().size()+deltas.size(),VERSION_WARNINGS);
        });
    }
    private Identity versionTarget(NamedParameterJdbcTemplate jdbc,VersionRequest request,Snapshot source,boolean lock) {
        text(request.destino().colecao(),6,"Coleção de destino",false);
        text(request.destino().descColecao(),200,"Descrição da coleção de destino",false);
        if (request.origem().colecao().equals(request.destino().colecao()))
            throw invalid("A referência é da mesma coleção. Abra sua própria ficha para editar, sem gerar uma cópia.");
        var hint=lock?" WITH (UPDLOCK,HOLDLOCK) ":" ";
        var collection=one(jdbc.queryForList("SELECT COLECAO,DESC_COLECAO,GRIFFE,INATIVO FROM dbo.COLECOES"+hint+
            "WHERE COLECAO=:colecao",Map.of("colecao",request.destino().colecao())),"Coleção de destino não cadastrada no Linx. Não será criada por inferência.");
        if (!equal(collection.get("DESC_COLECAO"),request.destino().descColecao())
            || !equal(collection.get("GRIFFE"),request.origem().griffe()) || truthy(collection.get("INATIVO")))
            throw conflict("A coleção de destino não corresponde à descrição/griffe ativa.");
        var headers=jdbc.queryForList("SELECT VERSAO_FICHA,U_COLECAO,OBS_VERSAO_FICHA FROM dbo.PRODUTO_FICHA_VERSAO"+hint+
            "WHERE PRODUTO=:produto ORDER BY VERSAO_FICHA",Map.of("produto",request.origem().produto()));
        int next=2;
        for (var header:headers) {
            if (Objects.toString(header.get("OBS_VERSAO_FICHA"),"").startsWith("MODAFLOW:"+request.pecaId()))
                throw conflict("Esta peça já possui uma integração. Consulte a versão no Linx antes de repetir.");
            if (equal(header.get("U_COLECAO"),request.destino().colecao()))
                throw conflict("Já existe ficha deste código nesta coleção. Acesse essa coleção e edite sua própria ficha.");
            var version=Objects.toString(header.get("VERSAO_FICHA"),"").trim();
            if (version.matches("[0-9]{1,5}")) next=Math.max(next,Integer.parseInt(version)+1);
        }
        if (equal(source.product().get("COLECAO"),request.destino().colecao()))
            throw conflict("O produto já pertence à coleção de destino; abra sua ficha existente.");
        if (next>99999) throw conflict("Não há número de versão disponível no limite char(5).");
        return new Identity(request.origem().produto(),request.origem().griffe(),request.destino().colecao(),
            request.destino().descColecao(),String.format(Locale.ROOT,"%05d",next));
    }
    private List<Object> protectedRows(NamedParameterJdbcTemplate jdbc,Identity target,boolean lock) {
        var hint=lock?" WITH (UPDLOCK,HOLDLOCK) ":" ";
        var rows=new ArrayList<Object>(); var parameters=params(target);
        rows.add(jdbc.queryForList("SELECT * FROM dbo.PRODUTOS"+hint+"WHERE PRODUTO=:produto",parameters));
        rows.add(jdbc.queryForList("SELECT * FROM dbo.PRODUTO_CORES"+hint+"WHERE PRODUTO=:produto ORDER BY COR_PRODUTO",parameters));
        for (var table:VERSION_TABLES) rows.add(jdbc.queryForList("SELECT * FROM dbo.["+table+"]"+hint+
            "WHERE PRODUTO=:produto AND VERSAO_FICHA<>:versao ORDER BY VERSAO_FICHA"+
            (table.equals("PRODUTO_FICHA_VERSAO")?"":",MATERIAL,ITEM")+(table.equals(MATERIAL_COLOR)?",COR_PRODUTO":""),parameters));
        return rows;
    }
    private static List<Map<String,Object>> reversion(List<Map<String,Object>> rows,String version) {
        return rows.stream().map(row -> { var copy=new HashMap<>(row); copy.put("VERSAO_FICHA",version); return (Map<String,Object>)copy; }).toList();
    }
    private List<String> copyColumns(NamedParameterJdbcTemplate jdbc,String table) {
        // Apenas as três tabelas fixas; nunca nomes de tabela/coluna recebidos do cliente.
        if (!VERSION_TABLES.contains(table)) throw invalid("Tabela não autorizada para cópia.");
        var rows=jdbc.queryForList("SELECT name FROM sys.columns WHERE object_id=OBJECT_ID(:tabela) AND is_identity=0 AND is_computed=0 AND system_type_id<>189 ORDER BY column_id",Map.of("tabela","dbo."+table));
        var columns=rows.stream().map(row -> Objects.toString(row.get("name"),"")).toList();
        if (!columns.containsAll(List.of("PRODUTO","VERSAO_FICHA")) || columns.stream().anyMatch(c -> !c.matches("[A-Za-z_][A-Za-z_0-9]*")))
            throw conflict("Metadados de cópia indisponíveis ou incompatíveis.");
        return columns;
    }

    private List<Patch> normalizeRequest(Request request) {
        var keys = new HashSet<String>(); var patches = new ArrayList<Patch>();
        if (request.alteracoes()==null || request.alteracoes().isEmpty() || request.alteracoes().size()>500)
            throw invalid("Envie de 1 a 500 alterações explícitas.");
        for (var patch : request.alteracoes()) {
            var values = normalize(patch);
            var key = patch.tabela()+new TreeMap<>(patch.chave());
            if (!keys.add(key)) throw invalid("A mesma linha foi enviada mais de uma vez.");
            patches.add(new Patch(patch.tabela(),Map.copyOf(patch.chave()),values,patch.excluir(),patch.descarteConfirmado()));
        }
        for (var parent : patches) if (MATERIAL.equals(parent.tabela()) && parent.excluir())
            for (var child : patches) if (MATERIAL_COLOR.equals(child.tabela())
                    && Objects.equals(parent.chave().get("MATERIAL"),child.chave().get("MATERIAL"))
                    && Objects.equals(parent.chave().get("ITEM"),child.chave().get("ITEM")))
                throw invalid("Não envie alterações de cor junto ao descarte do mesmo material.");
        return patches;
    }
    private static Map<String,Object> params(Identity id) {
        return Map.of("produto",id.produto(),"versao",id.versao(),"colecao",id.colecao());
    }
    private Snapshot snapshot(NamedParameterJdbcTemplate jdbc, Identity id, boolean lock) {
        return snapshot(jdbc,id,lock,false);
    }
    private Snapshot snapshot(NamedParameterJdbcTemplate jdbc, Identity id, boolean lock, boolean reference) {
        String hint = lock ? " WITH (UPDLOCK,HOLDLOCK) " : " ";
        var parameters = params(id);
        var product = one(jdbc.queryForList("SELECT * FROM dbo.PRODUTOS"+hint+"WHERE PRODUTO=:produto",parameters),
            "Produto inexistente. Cadastro novo exige perfil fiscal/operacional aprovado fora do portal; nenhum valor será copiado de outra peça.");
        if (!equal(product.get("GRIFFE"),id.griffe()) || !equal(product.get("GRUPO_PRODUTO"),"ROUPAS")
                || truthy(product.get("INATIVO")))
            throw conflict("Produto não corresponde à griffe ativa solicitada.");
        var header = one(jdbc.queryForList("SELECT * FROM dbo.PRODUTO_FICHA_VERSAO"+hint+
            "WHERE PRODUTO=:produto AND VERSAO_FICHA=:versao",parameters),"Cabeçalho da versão não encontrado.");
        boolean legacyReference = reference && Objects.toString(header.get("U_COLECAO"),"").isBlank()
            && equal(product.get("COLECAO"),id.colecao()) && equal(product.get("VERSAO_FICHA"),id.versao());
        if (!equal(header.get("U_COLECAO"),id.colecao()) && !legacyReference)
            throw conflict("A ficha não possui vínculo explícito com esta coleção.");
        var latest = reference ? List.<Map<String,Object>>of() : jdbc.queryForList("SELECT TOP (1) VERSAO_FICHA FROM dbo.PRODUTO_FICHA_VERSAO"+hint+
            "WHERE PRODUTO=:produto AND U_COLECAO=:colecao ORDER BY DATA_CRIACAO DESC,VERSAO_FICHA DESC",parameters);
        if (!reference && (latest.size()!=1 || !equal(latest.getFirst().get("VERSAO_FICHA"),id.versao())))
            throw conflict("Esta não é a versão atual desta coleção. Versões anteriores são somente leitura.");
        var collection = one(jdbc.queryForList("SELECT COLECAO,DESC_COLECAO,GRIFFE,INATIVO FROM dbo.COLECOES"+hint+
            "WHERE COLECAO=:colecao",parameters),"Coleção não encontrada.");
        if (!equal(collection.get("GRIFFE"),id.griffe()) || !equal(collection.get("DESC_COLECAO"),id.descColecao())
                || truthy(collection.get("INATIVO"))) throw conflict("Descrição/griffe/estado da coleção divergem do vínculo ERP.");
        var gradeParams = Map.of("grade", Objects.toString(product.get("GRADE"),""));
        var grade = one(jdbc.queryForList("SELECT * FROM dbo.PRODUTOS_TAMANHOS"+hint+"WHERE GRADE=:grade",gradeParams),"Grade do produto não encontrada.");
        return new Snapshot(product,header,collection,grade,
            jdbc.queryForList("SELECT * FROM dbo.PRODUTO_VERSAO_MATERIAL"+hint+"WHERE PRODUTO=:produto AND VERSAO_FICHA=:versao ORDER BY MATERIAL,ITEM",parameters),
            jdbc.queryForList("SELECT * FROM dbo.PRODUTO_CORES"+hint+"WHERE PRODUTO=:produto ORDER BY COR_PRODUTO",parameters),
            jdbc.queryForList("SELECT * FROM dbo.PRODUTO_VERSAO_MATERIAL_COR"+hint+"WHERE PRODUTO=:produto AND VERSAO_FICHA=:versao ORDER BY MATERIAL,ITEM,COR_PRODUTO",parameters));
    }
    private void validateConference(NamedParameterJdbcTemplate jdbc, Request request, Snapshot snapshot) {
        var conferenceSizes = new HashSet<>(request.conferencia().tamanhos());
        var erpSizes = new HashSet<String>();
        for (int i=1;i<=48;i++) {
            var value = Objects.toString(snapshot.grade().get("TAMANHO_"+i),"").trim();
            if (!value.isEmpty() && !erpSizes.add(value)) throw invalid("Grade ERP possui tamanhos repetidos.");
        }
        if (!conferenceSizes.equals(erpSizes)) throw invalid("A grade conferida deve conter exatamente os tamanhos do produto no Linx.");
        var items = request.conferencia().variantes().getFirst().insumos();
        for (var patch : request.alteracoes()) if (MATERIAL.equals(patch.tabela()) && patch.excluir()
                && items.stream().anyMatch(item -> patch.chave().get("MATERIAL").equals(item.codigo())))
            throw invalid("Um material marcado para descarte ainda consta na Principal conferida.");
        for (var patch : request.alteracoes()) if (MATERIAL.equals(patch.tabela()) && !patch.excluir()) {
            if (items.stream().noneMatch(item -> patch.chave().get("MATERIAL").equals(item.codigo())))
                throw invalid("O material alterado não consta na Principal conferida.");
            for (var entry : patch.valores().entrySet()) if (CONSUMPTIONS.contains(entry.getKey())) {
                var position = Integer.parseInt(entry.getKey().substring(1));
                var size = Objects.toString(snapshot.grade().get("TAMANHO_"+position),"").trim();
                if (size.isEmpty()) throw invalid("Consumo aponta para posição vazia da grade.");
                var matches = items.stream().filter(item -> patch.chave().get("MATERIAL").equals(item.codigo())
                    && item.consumoPorTamanho()!=null && item.consumoPorTamanho().containsKey(size))
                    .map(item -> item.consumoPorTamanho().get(size).valor()).distinct().toList();
                if (matches.size()!=1 || !equal(new java.math.BigDecimal(matches.getFirst().replace(',','.')),entry.getValue()))
                    throw invalid("O consumo solicitado diverge da conferência da Principal no tamanho "+size+".");
            }
        }
    }
    private List<Change> changes(NamedParameterJdbcTemplate jdbc, Identity id, List<Patch> patches, Snapshot snapshot) {
        var changes = new ArrayList<Change>();
        for (var patch : patches) {
            var row = row(snapshot,patch.tabela(),patch.chave());
            if (row==null) throw invalid("Linha ERP não encontrada. Inclusão de material/cor precisa de chave e mapeamento próprios; listas não serão substituídas.");
            if (COLOR.equals(patch.tabela())) {
                if (!equal(row.get("U_COLECAO"),id.colecao()) || truthy(row.get("U_INATIVO")))
                    throw invalid("Cor do produto inativa ou sem vínculo explícito com a coleção.");
                requireReference(jdbc,"SELECT COUNT(*) FROM dbo.CORES_BASICAS WHERE COR=:cor",Map.of("cor",patch.valores().get("COR")),"Cor básica inexistente.");
            }
            if (MATERIAL_COLOR.equals(patch.tabela()) && !patch.excluir())
                requireReference(jdbc,"SELECT COUNT(*) FROM dbo.MATERIAIS_CORES WHERE MATERIAL=:material AND COR_MATERIAL=:cor",
                    Map.of("material",patch.chave().get("MATERIAL"),"cor",patch.valores().get("COR_MATERIAL")),"Cor não pertence ao material.");
            if (patch.excluir()) {
                changes.add(new Change(patch.tabela(),patch.chave(),"DESCARTE", "Vínculo existente",null,true));
                if (MATERIAL.equals(patch.tabela())) {
                    long children = snapshot.materialColors().stream().filter(child -> matches(child,patch.chave())).count();
                    if (children>0) changes.add(new Change(MATERIAL_COLOR,patch.chave(),"VINCULOS_DESCARTADOS",children,null,true));
                }
            } else for (var entry : patch.valores().entrySet())
                if (!equal(row.get(entry.getKey()),entry.getValue())) changes.add(new Change(patch.tabela(),patch.chave(),entry.getKey(),row.get(entry.getKey()),entry.getValue(),false));
        }
        return changes;
    }
    private static void requireReference(NamedParameterJdbcTemplate jdbc,String sql,Map<String,Object> params,String message) {
        if (!Integer.valueOf(1).equals(jdbc.queryForObject(sql,params,Integer.class))) throw invalid(message);
    }
    private static Map<String,Object> row(Snapshot snapshot,String table,Map<String,String> key) {
        if (PRODUCT.equals(table)) return snapshot.product();
        var rows = switch(table) { case MATERIAL -> snapshot.materials(); case COLOR -> snapshot.colors(); default -> snapshot.materialColors(); };
        var matches = rows.stream().filter(value -> matches(value,key)).toList();
        if (matches.size()>1) throw conflict("Chave ERP ambígua.");
        return matches.isEmpty() ? null : matches.getFirst();
    }
    private static boolean matches(Map<String,Object> row,Map<String,String> key) {
        return key.entrySet().stream().allMatch(entry -> equal(row.get(entry.getKey()),entry.getValue()));
    }
    private void apply(NamedParameterJdbcTemplate jdbc,Identity id,Patch patch) {
        // Tabela/colunas passaram pela lista fechada. Somente valores são parâmetros SQL.
        var parameters = new HashMap<String,Object>(params(id)); parameters.putAll(patch.chave());
        String where = "PRODUTO=:produto" + (COLOR.equals(patch.tabela()) || PRODUCT.equals(patch.tabela()) ? "" : " AND VERSAO_FICHA=:versao")
            + patch.chave().keySet().stream().sorted().map(key -> " AND ["+key+"]=:"+key).collect(Collectors.joining());
        if (patch.excluir()) {
            if (MATERIAL.equals(patch.tabela())) jdbc.update("DELETE FROM dbo.PRODUTO_VERSAO_MATERIAL_COR WHERE "+where,parameters);
            jdbc.update("DELETE FROM dbo.["+patch.tabela()+"] WHERE "+where,parameters);
        } else {
            var fields = new ArrayList<String>();
            int index=0;
            for (var entry : patch.valores().entrySet()) {
                String parameter="value"+index++; fields.add("["+entry.getKey()+"]=:"+parameter); parameters.put(parameter,entry.getValue());
            }
            jdbc.update("UPDATE dbo.["+patch.tabela()+"] SET "+String.join(",",fields)+" WHERE "+where,parameters);
        }
    }
    private static void verifyApplied(List<Change> changes,Snapshot after) {
        for (var change : changes) {
            if (change.exclusao() && change.campo().equals("VINCULOS_DESCARTADOS")) {
                if (after.materialColors().stream().anyMatch(row -> matches(row,change.chave())))
                    throw conflict("O descarte dos vínculos não foi confirmado. Transação desfeita.");
                continue;
            }
            var row = row(after,change.tabela(),change.chave());
            if (change.exclusao() ? row!=null : row==null || !equal(row.get(change.campo()),change.depois()))
                throw conflict("A releitura não confirmou o resultado solicitado. Transação desfeita.");
        }
    }
    private static Map<String,Object> one(List<Map<String,Object>> rows,String message) {
        if (rows.size()!=1) throw conflict(message); return rows.getFirst();
    }
    private static boolean truthy(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue()!=0; }

    private void guardConnection(NamedParameterJdbcTemplate jdbc) {
        var identity = jdbc.queryForMap("""
            SELECT DB_NAME() AS BANCO, CONVERT(varchar(48),CONNECTIONPROPERTY('local_net_address')) AS IP,
              CONVERT(int,CONNECTIONPROPERTY('local_tcp_port')) AS PORTA,
              (SELECT encrypt_option FROM sys.dm_exec_connections WHERE session_id=@@SPID) AS ENCRYPTED,
              IS_SRVROLEMEMBER('sysadmin') AS SYSADMIN
            """,Map.of());
        if (!properties.database().equals(identity.get("BANCO")) || !properties.server().equals(identity.get("IP"))
                || !(identity.get("PORTA") instanceof Number port) || port.intValue()!=1433
                || !"TRUE".equals(identity.get("ENCRYPTED")) || !Integer.valueOf(0).equals(identity.get("SYSADMIN")))
            throw conflict("Destino, criptografia ou permissão do gravador não correspondem à homologação restrita.");
    }
    private void guard(NamedParameterJdbcTemplate jdbc) {
        guardConnection(jdbc);
        // Este hash deve ser levantado/revisado por SELECT antes de habilitar o gravador.
        var modules = jdbc.queryForList(SCHEMA_SQL,Map.of());
        if (modules.isEmpty() || modules.stream().anyMatch(row -> Boolean.TRUE.equals(row.get("BLOQUEADO"))))
            throw conflict("Dependências SQL indisponíveis, externas, criptografadas ou dinâmicas. Escrita bloqueada.");
        if (!hash(modules).equalsIgnoreCase(properties.getApprovedSchemaSha256()))
            throw conflict("Esquema/triggers/dependências diferem do retrato revisado. Nada foi gravado.");
    }
    /** Dependências transitivas; não executa o texto de nenhum objeto. Sem poder auditar, falha fechado. */
    static final String SCHEMA_SQL = """
        WITH roots AS (
          SELECT object_id FROM sys.tables WHERE schema_id=SCHEMA_ID('dbo') AND name IN
          ('PRODUTOS','PRODUTO_CORES','PRODUTO_FICHA_VERSAO','PRODUTO_VERSAO_MATERIAL','PRODUTO_VERSAO_MATERIAL_COR')
        ), graph AS (
          SELECT object_id,0 AS depth,CAST('/'+CONVERT(varchar(20),object_id)+'/' AS varchar(max)) AS path FROM roots
          UNION ALL
          SELECT D.referenced_id,G.depth+1,CAST(G.path+CONVERT(varchar(20),D.referenced_id)+'/' AS varchar(max)) FROM graph G
          JOIN sys.sql_expression_dependencies D ON D.referencing_id=G.object_id
          WHERE D.referenced_id IS NOT NULL AND G.depth<16
            AND CHARINDEX('/'+CONVERT(varchar(20),D.referenced_id)+'/',G.path)=0
          UNION ALL
          SELECT T.object_id,G.depth+1,CAST(G.path+CONVERT(varchar(20),T.object_id)+'/' AS varchar(max)) FROM graph G JOIN sys.triggers T ON T.parent_id=G.object_id
          WHERE T.is_disabled=0 AND G.depth<16
            AND CHARINDEX('/'+CONVERT(varchar(20),T.object_id)+'/',G.path)=0
        ), objects AS (SELECT DISTINCT object_id FROM graph)
        SELECT O.object_id,O.name,O.type,O.modify_date,
          M.definition,
          CONVERT(bit,CASE WHEN
            (O.type IN ('TR','P','FN','IF','TF','V') AND M.definition IS NULL)
            OR O.type='SN'
            OR EXISTS(SELECT 1 FROM graph G WHERE G.object_id=O.object_id AND G.depth=16)
            OR EXISTS(SELECT 1 FROM sys.sql_expression_dependencies D WHERE D.referencing_id=O.object_id
              AND (D.referenced_server_name IS NOT NULL OR D.referenced_database_name IS NOT NULL OR D.is_ambiguous=1))
            OR LOWER(ISNULL(M.definition,'')) LIKE '%sp_executesql%'
            OR LOWER(ISNULL(M.definition,'')) LIKE '%exec(%'
            OR LOWER(ISNULL(M.definition,'')) LIKE '%execute(%'
            OR LOWER(ISNULL(M.definition,'')) LIKE '%exec %'
            OR LOWER(ISNULL(M.definition,'')) LIKE '%execute %'
            OR LOWER(ISNULL(M.definition,'')) LIKE '%openquery%'
            OR LOWER(ISNULL(M.definition,'')) LIKE '%openrowset%'
            THEN 1 ELSE 0 END) AS BLOQUEADO
        FROM objects X JOIN sys.objects O ON O.object_id=X.object_id
        LEFT JOIN sys.sql_modules M ON M.object_id=O.object_id ORDER BY O.object_id
        OPTION(MAXRECURSION 32)
        """;
    private String hash(Object value) {
        Object object = value instanceof Snapshot snapshot ? List.of(snapshot.product(),snapshot.header(),snapshot.collection(),snapshot.grade(),snapshot.materials(),snapshot.colors(),snapshot.materialColors()) : value;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical(object)))); }
        catch (Exception error) { throw new IllegalStateException("Não foi possível calcular a conferência da transação."); }
    }
    private static Object canonical(Object value) {
        if (value instanceof byte[] bytes) return HexFormat.of().formatHex(bytes);
        if (value instanceof Map<?,?> map) {
            var result = new TreeMap<String,Object>(); map.forEach((key,item) -> result.put(key.toString(),canonical(item))); return result;
        }
        if (value instanceof List<?> list) return list.stream().map(LinxWriteService::canonical).toList();
        if (value instanceof java.util.Date || value instanceof java.time.temporal.TemporalAccessor) return value.toString();
        return value;
    }
}
