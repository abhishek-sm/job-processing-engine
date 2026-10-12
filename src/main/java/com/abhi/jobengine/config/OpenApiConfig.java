package com.abhi.jobengine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jobEngineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Job Processing Engine API")
                        .description("Submit and track asynchronous, tenant-scoped jobs. "
                                + "Jobs of different tenants run in parallel; jobs of the same "
                                + "tenant run sequentially.")
                        .version("v1"));
    }
}
