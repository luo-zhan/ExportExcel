package io.github.luozhan.excel.cursor.integration;

import io.github.luozhan.excel.core.ExportExcel;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 游标分页导出集成测试。
 * <p>
 * 使用 H2 内存数据库初始化 100 条 demo 数据，验证游标分页拦截器在真实 MyBatis
 * 环境下能正确改写 SQL 并完成分批导出。
 */
@SpringBootTest(classes = CursorExportIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cursor_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:cursor-test-schema.sql",
        "spring.sql.init.data-locations=classpath:cursor-test-data.sql",
        "spring.sql.init.mode=always",
        "mybatis-plus.configuration.map-underscore-to-camel-case=true",
        "logging.level.io.github.luozhan.excel=DEBUG"
})
public class CursorExportIntegrationTest {

    /**
     * {@code demo} 表中初始化的记录总数，需与 {@code cursor-test-data.sql} 保持一致
     */
    private static final int TOTAL_ROWS = 100;

    /**
     * 接口上配置的游标分批拉取大小，需与 {@link TestConfig#cursorDemo()} 的 {@code batchSize} 保持一致
     */
    private static final int BATCH_SIZE = 10;

    /**
     * 预期总查询次数：100/10 = 10 次拿到数据，+ 1 次空查询触发退出
     */
    private static final int EXPECTED_QUERY_COUNT = TOTAL_ROWS / BATCH_SIZE + 1;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SqlSessionFactory sqlSessionFactory;

    @Test
    @DisplayName("验证 SqlSessionFactory 和 CursorPaginationInterceptor 已注册")
    void shouldHaveSqlSessionFactoryAndInterceptor() {
        assertNotNull(sqlSessionFactory, "SqlSessionFactory 应已注册");
        boolean hasInterceptor = sqlSessionFactory.getConfiguration().getInterceptors().stream()
                .anyMatch(i -> i.getClass().getSimpleName().equals("CursorPaginationInterceptor"));
        assertTrue(hasInterceptor, "CursorPaginationInterceptor 应已注册为 MyBatis 插件");
    }

    @Test
    @DisplayName("普通请求返回 JSON 数据")
    void shouldReturnJsonForNormalRequest() throws Exception {
        mockMvc.perform(get("/cursor-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(TOTAL_ROWS));
    }

    @Test
    @DisplayName("游标分页导出：全量 100 条数据都应写入 Excel并以 id 升序")
    void shouldExportAll100RowsViaCursorPagination() throws Exception {
        mockMvc.perform(get("/cursor-demo/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(header().string("Content-Disposition",
                        containsString(".xlsx")))
                .andExpect(result -> {
                    byte[] content = result.getResponse().getContentAsByteArray();
                    assertTrue(content.length > 0, "Excel 文件不应为空");

                    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
                        Sheet sheet = workbook.getSheetAt(0);
                        // 第 1 行是表头，后续 100 行是数据
                        int dataRows = sheet.getPhysicalNumberOfRows() - 1;
                        assertEquals(TOTAL_ROWS, dataRows,
                                "应导出全部 " + TOTAL_ROWS + " 条数据，实际导出：" + dataRows);

                        // 验证数据按 id 升序排列（游标分页特征）
                        Row firstDataRow = sheet.getRow(1);
                        Row lastDataRow = sheet.getRow(TOTAL_ROWS);
                        // ID 列（第 1 列）
                        double firstId = firstDataRow.getCell(0).getNumericCellValue();
                        double lastId = lastDataRow.getCell(0).getNumericCellValue();
                        assertEquals(1.0, firstId, "首行数据 id 应为 1");
                        assertEquals(TOTAL_ROWS, lastId, "末行数据 id 应为 " + TOTAL_ROWS);
                    }
                });
    }

    @Test
    @DisplayName("游标分页导出：验证分批查询（batchSize=10，100 条数据应查询 11 次）")
    void shouldQueryInBatches() throws Exception {
        TestConfig.cursorQueryCount.set(0);
        mockMvc.perform(get("/cursor-demo/export"))
                .andExpect(status().isOk());
        // batchSize=10, 100 条数据：查 10 次拿到数据 + 第 11 次返回空触发退出
        int actualCount = TestConfig.cursorQueryCount.get();
        assertTrue(actualCount > 1, "应分多批查询，实际查询次数：" + actualCount);
        assertEquals(EXPECTED_QUERY_COUNT, actualCount,
                TOTAL_ROWS + " 条数据/batchSize=" + BATCH_SIZE
                        + "，应查询 " + EXPECTED_QUERY_COUNT + " 次（含最后一次空查询）");
    }

    @Test
    @DisplayName("游标分页导出：支持自定义文件名")
    void shouldExportWithCustomFileName() throws Exception {
        mockMvc.perform(get("/cursor-demo/export").param("fileName", "测试游标导出"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment;filename=")));
    }

    /**
     * 测试专用上下文：提供 /cursor-demo 接口并记录调用次数。
     */
    @Configuration
    @EnableAutoConfiguration
    @RestController
    @MapperScan("io.github.luozhan.excel.cursor.integration")
    static class TestConfig {

        /**
         * 记录 {@code /cursor-demo} 被拦截器驱动执行查询的次数
         */
        static final ThreadLocal<Integer> cursorQueryCount = ThreadLocal.withInitial(() -> 0);

        @Autowired
        private CursorDemoMapper cursorDemoMapper;

        @GetMapping("/cursor-demo")
        @ExportExcel(fileName = "游标分页测试", batchSize = 10, limit = 200)
        public List<CursorDemoVO> cursorDemo() {
            cursorQueryCount.set(cursorQueryCount.get() + 1);
            return cursorDemoMapper.selectAll();
        }
    }
}
