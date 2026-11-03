package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class ProductController
{
    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Page<ProductResponseDTO>> getLatestUpdatedProducts(Pageable pageable)
    {
        return ResponseEntity.ok(productService.getRecentlyUpdated(pageable));
    }

    @GetMapping(params = {"name", "!sku"})
    public ResponseEntity<Page<ProductResponseDTO>> getProductsByName(@RequestParam String name, Pageable pageable)
    {
        return ResponseEntity.ok(productService.getAllByName(name, pageable));
    }

    @GetMapping(params = {"category", "!name", "!sku"})
    public ResponseEntity<Page<ProductResponseDTO>> getProductsByCategory(@RequestParam Category category, Pageable pageable)
    {
        return ResponseEntity.ok(productService.getAllByCategory(category, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getProductById(@PathVariable int id)
    {
        return ResponseEntity.ok(productService.getById(id));
    }
}
