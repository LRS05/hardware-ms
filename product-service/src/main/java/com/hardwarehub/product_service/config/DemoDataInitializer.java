package com.hardwarehub.product_service.config;

import com.hardwarehub.product_service.entity.ProductEntity;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.enums.ProductStatus;
import com.hardwarehub.product_service.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "demo.data", name = "enabled", havingValue = "true")
public class DemoDataInitializer implements CommandLineRunner
{
    private final ProductRepository productRepository;

    @Override
    public void run(String... args)
    {
        buildProduct(Category.GRAPHIC_CARD, "DEMO-GTX-1080", "Demo GTX 1080", BigDecimal.valueOf(1500));
        buildProduct(Category.RAM_MEMORY, "DEMO-RAM-001", "Demo RAM 32 GB DDR5", BigDecimal.valueOf(1000));
    }

    private void buildProduct(Category category, String sku, String name, BigDecimal price)
    {
        if (productRepository.existsBySku(sku) || productRepository.existsByName(name))
        {
            return;
        }

        productRepository.save(ProductEntity.builder()
                .status(ProductStatus.ACTIVE)
                .category(category)
                .sku(sku)
                .name(name)
                .description("Sample product for testing the catalog, cart and checkout.")
                .price(price)
                .stock(10)
                .build()
        );
    }
}
