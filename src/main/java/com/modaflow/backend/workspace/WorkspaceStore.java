package com.modaflow.backend.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Instant;
import java.util.*;

/** Durable PLM storage isolated from all ERP connections and schemas. */
@Service
public class WorkspaceStore {
    public record Document(String id, String kind, long revision, String updatedAt, JsonNode data) {}
    public record Change(String id, String documentId, String kind, String at, String author,
                         String fromStage, String toStage, JsonNode data) {}
    public record Write(Long expectedRevision, JsonNode data) {}
    public record Attachment(String id, String categoria, String nome, long tamanho, String tipo, long criadoEm) {}
    private static final Set<String> KINDS = Set.of("products", "collections", "tags", "piece-types", "fixed-costs", "pricing", "flows", "custom-fields", "measurements", "teams");
    private final JdbcTemplate db;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;

    public WorkspaceStore(ObjectMapper json,
            @Value("${modaflow.workspace.url:jdbc:h2:file:./data/akr-workspace;DB_CLOSE_ON_EXIT=FALSE}") String url) {
        if (!url.startsWith("jdbc:h2:")) throw new IllegalArgumentException("Use um banco H2 dedicado ao PLM, nunca a conexão do ERP.");
        this.json = json;
        var source = new DriverManagerDataSource(url, "sa", "");
        db = new JdbcTemplate(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        db.execute("CREATE TABLE IF NOT EXISTS workspace_document (brand BIGINT NOT NULL, kind VARCHAR(40) NOT NULL, id VARCHAR(100) NOT NULL, source_key VARCHAR(600) NOT NULL, revision BIGINT NOT NULL, updated_at VARCHAR(40) NOT NULL, payload CLOB NOT NULL, PRIMARY KEY (brand,kind,id), UNIQUE(brand,kind,source_key))");
        db.execute("CREATE TABLE IF NOT EXISTS workspace_change (id VARCHAR(100) PRIMARY KEY, brand BIGINT NOT NULL, kind VARCHAR(40) NOT NULL, document_id VARCHAR(100) NOT NULL, event_at VARCHAR(40) NOT NULL, author VARCHAR(200) NOT NULL, from_stage VARCHAR(200), to_stage VARCHAR(200), payload CLOB NOT NULL)");
        db.execute("CREATE INDEX IF NOT EXISTS workspace_change_brand ON workspace_change(brand,event_at)");
        db.execute("CREATE TABLE IF NOT EXISTS workspace_attachment (brand BIGINT NOT NULL, document_id VARCHAR(100) NOT NULL, id VARCHAR(100) NOT NULL, category VARCHAR(30) NOT NULL, name VARCHAR(255) NOT NULL, media_type VARCHAR(150) NOT NULL, created_at BIGINT NOT NULL, content BLOB NOT NULL, PRIMARY KEY(brand,document_id,id))");
    }

    public Document product(long brand,String id) {
        return list(brand,"products").stream().filter(p -> p.id().equals(id)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Modelo não encontrado nesta marca."));
    }
    public List<Attachment> attachments(long brand,String document) {
        product(brand,document);
        return db.query("SELECT id,category,name,media_type,created_at,OCTET_LENGTH(content) size FROM workspace_attachment WHERE brand=? AND document_id=? ORDER BY created_at,id",(r,n) -> new Attachment(r.getString("id"),r.getString("category"),r.getString("name"),r.getLong("size"),r.getString("media_type"),r.getLong("created_at")),brand,document);
    }
    public byte[] attachmentBytes(long brand,String document,String id) {
        product(brand,document);
        return db.query("SELECT content FROM workspace_attachment WHERE brand=? AND document_id=? AND id=?",(r,n) -> r.getBytes(1),brand,document,id).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Anexo não encontrado."));
    }
    public Attachment putAttachment(long brand,String document,String id,String category,String name,String type,byte[] bytes,String author) {
        var product=product(brand,document);
        if(!id.matches("[a-zA-Z0-9_-]{1,100}") || !Set.of("variantes","referencias","desenhos","projeto","outros").contains(category)) bad("Anexo inválido.");
        boolean image=Set.of("variantes","referencias","desenhos").contains(category);
        if(name==null || name.isBlank() || name.length()>255 || type==null || type.length()>150) bad("Nome ou tipo de arquivo inválido.");
        if(bytes.length>(image?10:100)*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Arquivo excede o limite permitido.");
        if(image && !type.startsWith("image/")) bad("Selecione uma imagem.");
        long created=System.currentTimeMillis();
        return transaction.execute(status -> {
            var existing=attachments(brand,document).stream().filter(a -> a.id().equals(id)).findFirst();
            if(existing.isPresent()) return existing.get();
            db.update("INSERT INTO workspace_attachment(brand,document_id,id,category,name,media_type,created_at,content) VALUES(?,?,?,?,?,?,?,?)",brand,document,id,category,name,type,created,bytes);
            attachmentEvent(brand,product,author,"ANEXO_ADICIONADO",name);
            return new Attachment(id,category,name,bytes.length,type,created);
        });
    }
    public void removeAttachment(long brand,String document,String id,String author) {
        var product=product(brand,document);
        transaction.executeWithoutResult(status -> {
            var existing=attachments(brand,document).stream().filter(a -> a.id().equals(id)).findFirst();
            if(existing.isEmpty()) return;
            db.update("DELETE FROM workspace_attachment WHERE brand=? AND document_id=? AND id=?",brand,document,id);
            attachmentEvent(brand,product,author,"ANEXO_REMOVIDO",existing.get().nome());
        });
    }
    private void attachmentEvent(long brand,Document product,String author,String operation,String name) {
        var audit=json.createObjectNode().put("operation",operation).put("nome",name).put("revision",product.revision());
        audit.putArray("fields").add("anexos");
        db.update("INSERT INTO workspace_change(id,brand,kind,document_id,event_at,author,payload) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),brand,"products",product.id(),Instant.now().toString(),author,audit.toString());
    }

    public List<Document> list(long brand, String kind) {
        requireKind(kind);
        return db.query("SELECT * FROM workspace_document WHERE brand=? AND kind=? ORDER BY updated_at DESC,id", (r,n) -> new Document(r.getString("id"),kind,r.getLong("revision"),r.getString("updated_at"),read(r.getString("payload"))),brand,kind);
    }

    public List<Change> history(long brand) {
        return db.query("SELECT * FROM workspace_change WHERE brand=? ORDER BY event_at,id", (r,n) -> new Change(r.getString("id"),r.getString("document_id"),r.getString("kind"),r.getString("event_at"),r.getString("author"),r.getString("from_stage"),r.getString("to_stage"),read(r.getString("payload"))),brand);
    }

    public Document save(long brand, String kind, String id, Write input, String author) {
        requireKind(kind);
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,100}")) bad("Identificador inválido.");
        if (input == null || input.data() == null || !input.data().isObject()) bad("Informe os dados do registro.");
        var data = (ObjectNode) input.data().deepCopy();
        if (data.toString().length() > 12_000_000) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Registro maior que 12 MB. Reduza as imagens incorporadas.");
        if (data.path("nome").asText().isBlank() || data.path("nome").asText().length() > 200) bad("Informe um nome de até 200 caracteres.");
        return transaction.execute(status -> {
            var records = list(brand,kind);
            var existing = records.stream().filter(d -> d.id().equals(id)).findFirst().orElse(null);
            long revision = existing == null ? 0 : existing.revision();
            if (!Objects.equals(input.expectedRevision(), existing == null ? null : Long.valueOf(revision))) conflict();
            if (data.has("marcaId") && !data.path("marcaId").asText().equals(String.valueOf(brand))) bad("A marca do registro difere da marca selecionada.");
            data.put("marcaId",String.valueOf(brand));
            validateLinks(brand,kind,data,existing);
            String identity = data.path("sourceKey").asText("");
            if (identity.length()>600) bad("Identidade de origem muito longa.");
            String source = identity.isBlank() ? id : identity;
            String code = data.path("identificador").asText("");
            for (var other : records) if (!other.id().equals(id) && !code.isBlank() && code.equalsIgnoreCase(other.data().path("identificador").asText()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Já existe um registro com este identificador.");
            String now = Instant.now().toString();
            String from = existing == null ? null : existing.data().path("etapaAtual").asText(null);
            String to = data.path("etapaAtual").asText(null);
            if (existing == null) data.put("sharedAt", now);
            else data.set("sharedAt",existing.data().path("sharedAt"));
            try {
                if (existing == null) db.update("INSERT INTO workspace_document(brand,kind,id,source_key,revision,updated_at,payload) VALUES(?,?,?,?,?,?,?)",brand,kind,id,source,1,now,data.toString());
                else if (db.update("UPDATE workspace_document SET revision=?,updated_at=?,payload=? WHERE brand=? AND kind=? AND id=? AND revision=?",revision+1,now,data.toString(),brand,kind,id,revision) != 1) conflict();
            } catch (org.springframework.dao.DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Esta identidade já foi cadastrada. Recarregue a lista."); }
            var audit = json.createObjectNode();
            for (String key : List.of("nome","codigo","colecaoId","flowId","responsavel","status")) if (data.has(key)) audit.set(key,data.get(key));
            audit.put("revision",revision+1);
            audit.put("operation",existing == null ? "CRIAR" : "ATUALIZAR");
            var changed = audit.putArray("fields");
            data.fieldNames().forEachRemaining(key -> { if (existing == null || !Objects.equals(existing.data().get(key),data.get(key))) changed.add(key); });
            db.update("INSERT INTO workspace_change(id,brand,kind,document_id,event_at,author,from_stage,to_stage,payload) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),brand,kind,id,now,author,from,existing == null || Objects.equals(from,to)?null:to,audit.toString());
            return new Document(id,kind,revision+1,now,data);
        });
    }

    private void validateLinks(long brand, String kind, ObjectNode data, Document previous) {
        if (previous != null && !Objects.equals(previous.data().get("sourceKey"),data.get("sourceKey"))) bad("Preserve a identidade de origem.");
        if (kind.equals("products")) {
            if (data.path("id").asText().isBlank() || data.path("colecaoId").asText().isBlank()) bad("Produto exige identidade e coleção.");
            if(list(brand,"collections").stream().noneMatch(c -> c.data().path("id").equals(data.path("colecaoId")))) bad("A coleção não pertence à marca.");
            if(!data.path("typeId").asText().isBlank() && list(brand,"piece-types").stream().noneMatch(t -> t.id().equals(data.path("typeId").asText()))) bad("Tipo de peça não pertence à marca.");
            if(!data.path("teamId").asText().isBlank() && list(brand,"teams").stream().noneMatch(t -> t.id().equals(data.path("teamId").asText()))) bad("Time não pertence à marca.");
            if (previous != null && (!previous.data().path("id").equals(data.path("id")) || !previous.data().path("colecaoId").equals(data.path("colecaoId")))) bad("A identidade do produto e da coleção deve ser preservada.");
            var tags = list(brand,"tags");
            for (var tag : data.path("tagIds")) if (tags.stream().noneMatch(t -> t.id().equals(tag.asText()))) bad("Tag não pertence à marca.");
            validateCustom(brand,data,"produto");
            if(previous!=null && Set.of("01 geração de ficha","GERACAO_FICHA").contains(previous.data().path("etapaAtual").asText()) && Set.of("02 engenharia recebimento","ENGENHARIA_RECEBIMENTO").contains(data.path("etapaAtual").asText())) WorkspaceConferencia.validate(json,data);
            String flowId = data.path("flowId").asText();
            if (!flowId.isBlank()) {
                var flow = list(brand,"flows").stream().filter(f -> f.id().equals(flowId)).findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fluxo não encontrado nesta marca."));
                String stage = data.path("etapaAtual").asText();
                var stages = Arrays.asList(flow.data().path("etapas").asText().split("\\n"));
                if (!stages.contains(stage)) bad("A etapa não pertence ao fluxo selecionado.");
                if(stage.equals(flow.data().path("final").asText()) && !data.path("status").asText().equals("Cancelada")) data.put("status","Completa");
                boolean entered = previous == null || !previous.data().path("etapaAtual").asText().equals(stage) || !previous.data().path("flowId").asText().equals(flowId);
                if (entered && flow.data().path("prazoDias").asInt() > 0) data.put("stageDeadline",java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(flow.data().path("prazoDias").asInt()).toString());
                else if(entered) data.remove("stageDeadline");
                else if(previous != null && previous.data().has("stageDeadline")) data.set("stageDeadline",previous.data().get("stageDeadline"));
                if (previous != null && !previous.data().path("etapaAtual").asText().equals(stage)) {
                    if (flow.data().path("exigirResponsavel").asBoolean() && data.path("responsavel").asText().isBlank()) bad("O fluxo exige responsável para avançar.");
                    if (flow.data().path("exigirPrazo").asBoolean() && !validDate(data.path("previsaoEntrega").asText())) bad("O fluxo exige prazo válido para avançar.");
                    if (flow.data().path("exigirProva").asBoolean() && !data.path("fichaTecnica").path("provaRoupa").path("resultado").asText().equals("aprovada")) bad("O fluxo exige prova de roupa aprovada.");
                    if(stage.equals(flow.data().path("final").asText())) data.put("status","Completa");
                    else if(previous.data().path("status").asText().equals("Completa"))data.put("status","Em andamento");
                }
            }
            if(flowId.isBlank() && Set.of("20 Final","FINAL").contains(data.path("etapaAtual").asText()) && previous != null && !previous.data().path("etapaAtual").equals(data.path("etapaAtual"))) data.put("status","Completa");
            if(data.path("status").asText().equals("Completa")) {
                if(previous==null || !previous.data().path("status").asText().equals("Completa")) data.put("completedAt",Instant.now().toString());
                else if(previous.data().has("completedAt"))data.set("completedAt",previous.data().get("completedAt"));
            } else data.remove("completedAt");
        }
        if (kind.equals("collections")) {
            if(data.path("id").asText().isBlank()) bad("Coleção exige identidade.");
            if(previous != null && !previous.data().path("id").equals(data.path("id"))) bad("Preserve a identidade da coleção.");
            validateCustom(brand,data,"colecao");
        }
        if (kind.equals("flows")) {
            if(!data.path("teamId").asText().isBlank() && list(brand,"teams").stream().noneMatch(t -> t.id().equals(data.path("teamId").asText()))) bad("Time não pertence à marca.");
            var stages = Arrays.stream(data.path("etapas").asText().split("\\n")).map(String::trim).filter(s -> !s.isBlank()).toList();
            if (stages.isEmpty() || new HashSet<>(stages).size()!=stages.size()) bad("Informe etapas distintas e ordenadas.");
            data.put("etapas",String.join("\n",stages));
            if(data.path("prazoDias").asDouble()<0 || data.path("prazoDias").asDouble()!=data.path("prazoDias").asInt()) bad("Prazo por etapa deve ser um número inteiro de dias, a partir de zero.");
            if (!stages.contains(data.path("inicial").asText()) || !stages.contains(data.path("final").asText())) bad("Identifique etapas inicial e final presentes no fluxo.");
            if (previous != null) for (var p : list(brand,"products")) if (p.data().path("flowId").asText().equals(previous.id()) && !stages.contains(p.data().path("etapaAtual").asText())) bad("Uma etapa está em uso. Preserve-a antes de editar o fluxo.");
        }
        if (kind.equals("fixed-costs") && (!data.path("valor").isNumber() || data.path("valor").asDouble()<0 || data.path("unidadesBase").asDouble()<=0)) bad("Informe valor válido e unidades de rateio maiores que zero.");
        if (kind.equals("pricing")) {
            if(!Set.of("markup","margem").contains(data.path("modo").asText())) bad("Método de precificação inválido.");
            String scope=data.path("escopo").asText("Marca"), target=data.path("alvo").asText();
            if(scope.equals("Coleção") && list(brand,"collections").stream().noneMatch(c -> c.data().path("id").asText().equals(target))) bad("Selecione uma coleção desta marca.");
            if(scope.equals("Tipo de peça") && list(brand,"piece-types").stream().noneMatch(t -> t.id().equals(target) || t.data().path("nome").asText().equals(target))) bad("Selecione um tipo de peça desta marca.");
            if (!data.path("fator").isNumber()) bad("Informe o fator de precificação.");
            if (data.path("modo").asText().equals("markup") && data.path("fator").asDouble()<=0) bad("Markup deve ser maior que zero.");
            if (data.path("modo").asText().equals("margem") && (data.path("fator").asDouble()<0 || data.path("fator").asDouble()>=100)) bad("Margem deve ficar entre 0 e menos de 100%.");
        }
        if(kind.equals("custom-fields")) {
            if(!data.path("identificador").asText().matches("[a-zA-Z][a-zA-Z0-9_]{0,99}")) bad("Use um identificador com letras, números e sublinhado, começando por letra.");
            if(!Set.of("texto","numero","moeda","data","selecao","booleano").contains(data.path("tipo").asText()) || !Set.of("produto","colecao").contains(data.path("contexto").asText())) bad("Tipo ou contexto inválido.");
            if(previous != null) {
                if(!previous.data().path("identificador").equals(data.path("identificador"))) bad("Preserve o identificador do campo; crie outro campo para uma nova identidade.");
                String affected=previous.data().path("contexto").asText().equals("colecao")?"collections":"products";
                boolean used=list(brand,affected).stream().anyMatch(p -> p.data().path("customFields").has(previous.id()));
                if(used && (!previous.data().path("tipo").equals(data.path("tipo")) || !previous.data().path("contexto").equals(data.path("contexto")))) bad("Campo em uso: preserve o tipo e o contexto ou crie um novo campo.");
                if(used && data.path("tipo").asText().equals("selecao")) for(var p:list(brand,affected)) {
                    String value=p.data().path("customFields").path(previous.id()).asText();
                    if(!value.isBlank() && !Arrays.asList(data.path("opcoes").asText().split("\\n")).contains(value)) bad("Preserve as opções de seleção que já estão em uso.");
                }
            }
        }
        for (String date : List.of("inicio","fim")) if (!data.path(date).asText().isBlank()) try { java.time.LocalDate.parse(data.path(date).asText()); } catch(Exception e) { bad("Vigência inválida."); }
        if (!data.path("inicio").asText().isBlank() && !data.path("fim").asText().isBlank() && data.path("inicio").asText().compareTo(data.path("fim").asText())>0) bad("A vigência final deve ser posterior à inicial.");
    }

    private void validateCustom(long brand,ObjectNode data,String context) {
        for (var field : list(brand,"custom-fields")) {
            var cfg=field.data();
            if(cfg.path("archived").asBoolean() || !cfg.path("contexto").asText("produto").equals(context)) continue;
            var value=data.path("customFields").path(field.id());
            boolean absent=value.isMissingNode() || value.isNull() || value.asText().isBlank();
            if(cfg.path("obrigatorio").asBoolean() && absent) bad("Preencha o campo obrigatório: "+cfg.path("nome").asText());
            if(absent) continue;
            String type=cfg.path("tipo").asText();
            if ((type.equals("numero") || type.equals("moeda")) && !value.isNumber()) bad("Campo numérico inválido: "+cfg.path("nome").asText());
            if (type.equals("booleano") && !value.isBoolean()) bad("Campo booleano inválido.");
            if (type.equals("data")) try { java.time.LocalDate.parse(value.asText()); } catch(Exception e) { bad("Data inválida."); }
            if (type.equals("selecao") && !Arrays.asList(cfg.path("opcoes").asText().split("\\n")).contains(value.asText())) bad("Opção inválida no campo customizado.");
        }
    }
    private JsonNode read(String value) { try { return json.readTree(value); } catch(Exception e) { throw new IllegalStateException("Registro operacional ilegível",e); } }
    private boolean validDate(String value) {
        try { java.time.LocalDate.parse(value); return true; } catch(Exception e) {
            try { java.time.LocalDate.parse(value,java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)); return true; } catch(Exception ignored) { return false; }
        }
    }
    private void requireKind(String kind) { if(!KINDS.contains(kind)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cadastro desconhecido."); }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT,"Este registro foi alterado em outra sessão. Recarregue antes de salvar; suas alterações não foram gravadas."); }
}
