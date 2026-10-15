package com.modaflow.backend.integracao.linx.write;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static com.modaflow.backend.integracao.linx.write.LinxWriteContracts.*;

class LinxWriteRulesTest {
    @Test void semFiscalPrecoArbitrarioSqlLivreOuArredondamento() {
        for(String field: new String[]{"CUSTO","CLASSIF_FISCAL","U_NOME_PRODUTO","DESC_PRODUTO];DROP TABLE X;--"})
            assertThatThrownBy(() -> LinxWriteRules.normalize(new Patch("PRODUTOS",Map.of(),Map.of(field,"x"),false,false))).hasMessageContaining("global compartilhado");
        assertThatThrownBy(() -> LinxWriteRules.normalize(new Patch("PRODUTOS;DROP TABLE X",Map.of(),Map.of("DESC_PRODUTO","x"),false,false))).hasMessageContaining("não autorizada");
        for(String value: new String[]{"1.2345","-1","1.000,5","1000000","NaN"})
            assertThatThrownBy(() -> LinxWriteRules.normalize(new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1","ITEM","0001"),Map.of("C1",value),false,false)));
    }
    @Test void descarteSemConfirmacaoOuChaveParcialNaoEPermitido() {
        assertThatThrownBy(() -> LinxWriteRules.normalize(new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1"),Map.of(),true,true)));
        assertThatThrownBy(() -> LinxWriteRules.normalize(new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1","ITEM","0001"),Map.of(),true,false)));
        assertThatThrownBy(() -> LinxWriteRules.normalize(new Patch("PRODUTOS",Map.of(),Map.of(),true,true)));
    }
    @Test void zeroExplicitoNaoViraVazioENaoTruncaTextos() {
        assertThat(LinxWriteRules.normalize(new Patch("PRODUTO_VERSAO_MATERIAL",Map.of("MATERIAL","M1","ITEM","0001"),Map.of("C48","0"),false,false))).containsKey("C48");
        assertThatThrownBy(() -> LinxWriteRules.normalize(new Patch("PRODUTOS",Map.of(),Map.of("DESC_PRODUTO","á".repeat(51)),false,false)));
    }
}
