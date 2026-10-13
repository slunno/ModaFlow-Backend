package com.modaflow.backend.ficha.persistence;

import java.util.List;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.AuditEntry;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Identity;

/**
 * Leitura da trilha durável da ficha. A escrita da trilha é parte da confirmação
 * atômica de FichaRepositoryPort.persistWithAudit, não uma chamada posterior que
 * possa falhar depois de já ter gravado a ficha. Não aceita autoria do navegador.
 */
public interface FichaAuditPort {
    /** Ordenação estável pelo adaptador; afterEventoId nulo começa na primeira página. */
    List<AuditEntry> list(Identity identity, String afterEventoId, int limit);
}
