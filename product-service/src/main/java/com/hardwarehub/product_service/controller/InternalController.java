package com.hardwarehub.product_service.controller;


import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/products")
public class InternalController
{
    private final ProductService productService;

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable int id)
    {
        return ResponseEntity.ok(productService.getById(id));
    }

    @PatchMapping("/{id}/decrease")
    public ResponseEntity<Void> decreaseStockById(@PathVariable int id, @Valid @RequestBody StockRequestDTO requestDTO)
    {
        productService.decreaseStockById(id, requestDTO);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/increase")
    public ResponseEntity<Void> increaseStockById(@PathVariable int id, @Valid @RequestBody StockRequestDTO requestDTO)
    {
        productService.increaseStockById(id, requestDTO);
        return ResponseEntity.noContent().build();
    }
}
