package io.github.luozhan.excel;

import io.github.luozhan.excel.paramhandle.rsp.impl.MybatisPlusPageDataConverter;
import io.github.luozhan.excel.paramhandle.rsp.impl.SpringPageDataConverter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(classes = ExportExcelAutoConfigurationTest.TestConfig.class)
class ExportExcelAutoConfigurationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void shouldRegisterExportExcelAspectBean() {
        ExportExcelAspect aspect = applicationContext.getBean(ExportExcelAspect.class);
        assertNotNull(aspect);
    }

    @Test
    void shouldRegisterIPageToListConverterBean() {
        MybatisPlusPageDataConverter converter = applicationContext.getBean(MybatisPlusPageDataConverter.class);
        assertNotNull(converter);
    }

    @Test
    void shouldRegisterSpringPageToListConverterBean() {
        SpringPageDataConverter converter = applicationContext.getBean(SpringPageDataConverter.class);
        assertNotNull(converter);
    }

    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {
    }
}
