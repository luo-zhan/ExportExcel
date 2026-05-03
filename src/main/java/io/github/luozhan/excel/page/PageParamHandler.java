package io.github.luozhan.excel.page;

import org.springframework.core.ResolvableType;

/**
 * 分页参数处理器接口
 * 实现该接口并注入spring
 *
 * @param <T> 你项目使用的分页DTO类
 */
public abstract class PageParamHandler<T> {

    public abstract void accept(T page, int pageNumber, int pageSize);
    /**
     * 子类继承指定的泛型
     */
    @SuppressWarnings("unchecked")
    public Class<T> getSupportType() {
        ResolvableType handlerType = ResolvableType.forInstance(this).as(PageParamHandler.class);
        return (Class<T>) handlerType.getGeneric(0).resolve();
    }


}
