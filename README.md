# Financial Portfolio Management API

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


## 📁 Project Structure

src/main/java/Api_Assets/
├── ApiAssetsApplication.java # Spring Boot main
├── controller/
│ ├── AssetController.java # /api/assets
│ └── ChatController.java # /api/chat
├── entity/UserAsset.java # Portfolio data model
├── repository/UserAssetRepository.java
├── dto/
│ ├── DashboardAsset.java
│ ├── RiskAssessment.java
│ └── SellRequest.java
└── service/
├── AssetService.java # Risk logic
├── CryptoService.java # Binance
├── StockService.java # StockData.org
└── ChatService.java # Gemini AI


## 🏃 Quick Start

### Prerequisites
- Java 17+, Maven 3.8+

### 1. Clone & Build
```bash
git clone https://github.com/manvitaimmaneni/Archive-FinancialPortfolio.git
cd Archive-FinancialPortfolio
mvn clean install

### 2. Configure API Keys (application.properties)
stockdata.api.key=YOUR_STOCKDATA_KEY
gemini.api.key=YOUR_GEMINI_KEY

### 3. Run
mvn spring-boot:run

