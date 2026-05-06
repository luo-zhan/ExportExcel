package io.github.luozhan.excel.sample;

import io.github.luozhan.excel.ExportExcel;
import io.github.luozhan.excel.converter.ExcelDataConverter;
import io.github.luozhan.excel.page.PageParamHandler;
import io.github.luozhan.excel.sample.model.MyPageRequest;
import io.github.luozhan.excel.sample.model.MyPage;
import io.github.luozhan.excel.sample.model.DemoVO;
import io.github.luozhan.excel.sample.model.Result;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

@SpringBootApplication
@RestController
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    private static final List<DemoVO> TEST_DATA = Arrays.asList(
            new DemoVO("张三", 25, true, "技术研发部",
                    "zhangsan@company.com", "13800138001",
                    "北京市朝阳区建国路88号SOHO现代城A座2201室",
                    "高级Java开发工程师", "EMP001",
                    "10年Java开发经验，擅长分布式系统设计和微服务架构"),
            new DemoVO("李四", 30, false, "市场营销中心",
                    "lisi@company.com", "13900139002",
                    "上海市浦东新区",
                    "市场总监", "EMP002",
                    "资深"),
            new DemoVO("王五", 28, true, "财务部",
                    "wangwu@company.com.cn", "13700137003",
                    "广州市天河区珠江新城花城大道18号南天广场群星阁3102房",
                    "财务经理", "EMP003",
                    "注册会计师，拥有丰富的企业财务管理经验"),
            new DemoVO("赵六", 35, true, "人力资源部",
                    "zhaoliu@company.com", "13600136004",
                    "成都市",
                    "HR", "EMP004",
                    "认证人力资源管理师"),
            new DemoVO("钱七", 22, false, "技术研发部",
                    "qianqi@company.com", "13500135005",
                    "深圳市南山区科技园南区松坪山路3号特发信息港大厦A座7楼708室",
                    "前端开发实习生", "EMP005",
                    "应届毕业生")
    );



    @GetMapping("/list")
    @ExportExcel(fileName = "测试List导出")
    public List<DemoVO> list() {

        return TEST_DATA;
    }


    @GetMapping("/page")
    @ExportExcel(fileName = "page参数导出，分页参数无论怎么设置，都会导出所有数据",batchSize = 3)
    public Page<DemoVO> batch(MyPageRequest pageableParam) {
        int fromIndex = (pageableParam.getPageNum() - 1) * pageableParam.getPageSize();
        int toIndex = Math.min(fromIndex + pageableParam.getPageSize(), TEST_DATA.size());
        if (fromIndex >= TEST_DATA.size()) {
            return new PageImpl<>(new ArrayList<>()) ;
        }
        if(fromIndex==1){
            throw new RuntimeException("error");
        }
        return new PageImpl<>(TEST_DATA.subList(fromIndex, toIndex));
    }

    /**
     * 注册自定义分页参数处理器
     * 声明如何设置分页参数
     */
    @Bean
    public PageParamHandler<MyPageRequest> myPageParamHandler() {
        // 匿名内部类，入参是页码设置函数和一页条数设置函数
        return new PageParamHandler<MyPageRequest>() {
            @Override
            public void accept(MyPageRequest page, int pageNumber, int pageSize) {
                page.setPageNum(pageNumber);
                page.setPageSize(pageSize);
            }
        };
    }

    /**
     * Page -> List转换器
     * 声明如何从分页结果中拿到excel数据集
     */
    @Bean
    @SuppressWarnings("Convert2Lambda")
    public ExcelDataConverter<MyPage<?>> pageToListConverter(){
        return new ExcelDataConverter<MyPage<?>>() {
            @Override
            public List<?> convert(@NonNull MyPage<?> source) {
                return source.getContent();
            }
        };
    }

    /**
     * 统一响应体 -> List的转换器
     * 声明如何从Result中拿到excel数据集
     */
    @Bean
    @SuppressWarnings("Convert2Lambda")
    public ExcelDataConverter<Result<?>> resultToListConverter(){
        return new ExcelDataConverter<Result<?>>() {
            @Override
            public List<?> convert(@NonNull Result<?> source) {
                Object data = source.getData();
                if (data instanceof List){
                    return (List<?>)data;
                }
                if(data instanceof MyPage){
                    return ((Page<?>)data).getContent();
                }
                return Collections.singletonList(data);
            }
        };
    }

    @RestControllerAdvice
    public class GlobalExceptionHandler {
        /**
         * 捕获 运行时异常（通用异常）
         */
        @ExceptionHandler(RuntimeException.class)
        public Map<String, Object> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
            return getErrorMap(500, "服务器异常：" + e.getMessage(), request.getRequestURI());
        }
        private Map<String, Object> getErrorMap(int code, String msg, String path) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", code);      // 状态码
            result.put("msg", msg);        // 错误提示
            result.put("path", path);      // 请求接口路径
            result.put("timestamp", System.currentTimeMillis()); // 时间戳
            return result;
        }
    }
}
