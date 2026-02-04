// Centralized API helpers wired to Spring Boot backend.
//
// Assumes the backend is running at the same origin, e.g.:
//   http://localhost:8080
// with endpoints under /api/assets.
//
// If your backend runs on a different host/port, change API_BASE_URL accordingly.

const API_BASE_URL = ""; // same origin (e.g. http://localhost:8080)

/* --------------------------------------------------
   Low-level helper
   -------------------------------------------------- */

async function jsonFetch(path, options = {}) {
  const url = API_BASE_URL + path;

  const response = await fetch(url, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });

  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new Error(`Request to ${url} failed (${response.status}): ${text}`);
  }

  // Some Spring endpoints may return no content.
  if (response.status === 204) return null;

  return response.json();
}

/* --------------------------------------------------
   High-level API used by the frontend
   -------------------------------------------------- */

const API = {
  /* ------------------------
     Portfolio / Dashboard
     ------------------------ */

  /**
   * Dashboard / portfolio overview.
   *
   * Uses your endpoint:
   *   GET /api/assets/dashboard
   *
   * Expected backend response: array of assets, e.g.
   * [
   *   {
   *     "id": 1,
   *     "type": "STOCK",
   *     "symbol": "AAPL",
   *     "name": "Apple Inc",
   *     "buyPrice": 210.00,
   *     "qty": 10,
   *     "currentPrice": 235.82,
   *     "currentDate": "2026-02-01T18:20:00"
   *   },
   *   ...
   * ]
   *
   * Returns a normalized object that the UI expects:
   * {
   *   totalValue,
   *   totalPL,
   *   totalPLPercent,
   *   categories: { Stocks, Crypto, Bonds, Cash },
   *   trends: [{ label, description }]
   * }
   */
  async getPortfolio() {
    const dashboardAssets = await jsonFetch("/api/assets/dashboard");

    if (!Array.isArray(dashboardAssets)) {
      throw new Error("Unexpected /api/assets/dashboard response shape");
    }

    let totalValue = 0;
    let totalCost = 0;

    const categories = {
      Stocks: 0,
      Crypto: 0,
      Bonds: 0,
      Cash: 0,
    };

    dashboardAssets.forEach((a) => {
      const qty = Number(a.qty ?? 0);
      const buyPrice = Number(a.buyPrice ?? 0);
      const currentPrice = Number(a.currentPrice ?? 0);
      const value = currentPrice * qty;
      const cost = buyPrice * qty;

      totalValue += value;
      totalCost += cost;

      // Map your assetType/type to frontend categories
      const type = (a.assetType || a.type || "").toUpperCase();
      if (type === "STOCK") {
        categories.Stocks += value;
      } else if (type === "CRYPTO") {
        categories.Crypto += value;
      }
      // If you later add BONDS/CASH assetTypes, map them here too.
    });

    const totalPL = totalValue - totalCost;
    const totalPLPercent = totalCost ? (totalPL / totalCost) * 100 : 0;

    // Simple trend placeholders; you can enhance using dates in dashboardAssets
    const trendLabel = totalPL > 0 ? "Uptrend" : totalPL < 0 ? "Downtrend" : "Flat";

    const trends = [
      {
        label: "Overall",
        description: `${trendLabel} (${totalPLPercent.toFixed(2)}% total return)`,
      },
    ];

    return {
      totalValue,
      totalPL,
      totalPLPercent,
      categories,
      trends,
    };
  },

  /* ------------------------
     Holdings
     ------------------------ */

  /**
   * Holdings table data.
   *
   * Uses your endpoint:
   *   GET /api/assets
   *
   * Expected backend response: array of entities from user_assets table, e.g.
   * [
   *   {
   *     "id": 1,
   *     "assetType": "STOCK",
   *     "symbol": "AAPL",
   *     "name": "Apple Inc",
   *     "buyPrice": 210.00,
   *     "qty": 10,
   *     "currentPrice": 235.82,
   *     "sellingPrice": null,
   *     "sellingDate": null
   *   },
   *   ...
   * ]
   *
   * Returns:
   * [
   *   {
   *     id,
   *     symbol,
   *     name,
   *     category,      // "Stocks" / "Crypto"
   *     buyPrice,
   *     quantity,
   *     currentPrice
   *   },
   *   ...
   * ]
   */
  async getHoldings() {
    const assets = await jsonFetch("/api/assets");

    if (!Array.isArray(assets)) {
      throw new Error("Unexpected /api/assets response shape");
    }

    return assets
      // Optional: filter out fully sold positions if qty == 0
      .filter((a) => Number(a.qty ?? 0) !== 0)
      .map((a) => {
        const type = (a.assetType || a.type || "").toUpperCase();
        let category = "Other";
        if (type === "STOCK") category = "Stocks";
        else if (type === "CRYPTO") category = "Crypto";

        return {
          id: a.id,
          symbol: a.symbol,
          name: a.name,
          category,
          buyPrice: Number(a.buyPrice ?? 0),
          quantity: Number(a.qty ?? 0),
          currentPrice: Number(a.currentPrice ?? 0),
        };
      });
  },

  /* ------------------------
     Manage assets helpers
     ------------------------ */

  /**
   * Add a new asset.
   *
   * Uses your endpoint:
   *   POST /api/assets
   *
   * Body example (from README):
   * {
   *   "assetType": "STOCK",
   *   "symbol": "AAPL",
   *   "name": "Apple Inc",
   *   "buyPrice": 210.0,
   *   "qty": 10
   * }
   *
   * NOTE: The current frontend "Manage Assets" page uses a purely
   * client-side list. To fully integrate, call this function
   * from `initManageAssets()` when the form is submitted.
   */
  async addAsset({ symbol, name, category, buyPrice, quantity }) {
    // Map UI category back to your assetType
    let assetType = "STOCK";
    if (category === "Crypto") assetType = "CRYPTO";

    const body = {
      assetType,
      symbol,
      name,
      buyPrice,
      qty: quantity,
    };

    return jsonFetch("/api/assets", {
      method: "POST",
      body: JSON.stringify(body),
    });
  },

  /**
   * "Remove" an asset by selling it.
   *
   * Uses your endpoint:
   *   POST /api/assets/sell/{id}
   *
   * This sets qty=0 and copies currentPrice → sellingPrice on backend,
   * preserving history instead of deleting the row.
   */
  async sellAsset(id) {
    if (id == null) throw new Error("sellAsset requires an id");
    return jsonFetch(`/api/assets/sell/${id}`, {
      method: "POST",
      body: null,
    });
  },

  /* ------------------------
     Recommendations (stub)
     ------------------------ */

  /**
   * Top 3 recommendations placeholder.
   *
   * IDEAL BACKEND ENDPOINT (to implement later):
   *   GET /api/recommendations/top3
   * Response shape:
   * [
   *   { "symbol": "QQQ", "name": "Invesco QQQ", "action": "Buy", "rationale": "..." },
   *   ...
   * ]
   *
   * For now, returns static data so the Report page works.
   */
  async getTopRecommendations() {
    // When you implement the backend, switch to:
    // return jsonFetch("/api/recommendations/top3");
    return [
      {
        symbol: "QQQ",
        name: "Invesco QQQ",
        action: "Buy",
        rationale: "Increases diversified exposure to large-cap US tech.",
      },
      {
        symbol: "BND",
        name: "Vanguard Total Bond",
        action: "Hold",
        rationale: "Provides fixed income ballast and reduces volatility.",
      },
      {
        symbol: "BTC",
        name: "Bitcoin",
        action: "Trim",
        rationale: "Lock in partial gains and limit downside risk.",
      },
    ];
  },

  /* ------------------------
     Chat (stub)
     ------------------------ */

  /**
   * AI Chat endpoint.
   *
   * Planned backend endpoint:
   *   POST /api/chat
   * Body:    { "message": "..." }
   * Returns: { "reply": "..." }
   *
   * Until you implement it, this returns a friendly placeholder.
   */
  async sendChatMessage(message) {
    try {
      const data = await jsonFetch("/api/chat", {
        method: "POST",
        body: JSON.stringify({ message }),
      });
      return data.reply || "";
    } catch (err) {
      console.error("Chat API error or not implemented:", err);
      return (
        "Your question was: \"" +
        message +
        "\".\n\n" +
        "The /api/chat backend endpoint is not implemented yet. " +
        "Once you add it in Spring Boot, this chat will respond with AI-powered answers " +
        "about your portfolio."
      );
    }
  },
};