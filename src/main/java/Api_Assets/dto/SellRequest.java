
package Api_Assets.dto;

public class SellRequest {
    private String symbol;
    private int quantityToSell;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public int getQuantityToSell() { return quantityToSell; }
    public void setQuantityToSell(int quantityToSell) { this.quantityToSell = quantityToSell; }
}
