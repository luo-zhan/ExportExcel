package io.github.luozhan.excel.sample;

import io.github.luozhan.excel.ExportExcel;
import io.github.luozhan.excel.cursor.ExcelContext;
import io.github.luozhan.excel.sample.model.DemoVO;
import io.github.luozhan.excel.sample.model.MyPageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 示例控制器，覆盖 ExportExcel 组件三大典型导出场景。
 * <p>
 * 依赖静态内存数据，仅用于本地启动 {@link SampleApplication} 后人工验证接口表现，
 * 与集成测试（走 H2 内存库）互不依赖。
 *
 * <h3>接口一览</h3>
 * <ul>
 *   <li>{@code GET /list}　全量列表导出（{@link List} 返回值）。</li>
 *   <li>{@code GET /page}　分页导出（{@link Page} 返回值，启用 {@code batchSize} 分批拉取）。</li>
 *   <li>{@code GET /cursor} 游标分页导出（{@link CursorField} 标注 → 拦截器自动改写 SQL）。</li>
 * </ul>
 *
 * @author luozhan
 * @since 2026/6/3
 */
@RestController
public class SampleController {

    /**
     * 静态测试数据：共 5 条记录，涵盖不同部门 / 在职状态 / 可空字段场景。
     * <p>工号 EMP001~EMP005 作为游标键，可用于游标分页示例中的升序取数。
     */
    private static final List<DemoVO> SAMPLE_EMPLOYEES = Arrays.asList(
            // 完整字段样本：包含生日与收入
            new DemoVO("张三", 25, true, "技术研发部",
                    "zhangsan@company.com", "13800138001",
                    "北京市朝阳区建国路88号SOHO现代城A座2201室",
                    "高级Java开发工程师", "EMP001",
                    "10年Java开发经验，擅长分布式系统设计和微服务架构",
                    new Date(), new BigDecimal("123.4567")),
            // 部分可空字段样本：生日与收入为 null
            new DemoVO("李四", 30, false, "市场营销中心",
                    "lisi@company.com", "13900139002",
                    "上海市浦东新区",
                    "市场总监", "EMP002",
                    "资深", null, null),
            new DemoVO("王五", 28, true, "财务部",
                    "wangwu@company.com.cn", "13700137003",
                    "广州市天河区珠江新城花城大道18号南天广场群星阁3102房",
                    "财务经理", "EMP003",
                    "注册会计师，拥有丰富的企业财务管理经验", null, null),
            new DemoVO("赵六", 35, true, "人力资源部",
                    "zhaoliu@company.com", "13600136004",
                    "成都市",
                    "HR", "EMP004",
                    "认证人力资源管理师", null, null),
            new DemoVO("钱七", 22, false, "技术研发部",
                    "qianqi@company.com", "13500135005",
                    "深圳市南山区科技园南区松坪山路3号特发信息港大厦A座7楼708室",
                    "前端开发实习生", "EMP005",
                    "应届毕业生", null, null)
    );

    /**
     * 场景一：返回 {@link List}，默认全量一次性写入 Excel。
     */
    @GetMapping("/list")
    @ExportExcel(fileName = "测试List导出")
    public List<DemoVO> list() {
        return SAMPLE_EMPLOYEES;
    }

    /**
     * 场景二：返回 {@link Page}，未启用游标场景下走传统偏移量分页。
     * <p>{@code @ExportExcel(batchSize = 3)} 会驱动切面循环调用本接口，直至某一页返回空，
     * 从而达到“分页参数怎么传都导出全量”的效果。
     */
    @GetMapping("/page")
    @ExportExcel(fileName = "page参数导出，分页参数无论怎么设置，都会导出所有数据", batchSize = 3)
    public Page<DemoVO> page(MyPageRequest pageRequest) {
        int fromIndex = (pageRequest.getPageNum() - 1) * pageRequest.getPageSize();
        if (fromIndex >= SAMPLE_EMPLOYEES.size()) {
            return new PageImpl<>(new ArrayList<>());
        }
        int toIndex = Math.min(fromIndex + pageRequest.getPageSize(), SAMPLE_EMPLOYEES.size());
        return new PageImpl<>(SAMPLE_EMPLOYEES.subList(fromIndex, toIndex));
    }

    /**
     * 场景三：游标分页导出。
     * <p>
     * 真实场景下接口无需任何特殊代码，只要 VO 中某个字段被 {@link CursorField} 标注，
     * MyBatis 拦截器会自动将 SQL 改造为：
     * {@code WHERE employee_no > #{lastId} ORDER BY employee_no ASC LIMIT #{batchSize}}。
     * <p>
     * 此处 sample 没有数据库，因此通过读取 {@link ExcelContext} 模拟拦截器对内存数据的过滤效果，
     * 用于直观展示游标分页的循环查询过程。
     */
    @GetMapping("/cursor")
    @ExportExcel(fileName = "游标分页导出demo", batchSize = 2)
    public List<DemoVO> cursor() {
        ExcelContext.CursorState cursorState = ExcelContext.get();
        if (cursorState == null) {
            // 非导出请求或未激活游标分页，返回全量数据
            return SAMPLE_EMPLOYEES;
        }
        // sample 演示为单字段游标，多字段场景需逐列取 lastIds
        Object[] lastIds = cursorState.getLastIds();
        String lastEmployeeNo = lastIds == null || lastIds.length == 0 ? "" : String.valueOf(lastIds[0]);
        int batchSize = cursorState.getBatchSize();
        return SAMPLE_EMPLOYEES.stream()
                .filter(vo -> vo.getEmployeeNo().compareTo(lastEmployeeNo) > 0)
                .sorted(Comparator.comparing(DemoVO::getEmployeeNo))
                .limit(batchSize)
                .collect(Collectors.toList());
    }
}
