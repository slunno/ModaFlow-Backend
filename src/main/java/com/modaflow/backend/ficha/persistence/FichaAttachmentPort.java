package com.modaflow.backend.ficha.persistence;

import java.io.InputStream;
import java.util.Optional;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.AttachmentReference;
import com.modaflow.backend.ficha.persistence.FichaPersistenceContracts.Identity;

/**
 * Porta sem adaptador. Arquivos ficam fora do JSON; armazenamento físico, upload,
 * retenção/limites, checksum e exclusão ainda dependem de definição aprovada.
 * Não resolve URLs blob/data, não busca URLs arbitrárias e não converte anexos.
 */
public interface FichaAttachmentPort {
    Optional<AttachmentReference> findReference(Identity identity, String anexoId);

    /** Autorizar identidade antes de abrir; quem obtém o stream deve fechá-lo. */
    Optional<InputStream> open(Identity identity, String anexoId);
}
