

document.addEventListener("DOMContentLoaded", () => {
  highlightActiveNav();

  const page = document.body.dataset.page;
  switch (page) {
    case "dashboard":
      initDashboard();
      break;
    case "manage-assets":
      initManageAssets();
      break;
    case "holdings":
      initHoldings();
      break;
    case "charts":
      initCharts();
      break;
    case "chatbot":
      initChatbot();
      break;
    case "report":
      initReport();
      break;
    default:
      break;
  }
});




function highlightActiveNav() {
  const page = document.body.dataset.page;
  const navLinks = document.querySelectorAll("#sidebar .nav-link");

  navLinks.forEach((link) => {
    const href = link.getAttribute("href");
    if (!href) return;

    if (
        (page === "dashboard" && href.includes("index.html")) ||
        (page === "manage-assets" && href.includes("manage-assets.html")) ||
        (page === "holdings" && href.includes("holdings.html")) ||
        (page === "charts" && href.includes("charts.html")) ||
        (page === "chatbot" && href.includes("chatbot.html")) ||
        (page === "report" && href.includes("report.html"))
    ) {
      link.classList.add("active");
    }
  });
}

function getAvgBuyPriceForSymbol(symbol, holdings) {
  const symbolHoldings = holdings.filter(h => h.symbol === symbol && h.quantity > 0);

  if (symbolHoldings.length === 0) return 'N/A';

  const totalValue = symbolHoldings.reduce((sum, h) => sum + (h.buyPrice * h.quantity), 0);
  const totalQty = symbolHoldings.reduce((sum, h) => sum + h.quantity, 0);
  return (totalValue / totalQty).toFixed(2);
}

// GLOBAL SELL MODAL SUPPORT
let currentSellAsset = null;

function openSellModal(asset) {
  if (!asset || !asset.symbol) return;
  currentSellAsset = {
    symbol: asset.symbol,
    name: asset.name || '-',
    category: asset.category || '-',
    buyPrice: asset.buyPrice ?? 0,
    quantity: Number(asset.quantity) || 0,
    currentPrice: Number(asset.currentPrice) || asset.buyPrice || 0
  };
  const modal = document.getElementById('sellModal');
  if (!modal) return;
  document.getElementById('sell-symbol').textContent = currentSellAsset.symbol;
  document.getElementById('sell-available').textContent = currentSellAsset.quantity;
  document.getElementById('sell-max').textContent = currentSellAsset.quantity;
  document.getElementById('sell-qty').value = '';
  document.getElementById('sell-qty').max = currentSellAsset.quantity;
  document.getElementById('sell-error').classList.add('d-none');
  document.getElementById('sell-proceeds').textContent = '$0.00';
  new bootstrap.Modal(modal).show();
}

