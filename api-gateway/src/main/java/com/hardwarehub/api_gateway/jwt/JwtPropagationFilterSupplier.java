package com.hardwarehub.api_gateway.jwt;

import org.springframework.cloud.gateway.server.mvc.filter.SimpleFilterSupplier;

public class JwtPropagationFilterSupplier extends SimpleFilterSupplier
{
    public JwtPropagationFilterSupplier()
    {
        super(JwtPropagationFilter.class);
    }
}
