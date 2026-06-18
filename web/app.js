// ===== Configuracao =====
const API = "http://localhost:8090";

// ===== Formatadores e helpers de texto =====
const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

// "2026-06-20" -> "20/06/2026"
function dataBR(iso) {
  if (!iso) return "";
  const [ano, mes, dia] = iso.split("-");
  return `${dia}/${mes}/${ano}`;
}

// Escapa texto do usuario antes de jogar no HTML (evita quebrar o layout / injecao).
function esc(texto) {
  return String(texto ?? "")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

// Normaliza para busca: minusculas e SEM acento ("João" -> "joao").
function normalizar(texto) {
  return String(texto ?? "").toLowerCase().normalize("NFD").replace(/\p{Diacritic}/gu, "");
}

// ===== Mensagens na tela =====
const elMensagem = document.getElementById("mensagem");
let timerMensagem = null;
function mostrarMensagem(texto, tipo = "sucesso") {
  elMensagem.textContent = texto;
  elMensagem.className = "mensagem " + tipo;
  clearTimeout(timerMensagem);
  if (tipo === "sucesso") {
    timerMensagem = setTimeout(() => elMensagem.classList.add("oculto"), 5000);
  }
}

// ===== Helpers de API =====
async function api(caminho, opcoes = {}) {
  const resp = await fetch(API + caminho, opcoes);
  const texto = await resp.text();
  const dados = texto ? JSON.parse(texto) : null;
  if (!resp.ok) {
    throw new Error(dados && dados.erro ? dados.erro : "HTTP " + resp.status);
  }
  return dados;
}
function postJson(caminho, objeto) {
  return api(caminho, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(objeto) });
}
function putJson(caminho, objeto) {
  return api(caminho, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify(objeto) });
}
function del(caminho) {
  return api(caminho, { method: "DELETE" });
}

// ===== Estado e cache =====
// Cache = dados crus vindos da API. As funcoes "Visiveis()" derivam dele o que aparece na tela.
let clientesCache = [];
let faturasCache = [];

// Estado de cada lista (busca/ordenacao/filtro + conjunto de ids selecionados).
const vClientes = { busca: "", ordenar: "nome-asc", sel: new Set() };
const vFaturas = { busca: "", ordenar: "vencimento-asc", status: "TODAS", clienteId: null, sel: new Set() };

// Estado de edicao dos formularios (null = modo "criar").
let clienteEditandoId = null;
let faturaEditandoId = null;

// ===== Derivacao do que aparece (filtro -> busca -> ordenacao) =====
function clientesVisiveis() {
  let lista = clientesCache.slice();
  const q = normalizar(vClientes.busca);
  if (q) lista = lista.filter((c) => normalizar(c.nome).includes(q) || normalizar(c.email).includes(q));
  switch (vClientes.ordenar) {
    case "nome-asc":  lista.sort((a, b) => a.nome.localeCompare(b.nome, "pt")); break;
    case "nome-desc": lista.sort((a, b) => b.nome.localeCompare(a.nome, "pt")); break;
    // criadoEm e string ISO ("2026-06-14T17:23:35") -> comparar como texto = ordem cronologica.
    case "recentes":  lista.sort((a, b) => String(b.criadoEm).localeCompare(String(a.criadoEm))); break;
    case "antigos":   lista.sort((a, b) => String(a.criadoEm).localeCompare(String(b.criadoEm))); break;
  }
  return lista;
}