async function initDashboard() {
  const totalValueEl = document.getElementById("total-portfolio-value");
  const totalPLEl = document.getElementById("total-pl");
  const totalPLPercentEl = document.getElementById("total-pl-percent");
  const topCategoryEl = document.getElementById("top-category");
  const trendSummaryEl = document.getElementById("trend-summary");
  const categoryBarsContainer = document.getElementById("category-breakdown-bars");
  const trendList = document.getElementById("trend-list");
  const lastUpdatedEl = document.getElementById("last-updated");
  const refreshBtn = document.getElementById("refresh-dashboard");

  async function loadDashboard() {
    try {
      const data = await API.getPortfolio();

      // Total metrics
      totalValueEl.textContent = formatCurrency(data.totalValue);
      totalPLEl.textContent = formatCurrency(data.totalPL);

      const percent = data.totalPLPercent ?? (data.totalValue ? (data.totalPL / (data.totalValue - data.totalPL)) * 100 : 0);
      const percentStr = `${percent.toFixed(2)}% overall return.`;
      totalPLPercentEl.textContent = percentStr;
      totalPLEl.classList.toggle("pl-positive", data.totalPL >= 0);
      totalPLEl.classList.toggle("pl-negative", data.totalPL < 0);

      // Category breakdown
      renderCategoryBars(data.categories || {}, categoryBarsContainer);

      // Top category
      const topCat = Object.entries(data.categories || {}).sort((a, b) => b[1] - a[1])[0];
      topCategoryEl.textContent = topCat ? `${topCat[0]} (${formatPercent(topCat[1] / data.totalValue)})` : "—";

      // Trend summaries
      trendList.innerHTML = "";
      (data.trends || []).forEach((trend) => {
        const li = document.createElement("li");
        li.className = "list-group-item d-flex justify-content-between align-items-center";
        li.innerHTML = `<span class="fw-semibold">${trend.label}</span><span class="text-muted">${trend.description}</span>`;
        trendList.appendChild(li);
      });

      // Simple overall trend label
      if (data.totalPL > 0) {
        trendSummaryEl.textContent = "Uptrend";
      } else if (data.totalPL < 0) {
        trendSummaryEl.textContent = "Downtrend";
      } else {
        trendSummaryEl.textContent = "Flat";
      }

      // Last updated timestamp
      lastUpdatedEl.textContent = new Date().toLocaleString();
    } catch (err) {
      console.error("Failed to load dashboard:", err);
      if (lastUpdatedEl) {
        lastUpdatedEl.textContent = "Error loading data";
      }
    }
  }

  if (refreshBtn) {
    refreshBtn.addEventListener("click", () => {
      // Clear cache to force fresh data
      API.clearPriceCache();
      loadDashboard();
    });
  }

  await loadDashboard();
}

function renderCategoryBars(categories, container) {
  container.innerHTML = "";
  const entries = Object.entries(categories || {});
  if (!entries.length) {
    container.innerHTML = '<p class="small text-muted mb-0">No category data available.</p>';
    return;
  }

  const total = entries.reduce((sum, [, val]) => sum + (val || 0), 0) || 1;

  const colors = {
    Stocks: "#2563eb",
    Crypto: "#f97316",
  };

  entries.forEach(([name, value]) => {
    const percent = value / total;
    const row = document.createElement("div");
    row.className = "category-row";

    row.innerHTML = `
      <span class="category-label">${name}</span>
      <div class="category-bar-wrapper">
        <div class="category-bar" style="width:${(percent * 100).toFixed(1)}%;background:${
        colors[name] || "#4b5563"
    };"></div>
      </div>
      <span class="small text-muted">${formatPercent(percent)}</span>
    `;
    container.appendChild(row);
  });
}

/* MANAGE ASSETS */

