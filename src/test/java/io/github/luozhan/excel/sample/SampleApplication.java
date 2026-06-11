package io.github.luozhan.excel.sample;

import io.github.luozhan.excel.converter.BooleanToChineseConverter;
import io.github.luozhan.excel.cursor.CursorPaginationInterceptor;
import io.github.luozhan.excel.paramhandle.req.PageParamHandler;
import io.github.luozhan.excel.paramhandle.rsp.ExcelDataConverter;
import io.github.luozhan.excel.sample.model.MyPage;
import io.github.luozhan.excel.sample.model.MyPageRequest;
import io.github.luozhan.excel.sample.model.Result;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.domain.Page;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
public class SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleApplication.class, args);
    }

    /**
     * 注册布尔转换器
     */
    @Bean
    public BooleanToChineseConverter booleanToChineseConverter() {
        return new BooleanToChineseConverter();
    }

    /**
     * 显式注册游标分页拦截器，用于演示游标导出流程。
     * 真实项目中只要存在 MyBatis SqlSessionFactory，自动配置会自动注册该 Bean，无需手动声明。
     */
    @Bean
    public CursorPaginationInterceptor cursorPaginationInterceptor() {
        return new CursorPaginationInterceptor();
    }

    /**
     * 统一响应体 -> List的转换器
     * 声明如何从Result中拿到excel数据集
     */
    @Bean
    @SuppressWarnings("Convert2Lambda")
    public ExcelDataConverter<Result<?>> resultToListConverter() {
        return new ExcelDataConverter<Result<?>>() {
            @Override
            public List<?> convert(@NonNull Result<?> source) {
                Object data = source.getData();
                if (data instanceof List) {
                    return (List<?>) data;
                }
                if (data instanceof MyPage) {
                    return ((Page<?>) data).getContent();
                }
                return Collections.singletonList(data);
            }
        };
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
    public ExcelDataConverter<MyPage<?>> pageToListConverter() {
        return new ExcelDataConverter<MyPage<?>>() {
            @Override
            public List<?> convert(@NonNull MyPage<?> source) {
                return source.getContent();
            }
        };
    }


    @RestControllerAdvice
    public static class GlobalExceptionHandler {
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
            return result;
        }
    }

}
