package com.ailearning.platform.catalog.config;

import com.ailearning.platform.catalog.application.port.out.CatalogStore;
import com.ailearning.platform.catalog.application.port.out.CurriculumStore;
import com.ailearning.platform.catalog.application.service.impl.CatalogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogModuleConfig {
    @Bean CatalogService catalogService(CatalogStore store, CurriculumStore curricula) { return new CatalogService(store, curricula); }
}
