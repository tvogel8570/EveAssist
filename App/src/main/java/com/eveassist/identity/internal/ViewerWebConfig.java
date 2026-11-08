package com.eveassist.identity.internal;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class ViewerWebConfig implements WebMvcConfigurer {

    private final ViewerArgumentResolver viewers;

    ViewerWebConfig(ViewerArgumentResolver viewers) {
        this.viewers = viewers;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(viewers);
    }
}
