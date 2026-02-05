# Archive Portfolio Management 

A Spring Boot backend for managing **stock and cryptocurrency portfolios** with real-time prices, profit/loss tracking, risk assessment, and AI recommendations.

## 🚀 Features

- Add, view, and sell assets (stocks & crypto)
- **Live prices**: Binance (crypto) + StockData.org (stocks)
- Automatic **profit/loss** calculation (% + amount)
- **Risk assessment**: LOW/MEDIUM/HIGH for buy/sell
- **AI Chatbot** (Gemini): "top 3 stocks", diversification analysis
- **Dashboard** with portfolio summary

## 🛠️ Tech Stack

Backend: Spring Boot + Java

Database: JPA/H2/MySQL

APIs: Binance, StockData.org, Gemini AI

Build: Maven

Testing: JUnit 

## 📋 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/assets` | Add stock/crypto |
| GET | `/api/assets` | Get all assets |
| GET | `/api/assets/stocks` | All stock holdings |
| GET | `/api/assets/crypto` | All crypto holdings |
| POST | `/api/assets/sell/{id}` | Sell by asset ID |
| GET | `/api/assets/dashboard` | Live P/L dashboard |
| GET | `/api/assets/risk/buy/{symbol}/{qty}` | Buy risk assessment |
| GET | `/api/chat?message=...` | AI chatbot |

**Base URL**: `http://localhost:8080`


## 🏃 Quick Start

### Prerequisites
- Java 17+, Maven 3.8+

### 1. Clone and Build
```bash
git clone https://github.com/divya0267/Archive-FinancialPortfolio.git
cd Archive-FinancialPortfolio
mvn clean install

### 2.** Configure API Keys (application.properties)**
stockdata.api.key=YOUR_STOCKDATA_KEY
gemini.api.key=YOUR_GEMINI_KEY


### 3.**Run**
mvn spring-boot:run

