package com.modaflow.backend.integracao.linx.write;

import com.modaflow.backend.ficha.FichaConferenciaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class LinxWriteContracts {
    private LinxWriteContracts() {}
    public record Identity(@NotBlank @Size(max=12) String produto,
                           @NotBlank @Size(max=25) String griffe,
                           @NotBlank @Size(max=6) String colecao,
                           @NotBlank @Size(max=200) String descColecao,
                           @NotBlank @Size(max=5) String versao) {}
    /** São patches, nunca a lista substituta da ficha. Chaves completas e colunas fechadas. */
    public record Patch(@NotBlank String tabela, @NotNull @Size(max=3) Map<String, String> chave,
                        @NotNull @Size(max=50) Map<String, Object> valores,
                        boolean excluir, boolean descarteConfirmado) {}
    public record Request(@NotNull @Valid Identity identidade,
                          @NotNull @Valid FichaConferenciaRequest conferencia,
                          @NotNull @Size(min=1,max=500) List<@Valid Patch> alteracoes) {}
    public record Destination(@NotBlank @Size(max=6) String colecao,
                              @NotBlank @Size(max=200) String descColecao) {}
    /** A origem é somente referência. O UUID identifica a peça local para impedir reenvio duplicado. */
    public record VersionRequest(@NotNull java.util.UUID pecaId, @NotNull @Valid Identity origem,
                                 @NotNull @Valid Destination destino,
                                 @NotBlank @Size(max=40) String descricaoVersao,
                                 @NotNull @Valid FichaConferenciaRequest conferencia,
                                 @NotNull @Size(max=500) List<@Valid Patch> alteracoes) {}
    public record Confirm(@NotNull java.util.UUID planoId, boolean confirmar,
                          boolean confirmarCadastroCompartilhado) {}
    public record Change(String tabela, Map<String, String> chave, String campo,
                         Object antes, Object depois, boolean exclusao) {}
    public record Preview(java.util.UUID planoId, Instant expiraEm, Identity identidade,
                          List<Change> alteracoes, List<String> avisos, boolean exigeConfirmacaoGlobal) {}
    public record Receipt(String status, String escopo, java.util.UUID integracaoId,
                          Instant confirmadaEm, Identity identidade, int camposGravados,
                          List<String> avisos) {}
    public record Capability(boolean habilitada, String servidor, String banco, String escopo, String mensagem) {}
    public record SchemaReview(String servidor, String banco, String sha256, int objetos,
                               List<String> objetosBloqueados, String aviso) {}
}
