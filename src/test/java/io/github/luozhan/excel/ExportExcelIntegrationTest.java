package io.github.luozhan.excel;

import io.github.luozhan.excel.core.ExportExcel;
import io.github.luozhan.excel.cursor.integration.CursorExportIntegrationTest;
import io.github.luozhan.excel.integration.DemoMapper;
import io.github.luozhan.excel.integration.SimpleDemoVO;
import io.github.luozhan.excel.sample.model.TestPage;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ExportExcel 组件传统分页 / 全量 / limit 超限场景的 Web 集成测试。
 * <p>
 * 使用 H2 内存数据库初始化 {@code demo_user} 表（共 {@value #TOTAL_ROWS} 条记录），
 * 接口返回 {@link SimpleDemoVO}（不含游标注解）以避开游标分页拦截器的 SQL 改写，
 * 从而验证传统偏移量分页 / 全量导出 / {@code limit} 超限报错等原生路径。
 * <p>
 * H2 配置与 {@link CursorExportIntegrationTest}
 * 保持一致，仅数据库名与初始化脚本不同。
 */
@SpringBootTest(classes = ExportExcelIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:export_excel_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:export-excel-test-schema.sql",
        "spring.sql.init.data-locations=classpath:export-excel-test-data.sql",
        "spring.sql.init.mode=always",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true"
})
class ExportExcelIntegrationTest {

    /**
     * {@code demo_user} 表中的记录数，需与 {@code export-excel-test-data.sql} 保持一致
     */
    private static final int TOTAL_ROWS = 3;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetCounters() {
        TestConfig.batchEndpointCallCount.set(0);
    }

    @Test
    @DisplayName("非导出请求应返回 JSON 响应")
    void shouldReturnJsonWhenNotExportRequest() throws Exception {
        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("张三"));
    }

    @Test
    @DisplayName("路径以 /export 结尾应导出 Excel 文件")
    void shouldExportExcelWhenUrlEndsWithExport() throws Exception {
        mockMvc.perform(get("/list/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment;filename=")))
                .andExpect(header().string("Content-Disposition",
                        containsString(".xlsx")))
                .andExpect(result -> {
                    byte[] content = result.getResponse().getContentAsByteArray();
                    assertTrue(content.length > 0, "导出的 Excel 内容不应为空");
                });
    }

    @Test
    @DisplayName("支持通过请求参数覆盖导出文件名")
    void shouldExportWithCustomFileName() throws Exception {
        mockMvc.perform(get("/list/export")
                        .param("fileName", "自定义文件"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment;filename=")))
                .andExpect(header().string("Content-Disposition",
                        containsString(".xlsx")));
    }

    @Test
    @DisplayName("未传文件名参数时应使用注解默认名")
    void shouldExportWithDefaultFileName() throws Exception {
        mockMvc.perform(get("/list/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment;filename=")));
    }

    @Test
    @DisplayName("数据为空时仍应输出合法的 Excel 响应")
    void shouldExportEmptyList() throws Exception {
        mockMvc.perform(get("/empty-list/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }

    @Test
    @DisplayName("分页接口启用 batchSize 后应多次调用 Controller")
    void shouldExportInBatches() throws Exception {
        mockMvc.perform(get("/batch/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(result -> {
                    byte[] content = result.getResponse().getContentAsByteArray();
                    assertTrue(content.length > 0, "导出的 Excel 内容不应为空");
                });
        assertTrue(TestConfig.batchEndpointCallCount.get() > 1,
                "分批导出应多次调用 Controller，实际调用次数："
                        + TestConfig.batchEndpointCallCount.get());
    }

    @Test
    @DisplayName("分页接口：JSON 返回当前页，导出返回全量")
    void shouldReturnPageDataButExportAllData() throws Exception {
        // 非导出请求：仅返回当前页数据
        mockMvc.perform(get("/batch")
                        .param("current", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        // 导出请求：必须覆盖全量数据
        mockMvc.perform(get("/batch/export")
                        .param("current", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(result -> {
                    byte[] content = result.getResponse().getContentAsByteArray();
                    assertTrue(content.length > 0, "导出的 Excel 内容不应为空");
                    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
                        Sheet sheet = workbook.getSheetAt(0);
                        int dataRows = sheet.getPhysicalNumberOfRows() - 1;
                        assertEquals(TOTAL_ROWS, dataRows,
                                "导出的 Excel 应包含全量记录");
                    }
                });
        assertTrue(TestConfig.batchEndpointCallCount.get() > 1,
                "分批导出应多次调用 Controller，实际调用次数："
                        + TestConfig.batchEndpointCallCount.get());
    }

    @Test
    @DisplayName("导出量超过 limit 限制时应抛 IllegalArgumentException")
    void shouldThrowWhenExceedLimit() throws Exception {
        Exception thrown = assertThrows(Exception.class, () ->
                mockMvc.perform(get("/exceed-max/export"))
        );
        Throwable cause = thrown.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause,
                "根因应为 IllegalArgumentException");
        assertTrue(cause.getMessage().contains("导出数据量超过限制"),
                "异常信息应包含「导出数据量超过限制」，实际：" + cause.getMessage());
    }

    /**
     * 测试专用上下文。提供 4 个接口覆盖不同场景，数据读自 {@link DemoMapper}。
     */
    @RestController
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("io.github.luozhan.excel.integration")
    static class TestConfig {

        /**
         * {@code /batch} 接口被调用次数，用于验证分批导出是否多次拉取
         */
        static final AtomicInteger batchEndpointCallCount = new AtomicInteger(0);

        @Autowired
        private DemoMapper demoMapper;

        @GetMapping("/list")
        @ExportExcel(fileName = "测试导出")
        public List<SimpleDemoVO> list() {
            return demoMapper.selectAll();
        }

        @GetMapping("/empty-list")
        @ExportExcel(fileName = "空数据导出")
        public List<SimpleDemoVO> emptyList() {
            return demoMapper.selectEmpty();
        }

        @GetMapping("/batch")
        @ExportExcel(fileName = "分批导出", batchSize = 2)
        public Page<SimpleDemoVO> batch(TestPage<SimpleDemoVO> pageRequest) {
            batchEndpointCallCount.incrementAndGet();
            long offset = (pageRequest.getCurrent() - 1) * pageRequest.getSize();
            long size = pageRequest.getSize();
            List<SimpleDemoVO> records = demoMapper.selectPage(offset, size);
            return new PageImpl<>(records);
        }

        @GetMapping("/exceed-max")
        @ExportExcel(fileName = "超限导出", limit = 1)
        public List<SimpleDemoVO> exceedMax() {
            return demoMapper.selectAll();
        }
    }
}