function faturasVisiveis() {
  let lista = faturasCache.slice();
  if (vFaturas.clienteId != null) lista = lista.filter((f) => f.cliente && f.cliente.id === vFaturas.clienteId);
  if (vFaturas.status !== "TODAS") lista = lista.filter((f) => f.status === vFaturas.status);
  const q = normalizar(vFaturas.busca);
  if (q) lista = lista.filter((f) => normalizar(f.cliente ? f.cliente.nome : "").includes(q) || normalizar(f.descricao).includes(q));
  const nomeCli = (f) => (f.cliente ? f.cliente.nome : "");
  switch (vFaturas.ordenar) {
    // data_vencimento e "yyyy-MM-dd" -> comparar como texto = ordem cronologica.
    case "vencimento-asc":  lista.sort((a, b) => String(a.dataVencimento).localeCompare(String(b.dataVencimento))); break;
    case "vencimento-desc": lista.sort((a, b) => String(b.dataVencimento).localeCompare(String(a.dataVencimento))); break;
    case "valor-desc":      lista.sort((a, b) => Number(b.valor) - Number(a.valor)); break;
    case "valor-asc":       lista.sort((a, b) => Number(a.valor) - Number(b.valor)); break;
    case "cliente-asc":     lista.sort((a, b) => nomeCli(a).localeCompare(nomeCli(b), "pt")); break;
    case "status":          lista.sort((a, b) => a.status.localeCompare(b.status)); break;
  }
  return lista;
}

// ===== Atualiza o "selecionar tudo" e a barra de lote =====
// Conta apenas selecionados QUE ESTAO VISIVEIS (assim nunca agimos sobre item escondido por filtro).
function atualizarSelecaoUI(tipo, visiveis) {
  const v = tipo === "clientes" ? vClientes : vFaturas;
  const selVisiveis = visiveis.filter((x) => v.sel.has(x.id)).length;

  const selAll = document.getElementById(tipo === "clientes" ? "selAllClientes" : "selAllFaturas");
  selAll.checked = visiveis.length > 0 && selVisiveis === visiveis.length;
  selAll.indeterminate = selVisiveis > 0 && selVisiveis < visiveis.length;

  const barra = document.getElementById(tipo === "clientes" ? "barraClientes" : "barraFaturas");
  const contador = document.getElementById(tipo === "clientes" ? "contadorClientes" : "contadorFaturas");
  if (selVisiveis > 0) {
    barra.classList.remove("oculto");
    contador.textContent = selVisiveis + " selecionado(s)";
  } else {
    barra.classList.add("oculto");
  }
}

// ===== Render das tabelas (so o <tbody>) =====
async function carregarResumo() {
  try {
    const r = await api("/api/dashboard/resumo");
    document.getElementById("cardAReceber").textContent = moeda.format(r.totalAReceber);
    document.getElementById("cardAtrasado").textContent = moeda.format(r.totalAtrasado);
    document.getElementById("cardPendentes").textContent = r.pendentes;
    document.getElementById("cardAtrasadas").textContent = r.atrasadas;
  } catch (e) {
    mostrarMensagem("Nao foi possivel carregar o resumo: " + e.message, "erro");
  }
}

function renderClientes() {
  const tbody = document.getElementById("tabelaClientes");
  const visiveis = clientesVisiveis();
  if (clientesCache.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="vazio">Nenhum cliente cadastrado.</td></tr>';
  } else if (visiveis.length === 0) {
    tbody.innerHTML = '<tr><td colspan="5" class="vazio">Nenhum cliente encontrado para a busca.</td></tr>';
  } else {
    tbody.innerHTML = visiveis.map((c) => `<tr>
      <td class="col-check"><input type="checkbox" class="check-linha" data-id="${c.id}" ${vClientes.sel.has(c.id) ? "checked" : ""} /></td>
      <td>${esc(c.nome)}</td>
      <td>${esc(c.email)}</td>
      <td>${esc(c.telefone || "—")}</td>
      <td>
        <div class="acoes">
          <button class="botao botao-pequeno botao-secundario" data-acao="ver-faturas" data-id="${c.id}">Ver faturas</button>
          <button class="botao botao-pequeno" data-acao="editar" data-id="${c.id}">Editar</button>
          <button class="botao botao-pequeno botao-perigo" data-acao="excluir" data-id="${c.id}">Excluir</button>
        </div>
      </td>
    </tr>`).join("");
  }
  atualizarSelecaoUI("clientes", visiveis);
}

