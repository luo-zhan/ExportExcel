package io.github.luozhan.excel.sample;

import io.github.luozhan.excel.ExportExcel;
import io.github.luozhan.excel.integration.DemoMapper;
import io.github.luozhan.excel.integration.SimpleDemoVO;
import io.github.luozhan.excel.sample.model.TestPage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 示例控制器，演示 ExportExcel 组件两种典型导出场景。
 *
 * <p>
 * 数据来自 H2 内存数据库 {@code demo_user} 表，通过 {@link DemoMapper} 查询，
 * 响应体使用 {@link SimpleDemoVO}（不含游标注解，走传统全量 / 偏移量分页路径）。
 *
 * <h3>接口一览</h3>
 * <ul>
 *   <li>{@code GET /list}　全量列表导出（{@link List} 返回值）。</li>
 *   <li>{@code GET /page}　分页导出（{@link Page} 返回值，启用 {@code batchSize} 分批拉取）。</li>
 * </ul>
 *
 * @author luozhan
 * @since 2026/6/3
 */
@RestController
public class SampleController {

    @Autowired
    private DemoMapper demoMapper;

    /**
     * 场景一：返回 {@link List}，默认全量一次性写入 Excel。
     */
    @GetMapping("/list")
    @ExportExcel(fileName = "测试List导出")
    public List<SimpleDemoVO> list() {
        return demoMapper.selectAll();
    }

    /**
     * 场景二：返回 {@link Page}，走传统偏移量分页。
     * <p>{@code @ExportExcel(batchSize = 3)} 会驱动切面循环调用本接口，直至某一页返回空，
     * 从而达到“分页参数怎么传都导出所有数据”的效果。
     */
    @GetMapping("/page")
    @ExportExcel(fileName = "page参数导出，分页参数无论怎么设置，都会导出所有数据", batchSize = 3)
    public Page<SimpleDemoVO> page(TestPage<SimpleDemoVO> pageRequest) {
        long offset = (pageRequest.getCurrent() - 1) * pageRequest.getSize();
        long size = pageRequest.getSize();
        List<SimpleDemoVO> records = demoMapper.selectPage(offset, size);
        return new PageImpl<>(records);
    }
}
