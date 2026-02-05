# Archive-Portfolio

Financial Portfolio Management API
A Spring Boot–based backend for managing a personal stock and cryptocurrency portfolio with real-time prices, profit/loss tracking, risk assessment, and AI-powered recommendations.

Features
Add, view, and sell assets (stocks and crypto)

Live price fetching:

Crypto via Binance (e.g., BTC, ETH, SOL, ADA, XRP)
Stocks via StockData.org
Automatic profit/loss calculation (amount + percentage)
Portfolio dashboard with current status (PROFIT / LOSS)
Buy/Sell risk assessment (LOW / MEDIUM / HIGH)

AI chat endpoint using Gemini:
“top 3 stocks”, “top 3 crypto”, “top 5 assets”


Tech Stack
Language: Java

Framework: Spring Boot

Build Tool: Maven

Persistence: Spring Data JPA, H2/MySQL

HTTP Client: RestTemplate / HttpClient

External APIs: Binance, StockData.org

AI Integration: Google Gemini API (HTTP JSON)

Project Structure
text
src/
 └─ main/
     ├─ java/
     │   └─ Api_Assets/
     │       ├─ ApiAssetsApplication.java      # Main Spring Boot class
     │       ├─ controller/
     │       │   ├─ AssetController.java       # /api/assets, dashboard, risk
     │       │   └─ ChatController.java        # /api/chat
     │       ├─ entity/
     │       │   └─ UserAsset.java             # JPA entity for user_assets
     │       ├─ repository/
     │       │   └─ UserAssetRepository.java   # JPA repository
     │       ├─ dto/
     │       │   ├─ DashboardAsset.java
     │       │   ├─ RiskAssessment.java
     │       │   ├─ AssetRecommendation.java
     │       │   ├─ SellRequest.java
     │       │   └─ UserAssetDTO.java
     │       └─ service/
     │           ├─ AssetService.java          # Buy/Sell risk logic
     │           ├─ StockService.java          # StockData.org integration
     │           ├─ CryptoService.java         # Binance integration
     │           ├─ RecommendationService.java # Top N assets logic
     │           └─ GeminiService.java         # Gemini API client
     └─ resources/
         ├─ application.properties (or .yml)
         

Getting Started
Prerequisites
Java 17+

Maven 3.8+

API keys:

stockdata.api.key for StockData.org

gemini.api.key for Gemini

Configuration
In src/main/resources/application.properties (or .yml):

Server
server.port=8080

JPA / H2 example
spring.datasource.url=jdbc:h2:mem:portfolio
spring.datasource.driverClassName=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update

StockData.org
stockdata.api.key=YOUR_STOCKDATA_API_KEY

Gemini
gemini.api.key=YOUR_GEMINI_API_KEY
gemini.api.base-url=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=

Running the Application

# Build
mvn clean install

# Run
mvn spring-boot:run
# or
java -jar target/financial-portfolio-*.jar
The API will be available at:
http://localhost:8080

REST API Endpoints
Assets
POST /api/assets
Add a new asset (stock/crypto).

Example JSON:

json
{
  "assetType": "STOCK",
  "symbol": "AAPL",
  "name": "Apple Inc",
  "buyPrice": 150.00,
  "qty": 10
}

GET /api/assets
Get all assets.

GET /api/assets/stocks
Get all stock holdings.

GET /api/assets/crypto
Get all crypto holdings.

POST /api/assets/sell/{id}
Sell entire position by asset ID 

POST /api/assets/sell
Partial sell by symbol and quantity.

Dashboard & Profit/Loss
GET /api/assets/dashboard
Returns list of DashboardAsset with:

-> live currentPrice

-> difference amount

-> profit/loss percentage

-> status = PROFIT / LOSS

GET /api/assets/profit-loss
Profit/loss summary for all holdings.

Risk Assessment
GET /api/assets/risk/{symbol}
Simple risk message based on average buy price and current price.

GET /api/assets/risk/buy/{symbol}/{qty}
Returns RiskAssessment for a BUY action:

GET /api/assets/risk/sell/{symbol}/{qty}
Same, but for SELL action.

AI Chat
GET /api/chat?message=top 3 stocks

POST /api/chat with body:

json
{ "message": "top 3 crypto" }
Returns a ChatResponse with formatted AI suggestions using Gemini based on your top N assets and diversification.

Git Workflow (Team of 3)
Main branch: main

Feature branches:

feature/CRUD_Operations

feature/Report

feature/Risk-analysis

feature/Chatbot

feature/Dashboard-ui

Typical flow:

git checkout main
git pull origin main
git checkout -b feature/my-feature

# work, commit
git add .
git commit -m "feat: ..."

git push -u origin feature/my-feature
# open PR → review → merge into main



