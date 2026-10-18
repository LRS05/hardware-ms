package com.hardwarehub.api_gateway.jwt;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

public class JwtPropagationFilter
{
    public static HandlerFilterFunction<ServerResponse, ServerResponse> jwtPropagation()
    {
        return (request, next) ->
        {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication instanceof JwtAuthenticationToken jwt
                    && authentication.isAuthenticated())
            {
                ServerRequest newRequest = ServerRequest.from(request)
                        .headers(headers -> headers
                                .setBearerAuth(jwt.getToken().getTokenValue()))
                        .build();

                return next.handle(newRequest);
            }
            return next.handle(request);
        };
    }
}
