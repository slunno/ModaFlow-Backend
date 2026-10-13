package com.modaflow.backend.workspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.modaflow.backend.security.TenantAccessValidator;
import com.modaflow.backend.security.UserPrincipal;
import com.modaflow.backend.usuario.UsuarioMarcaRoleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkspaceStoreTest {
    private final ObjectMapper json=new ObjectMapper();
    private WorkspaceStore memory(){return new WorkspaceStore(json,"jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1");}
    private ObjectNode data(String name){return json.createObjectNode().put("nome",name);}
    private WorkspaceStore.Document save(WorkspaceStore s,String kind,String id,ObjectNode data){return s.save(1,kind,id,new WorkspaceStore.Write(null,data),"Operador");}
    private ObjectNode product(WorkspaceStore s){save(s,"collections","col",data("Coleção").put("id","c1"));return data("Modelo").put("id","p1").put("colecaoId","c1").put("sourceKey","product:c1:p1").put("etapaAtual","Desenho");}

    @Test void gravaEmDiscoERelêEmOutraInstancia(@TempDir Path dir){
        String url="jdbc:h2:file:"+dir.resolve("plm").toString().replace('\\','/')+";DB_CLOSE_ON_EXIT=FALSE";
        var first=new WorkspaceStore(json,url); save(first,"tags","tag",data("Linha premium"));
        var reopened=new WorkspaceStore(json,url);
        assertThat(reopened.list(1,"tags")).hasSize(1);
        assertThat(reopened.list(1,"tags").getFirst().data().path("nome").asText()).isEqualTo("Linha premium");
        assertThat(reopened.list(2,"tags")).isEmpty();
    }
    @Test void conflitoNaoSobrescreveEdicaoNemCriaHistoricoFalso(){
        var s=memory();var old=save(s,"products","p",product(s));
        var next=(ObjectNode)old.data().deepCopy();next.put("etapaAtual","Corte");
        var saved=s.save(1,"products","p",new WorkspaceStore.Write(1L,next),"Ana");
        assertThat(saved.revision()).isEqualTo(2);
        next.put("nome","Sobrescrita rejeitada");
        assertThatThrownBy(()->s.save(1,"products","p",new WorkspaceStore.Write(1L,next),"Bia")).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(409));
        assertThat(s.product(1,"p").data().path("nome").asText()).isEqualTo("Modelo");
        assertThat(s.history(1).stream().filter(e->e.toStage()!=null).toList()).hasSize(1);
        assertThat(s.history(2)).isEmpty();
    }
    @Test void importacaoNaoDuplicaIdentidadeEArquivamentoPreservaVinculos(){
        var s=memory();var p=product(s);save(s,"products","p",p);
        assertThatThrownBy(()->save(s,"products","another",p)).isInstanceOf(ResponseStatusException.class);
        var tag=save(s,"tags","t",data("Tema"));
        var archived=(ObjectNode)tag.data().deepCopy();archived.put("archived",true);
        s.save(1,"tags","t",new WorkspaceStore.Write(1L,archived),"Ana");
        var next=(ObjectNode)s.product(1,"p").data().deepCopy();next.putArray("tagIds").add("t");
        assertThat(s.save(1,"products","p",new WorkspaceStore.Write(1L,next),"Ana").data().path("tagIds").get(0).asText()).isEqualTo("t");
    }
    @Test void validaMarcaCamposTipadosEPrazoNoAvanco(){
        var s=memory();var p=product(s);
        save(s,"custom-fields","f",data("Aprovado").put("identificador","aprovado").put("tipo","booleano").put("contexto","produto").put("obrigatorio",true));
        assertThatThrownBy(()->save(s,"products","p",p)).isInstanceOf(ResponseStatusException.class);
        p.putObject("customFields").put("f",false);
        save(s,"flows","flow",data("Desenvolvimento").put("etapas","Desenho\nCorte").put("inicial","Desenho").put("final","Corte").put("exigirPrazo",true));
        p.put("flowId","flow");var record=save(s,"products","p",p);
        var next=(ObjectNode)record.data().deepCopy();next.put("etapaAtual","Corte").put("previsaoEntrega","—");
        assertThatThrownBy(()->s.save(1,"products","p",new WorkspaceStore.Write(1L,next),"Ana")).isInstanceOf(ResponseStatusException.class);
        next.put("previsaoEntrega","2026-10-30");assertThat(s.save(1,"products","p",new WorkspaceStore.Write(1L,next),"Ana").revision()).isEqualTo(2);
        assertThatThrownBy(()->s.save(2,"products","p",new WorkspaceStore.Write(null,next),"Outra marca")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void compartilhaBinarioIdempotenteRespeitaMarcaERegistraRemocao(){
        var s=memory();save(s,"products","p",product(s));byte[] bytes="arquivo de conferência".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        s.putAttachment(1,"p","a","outros","ficha.txt","text/plain",bytes,"Ana");
        s.putAttachment(1,"p","a","outros","ficha.txt","text/plain",bytes,"Ana");
        assertThat(s.attachments(1,"p")).hasSize(1);assertThat(s.attachmentBytes(1,"p","a")).isEqualTo(bytes);
        assertThatThrownBy(()->s.attachmentBytes(2,"p","a")).isInstanceOf(ResponseStatusException.class);
        s.removeAttachment(1,"p","a","Ana");assertThat(s.attachments(1,"p")).isEmpty();
        assertThat(s.history(1).stream().filter(e->e.data().path("operation").asText().startsWith("ANEXO_")).count()).isEqualTo(2);
    }
    @Test void controladorBloqueiaEscritaSemPapelNaMarca(){
        var s=memory();var access=mock(TenantAccessValidator.class);var roles=mock(UsuarioMarcaRoleRepository.class);
        when(access.requireTenantForCurrentUser()).thenReturn(1L);when(roles.findByUsuarioId(7L)).thenReturn(List.of());
        var controller=new WorkspaceController(s,access,roles);var user=new UserPrincipal(7L,"Consulta","test@local","",List.of());
        assertThatThrownBy(()->controller.save("tags","t",new WorkspaceStore.Write(null,data("Tag")),user)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode().value()).isEqualTo(403));
        assertThat(s.list(1,"tags")).isEmpty();
    }
    @Test void prazoDeEtapaEConclusaoSaoConfirmadosPeloServidor(){
        var s=memory();var p=product(s);
        save(s,"flows","f",data("Operação").put("etapas","Desenho\nCorte\nFinal").put("inicial","Desenho").put("final","Final").put("prazoDias",3));
        p.put("flowId","f");var created=save(s,"products","p",p);
        assertThat(created.data().path("stageDeadline").asText()).isEqualTo(java.time.LocalDate.now(java.time.ZoneId.of("America/Sao_Paulo")).plusDays(3).toString());
        var next=(ObjectNode)created.data().deepCopy();next.put("etapaAtual","Final").put("completedAt","1900-01-01T00:00:00Z");
        var completed=s.save(1,"products","p",new WorkspaceStore.Write(1L,next),"Ana");
        assertThat(completed.data().path("status").asText()).isEqualTo("Completa");
        assertThat(java.time.Instant.parse(completed.data().path("completedAt").asText())).isAfter(java.time.Instant.now().minusSeconds(5));
    }
    @Test void conferenciaNaoPodeSerIgnoradaPorMovimentoDiretoNoKanban(){
        var s=memory();var p=product(s);p.put("etapaAtual","01 geração de ficha");save(s,"products","p",p);
        p.put("etapaAtual","02 engenharia recebimento");
        assertThatThrownBy(()->s.save(1,"products","p",new WorkspaceStore.Write(1L,p),"Ana")).isInstanceOf(com.modaflow.backend.common.exception.BusinessRuleException.class);
        assertThat(s.product(1,"p").revision()).isEqualTo(1);
    }
}
