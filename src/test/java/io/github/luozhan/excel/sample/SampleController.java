package io.github.luozhan.excel.sample;

import io.github.luozhan.excel.ExportExcel;
import io.github.luozhan.excel.sample.model.DemoVO;
import io.github.luozhan.excel.sample.model.MyPageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 测试Controller
 *
 * @author R
 * @since 2026/6/3
 */
@RestController
public class SampleController {

    private static final List<DemoVO> TEST_DATA = Arrays.asList(
            new DemoVO("张三", 25, true, "技术研发部",
                    "zhangsan@company.com", "13800138001",
                    "北京市朝阳区建国路88号SOHO现代城A座2201室",
                    "高级Java开发工程师", "EMP001",
                    "10年Java开发经验，擅长分布式系统设计和微服务架构", new Date(), new BigDecimal("123.4567")),
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


    @GetMapping("/list")
    @ExportExcel(fileName = "测试List导出")
    public List<DemoVO> list() {

        return TEST_DATA;
    }


    @GetMapping("/page")
    @ExportExcel(fileName = "page参数导出，分页参数无论怎么设置，都会导出所有数据", batchSize = 3)
    public Page<DemoVO> batch(MyPageRequest pageableParam) {
        int fromIndex = (pageableParam.getPageNum() - 1) * pageableParam.getPageSize();
        int toIndex = Math.min(fromIndex + pageableParam.getPageSize(), TEST_DATA.size());
        if (fromIndex >= TEST_DATA.size()) {
            return new PageImpl<>(new ArrayList<>());
        }

        return new PageImpl<>(TEST_DATA.subList(fromIndex, toIndex));
    }


}
