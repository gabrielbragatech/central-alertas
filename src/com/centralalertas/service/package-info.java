/**
 * Camada de Service: as regras de negocio. Orquestra os DAOs para identificar faturas
 * a vencer/atrasadas, disparar alertas e evitar envios duplicados. Os Handlers chamam
 * os Services — nunca os DAOs diretamente.
 */
package com.centralalertas.service;
