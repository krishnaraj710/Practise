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

/* NAVIGATION */

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

/* DASHBOARD */

async function initDashboard() {
  const totalValueEl = document.getElementById("total-portfolio-value");
  const totalPLEl = document.getElementById("total-pl");
  const totalPLPercentEl = document.getElementById("total-pl-percent");
  const topCategoryEl = document.getElementById("top-category");
  const trendSummaryEl = document.getElementById("trend-summary");
  const categoryBarsContainer = document.getElementById("category-breakdown-bars");
  const trendList = document.getElementById("trend-list");
  const lastUpdatedEl = document.getElementById("last-updated");

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
    Bonds: "#16a34a",
    Cash: "#6b7280",
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

function initManageAssets() {
  // Simple in-memory asset list for now.
  // BACKEND INTEGRATION:
  // - On load, you can populate this using API.getHoldings() or your own /api/assets.
  let assets = [];

  const form = document.getElementById("asset-form");
  const formTitle = document.getElementById("asset-form-title");
  const submitLabel = document.getElementById("asset-form-submit-label");
  const cancelEditBtn = document.getElementById("asset-form-cancel-edit");
  const tableBody = document.getElementById("asset-table-body");
  const searchInput = document.getElementById("asset-search");
  const countEl = document.getElementById("asset-count");

  function resetForm() {
    form.reset();
    document.getElementById("asset-id").value = "";
    formTitle.textContent = "Add New Asset";
    submitLabel.textContent = "Add Asset";
    cancelEditBtn.classList.add("d-none");
  }

  function renderTable(filter = "") {
    const filterLower = filter.toLowerCase();
    tableBody.innerHTML = "";

    const filtered = assets.filter((a) => {
      const combined = `${a.symbol} ${a.name}`.toLowerCase();
      return combined.includes(filterLower);
    });

    filtered.forEach((asset, index) => {
      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td class="fw-semibold">${asset.symbol}</td>
        <td>${asset.name || "-"}</td>
        <td>${asset.category}</td>
        <td class="text-end">${formatCurrency(asset.buyPrice)}</td>
        <td class="text-end">${asset.quantity}</td>
        <td class="text-end">
          <button class="btn btn-link btn-sm text-decoration-none me-1 edit-asset" data-index="${index}">
            <i class="bi bi-pencil-square"></i>
          </button>
          <button class="btn btn-link btn-sm text-decoration-none text-danger delete-asset" data-index="${index}">
            <i class="bi bi-trash"></i>
          </button>
        </td>
      `;
      tableBody.appendChild(tr);
    });

    countEl.textContent = `${assets.length} asset${assets.length !== 1 ? "s" : ""}`;
  }

  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const id = document.getElementById("asset-id").value;
    const symbol = document.getElementById("asset-symbol").value.trim().toUpperCase();
    const name = document.getElementById("asset-name").value.trim();
    const category = document.getElementById("asset-category").value;
    const buyPrice = parseFloat(document.getElementById("asset-buy-price").value);
    const quantity = parseFloat(document.getElementById("asset-quantity").value);

    if (!symbol || !category || isNaN(buyPrice) || isNaN(quantity)) return;

    if (id) {
      // Edit
      const idx = Number(id);
      assets[idx] = { symbol, name, category, buyPrice, quantity };

      // BACKEND INTEGRATION:
      // Replace with PUT /api/assets/:id or similar.
    } else {
      // Add
      assets.push({ symbol, name, category, buyPrice, quantity });

      // BACKEND INTEGRATION:
      // Replace with POST /api/assets with body { symbol, name, category, buyPrice, quantity }.
    }

    resetForm();
    renderTable(searchInput.value);
  });

  cancelEditBtn.addEventListener("click", () => {
    resetForm();
  });

  tableBody.addEventListener("click", (e) => {
    const editBtn = e.target.closest(".edit-asset");
    const deleteBtn = e.target.closest(".delete-asset");

    if (editBtn) {
      const idx = Number(editBtn.dataset.index);
      const asset = assets[idx];
      document.getElementById("asset-id").value = idx;
      document.getElementById("asset-symbol").value = asset.symbol;
      document.getElementById("asset-name").value = asset.name;
      document.getElementById("asset-category").value = asset.category;
      document.getElementById("asset-buy-price").value = asset.buyPrice;
      document.getElementById("asset-quantity").value = asset.quantity;

      formTitle.textContent = "Edit Asset";
      submitLabel.textContent = "Save Changes";
      cancelEditBtn.classList.remove("d-none");
    }

    if (deleteBtn) {
      const idx = Number(deleteBtn.dataset.index);
      // BACKEND INTEGRATION:
      // Replace with DELETE /api/assets/:id, then refresh the local list.
      assets.splice(idx, 1);
      renderTable(searchInput.value);
      resetForm();
    }
  });

  searchInput.addEventListener("input", (e) => {
    renderTable(e.target.value);
  });

  // Initial render
  renderTable();
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

  let holdings = [];

  async function loadHoldings() {
    try {
      statusEl.textContent = "Loading holdings…";
      holdings = await API.getHoldings();
      renderHoldings();
      statusEl.textContent = `Last updated at ${new Date().toLocaleTimeString()}`;
    } catch (err) {
      console.error("Error loading holdings:", err);
      statusEl.textContent = "Failed to load holdings. Check backend /api/holdings.";
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
          <td class="text-end ${pl >= 0 ? "pl-positive" : "pl-negative"}">${plPercent.toFixed(2)}%</td>
        `;
        tableBody.appendChild(tr);
      });

    totalValueEl.textContent = formatCurrency(totalValue);
    totalPLEl.textContent = formatCurrency(totalPL);
    totalPLEl.classList.toggle("pl-positive", totalPL >= 0);
    totalPLEl.classList.toggle("pl-negative", totalPL < 0);
    countEl.textContent = count.toString();
  }

  searchInput.addEventListener("input", (e) => {
    renderHoldings(e.target.value);
  });

  refreshBtn.addEventListener("click", () => {
    loadHoldings();
  });

  loadHoldings();
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

    categoryPieChart = new Chart(ctx, {
      type: "pie",
      data: {
        labels,
        datasets: [
          {
            data: values,
            backgroundColor: ["#2563eb", "#f97316", "#16a34a", "#6b7280"],
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

  function appendMessage(text, role) {
    const wrapper = document.createElement("div");
    wrapper.className = `chat-message ${role}`;
    const label = role === "user" ? "You" : role === "assistant" ? "Assistant" : "System";

    wrapper.innerHTML = `
      <div class="small text-muted mb-1">${label}</div>
      <div class="chat-bubble">${escapeHtml(text)}</div>
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
  const recommendationsEl = document.getElementById("report-recommendations");
  const gainersCountEl = document.getElementById("gainers-count");
  const losersCountEl = document.getElementById("losers-count");

  async function loadReport() {
    const [portfolio, holdings, recommendations] = await Promise.all([
      API.getPortfolio(),
      API.getHoldings(),
      API.getTopRecommendations(),
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

    // Insights
    insightsEl.innerHTML = "";
    const insights = [];

    const categories = portfolio.categories || {};
    const sortedCats = Object.entries(categories).sort((a, b) => b[1] - a[1]);
    if (sortedCats[0]) {
      const [topCat, topVal] = sortedCats[0];
      const allocationPercent = totalVal ? (topVal / totalVal) * 100 : 0;
      insights.push(
        `Your largest allocation is in ${topCat} (${allocationPercent.toFixed(
          1
        )}% of portfolio value). Consider if this aligns with your risk tolerance.`
      );
    }

    if (gainers[0]) {
      insights.push(
        `${gainers[0].symbol} has contributed the most to your returns recently. Review whether to rebalance or let winners run.`
      );
    }

    if (losers[0]) {
      insights.push(
        `${losers[0].symbol} is your largest detractor. Investigate whether fundamentals still support your thesis.`
      );
    }

    if (!insights.length) {
      insights.push("No specific insights available yet. Add holdings to generate a richer report.");
    }

    insights.forEach((text) => {
      const li = document.createElement("li");
      li.className = "mb-2";
      li.textContent = text;
      insightsEl.appendChild(li);
    });

    // Recommendations
    recommendationsEl.innerHTML = "";
    recommendations.forEach((r) => {
      const li = document.createElement("li");
      li.className = "list-group-item small";
      li.innerHTML = `
        <div class="d-flex justify-content-between align-items-center">
          <div>
            <span class="fw-semibold">${r.symbol}</span>
            <span class="text-muted ms-1">${r.name || ""}</span>
          </div>
          <span class="badge ${
            r.action === "Buy"
              ? "bg-success-subtle text-success"
              : r.action === "Sell" || r.action === "Trim"
              ? "bg-danger-subtle text-danger"
              : "bg-secondary-subtle text-secondary"
          }">
            ${r.action || "Review"}
          </span>
        </div>
        <div class="mt-1 text-muted">${r.rationale || ""}</div>
      `;
      recommendationsEl.appendChild(li);
    });
  }

  refreshBtn.addEventListener("click", () => {
    loadReport();
  });

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