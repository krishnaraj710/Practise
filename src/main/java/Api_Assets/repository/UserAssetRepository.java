package Api_Assets.repository;


import Api_Assets.entity.UserAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAssetRepository extends JpaRepository<UserAsset, Long> {

    List<UserAsset> findByAssetType(String assetType);

    // ALL assets (STOCK + CRYPTO)
    List<UserAsset> findAll();
    List<UserAsset> findBySymbol(String symbol);
    // STOCKS ONLY (all stocks user holds)
    @Query("SELECT u FROM UserAsset u WHERE u.assetType = 'STOCK'")
    List<UserAsset> findAllStocks();

    // CRYPTO ONLY (all crypto user holds)
    @Query("SELECT u FROM UserAsset u WHERE u.assetType = 'CRYPTO'")
    List<UserAsset> findAllCrypto();

    // SPECIFIC STOCK by symbol
    @Query("SELECT u FROM UserAsset u WHERE u.assetType = 'STOCK' AND u.symbol = :symbol")
    Optional<UserAsset> findStockBySymbol(@Param("symbol") String symbol);

    // SPECIFIC CRYPTO by symbol
    @Query("SELECT u FROM UserAsset u WHERE u.assetType = 'CRYPTO' AND u.symbol = :symbol")
    Optional<UserAsset> findCryptoBySymbol(@Param("symbol") String symbol);
}

