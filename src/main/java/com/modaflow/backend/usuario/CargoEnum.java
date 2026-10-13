package com.modaflow.backend.usuario;

import com.modaflow.backend.marca.Marca;

/**
 * ============================================================================
 * ENUM: CargoEnum
 * DESCRIÇÃO: Define os cargos e permissões dos usuários no ModaFlow PLM.
 * USADO EM: UsuarioMarcaRole (vínculo de usuário com marca), Security Roles.
 * ============================================================================
 */
public enum CargoEnum {
    /** Administrador global do sistema */
    ADMIN,
    /** Gestor de Coleção / Marca */
    GESTOR,
    ASSISTENTE,
    COORDENADOR,
    /** Estilista / Designer responsável pela criação visual */
    ESTILISTA,
    /** Modelista responsável pela modelagem técnica */
    MODELISTA,
    /** Comprador / Responsável por suprimentos e insumos */
    COMPRADOR,
    /** Visualizador com acesso somente leitura */
    VISUALIZADOR
}
