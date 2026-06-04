package io.github.luozhan.excel;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.luozhan.excel.paramhandle.req.impl.MybatisPlusPageParamHandler;
import io.github.luozhan.excel.paramhandle.rsp.impl.MybatisPlusPageDataConverter;
import io.github.luozhan.excel.paramhandle.rsp.impl.SpringPageDataConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
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

    /**
     * 注册导出tHandlerMapping：识别 /xxx/export 请求并映射到查询接口
     */
    @Bean
    public ExportHandlerMapping exportFallbackHandlerMapping(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new ExportHandlerMapping(handlerMapping);
    }

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

}