function renderFaturas() {
  // Se estamos filtrando por um cliente que nao existe mais, limpa o filtro.
  if (vFaturas.clienteId != null && !clientesCache.some((c) => c.id === vFaturas.clienteId)) {
    vFaturas.clienteId = null;
  }

  // Atualiza o chip "Faturas de X".
  const chip = document.getElementById("chipCliente");
  if (vFaturas.clienteId != null) {
    const c = clientesCache.find((x) => x.id === vFaturas.clienteId);
    chip.querySelector(".chip-texto").textContent = "Faturas de: " + (c ? c.nome : "#" + vFaturas.clienteId);
    chip.classList.remove("oculto");
  } else {
    chip.classList.add("oculto");
  }

  const tbody = document.getElementById("tabelaFaturas");
  const visiveis = faturasVisiveis();
  if (faturasCache.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" class="vazio">Nenhuma fatura cadastrada.</td></tr>';
  } else if (visiveis.length === 0) {
    tbody.innerHTML = '<tr><td colspan="7" class="vazio">Nenhuma fatura encontrada para os filtros.</td></tr>';
  } else {
    tbody.innerHTML = visiveis.map((f) => {
      const classeBadge = "badge badge-" + f.status.toLowerCase();
      const cliente = f.cliente ? f.cliente.nome : "(sem cliente)";
      const opcoes = ["PENDENTE", "PAGA", "ATRASADA"]
        .map((s) => `<option value="${s}" ${s === f.status ? "selected" : ""}>${s}</option>`).join("");
      return `<tr>
        <td class="col-check"><input type="checkbox" class="check-linha" data-id="${f.id}" ${vFaturas.sel.has(f.id) ? "checked" : ""} /></td>
        <td>${esc(cliente)}</td>
        <td>${esc(f.descricao || "—")}</td>
        <td>${moeda.format(f.valor)}</td>
        <td>${dataBR(f.dataVencimento)}</td>
        <td><span class="${classeBadge}">${esc(f.status)}</span></td>
        <td>
          <div class="acoes">
            <select class="status-select" data-acao="status" data-id="${f.id}" title="Mudar status">${opcoes}</select>
            <button class="botao botao-pequeno" data-acao="editar" data-id="${f.id}">Editar</button>
            <button class="botao botao-pequeno botao-perigo" data-acao="excluir" data-id="${f.id}">Excluir</button>
          </div>
        </td>
      </tr>`;
    }).join("");
  }
  atualizarSelecaoUI("faturas", visiveis);
}

// ===== Carregamento (fetch -> cache -> render) =====
async function carregarClientes() {
  const select = document.getElementById("selectCliente");
  const selecionadoAntes = select.value;
  try {
    const clientes = await api("/api/clientes");
    clientesCache = clientes;
    // Remove da selecao ids que nao existem mais.
    for (const id of [...vClientes.sel]) if (!clientes.some((c) => c.id === id)) vClientes.sel.delete(id);

    // <select> do formulario de fatura.
    if (clientes.length === 0) {
      select.innerHTML = '<option value="">Cadastre um cliente primeiro</option>';
    } else {
      select.innerHTML = clientes.map((c) => `<option value="${c.id}">${esc(c.nome)}</option>`).join("");
      select.value = selecionadoAntes; // mantem a escolha se o cliente ainda existir
    }
    renderClientes();
  } catch (e) {
    document.getElementById("tabelaClientes").innerHTML =
      '<tr><td colspan="5" class="vazio">Erro ao carregar clientes.</td></tr>';
    mostrarMensagem("Nao foi possivel carregar os clientes: " + e.message, "erro");
  }
}

async function carregarFaturas() {
  try {
    const faturas = await api("/api/faturas");
    faturasCache = faturas;
    for (const id of [...vFaturas.sel]) if (!faturas.some((f) => f.id === id)) vFaturas.sel.delete(id);
    renderFaturas();
  } catch (e) {
    document.getElementById("tabelaFaturas").innerHTML =
      '<tr><td colspan="7" class="vazio">Erro ao carregar faturas.</td></tr>';
    mostrarMensagem("Nao foi possivel carregar as faturas: " + e.message, "erro");
  }
}

// Recarrega tudo de uma vez para a tela ficar sempre consistente.
async function atualizarPainel() {
  await Promise.all([carregarResumo(), carregarClientes(), carregarFaturas()]);
}