async function initManageAssets() {

  let assets = [];

  const form = document.getElementById("asset-form");
  const tableBody = document.getElementById("asset-table-body");
  const searchInput = document.getElementById("asset-search");
  const countEl = document.getElementById("asset-count");
  const statusEl = document.getElementById("asset-status");

  function resetForm() {
    form.reset();
  }
  function renderTable(filter = "") {
    const filterLower = filter.toLowerCase();
    tableBody.innerHTML = "";

    const filtered = assets.filter((a) => {
      const combined = `${a.symbol} ${a.name}`.toLowerCase();
      return combined.includes(filterLower);
    });

    filtered.forEach((asset) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
      <td class="fw-semibold">${asset.symbol}</td>
      <td>${asset.name || "-"}</td>
      <td>${asset.category}</td>
      <td class="text-end">${formatCurrency(asset.buyPrice)}</td>
      <td class="text-end">${asset.quantity}</td>
    `;
      tableBody.appendChild(tr);
    });

    countEl.textContent = `${assets.length} asset${assets.length !== 1 ? "s" : ""}`;
    if (statusEl) {
      statusEl.textContent = assets.length
          ? "Current holdings. Add more above; sell from Holdings page."
          : "No assets yet. Add one using the form.";
    }
  }


  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const symbol = document.getElementById("asset-symbol").value.trim().toUpperCase();
    const name = document.getElementById("asset-name").value.trim();
    const category = document.getElementById("asset-category").value;
    const buyPrice = parseFloat(document.getElementById("asset-buy-price").value);
    const quantity = parseFloat(document.getElementById("asset-quantity").value);

    if (!symbol || !category || isNaN(buyPrice) || isNaN(quantity)) return;

    try {
      if (statusEl) statusEl.textContent = `Saving ${symbol}…`;
      await API.addAsset({ symbol, name, category, buyPrice, quantity });
      assets.push({ symbol, name, category, buyPrice, quantity });
      if (statusEl) statusEl.textContent = `Added ${symbol}. Sell from Holdings page.`;
    } catch (err) {
      console.error("Error saving asset:", err);
      if (statusEl) statusEl.textContent = "Failed to save. Check backend.";
      alert("Failed to save asset to backend.");
      return;
    }
    resetForm();
    renderTable(searchInput.value);
  });

  searchInput.addEventListener("input", (e) => {
    renderTable(e.target.value);
  });

  // Initial render
  async function loadFromBackend() {
    if (!statusEl) {
      renderTable();
      return;
    }

    try {
      statusEl.textContent = "Loading assets from backend…";
      const backendHoldings = await API.getHoldings();
      assets = backendHoldings.map((h) => ({
        symbol: h.symbol,
        name: h.name,
        category: h.category,
        buyPrice: h.buyPrice,
        quantity: h.quantity,
        currentPrice: h.currentPrice ?? h.buyPrice,
      }));
      renderTable();
      statusEl.textContent = assets.length
          ? "Loaded assets from backend. New entries will also be saved."
          : "No holdings found yet. Add one using the form.";
    } catch (err) {
      console.error("Error loading assets for manage page:", err);
      statusEl.textContent =
          "Failed to load existing holdings. You can still add new ones.";
      renderTable();
    }
  }

  await loadFromBackend();
}

/* HOLDINGS */

async function initHoldings() {
  const tableBody = document.getElementById("holdings-table-body");
  const totalValueEl = document.getElementById("holdings-total-value");
  const totalPLEl = document.getElementById("holdings-total-pl");
  const countEl = document.getElementById("holdings-count");
  const statusEl = document.getElementById("holdings-status");
  const searchInput = document.getElementById("holdings-search");
  const refreshBtn = document.getElementById("refresh-holdings");
  const historyTableBody = document.getElementById("history-table-body");
  const historyStatusEl = document.getElementById("history-status");

  let holdings = [];
  let history = [];

  async function loadHoldings() {
    try {
      statusEl.textContent = "Loading holdings…";
      holdings = await API.getHoldings();
      renderHoldings(searchInput.value);
      if (!holdings.length) {
        statusEl.textContent = "No active holdings.";
      } else {
        statusEl.textContent = `Last updated at ${new Date().toLocaleTimeString()}`;
      }
    } catch (err) {
      console.error("Error loading holdings:", err);
      statusEl.textContent =
          "Failed to load holdings. Check backend /api/assets/holdings.";
    }
  }

  function renderHoldings(filter = "") {
    const filterLower = filter.toLowerCase();
    tableBody.innerHTML = "";

    let totalValue = 0;
    let totalPL = 0;
    let count = 0;

    holdings
        .filter((h) => {
          const combined = `${h.symbol} ${h.name}`.toLowerCase();
          return combined.includes(filterLower);
        })
        .forEach((h) => {
          const cost = h.buyPrice * h.quantity;
          const value = h.currentPrice * h.quantity;
          const pl = value - cost;
          const plPercent = cost ? (pl / cost) * 100 : 0;

          totalValue += value;
          totalPL += pl;
          count += 1;

          const tr = document.createElement("tr");
          tr.innerHTML = `
          <td class="fw-semibold">${h.symbol}</td>
          <td>${h.name || "-"}</td>
          <td>${h.category || "-"}</td>
          <td class="text-end">${formatCurrency(h.buyPrice)}</td>
          <td class="text-end">${h.quantity}</td>
          <td class="text-end">${formatCurrency(h.currentPrice)}</td>
          <td class="text-end ${pl >= 0 ? "pl-positive" : "pl-negative"}">${formatCurrency(pl)}</td>
          <td class="text-end ${pl >= 0 ? "pl-positive" : "pl-negative"}">${plPercent.toFixed(
              2
          )}%</td>
          <td class="text-end">
            <button class="btn btn-danger btn-sm btn-sell-position" data-asset="${encodeURIComponent(JSON.stringify({symbol:h.symbol,name:h.name,category:h.category,buyPrice:h.buyPrice,quantity:h.quantity,currentPrice:h.currentPrice}))}">Sell</button>
          </td>
        `;
          tableBody.appendChild(tr);
        });

    if (!count) {
      tableBody.innerHTML = `
        <tr>
          <td colspan="9" class="text-center text-muted py-3">
            No holdings match your search.
          </td>
        </tr>
      `;
    }

    totalValueEl.textContent = formatCurrency(totalValue);
    totalPLEl.textContent = formatCurrency(totalPL);
    totalPLEl.classList.toggle("pl-positive", totalPL >= 0);
    totalPLEl.classList.toggle("pl-negative", totalPL < 0);
    countEl.textContent = count.toString();
  }

  async function loadHistory() {
    if (!historyTableBody || !historyStatusEl) return;

    try {
      historyStatusEl.textContent = "Loading history…";
      history = await API.getHistory();
      renderHistory();
      if (!history.length) {
        historyStatusEl.textContent = "No sold assets yet.";
      } else {
        historyStatusEl.textContent = `Last updated at ${new Date().toLocaleTimeString()}`;
      }
    } catch (err) {
      console.error("Error loading history:", err);
      historyStatusEl.textContent =
          "Failed to load history. Check backend /api/assets/history.";
    }
  }

  function renderHistory() {
    if (!historyTableBody) return;

    historyTableBody.innerHTML = "";

    if (!history.length) {
      historyTableBody.innerHTML = `
        <tr>
          <td colspan="6" class="text-center text-muted py-3">
            No sold assets yet.
          </td>
        </tr>
      `;
      return;
    }

    history.forEach((h) => {
      const soldAtText = h.soldAt
          ? new Date(h.soldAt).toLocaleString()
          : "-";

      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td class="fw-semibold">${h.symbol}</td>
        <td>${h.name || "-"}</td>
        <td>${h.assetType || "-"}</td>
        <td class="text-end">${formatCurrency(h.buyPrice)}</td>
        <td class="text-end">${formatCurrency(h.sellPrice)}</td>
        <td class="text-end">${soldAtText}</td>
      `;
      historyTableBody.appendChild(tr);
    });
  }

  searchInput.addEventListener("input", (e) => {
    renderHoldings(e.target.value);
  });

  // Sell button click delegation (avoids JSON-in-onclick issues)
  tableBody.addEventListener("click", (e) => {
    const btn = e.target.closest(".btn-sell-position");
    if (!btn || !btn.dataset.asset) return;
    try {
      const asset = JSON.parse(decodeURIComponent(btn.dataset.asset));
      openSellModal(asset);
    } catch (err) {
      console.error("Sell button parse error:", err);
    }
  });

  refreshBtn.addEventListener("click", () => {
    // Clear cache to force fresh data
    API.clearPriceCache();
    loadHoldings();
    loadHistory();
  });

  // Sell modal handlers (holdings page)
  const confirmSellEl = document.getElementById('confirm-sell');
  if (confirmSellEl) {
    confirmSellEl.addEventListener('click', async () => {
    const qty = parseInt(document.getElementById('sell-qty').value);
    const maxQty = parseInt(document.getElementById('sell-max').textContent);

    if (!qty || qty <= 0 || qty > maxQty) {
      document.getElementById('sell-error').textContent = `Enter 1-${maxQty}`;
      document.getElementById('sell-error').classList.remove('d-none');
      return;
    }

    try {
      const response = await fetch('/api/assets/sell', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ symbol: currentSellAsset.symbol, quantityToSell: qty })
      });

      if (response.ok) {
        bootstrap.Modal.getInstance(document.getElementById('sellModal'))?.hide();
        API.clearPriceCache();
        loadHoldings();
        loadHistory();
      } else {
        throw new Error(await response.text());
      }
    } catch (error) {
      document.getElementById('sell-error').textContent = error.message;
      document.getElementById('sell-error').classList.remove('d-none');
    }
  });
  }

  // Live proceeds preview (holdings)
  const sellQtyEl = document.getElementById('sell-qty');
  if (sellQtyEl) {
    sellQtyEl.addEventListener('input', function() {
    const qty = parseInt(this.value) || 0;
    const proceeds = (qty * (currentSellAsset?.currentPrice || 0)).toLocaleString('en-US', {
      style: 'currency', currency: 'USD', minimumFractionDigits: 2
    });
    const proceedsEl = document.getElementById('sell-proceeds');
    if (proceedsEl) proceedsEl.textContent = proceeds;
  });
  }

  loadHoldings();
  loadHistory();
}

