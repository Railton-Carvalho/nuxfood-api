package com.nuxfood.pedidos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI nuxfoodOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nuxfood API")
                        .description("API de pedidos do Nuxfood")
                        .version("v1"))
                .addServersItem(new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Ambiente local"));
    }
}
