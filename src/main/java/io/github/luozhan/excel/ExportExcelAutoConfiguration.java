package io.github.luozhan.excel;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.github.luozhan.excel.cursor.CursorMetadataResolver;
import io.github.luozhan.excel.cursor.CursorPaginationInterceptor;
import io.github.luozhan.excel.paramhandle.req.impl.MybatisPlusPageParamHandler;
import io.github.luozhan.excel.paramhandle.rsp.impl.MybatisPlusPageDataConverter;
import io.github.luozhan.excel.paramhandle.rsp.impl.SpringPageDataConverter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * ExportExcel自动配置
 *
 * @author luozhan
 * @since 2026-05-04
 */
@AutoConfiguration
@Import(ExportExcelAspect.class)
public class ExportExcelAutoConfiguration {

    /**
     * 游标元数据解析器：扫描 VO 上 @CursorField / @CursorEntity 并缓存。
     * 始终注册（即使无 MyBatis），便于 Aspect 直接依赖。
     */
    @Bean
    public CursorMetadataResolver cursorMetadataResolver() {
        return new CursorMetadataResolver();
    }

    /**
     * 注册导出tHandlerMapping：识别 /xxx/export 请求并映射到查询接口
     */
    @Bean
    public ExportHandlerMapping exportFallbackHandlerMapping(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        return new ExportHandlerMapping(handlerMapping);
    }

    @Bean
    @ConditionalOnClass(Page.class)
    public SpringPageDataConverter springPageToListConverter() {
        return new SpringPageDataConverter();
    }

    @Bean
    @ConditionalOnClass(IPage.class)
    public MybatisPlusPageDataConverter mybatisPlusPageDataConverter() {
        return new MybatisPlusPageDataConverter();
    }

    @Bean
    @ConditionalOnClass(IPage.class)
    public MybatisPlusPageParamHandler mybatisPlusPageParamHandler() {
        return new MybatisPlusPageParamHandler();
    }

    /**
     * 注册游标分页拦截器（需同时满足：项目配置了 MyBatis SqlSessionFactory 且 classpath 中存在 JSqlParser）
     * 拦截器通过 ThreadLocal 判断是否需要激活，对普通查询零影响
     */
    @Bean
    @ConditionalOnBean(SqlSessionFactory.class)
    @ConditionalOnClass(name = "net.sf.jsqlparser.parser.CCJSqlParserUtil")
    public CursorPaginationInterceptor cursorPaginationInterceptor() {
        return new CursorPaginationInterceptor();
    }

}
