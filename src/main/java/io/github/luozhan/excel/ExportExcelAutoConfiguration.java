package io.github.luozhan.excel;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.luozhan.excel.paramhandle.req.impl.MybatisPlusPageParamHandler;
import io.github.luozhan.excel.paramhandle.rsp.impl.MybatisPlusPageDataConverter;
import io.github.luozhan.excel.paramhandle.rsp.impl.SpringPageDataConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ExportExcel自动配置
 *
 * @author luozhan
 * @since 2026-05-04
 */
@AutoConfiguration
@Import(ExportExcelAspect.class)
public class ExportExcelAutoConfiguration {

    @Bean
    @ConditionalOnClass(Page.class)
    public SpringPageDataConverter springPageToListConverter() {
        return new SpringPageDataConverter();
    }

    @Bean
    @ConditionalOnClass(IPage.class)
    public MybatisPlusPageDataConverter mybatisPlusPageDataConverter() {
        return new MybatisPlusPageDataConverter();
    }

    @Bean
    @ConditionalOnClass(IPage.class)
    public MybatisPlusPageParamHandler mybatisPlusPageParamHandler() {
        return new MybatisPlusPageParamHandler();
    }

    @Bean
    public FilterRegistrationBean<ExportExcelFilter> exportExcelFilterRegistration(RequestMappingHandlerMapping handlerMapping) {
        FilterRegistrationBean<ExportExcelFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ExportExcelFilter(handlerMapping));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("exportExcelFilter");
        return registration;
    }
}
