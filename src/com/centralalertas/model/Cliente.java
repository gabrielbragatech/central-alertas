package com.centralalertas.model;

import java.time.LocalDateTime;

/**
 * Um cliente (tabela cliente). Tem muitas faturas (relacao 1:N).
 * So dados e getters/setters — nenhuma logica nem acesso a banco.
 */
public class Cliente {

    private long id;                 // 0 enquanto nao foi salvo; o banco gera o id
    private String nome;
    private String email;
    private String telefone;         // pode ser nulo (coluna aceita NULL)
    private LocalDateTime criadoEm;  // preenchido pelo DAO no momento da insercao

    // Construtor vazio: util para o DAO criar o objeto e ir preenchendo com os setters.
    public Cliente() {
    }

    // Construtor de conveniencia para criar um cliente novo (sem id/criadoEm ainda).
    public Cliente(String nome, String email, String telefone) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(LocalDateTime criadoEm) {
        this.criadoEm = criadoEm;
    }

    // toString so para imprimir o objeto de forma legivel (uso no teste do Main).
    @Override
    public String toString() {
        return "Cliente{id=" + id + ", nome='" + nome + "', email='" + email
                + "', telefone='" + telefone + "', criadoEm=" + criadoEm + "}";
    }
}
