/**
 * Camada DAO: acesso ao banco com JDBC puro e {@code PreparedStatement} (sem ORM).
 * Cada DAO faz o CRUD de uma entidade e mapeia o {@code ResultSet} para os POJOs do
 * pacote {@code model}. E a unica camada que conhece SQL.
 */
package com.centralalertas.dao;
