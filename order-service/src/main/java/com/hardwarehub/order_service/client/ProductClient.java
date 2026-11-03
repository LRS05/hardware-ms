package com.hardwarehub.order_service.client;

import com.hardwarehub.order_service.client.dto.ProductResponseDTO;
import com.hardwarehub.order_service.client.dto.StockRequestDTO;
import com.hardwarehub.order_service.exception.ProductIntegrationException;
import com.hardwarehub.order_service.exception.ProductNotFoundException;
import com.hardwarehub.order_service.exception.ProductOutOfStockException;
import com.hardwarehub.order_service.exception.ProductTimeoutException;
import com.hardwarehub.order_service.exception.ProductUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.function.Supplier;

@Component
public class ProductClient
{
    private final RestClient restClient;

    public ProductClient(
            RestClient.Builder restClient,
            @Value("${internal.auth.api-key}") String apiKey)
    {
        this.restClient = restClient
                .baseUrl("http://product-service/internal/products")
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    public ProductResponseDTO getById(int id)
    {
        return execute(() ->
                restClient.get()
                        .uri("/{id}", id)
                        .retrieve()
                        .body(ProductResponseDTO.class)
        );
    }

    public void decreaseStockById(int id, int quantity)
    {
        execute(() ->
                restClient.patch()
                        .uri("/{id}/decrease", id)
                        .body(new StockRequestDTO(quantity))
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    public void increaseStockById(int id, int quantity)
    {
        execute(() ->
                restClient.patch()
                        .uri("/{id}/increase", id)
                        .body(new StockRequestDTO(quantity))
                        .retrieve()
                        .toBodilessEntity()
        );
    }

    private <T> T execute(Supplier<T> operation)
    {
        try
        {
            return operation.get();
        }
        catch (HttpClientErrorException.NotFound e)
        {
            throw new ProductNotFoundException("Product not found.");
        }
        catch (HttpClientErrorException.Conflict e)
        {
            throw new ProductOutOfStockException("The product is out of stock.");
        }
        catch (RestClientResponseException e)
        {
            throw new ProductIntegrationException("Error communicating with product service.");
        }
        catch (ResourceAccessException e)
        {
            if (isTimeout(e))
            {
                throw new ProductTimeoutException("Product service did not respond in time.");
            }

            throw new ProductUnavailableException("Product service is unavailable.");
        }
        catch (RestClientException e)
        {
            throw new ProductIntegrationException("Invalid response from product service.");
        }
        catch (IllegalStateException e)
        {
            if ("No instances available for product-service".equals(e.getMessage()))
            {
                throw new ProductUnavailableException("Product service is unavailable.");
            }

            throw e;
        }
    }

    private boolean isTimeout(Throwable exception)
    {
        Throwable cause = exception;

        while (cause != null)
        {
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException)
            {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }
}
