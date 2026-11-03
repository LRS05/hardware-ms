package com.hardwarehub.product_service.controller;

import com.hardwarehub.product_service.dto.ProductRequestDTO;
import com.hardwarehub.product_service.dto.ProductResponseDTO;
import com.hardwarehub.product_service.dto.ProductUpdateRequestDTO;
import com.hardwarehub.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/products")
public class AdminController
{
    private final ProductService productService;

    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
    @PostMapping
    public ResponseEntity<ProductResponseDTO> createProduct(@Valid @RequestBody ProductRequestDTO requestDTO)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(requestDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDTO> getById(@PathVariable int id)
    {
        return ResponseEntity.ok(productService.getByIdForAdmin(id));
    }

    @GetMapping
    public ResponseEntity<ProductResponseDTO> getProductBySku(@RequestParam String sku)
    {
        return ResponseEntity.ok(productService.getBySku(sku));
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
