package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.dto.ProductRequestDTO;
import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.dto.ProductUpdateRequestDTO;
import com.hardwarehub.product_service.dto.StockRequestDTO;
import com.hardwarehub.product_service.enums.Category;
import com.hardwarehub.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping(params = {"sku", "!name"})
    public ResponseEntity<ProductResponseDTO> getProductBySku(@RequestParam String sku)
    {
        return ResponseEntity.ok(productService.getBySku(sku));
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

    /*
    *
    *    Admin / Staff endpoints
    *
     */

    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO requestDTO)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(requestDTO));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> updateProductById(@PathVariable int id, @Valid @RequestBody ProductUpdateRequestDTO requestDTO)
    {
        return ResponseEntity.ok(productService.updateById(id, requestDTO));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<String> deactivateById(@PathVariable int id)
    {
        productService.deactivateById(id);
        return ResponseEntity.ok("Product with id " + id + " successfully deactivated.");
    }
}
