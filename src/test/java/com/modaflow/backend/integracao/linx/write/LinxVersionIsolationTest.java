package com.modaflow.backend.integracao.linx.write;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.*;
import static com.modaflow.backend.integracao.linx.write.LinxWriteContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Estado JDBC em memória. Não inicia Spring, SQL Server, sockets ou credenciais reais. */
class LinxVersionIsolationTest {
    final LinxWriteServiceTest fixture=new LinxWriteServiceTest();
    NamedParameterJdbcTemplate jdbc;
    LinxWriteService service;
    final Map<String,List<Map<String,Object>>> tables=new HashMap<>();
    final UUID piece=UUID.fromString("f24cbbad-63db-474a-b3a3-cf329698156a");
    boolean corruptSource;
    @BeforeEach void setup() throws Exception {
        fixture.setup(); jdbc=fixture.jdbc; service=fixture.service;
        tables.put("PRODUTO_FICHA_VERSAO",new ArrayList<>(List.of(new HashMap<>(Map.of(
            "PRODUTO","TESTE01","VERSAO_FICHA","00001","U_COLECAO","TESTE","DESC_VERSAO_FICHA","Referência",
            "OBS_VERSAO_FICHA","Observação original","RESPONSAVEL","Origem","STATUS_VERSAO_FICHA","A")))));
        tables.put("PRODUTO_VERSAO_MATERIAL",fixture.materialRows);
        tables.put("PRODUTO_VERSAO_MATERIAL_COR",fixture.colorRows);
        for (var rows:List.of(fixture.materialRows,fixture.colorRows)) for(var row:rows) {
            row.put("PRODUTO","TESTE01"); row.put("VERSAO_FICHA","00001");
        }
        doAnswer(invocation -> {
            String sql=invocation.getArgument(0); Map<String,Object> params=invocation.getArgument(1);
            if(sql.equals(LinxWriteService.SCHEMA_SQL)) return List.of(new TreeMap<>(Map.of("object_id",1,"name","PRODUTOS","BLOQUEADO",false)));
            if(sql.contains("FROM sys.columns")) {
                var table=params.get("tabela").toString().substring(4);
                return tables.get(table).getFirst().keySet().stream().sorted().map(c -> Map.of("name",c)).toList();
            }
            if(sql.contains("dbo.PRODUTOS_TAMANHOS")) return List.of(Map.of("TAMANHO_1","P"));
            if(sql.contains("dbo.COLECOES")) return List.of(Map.of("COLECAO",params.get("colecao"),"DESC_COLECAO",
                params.get("colecao").equals("OUTRA")?"Coleção nova":"Coleção teste","GRIFFE","KING&JOE","INATIVO",false));
            if(sql.contains("dbo.PRODUTO_CORES")) return List.of(Map.of("COR_PRODUTO","CP1","COR","B1","U_COLECAO","TESTE"));
            for(var table:List.of("PRODUTO_FICHA_VERSAO","PRODUTO_VERSAO_MATERIAL_COR","PRODUTO_VERSAO_MATERIAL"))
                if(sql.contains("dbo."+table+" ") || sql.contains("dbo.["+table+"]")) {
                    var rows=tables.get(table).stream()
                        .filter(row -> !sql.contains("VERSAO_FICHA=:versao") || row.get("VERSAO_FICHA").equals(params.get("versao")))
                        .filter(row -> !sql.contains("VERSAO_FICHA<>:versao") || !row.get("VERSAO_FICHA").equals(params.get("versao")))
                        .filter(row -> !sql.contains("U_COLECAO=:colecao") || Objects.equals(row.get("U_COLECAO"),params.get("colecao")))
                        .sorted(Comparator.comparing(row -> row.get("VERSAO_FICHA").toString())).toList();
                    if(sql.contains("TOP (1)")) return rows.isEmpty()?List.of():List.of(new HashMap<>(rows.getLast()));
                    return rows.stream().map(HashMap::new).toList();
                }
            if(sql.contains("dbo.PRODUTOS")) return List.of(new HashMap<>(fixture.product));
            throw new AssertionError(sql);
        }).when(jdbc).queryForList(anyString(),anyMap());
        doAnswer(invocation -> {
            String sql=invocation.getArgument(0); Map<String,Object> params=invocation.getArgument(1);
            if(sql.startsWith("INSERT INTO")) {
                var table=tables.keySet().stream().filter(t -> sql.startsWith("INSERT INTO dbo.["+t+"]")).findFirst().orElseThrow();
                var copies=tables.get(table).stream().filter(row -> row.get("VERSAO_FICHA").equals(params.get("versao")))
                    .map(row -> { var copy=new HashMap<>(row); copy.put("VERSAO_FICHA",params.get("novaVersao"));
                        if(table.equals("PRODUTO_FICHA_VERSAO")) {
                            copy.put("U_COLECAO",params.get("novaColecao")); copy.put("DESC_VERSAO_FICHA",params.get("novaDescricao"));
                            copy.put("OBS_VERSAO_FICHA",params.get("novaObservacao")); copy.put("RESPONSAVEL",params.get("novoResponsavel"));
                        } return (Map<String,Object>)copy; }).toList();
                tables.get(table).addAll(copies);
                if(corruptSource) fixture.product.put("DESC_PRODUTO","Alteração indevida de trigger");
                return copies.size();
            }
            if(sql.startsWith("UPDATE dbo.[PRODUTO_VERSAO_MATERIAL]")) {
                tables.get("PRODUTO_VERSAO_MATERIAL").stream().filter(row -> row.get("VERSAO_FICHA").equals(params.get("versao")))
                    .forEach(row -> row.put("DESC_USO_MATERIAL",params.get("value0")));
                return 1;
            }
            throw new AssertionError("DML não esperado: "+sql);
        }).when(jdbc).update(anyString(),anyMap());
    }
    VersionRequest request() {
        return new VersionRequest(piece,fixture.identity,new Destination("OUTRA","Coleção nova"),"Ficha nova",
            fixture.request(fixture.name("Novo uso")).conferencia(),List.of(fixture.name("Novo uso")));
    }
    @Test void criaVersaoParaOutraColecaoSemAlterarReferenciaNemPonteiroGlobal() {
        var preview=service.prepareVersion(1,9,request());
        assertThat(preview.identidade().versao()).isEqualTo("00002");
        verify(jdbc,never()).update(anyString(),anyMap());
        var receipt=service.confirmVersion(1,9,new Confirm(preview.planoId(),true,false));
        assertThat(receipt.escopo()).isEqualTo("NOVA_VERSAO_COLECAO");
        assertThat(receipt.integracaoId()).isEqualTo(piece);
        assertThat(tables.get("PRODUTO_FICHA_VERSAO")).hasSize(2);
        assertThat(tables.get("PRODUTO_FICHA_VERSAO").getLast()).containsEntry("U_COLECAO","OUTRA");
        assertThat(fixture.materialRows.getFirst()).containsEntry("DESC_USO_MATERIAL","Corpo");
        assertThat(fixture.materialRows.getLast()).containsEntry("DESC_USO_MATERIAL","Novo uso").containsEntry("VERSAO_FICHA","00002");
        assertThat(fixture.product).containsEntry("VERSAO_FICHA","00001").containsEntry("COLECAO","TESTE");
        verify(jdbc,never()).update(contains("UPDATE dbo.[PRODUTOS]"),anyMap());
        assertThatThrownBy(() -> service.confirmVersion(1,9,new Confirm(preview.planoId(),true,false))).hasMessageContaining("Prévia inexistente");
    }
    @Test void naoDuplicaPecaDepoisDeRespostaPerdida() {
        var preview=service.prepareVersion(1,9,request());
        service.confirmVersion(1,9,new Confirm(preview.planoId(),true,false));
        assertThatThrownBy(() -> service.prepareVersion(1,9,request())).hasMessageContaining("já possui uma integração");
    }
    @Test void concorrenciaNaReservaNaoAtualizaNemCriaAposPreviaObsoleta() {
        var preview=service.prepareVersion(1,9,request());
        fixture.product.put("DESC_PRODUTO","Editado externamente");
        assertThatThrownBy(() -> service.confirmVersion(1,9,new Confirm(preview.planoId(),true,false))).hasMessageContaining("mudaram após a prévia");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void rejeitaDestinoJaExistenteOuDescricaoDivergente() {
        var source=request();
        var wrong=new VersionRequest(piece,source.origem(),new Destination("OUTRA","Nome incorreto"),source.descricaoVersao(),source.conferencia(),source.alteracoes());
        assertThatThrownBy(() -> service.prepareVersion(1,9,wrong)).hasMessageContaining("descrição/griffe");
        var copy=new HashMap<>(tables.get("PRODUTO_FICHA_VERSAO").getFirst()); copy.put("VERSAO_FICHA","00002"); copy.put("U_COLECAO","OUTRA");
        tables.get("PRODUTO_FICHA_VERSAO").add(copy);
        assertThatThrownBy(() -> service.prepareVersion(1,9,source)).hasMessageContaining("Já existe ficha");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
    @Test void qualquerEfeitoColateralNaReferenciaImpedeReciboEExigeRollback() {
        var preview=service.prepareVersion(1,9,request()); corruptSource=true;
        assertThatThrownBy(() -> service.confirmVersion(1,9,new Confirm(preview.planoId(),true,false))).hasMessageContaining("cadastro compartilhado foi alterado");
        verify(fixture.database).transaction(eq(false),any());
    }
    @Test void tokenNaoServeParaOutroUsuarioEOuMarca() {
        var preview=service.prepareVersion(1,9,request());
        assertThatThrownBy(() -> service.confirmVersion(1,10,new Confirm(preview.planoId(),true,false))).hasMessageContaining("outra sessão");
        assertThatThrownBy(() -> service.confirmVersion(2,9,new Confirm(preview.planoId(),true,false))).hasMessageContaining("outra sessão");
        verify(jdbc,never()).update(anyString(),anyMap());
    }
}
