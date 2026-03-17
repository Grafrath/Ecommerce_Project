package com.team_e_commerce.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer() {
        return resolver -> {
            // 프론트엔드가 1을 보내면 스프링 내부적으로 0으로 변환 (1-based index)
            resolver.setOneIndexedParameters(true);

            // 기본 1페이지(내부 0), 20개 사이즈로 고정
            resolver.setFallbackPageable(PageRequest.of(0, 20));
        };
    }
}