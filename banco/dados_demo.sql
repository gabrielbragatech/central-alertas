-- ============================================================================
--  Central de Alertas — Dados de DEMONSTRACAO
--  Como usar: phpMyAdmin -> selecione o banco "central_alertas" -> aba SQL ->
--  cole este arquivo -> Executar. (Tambem da para usar a aba "Importar".)
--
--  ATENCAO: este script APAGA todos os dados atuais das 3 tabelas e recria do zero.
-- ============================================================================

-- Garante acentuacao correta e o banco certo.
SET NAMES utf8mb4;
USE central_alertas;

-- ----------------------------------------------------------------------------
-- 1) LIMPEZA
--    Usamos DELETE (e nao TRUNCATE) de proposito: o phpMyAdmin/MariaDB recusam
--    TRUNCATE numa tabela referenciada por chave estrangeira (erro #1701), mesmo
--    desligando a checagem de FK. Ja o DELETE na ordem filho -> pai respeita as FKs:
--    apagamos primeiro a tabela filha (alerta_enviado), depois fatura, por fim cliente.
--    Como o DELETE nao zera o contador de id, em seguida reiniciamos o AUTO_INCREMENT
--    de cada tabela com ALTER TABLE (assim os ids voltam a comecar em 1).
-- ----------------------------------------------------------------------------
DELETE FROM alerta_enviado;
DELETE FROM fatura;
DELETE FROM cliente;

ALTER TABLE alerta_enviado AUTO_INCREMENT = 1;
ALTER TABLE fatura         AUTO_INCREMENT = 1;
ALTER TABLE cliente        AUTO_INCREMENT = 1;

-- ----------------------------------------------------------------------------
-- 2) CLIENTES (5)
--    Apos a limpeza acima, os ids comecam em 1, na ordem abaixo.
--    O cliente de id = 2 (TechBraga Solucoes) usa o e-mail REAL da demonstracao,
--    para os e-mails de cobranca chegarem de verdade.
-- ----------------------------------------------------------------------------
INSERT INTO cliente (nome, email, telefone, criado_em) VALUES
  ('Mercado Bom Preco Ltda',     'financeiro@mercadobompreco.com.br', '(11) 3344-5566',  NOW()), -- id 1
  ('TechBraga Solucoes',         'gabrielbraga.tech@gmail.com',       '(11) 99876-5432', NOW()), -- id 2  <-- e-mail real
  ('Restaurante Sabor Caseiro',  'contato@saborcaseiro.com.br',       '(21) 2555-1010',  NOW()), -- id 3
  ('Auto Pecas Veloz',           'financeiro@autopecasveloz.com.br',  '(31) 3222-4567',  NOW()), -- id 4
  ('Clinica Vida e Saude',       'administracao@vidaesaude.com.br',    '(47) 3033-7788',  NOW()); -- id 5

-- ----------------------------------------------------------------------------
-- 3) FATURAS (10)
--    Datas calculadas a partir de HOJE com CURDATE() +/- INTERVAL, entao o script
--    continua valido em qualquer dia que voce rodar.
--    De proposito, TODAS as faturas que disparam e-mail HOJE sao do cliente 2
--    (e-mail real) — assim a demo so envia e-mail de verdade, sem "bounce" para
--    dominios ficticios dos outros clientes.
-- ----------------------------------------------------------------------------
INSERT INTO fatura (cliente_id, descricao, valor, data_vencimento, status, criado_em) VALUES
  -- Cliente 2 (Gabriel) — estas 3 disparam e-mail real ao rodar a cobranca:
  (2, 'Plano de suporte mensal',          450.00,  CURDATE() + INTERVAL 2 DAY,   'PENDENTE', NOW()), -- vence em 2 dias -> alerta "vencimento proximo"
  (2, 'Licenca anual de software',        1200.00, CURDATE() - INTERVAL 5 DAY,   'ATRASADA', NOW()), -- ja atrasada -> alerta "atrasada"
  (2, 'Consultoria do mes passado',       980.00,  CURDATE() - INTERVAL 10 DAY,  'PENDENTE', NOW()), -- vencida e ainda pendente -> a cobranca marca ATRASADA e envia

  -- Demais clientes — faturas futuras (nao disparam nada hoje) e uma paga:
  (1, 'Compra de mercadorias',            2350.75, CURDATE() + INTERVAL 10 DAY,  'PENDENTE', NOW()),
  (1, 'Mensalidade do sistema',           199.90,  CURDATE() + INTERVAL 25 DAY,  'PENDENTE', NOW()),
  (3, 'Fornecimento de insumos',          875.40,  CURDATE() + INTERVAL 5 DAY,   'PENDENTE', NOW()),
  (3, 'Servico de consultoria',           1500.00, CURDATE() - INTERVAL 1 MONTH, 'PAGA',     NOW()), -- paga (verde, fora dos totais a receber/atrasado)
  (4, 'Reposicao de estoque',             640.00,  CURDATE() + INTERVAL 1 DAY,   'PENDENTE', NOW()),
  (5, 'Plano de saude ocupacional',       3200.00, CURDATE() + INTERVAL 15 DAY,  'PENDENTE', NOW()),
  (5, 'Exames admissionais',              540.25,  CURDATE() + INTERVAL 7 DAY,   'PENDENTE', NOW());

-- Fim. Confira no painel: varias PENDENTES, 1 ATRASADA (vermelho) e 1 PAGA (verde).
-- Ao clicar em "Disparar Cobranca Agora", o cliente Gabriel recebe os e-mails e a
-- fatura "Consultoria do mes passado" muda de PENDENTE para ATRASADA.
