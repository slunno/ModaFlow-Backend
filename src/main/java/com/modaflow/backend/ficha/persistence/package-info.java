/**
 * Persistência opcional de rascunhos centrais do portal, desativada por padrão.
 *
 * <p>O adaptador JDBC usa exclusivamente modaflow.* em .6/HOMOLOG_COBRANCA,
 * com pool dedicado, guarda anterior ao pool e conferência na conexão em uso.
 * Não altera o DataSource primário nem o Linx/KINGEJOE. Os endpoints exigem
 * usuário autenticado e vínculo à marca pelo validador existente; revisão,
 * horário e autoria são do servidor. Revisão de rascunho não é versão ERP.</p>
 *
 * <p>URLs locais presentes em conteúdo legado não são transformadas em referências
 * persistidas. Anexos ficam no navegador; o adaptador exige referências vazias,
 * e o serviço recusa mídias locais embutidas, sem apagá-las no navegador.</p>
 */
package com.modaflow.backend.ficha.persistence;
