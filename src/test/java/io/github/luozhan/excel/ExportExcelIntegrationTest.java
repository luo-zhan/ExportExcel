package io.github.luozhan.excel;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.luozhan.excel.sample.model.DemoVO;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ExportExcelIntegrationTest.TestConfig.class)
@AutoConfigureMockMvc
class ExportExcelIntegrationTest {

    private static final List<DemoVO> TEST_DATA = Arrays.asList(
            new DemoVO("张三", 25, true, "技术研发部",
                    "zhangsan@company.com", "13800138001",
                    "北京市朝阳区建国路88号", "Java开发", "EMP001", "10年经验"),
            new DemoVO("李四", 30, false, "市场部",
                    "lisi@company.com", "13900139002",
                    "上海市浦东新区", "市场总监", "EMP002", "资深"),
            new DemoVO("王五", 28, true, "财务部",
                    "wangwu@company.com", "13700137003",
                    "广州市天河区", "财务经理", "EMP003", "注册会计师")
    );

    static final AtomicInteger batchCallCount = new AtomicInteger(0);

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Test
    void shouldReturnJsonWhenNotExportRequest() throws Exception {
        mockMvc.perform(get("/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("张三"));
    }

    @Test
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
                    assertTrue(content.length > 0, "Excel content should not be empty");
                });
    }

    @Test
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
    void shouldExportWithDefaultFileName() throws Exception {
        mockMvc.perform(get("/list/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment;filename=")));
    }

    @Test
    void shouldExportEmptyList() throws Exception {
        mockMvc.perform(get("/empty-list/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")));
    }

    @Test
    void shouldExportInBatches() throws Exception {
        batchCallCount.set(0);
        mockMvc.perform(get("/batch/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(result -> {
                    byte[] content = result.getResponse().getContentAsByteArray();
                    assertTrue(content.length > 0, "Excel content should not be empty");
                });
        assertTrue(batchCallCount.get() > 1,
                "Controller should be called multiple times for batch export, but was called "
                        + batchCallCount.get() + " times");
    }

    @Test
    void shouldReturnPageDataButExportAllData() throws Exception {
        mockMvc.perform(get("/batch")
                        .param("current", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));

        batchCallCount.set(0);
        mockMvc.perform(get("/batch/export")
                        .param("current", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type",
                        containsString("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")))
                .andExpect(result -> {
                    byte[] content = result.getResponse().getContentAsByteArray();
                    assertTrue(content.length > 0, "Excel content should not be empty");
                    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
                        Sheet sheet = workbook.getSheetAt(0);
                        int dataRows = sheet.getPhysicalNumberOfRows() - 1;
                        assertEquals(TEST_DATA.size(), dataRows,
                                "Exported Excel should contain all data rows");
                    }
                });
        assertTrue(batchCallCount.get() > 1,
                "Controller should be called multiple times for batch export, but was called "
                        + batchCallCount.get() + " times");
    }

    @Test
    void shouldThrowWhenExceedLimit() throws Exception {
        Exception thrown = assertThrows(Exception.class, () ->
                mockMvc.perform(get("/exceed-max/export"))
        );
        Throwable cause = thrown.getCause();
        assertInstanceOf(IllegalArgumentException.class, cause,
                "Root cause should be IllegalArgumentException");
        assertTrue(cause.getMessage().contains("导出数据量超过限制"),
                "Should contain max size limit message, but got: " + cause.getMessage());
    }

    @RestController
    @Configuration
    @EnableAutoConfiguration
    static class TestConfig {

        @GetMapping("/list")
        @ExportExcel(fileName = "测试导出")
        public List<DemoVO> list() {
            return TEST_DATA;
        }

        @GetMapping("/empty-list")
        @ExportExcel(fileName = "空数据导出")
        public List<DemoVO> emptyList() {
            return new ArrayList<>();
        }

        @GetMapping("/batch")
        @ExportExcel(fileName = "分批导出", batchSize = 2)
        public Page<DemoVO> batch(IPage<?> pageableParam) {
            batchCallCount.incrementAndGet();
            long fromIndex = (pageableParam.getCurrent() - 1) * pageableParam.getSize();
            long toIndex = Math.min(fromIndex + pageableParam.getSize(), TEST_DATA.size());
            if (fromIndex >= TEST_DATA.size()) {
                return new PageImpl<>(new ArrayList<>()) ;
            }
            return new PageImpl<>(TEST_DATA.subList((int) fromIndex, (int) toIndex));
        }

        @GetMapping("/exceed-max")
        @ExportExcel(fileName = "超限导出", limit = 1)
        public List<DemoVO> exceedMax() {
            return TEST_DATA;
        }
    }
}