/* CHARTS (Chart.js) */

let categoryPieChart;
let plBarChart;

async function initCharts() {
  const refreshBtn = document.getElementById("refresh-charts");

  async function renderCharts() {
    const [portfolio, holdings] = await Promise.all([API.getPortfolio(), API.getHoldings()]);
    renderCategoryPie(portfolio);
    renderPLBar(holdings);
  }

  function renderCategoryPie(portfolio) {
    const ctx = document.getElementById("category-pie-chart");
    if (!ctx) return;

    const categories = portfolio.categories || {};
    const labels = Object.keys(categories);
    const values = Object.values(categories);

    if (categoryPieChart) categoryPieChart.destroy();

    // Color mapping: Stocks = blue, Crypto = orange
    const colorMap = {
      Stocks: "#2563eb",
      Crypto: "#f97316",
    };
    const backgroundColor = labels.map(label => colorMap[label] || "#6b7280");

    categoryPieChart = new Chart(ctx, {
      type: "pie",
      data: {
        labels,
        datasets: [
          {
            data: values,
            backgroundColor,
          },
        ],
      },
      options: {
        plugins: {
          legend: {
            position: "bottom",
          },
        },
      },
    });
  }

  function renderPLBar(holdings) {
    const ctx = document.getElementById("pl-bar-chart");
    if (!ctx) return;

    const labels = [];
    const data = [];

    holdings.forEach((h) => {
      const cost = h.buyPrice * h.quantity;
      const value = h.currentPrice * h.quantity;
      const pl = value - cost;
      labels.push(h.symbol);
      data.push(pl);
    });

    if (plBarChart) plBarChart.destroy();

    plBarChart = new Chart(ctx, {
      type: "bar",
      data: {
        labels,
        datasets: [
          {
            label: "P/L",
            data,
            backgroundColor: data.map((v) => (v >= 0 ? "#16a34a" : "#dc2626")),
          },
        ],
      },
      options: {
        plugins: {
          legend: {
            display: false,
          },
        },
        scales: {
          y: {
            ticks: {
              callback: (value) => formatCurrency(value),
            },
          },
        },
      },
    });
  }

  refreshBtn.addEventListener("click", () => {
    // Clear cache to force fresh data
    API.clearPriceCache();
    renderCharts();
  });

  renderCharts();
}

