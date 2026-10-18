package com.hardwarehub.order_service.client;

import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient
{
    private final RestClient restClient;

    public ProductClient(RestClient.Builder restClient)
    {
        this.restClient = restClient
                .baseUrl("http://product-service/api/products")
                .build();
    }

    public ProductResponseDTO getById(int id)
    {
        try
        {
            return restClient.get()
                    .uri("/{id}", id)
                    .retrieve()
                    .body(ProductResponseDTO.class);
        }
        catch (HttpClientErrorException.NotFound e)
        {
            throw new ProductNotFoundException("Product not found.");
        }
    }
}
