package com.modaflow.backend.marca;

import com.modaflow.backend.marca.Marca;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * REPOSITÓRIO: MarcaRepository
 * DESCRIÇÃO: Operações de banco de dados para a entidade Marca (Tenants).
 */
@Repository
public interface MarcaRepository extends JpaRepository<Marca, Long> {
    Optional<Marca> findByCode(String code);
}
