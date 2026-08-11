package com.oopsw.gatewayservice;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRouteConfig {
    /*
    @Bean
    public RouterFunction<ServerResponse> userRoutes(){
        return route("user-service")
                .GET("/users/**", http())
                .before(uri("http://localhost:7001"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> itemRoutes(){
        return route("item-service")
                .GET("/items/**", http())
                .before(uri("http://localhost:7002"))
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> orderRoutes(){
        return route("order-service")
                .GET("/orders/**", http())
                .before(uri("http://localhost:7003"))
                .build();
    }

     */
}
