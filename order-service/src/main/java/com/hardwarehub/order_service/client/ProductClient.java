package com.hardwarehub.order_service.client;

import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.client.dto.StockRequestDTO;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
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
                .baseUrl("http://product-service/api/v1/products")
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
        catch (HttpClientErrorException.Conflict e)
        {
            throw new ProductOutOfStockException("The product is out of stock.");
        }
        catch (HttpClientErrorException.NotFound e)
        {
            throw new ProductNotFoundException("Product not found.");
        }
    }

    public void decreaseStockById(int id, int quantity)
    {
        try
        {
            restClient.patch()
                    .uri("/{id}/decrease", id)
                    .body(new StockRequestDTO(quantity))
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (HttpClientErrorException.Conflict e)
        {
            throw new ProductOutOfStockException("This product is out of stock.");
        }
        catch (HttpClientErrorException.NotFound e)
        {
            throw new ProductNotFoundException("Product not found.");
        }
    }

    public void increaseStockById(int id, int quantity)
    {
        try
        {
            restClient.patch()
                    .uri("/{id}/increase", id)
                    .body(new StockRequestDTO(quantity))
                    .retrieve()
                    .toBodilessEntity();
        }
        catch (HttpClientErrorException.Conflict e)
        {
            throw new ProductOutOfStockException("This product is out of stock.");
        }
        catch (HttpClientErrorException.NotFound e)
        {
            throw new ProductNotFoundException("Product not found.");
        }
    }
}