/* CHATBOT */

function initChatbot() {
  const form = document.getElementById("chat-form");
  const input = document.getElementById("chat-input");
  const messagesContainer = document.getElementById("chat-messages");
  const statusBadge = document.getElementById("chat-status");
  const sendBtn = document.getElementById("chat-send-btn");
  const suggestions = document.querySelectorAll(".chat-suggestion");

  /** Renders **text** as bold; escapes HTML for safety. */
  function formatChatMessage(text) {
    const escaped = escapeHtml(text);
    return escaped
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/\n/g, "<br>");
  }

  function appendMessage(text, role) {
    const wrapper = document.createElement("div");
    wrapper.className = `chat-message ${role}`;
    const label = role === "user" ? "You" : role === "assistant" ? "Assistant" : "System";
    const content = role === "user" ? escapeHtml(text) : formatChatMessage(text);

    wrapper.innerHTML = `
      <div class="small text-muted mb-1">${label}</div>
      <div class="chat-bubble">${content}</div>
    `;
    messagesContainer.appendChild(wrapper);
    messagesContainer.scrollTop = messagesContainer.scrollHeight;
  }

  async function handleSubmit(message) {
    if (!message.trim()) return;

    appendMessage(message, "user");
    input.value = "";
    input.style.height = "";
    statusBadge.textContent = "Thinking…";
    statusBadge.classList.remove("bg-success-subtle", "text-success");
    statusBadge.classList.add("bg-warning-subtle", "text-warning");
    sendBtn.disabled = true;

    const reply = await API.sendChatMessage(message);

    appendMessage(reply, "assistant");
    statusBadge.textContent = "Ready";
    statusBadge.classList.remove("bg-warning-subtle", "text-warning");
    statusBadge.classList.add("bg-success-subtle", "text-success");
    sendBtn.disabled = false;
  }

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    handleSubmit(input.value);
  });

  // Autosize textarea
  input.addEventListener("input", () => {
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, 120)}px`;
  });

  suggestions.forEach((btn) => {
    btn.addEventListener("click", () => {
      const text = btn.textContent.trim();
      handleSubmit(text);
    });
  });
}

/* REPORT / SUMMARY */

async function initReport() {
  const refreshBtn = document.getElementById("refresh-report");
  const highlightsEl = document.getElementById("report-highlights");
  const gainersEl = document.getElementById("report-gainers");
  const losersEl = document.getElementById("report-losers");
  const insightsEl = document.getElementById("report-insights");
  const gainersCountEl = document.getElementById("gainers-count");
  const losersCountEl = document.getElementById("losers-count");
  const weeklyReportBtn = document.getElementById("load-weekly-report");
  const weeklyReportContent = document.getElementById("weekly-report-content");
  const diversificationEl = document.getElementById("report-diversification");

  async function loadReport() {
    const [portfolio, holdings, news] = await Promise.all([
      API.getPortfolio(),
      API.getHoldings(),
      API.getMarketNews(),
    ]);

    // Highlights
    const totalVal = portfolio.totalValue || 0;
    const totalPL = portfolio.totalPL || 0;
    const totalPLPercent =
        portfolio.totalPLPercent ??
        (totalVal ? (totalPL / (totalVal - totalPL)) * 100 : 0);

    highlightsEl.innerHTML = "";
    [
      `Total portfolio value: ${formatCurrency(totalVal)}`,
      `Total P/L: ${formatCurrency(totalPL)} (${totalPLPercent.toFixed(2)}%)`,
      `Category with highest allocation: ${
          Object.entries(portfolio.categories || {}).sort((a, b) => b[1] - a[1])[0]?.[0] || "N/A"
      }`,
    ].forEach((t) => {
      const li = document.createElement("li");
      li.className = "mb-2";
      li.textContent = t;
      highlightsEl.appendChild(li);
    });

    // Gainers & losers
    const enriched = holdings.map((h) => {
      const cost = h.buyPrice * h.quantity;
      const value = h.currentPrice * h.quantity;
      const pl = value - cost;
      const plPercent = cost ? (pl / cost) * 100 : 0;
      return { ...h, pl, plPercent };
    });

    const gainers = enriched.filter((h) => h.pl > 0).sort((a, b) => b.pl - a.pl).slice(0, 3);
    const losers = enriched.filter((h) => h.pl < 0).sort((a, b) => a.pl - b.pl).slice(0, 3);

    gainersEl.innerHTML = "";
    losersEl.innerHTML = "";

    gainers.forEach((g) => {
      const li = document.createElement("li");
      li.className = "list-group-item d-flex justify-content-between align-items-center";
      li.innerHTML = `
        <span>
          <span class="fw-semibold">${g.symbol}</span>
          <span class="text-muted ms-1">${g.name || ""}</span>
        </span>
        <span class="pl-positive fw-semibold">${formatCurrency(g.pl)} (${g.plPercent.toFixed(2)}%)</span>
      `;
      gainersEl.appendChild(li);
    });

    losers.forEach((l) => {
      const li = document.createElement("li");
      li.className = "list-group-item d-flex justify-content-between align-items-center";
      li.innerHTML = `
        <span>
          <span class="fw-semibold">${l.symbol}</span>
          <span class="text-muted ms-1">${l.name || ""}</span>
        </span>
        <span class="pl-negative fw-semibold">${formatCurrency(l.pl)} (${l.plPercent.toFixed(2)}%)</span>
      `;
      losersEl.appendChild(li);
    });

    gainersCountEl.textContent = gainers.length.toString();
    losersCountEl.textContent = losers.length.toString();

    // Market news (stocks & crypto)
    insightsEl.innerHTML = "";
    const headlines = Array.isArray(news) && news.length ? news : ["No market news available."];
    headlines.forEach((text) => {
      const li = document.createElement("li");
      li.className = "mb-2";
      li.textContent = text;
      insightsEl.appendChild(li);
    });

    // Stock diversification (based on holdings)
    const stockHoldings = holdings.filter((h) => (h.assetType || h.category || "").toUpperCase() === "STOCK" || (h.category || "") === "Stocks");
    const stockCount = new Set(stockHoldings.map((h) => (h.symbol || "").toUpperCase())).size;
    let divText = "No stock holdings.";
    if (stockCount > 0) {
      if (stockCount <= 2) divText = `Concentrated. Why: You hold only ${stockCount} stock(s) — high risk. Add more to diversify.`;
      else if (stockCount <= 4) divText = `Moderate. Why: ${stockCount} stocks — add 1–2 more for better diversification.`;
      else if (stockCount >= 8) divText = `Well diversified. Why: ${stockCount} stocks — good spread, lower single-name risk.`;
      else divText = `Good diversification. Why: ${stockCount} stocks — balanced.`;
    }
    if (diversificationEl) diversificationEl.textContent = divText;
  }

  refreshBtn.addEventListener("click", () => {
    // Clear cache to force fresh data
    API.clearPriceCache();
    loadReport();
  });

  // Weekly report button handler
  if (weeklyReportBtn && weeklyReportContent) {
    weeklyReportBtn.addEventListener("click", async () => {
      try {
        weeklyReportBtn.disabled = true;
        weeklyReportBtn.innerHTML = '<i class="bi bi-hourglass-split me-1"></i> Loading...';
        weeklyReportContent.textContent = "Generating weekly report...";

        const report = await API.getWeeklyReport();
        weeklyReportContent.innerHTML = `<pre style="white-space: pre-wrap; font-family: inherit; margin: 0;">${escapeHtml(report)}</pre>`;
      } catch (err) {
        console.error("Error loading weekly report:", err);
        weeklyReportContent.textContent = "Failed to load weekly report. Please try again.";
      } finally {
        weeklyReportBtn.disabled = false;
        weeklyReportBtn.innerHTML = '<i class="bi bi-download me-1"></i> Load Weekly Report';
      }
    });
  }

  loadReport();
}

/* Utility functions */

function formatCurrency(value) {
  if (typeof value !== "number" || isNaN(value)) return "$0.00";
  return new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
    maximumFractionDigits: 2,
  }).format(value);
}

function formatPercent(value) {
  if (typeof value !== "number" || isNaN(value)) return "0.0%";
  return `${(value * 100).toFixed(1)}%`;
}

function escapeHtml(str) {
  return str
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
}

