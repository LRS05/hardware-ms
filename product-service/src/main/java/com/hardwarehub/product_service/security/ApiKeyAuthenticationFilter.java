package com.hardwarehub.product_service.security;

import com.hardwarehub.product_service.dto.ErrorResponseDTO;
import com.hardwarehub.product_service.enums.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter
{
    private final AuthenticationManager authenticationManager;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request)
    {
        String path = request.getRequestURI();
        return !path.equals("/internal") && !path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException
    {
        try
        {
            String apiKey = request.getHeader("X-API-Key");

            if (apiKey == null || apiKey.isBlank())
            {
                throw new BadCredentialsException("Invalid API key.");
            }

            // Wraps the API key in an authentication token.
            ApiKeyAuthenticationToken token = ApiKeyAuthenticationToken.buildUnauthorizedKey(apiKey);
            try
            {
                // Validates the API key using the appropriate AuthenticationProvider.
                Authentication authentication = authenticationManager.authenticate(token);

                SecurityContext context = SecurityContextHolder.createEmptyContext();

                context.setAuthentication(authentication);

                SecurityContextHolder.setContext(context);
            }
            finally
            {
                // Clears the API key stored in the token.
                token.eraseCredentials();
            }
        }
        catch (AuthenticationException e)
        {
            errorResponse(response, HttpStatus.UNAUTHORIZED, request.getRequestURI());
            SecurityContextHolder.clearContext();
            return;
        }

        try
        {
            filterChain.doFilter(request, response);
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }
    }

    private void errorResponse(HttpServletResponse response, HttpStatus status, String path) throws IOException
    {
        ErrorResponseDTO error = new ErrorResponseDTO(
                LocalDateTime.now(),
                ApiError.INVALID_API_KEY,
                status.value(),
                path,
                "Invalid API Key."
        );

        response.setContentType("application/json");
        response.setStatus(status.value());
        objectMapper.writeValue(response.getWriter(), error);
    }
}
