package com.centralalertas.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Representa uma fatura (tabela fatura). Pertence a UM cliente (lado N do 1:N).
 *
 * <p>Guardamos o objeto {@link Cliente} associado (e nao so o cliente_id) porque
 * depois precisaremos de dados do cliente — como o e-mail — para enviar os alertas.</p>
 */
public class Fatura {

    private long id;                  // 0 enquanto nao foi salvo
    private Cliente cliente;          // cliente dono da fatura (mapeia cliente_id)
    private String descricao;         // pode ser nulo
    private BigDecimal valor;         // dinheiro -> BigDecimal (nunca double)
    private LocalDate dataVencimento; // coluna DATE (sem hora)
    private StatusFatura status;      // PENDENTE / PAGA / ATRASADA
    private LocalDateTime criadoEm;   // preenchido pelo DAO na insercao

    public Fatura() {
    }

    // Construtor de conveniencia para criar uma fatura nova.
    public Fatura(Cliente cliente, String descricao, BigDecimal valor, LocalDate dataVencimento) {
        this.cliente = cliente;
        this.descricao = descricao;
        this.valor = valor;
        this.dataVencimento = dataVencimento;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public void setDataVencimento(LocalDate dataVencimento) {
        this.dataVencimento = dataVencimento;
    }

    public StatusFatura getStatus() {
        return status;
    }

    public void setStatus(StatusFatura status) {
        this.status = status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    @Override
    public String toString() {
        // Mostra so o nome do cliente para nao poluir; evita NullPointer se cliente for nulo.
        String nomeCliente = (cliente != null) ? cliente.getNome() : "null";
        return "Fatura{id=" + id + ", cliente='" + nomeCliente + "', descricao='" + descricao
                + "', valor=" + valor + ", dataVencimento=" + dataVencimento
                + ", status=" + status + ", criadoEm=" + criadoEm + "}";
    }
}