// ===== Cliente: criar / editar / excluir =====
async function salvarCliente(evento) {
  evento.preventDefault();
  const form = evento.target;
  const corpo = { nome: form.nome.value.trim(), email: form.email.value.trim(), telefone: form.telefone.value.trim() };
  try {
    if (clienteEditandoId === null) {
      const criado = await postJson("/api/clientes", corpo);
      mostrarMensagem(`Cliente "${criado.nome}" cadastrado.`, "sucesso");
    } else {
      await putJson("/api/clientes/" + clienteEditandoId, corpo);
      mostrarMensagem("Cliente atualizado.", "sucesso");
    }
    cancelarEdicaoCliente();
    await atualizarPainel();
  } catch (e) {
    mostrarMensagem("Erro ao salvar cliente: " + e.message, "erro");
  }
}

function iniciarEdicaoCliente(id) {
  const c = clientesCache.find((x) => x.id === id);
  if (!c) return;
  const form = document.getElementById("formCliente");
  form.nome.value = c.nome;
  form.email.value = c.email;
  form.telefone.value = c.telefone || "";
  clienteEditandoId = id;
  document.getElementById("tituloFormCliente").textContent = "Editar cliente #" + id;
  document.getElementById("btnSubmitCliente").textContent = "Salvar alteracoes";
  document.getElementById("btnCancelarCliente").classList.remove("oculto");
  form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function cancelarEdicaoCliente() {
  clienteEditandoId = null;
  document.getElementById("formCliente").reset();
  document.getElementById("tituloFormCliente").textContent = "Novo cliente";
  document.getElementById("btnSubmitCliente").textContent = "Cadastrar cliente";
  document.getElementById("btnCancelarCliente").classList.add("oculto");
}

async function excluirCliente(id) {
  const c = clientesCache.find((x) => x.id === id);
  const nome = c ? c.nome : "#" + id;
  if (!confirm(`Excluir o cliente "${nome}"?\nIsso apaga tambem as faturas e o historico de alertas dele. Esta acao nao pode ser desfeita.`)) return;
  try {
    await del("/api/clientes/" + id);
    mostrarMensagem("Cliente excluido.", "sucesso");
    if (clienteEditandoId === id) cancelarEdicaoCliente();
    await atualizarPainel();
  } catch (e) {
    mostrarMensagem("Erro ao excluir cliente: " + e.message, "erro");
  }
}

// ===== Fatura: criar / editar / excluir / mudar status =====
async function salvarFatura(evento) {
  evento.preventDefault();
  const form = evento.target;
  const corpo = {
    clienteId: Number(form.clienteId.value),
    descricao: form.descricao.value.trim(),
    valor: Number(form.valor.value),
    dataVencimento: form.dataVencimento.value,
    status: form.status.value,
  };
  try {
    if (faturaEditandoId === null) {
      await postJson("/api/faturas", corpo);
      mostrarMensagem("Fatura cadastrada.", "sucesso");
    } else {
      await putJson("/api/faturas/" + faturaEditandoId, corpo);
      mostrarMensagem("Fatura atualizada.", "sucesso");
    }
    cancelarEdicaoFatura();
    await atualizarPainel();
  } catch (e) {
    mostrarMensagem("Erro ao salvar fatura: " + e.message, "erro");
  }
}

function iniciarEdicaoFatura(id) {
  const f = faturasCache.find((x) => x.id === id);
  if (!f) return;
  const form = document.getElementById("formFatura");
  form.clienteId.value = f.cliente ? f.cliente.id : "";
  form.descricao.value = f.descricao || "";
  form.valor.value = f.valor;
  form.dataVencimento.value = f.dataVencimento;
  form.status.value = f.status;
  faturaEditandoId = id;
  document.getElementById("tituloFormFatura").textContent = "Editar fatura #" + id;
  document.getElementById("btnSubmitFatura").textContent = "Salvar alteracoes";
  document.getElementById("btnCancelarFatura").classList.remove("oculto");
  form.scrollIntoView({ behavior: "smooth", block: "center" });
}

function cancelarEdicaoFatura() {
  faturaEditandoId = null;
  document.getElementById("formFatura").reset();
  document.getElementById("tituloFormFatura").textContent = "Nova fatura";
  document.getElementById("btnSubmitFatura").textContent = "Cadastrar fatura";
  document.getElementById("btnCancelarFatura").classList.add("oculto");
}

async function excluirFatura(id) {
  if (!confirm(`Excluir a fatura #${id}?\nIsso apaga tambem os alertas dela. Esta acao nao pode ser desfeita.`)) return;
  try {
    await del("/api/faturas/" + id);
    mostrarMensagem("Fatura excluida.", "sucesso");
    if (faturaEditandoId === id) cancelarEdicaoFatura();
    await atualizarPainel();
  } catch (e) {
    mostrarMensagem("Erro ao excluir fatura: " + e.message, "erro");
  }
}

async function mudarStatusFatura(id, novoStatus) {
  const f = faturasCache.find((x) => x.id === id);
  if (!f) return;
  const corpo = {
    clienteId: f.cliente ? f.cliente.id : 0,
    descricao: f.descricao,
    valor: f.valor,
    dataVencimento: f.dataVencimento,
    status: novoStatus,
  };
  try {
    await putJson("/api/faturas/" + id, corpo);
    mostrarMensagem(`Fatura #${id} marcada como ${novoStatus}.`, "sucesso");
    await atualizarPainel();
  } catch (e) {
    mostrarMensagem("Erro ao mudar status: " + e.message, "erro");
    await carregarFaturas(); // volta o select ao valor real
  }
}

// ===== Exclusao em LOTE =====
// Exclui os ids selecionados E visiveis (interseccao), um a um. Conta sucessos e falhas
// (ex.: cliente com faturas vinculadas -> 409) e mostra um resumo no fim.
async function excluirSelecionados(tipo) {
  const v = tipo === "clientes" ? vClientes : vFaturas;
  const visiveis = tipo === "clientes" ? clientesVisiveis() : faturasVisiveis();
  const ids = visiveis.filter((x) => v.sel.has(x.id)).map((x) => x.id);
  if (ids.length === 0) return;

  const rotulo = tipo === "clientes" ? "cliente(s)" : "fatura(s)";
  if (!confirm(`Excluir ${ids.length} ${rotulo} selecionado(s)? Esta acao nao pode ser desfeita.`)) return;

  const base = tipo === "clientes" ? "/api/clientes/" : "/api/faturas/";
  let ok = 0;
  let falhas = 0;
  for (const id of ids) {
    try {
      await del(base + id);
      v.sel.delete(id);
      ok++;
    } catch (e) {
      falhas++;
    }
  }
  if (falhas === 0) {
    mostrarMensagem(`${ok} ${rotulo} excluido(s) com sucesso.`, "sucesso");
  } else {
    mostrarMensagem(`${ok} excluido(s); ${falhas} nao puderam ser excluido(s) (possuem vinculos).`, "erro");
  }
  await atualizarPainel();
}

// ===== Disparo da cobranca =====
async function dispararCobranca() {
  const botao = document.getElementById("btnDisparar");
  botao.disabled = true;
  botao.textContent = "Disparando...";
  try {
    const r = await postJson("/api/cobrancas/disparar", {});
    mostrarMensagem(
      `Cobranca executada: ${r.enviados} e-mail(s) enviados, ${r.falhas} falha(s), ${r.pulados} pulado(s).`,
      "sucesso"
    );
    await atualizarPainel();
  } catch (e) {
    mostrarMensagem("Erro ao disparar cobranca: " + e.message, "erro");
  } finally {
    botao.disabled = false;
    botao.textContent = "Disparar Cobranca Agora";
  }
}

// ===== Ligacao dos eventos (uma vez so) =====
// Formularios e botoes fixos.
document.getElementById("formCliente").addEventListener("submit", salvarCliente);
document.getElementById("formFatura").addEventListener("submit", salvarFatura);
document.getElementById("btnCancelarCliente").addEventListener("click", cancelarEdicaoCliente);
document.getElementById("btnCancelarFatura").addEventListener("click", cancelarEdicaoFatura);
document.getElementById("btnDisparar").addEventListener("click", dispararCobranca);

// Toolbar de clientes (busca/ordenacao). Mudar a busca limpa a selecao; ordenar mantem.
document.getElementById("buscaClientes").addEventListener("input", (e) => {
  vClientes.busca = e.target.value; vClientes.sel.clear(); renderClientes();
});
document.getElementById("ordenarClientes").addEventListener("change", (e) => {
  vClientes.ordenar = e.target.value; renderClientes();
});

// Toolbar de faturas (busca/status/ordenacao). Busca e status limpam a selecao; ordenar mantem.
document.getElementById("buscaFaturas").addEventListener("input", (e) => {
  vFaturas.busca = e.target.value; vFaturas.sel.clear(); renderFaturas();
});
document.getElementById("filtroStatusFaturas").addEventListener("change", (e) => {
  vFaturas.status = e.target.value; vFaturas.sel.clear(); renderFaturas();
});
document.getElementById("ordenarFaturas").addEventListener("change", (e) => {
  vFaturas.ordenar = e.target.value; renderFaturas();
});
document.getElementById("limparChip").addEventListener("click", () => {
  vFaturas.clienteId = null; vFaturas.sel.clear(); renderFaturas();
});

// "Selecionar tudo" (marca/desmarca os VISIVEIS).
document.getElementById("selAllClientes").addEventListener("change", (e) => {
  const vis = clientesVisiveis();
  if (e.target.checked) vis.forEach((c) => vClientes.sel.add(c.id));
  else vis.forEach((c) => vClientes.sel.delete(c.id));
  renderClientes();
});
document.getElementById("selAllFaturas").addEventListener("change", (e) => {
  const vis = faturasVisiveis();
  if (e.target.checked) vis.forEach((f) => vFaturas.sel.add(f.id));
  else vis.forEach((f) => vFaturas.sel.delete(f.id));
  renderFaturas();
});

// Botoes de exclusao em lote.
document.getElementById("excluirSelClientes").addEventListener("click", () => excluirSelecionados("clientes"));
document.getElementById("excluirSelFaturas").addEventListener("click", () => excluirSelecionados("faturas"));

// EVENT DELEGATION nas tabelas: como as linhas sao recriadas, ouvimos no <tbody>.
const tbodyClientes = document.getElementById("tabelaClientes");
tbodyClientes.addEventListener("click", (e) => {
  const botao = e.target.closest("button[data-acao]");
  if (!botao) return;
  const id = Number(botao.dataset.id);
  if (botao.dataset.acao === "editar") iniciarEdicaoCliente(id);
  else if (botao.dataset.acao === "excluir") excluirCliente(id);
  else if (botao.dataset.acao === "ver-faturas") {
    vFaturas.clienteId = id;
    vFaturas.sel.clear();
    renderFaturas();
    document.getElementById("secaoFaturas").scrollIntoView({ behavior: "smooth", block: "start" });
  }
});
tbodyClientes.addEventListener("change", (e) => {
  const chk = e.target.closest("input.check-linha");
  if (!chk) return;
  const id = Number(chk.dataset.id);
  if (chk.checked) vClientes.sel.add(id); else vClientes.sel.delete(id);
  renderClientes();
});

const tbodyFaturas = document.getElementById("tabelaFaturas");
tbodyFaturas.addEventListener("click", (e) => {
  const botao = e.target.closest("button[data-acao]");
  if (!botao) return;
  const id = Number(botao.dataset.id);
  if (botao.dataset.acao === "editar") iniciarEdicaoFatura(id);
  else if (botao.dataset.acao === "excluir") excluirFatura(id);
});
tbodyFaturas.addEventListener("change", (e) => {
  // Pode ser o checkbox da linha OU o select de status: diferenciamos por closest.
  const chk = e.target.closest("input.check-linha");
  if (chk) {
    const id = Number(chk.dataset.id);
    if (chk.checked) vFaturas.sel.add(id); else vFaturas.sel.delete(id);
    renderFaturas();
    return;
  }
  const sel = e.target.closest("select[data-acao='status']");
  if (sel) mudarStatusFatura(Number(sel.dataset.id), sel.value);
});

// ===== Carga inicial =====
carregarResumo();
carregarClientes();
carregarFaturas();
