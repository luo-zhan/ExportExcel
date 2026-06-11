package io.github.luozhan.excel;

import io.github.luozhan.excel.paramhandle.rsp.impl.MybatisPlusPageDataConverter;
import io.github.luozhan.excel.paramhandle.rsp.impl.SpringPageDataConverter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link ExportExcelAutoConfiguration} 自动装配集成测试：
 * 验证默认上下文下核心 Bean 的注册情况。
 */
@SpringBootTest(classes = ExportExcelAutoConfigurationTest.TestConfig.class)
class ExportExcelAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("应注册 ExportExcelAspect 切面 Bean")
    void shouldRegisterExportExcelAspectBean() {
        ExportExcelAspect aspect = applicationContext.getBean(ExportExcelAspect.class);
        assertNotNull(aspect);
    }

    @Test
    @DisplayName("应注册 MybatisPlus IPage 到 List 的转换器 Bean")
    void shouldRegisterMybatisPlusPageDataConverterBean() {
        MybatisPlusPageDataConverter converter = applicationContext.getBean(MybatisPlusPageDataConverter.class);
        assertNotNull(converter);
    }

    @Test
    @DisplayName("应注册 Spring Page 到 List 的转换器 Bean")
    void shouldRegisterSpringPageDataConverterBean() {
        SpringPageDataConverter converter = applicationContext.getBean(SpringPageDataConverter.class);
        assertNotNull(converter);
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }
}
