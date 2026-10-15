package com.modaflow.backend.integracao.linx.write;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modaflow.backend.ficha.FichaConferenciaRequest;
import com.modaflow.backend.ficha.FichaConferenciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.modaflow.backend.integracao.linx.write.LinxWriteContracts.*;

/** Nenhum servidor, pool real, conexão JDBC ou contexto Spring é iniciado. */
class LinxWriteServiceTest {
    LinxWriteDatabase database;
    NamedParameterJdbcTemplate jdbc;
    LinxWriteProperties properties;
    LinxWriteService service;
    Map<String,Object> product;
    List<Map<String,Object>> materialRows;
    List<Map<String,Object>> colorRows;
    boolean ignoreWrites;
    final Identity identity = new Identity("TESTE01","KING&JOE","TESTE","Coleção teste","00001");
    @BeforeEach @SuppressWarnings("unchecked") void setup() throws Exception {
        jdbc=mock(NamedParameterJdbcTemplate.class); database=mock(LinxWriteDatabase.class);
        properties=new LinxWriteProperties(); properties.setEnabled(true);
        properties.getTenantGriffes().put(1L,"KING&JOE"); properties.getAllowedUserIds().addAll(Set.of(9L,10L));
        var modules=List.of(new TreeMap<String,Object>(Map.of("object_id",1,"name","PRODUTOS","BLOQUEADO",false)));
        properties.setApprovedSchemaSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(new ObjectMapper().writeValueAsBytes(modules))));
        service=new LinxWriteService(properties,Optional.of(database),mock(FichaConferenciaService.class));
        when(database.transaction(anyBoolean(),any())).thenAnswer(invocation ->
            ((Function<NamedParameterJdbcTemplate,Object>)invocation.getArgument(1)).apply(jdbc));
        product=new HashMap<>(Map.of("PRODUTO","TESTE01","GRIFFE","KING&JOE","GRUPO_PRODUTO","ROUPAS",
            "COLECAO","TESTE","VERSAO_FICHA","00001","GRADE","PM","DESC_PRODUTO","Nome antigo","INATIVO",false));
        materialRows=new ArrayList<>(List.of(new HashMap<>(Map.of("MATERIAL","M1","ITEM","0001","DESC_USO_MATERIAL","Corpo","C1",new BigDecimal("1.000")))));
        colorRows=new ArrayList<>(List.of(new HashMap<>(Map.of("MATERIAL","M1","ITEM","0001","COR_PRODUTO","CP1","COR_MATERIAL","CM1"))));
        when(jdbc.queryForMap(anyString(),anyMap())).thenReturn(Map.of("BANCO","HOMOLOG_COBRANCA","IP","192.168.1.6","PORTA",1433,"ENCRYPTED","TRUE","SYSADMIN",0));
        when(jdbc.queryForList(anyString(),anyMap())).thenAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if (sql.equals(LinxWriteService.SCHEMA_SQL)) return modules;
            if (sql.contains("dbo.PRODUTOS_TAMANHOS")) return List.of(Map.of("TAMANHO_1","P"));
            if (sql.contains("dbo.PRODUTO_FICHA_VERSAO")) return List.of(Map.of("U_COLECAO","TESTE","VERSAO_FICHA","00001"));
            if (sql.contains("dbo.COLECOES")) return List.of(Map.of("COLECAO","TESTE","DESC_COLECAO","Coleção teste","GRIFFE","KING&JOE","INATIVO",false));
            if (sql.contains("dbo.PRODUTO_VERSAO_MATERIAL_COR")) return colorRows.stream().map(HashMap::new).toList();
            if (sql.contains("dbo.PRODUTO_VERSAO_MATERIAL")) return materialRows.stream().map(HashMap::new).toList();
            if (sql.contains("dbo.PRODUTO_CORES")) return List.of(Map.of("COR_PRODUTO","CP1","COR","B1","U_COLECAO","TESTE"));
            if (sql.contains("dbo.PRODUTOS")) return List.of(new HashMap<>(product));
            throw new AssertionError("SQL inesperado: "+sql);
        });
        when(jdbc.update(anyString(),anyMap())).thenAnswer(invocation -> {
            String sql=invocation.getArgument(0); Map<String,Object> params=invocation.getArgument(1);
            if (!ignoreWrites) {
                if (sql.startsWith("UPDATE dbo.[PRODUTOS]")) product.put("DESC_PRODUTO",params.get("value0"));
                if (sql.startsWith("UPDATE dbo.[PRODUTO_VERSAO_MATERIAL]")) {
                    if (sql.contains("[C1]")) materialRows.getFirst().put("C1",params.get("value0"));
                    if (sql.contains("[DESC_USO_MATERIAL]")) materialRows.getFirst().put("DESC_USO_MATERIAL",params.get("value0"));
                }
                if (sql.startsWith("DELETE FROM dbo.PRODUTO_VERSAO_MATERIAL_COR")) colorRows.clear();
                if (sql.startsWith("DELETE FROM dbo.[PRODUTO_VERSAO_MATERIAL]")) materialRows.clear();
            }
            return 1;
        });
    }
    Request request(Patch... patches) {
        var item=new FichaConferenciaRequest.Insumo("i","Material teste","M1","tecidos","outro","1","Metros","1",
            Map.of("P",new FichaConferenciaRequest.Consumo("2.5",false)));
        var insumos=Arrays.stream(patches).anyMatch(patch -> patch.excluir() && patch.tabela().equals("PRODUTO_VERSAO_MATERIAL"))
            ? List.<FichaConferenciaRequest.Insumo>of() : List.of(item);
        return new Request(identity,new FichaConferenciaRequest(List.of("P"),true,true,
            List.of(new FichaConferenciaRequest.Variante("principal","Principal",Map.of(),insumos))),List.of(patches));
    }
    Patch name(String value) { return new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1","ITEM","0001"),Map.of("DESC_USO_MATERIAL",value),false,false); }
    @Test void desativadoNaoAbreNemConsultaBanco() {
        properties.setEnabled(false);
        assertThat(service.capability().habilitada()).isFalse();
        assertThatThrownBy(() -> service.prepare(1,9,request(name("Novo")))).hasMessageContaining("503");
        verifyNoInteractions(database,jdbc);
    }
    @Test void exigeUsuarioPermitidoEVinculoDaMarca() {
        assertThatThrownBy(() -> service.prepare(1,99,request(name("Novo")))).hasMessageContaining("403");
        assertThatThrownBy(() -> service.prepare(2,9,request(name("Novo")))).hasMessageContaining("403");
        verifyNoInteractions(database,jdbc);
    }
    @Test void previaSomenteLeEConfirmaApenasAVersaoDaColecao() {
        var plan=service.prepare(1,9,request(name("Novo")));
        assertThat(plan.alteracoes()).singleElement().satisfies(change -> {
            assertThat(change.antes()).isEqualTo("Corpo"); assertThat(change.depois()).isEqualTo("Novo");
        });
        verify(jdbc,never()).update(anyString(),anyMap());
        assertThat(plan.exigeConfirmacaoGlobal()).isFalse();
        assertThatThrownBy(() -> service.confirm(1,9,new Confirm(plan.planoId(),false,false))).hasMessageContaining("422");
        var receipt=service.confirm(1,9,new Confirm(plan.planoId(),true,false));
        assertThat(receipt.status()).isEqualTo("CONFIRMADA");
        assertThat(receipt.escopo()).isEqualTo("CAMPOS_MAPEADOS");
        assertThat(receipt.identidade().versao()).isEqualTo("00001");
        verify(jdbc).update(eq("UPDATE dbo.[PRODUTO_VERSAO_MATERIAL] SET [DESC_USO_MATERIAL]=:value0 WHERE PRODUTO=:produto AND VERSAO_FICHA=:versao AND [ITEM]=:ITEM AND [MATERIAL]=:MATERIAL"),anyMap());
        assertThat(product.get("DESC_PRODUTO")).isEqualTo("Nome antigo");
        assertThatThrownBy(() -> service.confirm(1,9,new Confirm(plan.planoId(),true,true))).hasMessageContaining("409");
    }
    @Test void concorrenciaInterrompeAntesDeQualquerDml() {
        var plan=service.prepare(1,9,request(name("Novo"))); product.put("DESC_PRODUTO","Mudança no ERP");
        assertThatThrownBy(() -> service.confirm(1,9,new Confirm(plan.planoId(),true,true))).hasMessageContaining("após a prévia");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void tokenDeOutroUsuarioNaoPodeSerUsado() {
        var plan=service.prepare(1,9,request(name("Novo")));
        assertThatThrownBy(() -> service.confirm(1,10,new Confirm(plan.planoId(),true,true))).hasMessageContaining("outra sessão");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void releituraObrigatoriaNaoConfirmaTriggerQueIgnorouAlteracao() {
        var plan=service.prepare(1,9,request(name("Novo"))); ignoreWrites=true;
        assertThatThrownBy(() -> service.confirm(1,9,new Confirm(plan.planoId(),true,true))).hasMessageContaining("Transação desfeita");
        verify(database).transaction(eq(false),any());
    }
    @Test void consumoConferePosicaoDaGradeEValorDaPrincipal() {
        var patch=new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1","ITEM","0001"),Map.of("C1","2,5"),false,false);
        var plan=service.prepare(1,9,request(patch));
        assertThat(plan.exigeConfirmacaoGlobal()).isFalse();
        service.confirm(1,9,new Confirm(plan.planoId(),true,false));
        assertThat(materialRows.getFirst().get("C1")).isEqualTo(new BigDecimal("2.5"));
        var invalid=new Patch("PRODUTO_VERSAO_MATERIAL",patch.chave(),Map.of("C2","2.5"),false,false);
        assertThatThrownBy(() -> service.prepare(1,9,request(invalid))).hasMessageContaining("posição vazia");
    }
    @Test void omitirLinhaNaoExcluiEMantemCamposNaoSolicitados() {
        var plan=service.prepare(1,9,request(name("Novo")));
        service.confirm(1,9,new Confirm(plan.planoId(),true,true));
        assertThat(materialRows).hasSize(1); assertThat(colorRows).hasSize(1);
        verify(jdbc,never()).update(startsWith("DELETE"),anyMap());
        assertThat(product.get("GRADE")).isEqualTo("PM");
    }
    @Test void descarteExplicitoRemoveFilhosAntesDoPaiComChaveCompleta() {
        var patch=new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1","ITEM","0001"),Map.of(),true,true);
        var plan=service.prepare(1,9,request(patch));
        assertThat(plan.alteracoes()).hasSize(2);
        service.confirm(1,9,new Confirm(plan.planoId(),true,false));
        var ordered=inOrder(jdbc);
        ordered.verify(jdbc).update(startsWith("DELETE FROM dbo.PRODUTO_VERSAO_MATERIAL_COR"),argThat((Map<String,?> params) ->
            params.get("produto").equals("TESTE01") && params.get("versao").equals("00001") && params.get("ITEM").equals("0001")));
        ordered.verify(jdbc).update(startsWith("DELETE FROM dbo.[PRODUTO_VERSAO_MATERIAL]"),anyMap());
    }
    @Test void noopNaoDisparaAtualizacaoDesnecessaria() {
        var unchanged=new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1","ITEM","0001"),Map.of("DESC_USO_MATERIAL","Corpo"),false,false);
        assertThatThrownBy(() -> service.prepare(1,9,request(unchanged))).hasMessageContaining("Nenhuma alteração");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void cadastroNovoNaoCopiaFiscalDeOutraPeca() {
        doReturn(List.of()).when(jdbc).queryForList(contains("SELECT * FROM dbo.PRODUTOS WHERE"),anyMap());
        assertThatThrownBy(() -> service.prepare(1,9,request(name("Novo")))).hasMessageContaining("perfil fiscal");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void bancoErradoEEsquemaDivergenteFalhamAntesDoDml() {
        properties.setApprovedSchemaSha256("0".repeat(64));
        assertThatThrownBy(() -> service.prepare(1,9,request(name("Novo")))).hasMessageContaining("retrat");
        when(jdbc.queryForMap(anyString(),anyMap())).thenReturn(Map.of("BANCO","KINGEJOE"));
        assertThatThrownBy(() -> service.prepare(1,9,request(name("Novo")))).hasMessageContaining("Destino");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void revisaoSeparadaPodeSerFeitaSemLiberarEscrita() {
        properties.setEnabled(false); properties.setReviewEnabled(true); properties.setApprovedSchemaSha256("");
        var review=service.review(9);
        assertThat(review.sha256()).matches("[a-f0-9]{64}");
        assertThat(review.objetosBloqueados()).isEmpty();
        assertThat(service.capability().habilitada()).isFalse();
        verify(jdbc,never()).update(anyString(),anyMap());
        assertThatThrownBy(() -> service.prepare(1,9,request(name("Novo")))).hasMessageContaining("503");
    }
    @Test void naoUsaOPonteiroGlobalParaAtualizarUmaColecao() {
        product.put("COLECAO","OUTRA"); product.put("VERSAO_FICHA","00009");
        var plan=service.prepare(1,9,request(name("Alterado só aqui")));
        service.confirm(1,9,new Confirm(plan.planoId(),true,false));
        assertThat(product).containsEntry("COLECAO","OUTRA").containsEntry("VERSAO_FICHA","00009");
        verify(jdbc,never()).update(contains("dbo.[PRODUTOS]"),anyMap());
    }
    @Test void naoAtualizaVersaoAnteriorDaMesmaColecaoNemFichasDeOutraColecao() {
        doReturn(List.of(Map.of("VERSAO_FICHA","00002"))).when(jdbc).queryForList(startsWith("SELECT TOP (1) VERSAO_FICHA"),anyMap());
        assertThatThrownBy(() -> service.prepare(1,9,request(name("Novo")))).hasMessageContaining("Versões anteriores");
        doReturn(List.of(Map.of("U_COLECAO","OUTRA"))).when(jdbc).queryForList(startsWith("SELECT * FROM dbo.PRODUTO_FICHA_VERSAO"),anyMap());
        assertThatThrownBy(() -> service.prepare(1,9,request(name("Novo")))).hasMessageContaining("vínculo explícito");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void naoPermiteEscritaGlobalMesmoComConsentimentoDoCliente() {
        assertThatThrownBy(() -> service.prepare(1,9,request(new Patch("PRODUTOS",Map.of(),Map.of("DESC_PRODUTO","Novo"),false,false))))
            .hasMessageContaining("global compartilhado");
        verifyNoInteractions(database,jdbc);
    }
}
