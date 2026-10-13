package com.modaflow.backend.ficha.persistence;

import java.util.Optional;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Identity;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.ServerWrite;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Snapshot;

/**
 * Porta independente do adaptador. A implementação SQL só é registrada quando
 * a configuração opt-in está habilitada e validada para o homolog .6.
 * O adaptador confere identidade/revisão e persiste ficha + auditoria atomicamente.
 * Nesta etapa, anexos permanecem locais e referências não vazias são rejeitadas.
 */
public interface FichaRepositoryPort {
    Optional<Snapshot> find(Identity identity);

    /**
     * CRIAR falha se já existir; ATUALIZAR falha se ausente/revisão divergente.
     * Lança FichaRevisionConflictException sem sobrescrever/auditar uma gravação
     * rejeitada. Nunca retornar sucesso antes do commit nem converter falha em
     * armazenamento local/em memória. Autorização é responsabilidade do serviço.
     */
    Snapshot persistWithAudit(ServerWrite command);
}
