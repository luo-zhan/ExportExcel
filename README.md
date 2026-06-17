# Export Excel

![Java](https://img.shields.io/badge/Java-1.8+-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-6DB33F?logo=springboot&logoColor=white)
![Apache Fesod](https://img.shields.io/badge/Apache%20Fesod-2.0.1--incubating-blue?logo=apache&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white)
![License](https://img.shields.io/badge/License-Apache%20License%202.0-blue)

基于 [Apache Fesod](https://github.com/apache/fesod) 的 Spring Boot Excel 导出组件。在现有查询接口上添加 `@ExportExcel`
，前端把原 URL 改为 `/export` 后缀即可触发导出，无需改动业务代码。

## 特性

- **注解驱动**：Controller 方法标注 `@ExportExcel` 即可启用导出
- **零侵入**：前端在原有 URL 后追加 `/export`，无需新增接口
- **分批流式导出**：自动识别分页参数，分批查询并流式写入 Excel，内存占用低
- **自适应列宽**：根据表头和单元格内容自动调整列宽
- **多框架支持**：返回类型支持 `List`、MyBatis-Plus `IPage`、Spring Data `Page`；入参分页支持 MyBatis-Plus `IPage` 及自定义分页
  DTO
- **复用 Swagger 注解**：支持读取 `@ApiModelProperty` 或 `@Schema` 作为列名，减少重复配置
- **游标分页**：支持单字段 / 多字段复合游标分页，自动改写 SQL，避免深分页问题
- **可扩展**：支持自定义响应转换器、分页参数处理器、单元格写处理器、数据转换器

## 环境要求

- JDK 1.8+
- Spring Boot 2.7.x（`spring-boot-starter-web`）
- 游标分页导出需要 MyBatis

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.luo-zhan</groupId>
    <artifactId>export-excel</artifactId>
    <version>1.3.0</version>
</dependency>
```


### 2. 在 VO 上配置列名

使用 `@ExcelProperty` 声明导出的列名（用法与 EasyExcel 一致）：

```java
public class DemoVO {

    @ExcelProperty("员工姓名")
    private String name;

    @ExcelProperty("年龄")
    private Integer age;

    @ExcelIgnore
    private BigDecimal income; // 不需要导出
}
```

忽略的字段较多时，也可以在类上标记 `@ExcelIgnoreUnannotated`，只导出带有 `@ExcelProperty` 的字段。

#### 复用 Swagger 注解配置列名

如果 VO 已使用 Swagger 注解，可将 `@ExcelProperty` 留空，组件会自动读取 `@ApiModelProperty#value` 或
`@Schema#description/title` 作为列名：

```java

@ExcelIgnoreUnannotated
public class DemoVO {

    @ExcelProperty
    @ApiModelProperty("员工姓名")
    private String name;

    @ExcelProperty
    @Schema(description = "年龄")
    private Integer age;
}
```

优先级：`@ExcelProperty.value` > Swagger 注解 > 字段名。

### 3. 接口上标注 @ExportExcel

```java

@GetMapping("/page")
@ExportExcel
public IPage<DemoVO> page(IPage<DemoVO> pageRequest, QueryDTO query) {
    return demoService.list(pageRequest, query);
}
```

### 4. 前端触发导出

在原查询接口 URL 后加上 `/export`：

```
GET /page/export
```

可通过 `fileName` 请求参数指定导出文件名（不需要 `.xlsx` 后缀）：

```
GET /page/export?fileName=员工数据报表
```

## 注解参数

`@ExportExcel` 支持以下参数：

| 参数          | 说明                                     | 默认值        |
|-------------|----------------------------------------|------------|
| `fileName`  | 导出文件名，会被 URL 参数 `fileName` 覆盖          | `"导出数据"`   |
| `sheetName` | Sheet 名称                               | `"sheet1"` |
| `limit`     | 导出数据量上限，超过时抛异常终止（分批导出会在 Sheet 内写入终止提示） | `10000`    |
| `batchSize` | 分批查询时每批数量，仅在方法有分页参数或启用游标分页时生效          | `1000`     |

> 数据量较大时建议启用下面的「游标分页」，避免深分页问题。

## 游标分页导出（推荐大数据量场景）

传统偏移量分页（`LIMIT x OFFSET y`）在深分页时性能差，且可能出现数据重复或遗漏。本组件提供零侵入的游标分页方案：**在 VO
字段上标注 `@CursorField` 即可**；若 Mapper 实际返回类型与 VO 不一致，再额外标注 `@CursorEntity`。

### 启用方式

```java
public class UserVO {

    @CursorField
    private Long id;

    @ExcelProperty("姓名")
    private String name;
}
```

Controller 方法无需特殊代码，组件会自动把 lastId 作为字面量拼入 SQL（首次查询无 `WHERE` 条件）：

```sql
-- 首次查询
SELECT...FROM user
ORDER BY id ASC LIMIT 1000

-- 后续查询
SELECT...FROM user
WHERE id > 1000
ORDER BY id ASC LIMIT 1000
```

### 指定数据库列名 / 表别名前缀

当 VO 字段名与数据库列名不一致，或多表查询需要表别名时，通过 `value` 显式指定：

```java
public class UserVO {

    @CursorField("t.id")
    private Long id;
}
```

留空时默认使用 VO 字段名并自动驼峰转下划线（如 `createTime` → `create_time`）。

### 多字段复合游标

多个字段同时标注 `@CursorField`，并通过 `order` 声明元组顺序，可组成复合游标：

```java
public class UserVO {

    @CursorField(value = "create_time", order = 0)
    private Date createTime;

    @CursorField(value = "id", order = 1)
    private Long id;
}
```

组件会生成（首次查询无 `WHERE` 条件）：

```sql
-- 首次查询
SELECT...FROM user
ORDER BY create_time ASC, id ASC LIMIT 1000

-- 后续查询
SELECT...FROM user
WHERE (create_time, id) > ('2024-01-01 10:00:00', 1000)
ORDER BY create_time ASC, id ASC LIMIT 1000
```

### 兼容原始 ORDER BY 方向

拦截器会自动解析原始 SQL 的 `ORDER BY` 字段：

- 匹配到 `@CursorField` 中的字段时，自动识别升 / 降序并统一所有游标字段方向；
- 未匹配到时会抛出异常，提示需要在 `@CursorField` 中配置该字段。

### VO 与 Mapper 返回类型不一致

如果 Mapper 返回 `UserDO`，Service 再转成 `UserVO`，需要在 `UserVO` 上标注 `@CursorEntity` 指定真实返回类型：

```java

@CursorEntity(UserDO.class)
public class UserVO {
    @CursorField
    private Long id;
}
```

### 注意事项

- 游标字段需为可比较、有序且有索引的列
- 启用后原查询的 `ORDER BY` 会被覆盖为按游标字段排序
- 仅在导出场景生效，普通 API 调用零影响
- 需要项目已配置 MyBatis（`SqlSessionFactory`）；未配置时自动降级为传统分页

## 支持的返回类型

组件会自动从方法返回值中提取数据：

| 返回类型        | 说明                           |
|-------------|------------------------------|
| `List<VO>`  | 直接使用列表数据                     |
| `IPage<VO>` | MyBatis-Plus 分页，自动取 records  |
| `Page<VO>`  | Spring Data 分页，自动取 content   |
| 其他类型        | 自定义 `ExcelDataConverter` 转换器 |

## 自定义扩展

### 自定义响应转换器

返回类型不在内置支持范围内时，实现 `ExcelDataConverter` 并注册为 Spring Bean：

```java
@Component
public class MyResultConverter implements ExcelDataConverter<MyResult<?>> {

    @Override
    public List<?> convert(MyResult<?> source) {
        return source.getData();
    }
}
```

### 自定义分页参数处理器

使用自定义分页 DTO 时，继承 `PageParamHandler` 并注册为 Spring Bean：

```java
@Component
public class MyPageParamHandler extends PageParamHandler<MyPageRequest> {

    @Override
    public void accept(MyPageRequest page, int pageNumber, int pageSize) {
        page.setPageNum(pageNumber);
        page.setPageSize(pageSize);
    }
}
```

### 自定义数据转换器

组件内置以下 Fesod 转换器，注册为 Bean 即可生效：

- [`BooleanToChineseConverter`](src/main/java/io/github/luozhan/excel/converter/BooleanToChineseConverter.java)：将
  `Boolean` 显示为 `是/否`
- [`LongToStringConverter`](src/main/java/io/github/luozhan/excel/converter/LongToStringConverter.java)：将 `Long`
  按字符串写入，避免 15 位后精度丢失

```java
@Bean
public BooleanToChineseConverter booleanToChineseConverter() {
    return new BooleanToChineseConverter();
}

@Bean
public LongToStringConverter longToStringConverter() {
    return new LongToStringConverter();
}
```

如需对其他 Java 类型做统一格式化，可参照上述转换器实现 `org.apache.fesod.sheet.converters.Converter` 并注册到 Spring 容器。

## 试用

下载源码后启动 [`SampleApplication`](src/test/java/io/github/luozhan/excel/sample/SampleApplication.java)，访问：

```
http://localhost:8080/list/export
http://localhost:8080/page/export
```

## 实现说明

本组件采用自定义 `HandlerMapping` + 注解 AOP 实现：

1. `ExportHandlerMapping` 拦截以 `/export` 结尾的请求，将路径重写为原始接口路径后交给 Spring MVC 处理；
2. `ExportExcelAspect` 在方法执行前后接管响应，根据返回类型分批查询数据；
3. 游标分页模式下，通过 ThreadLocal 与 MyBatis 拦截器协作，自动改写目标 SQL，对业务代码和普通查询零影响。

## 许可证

Apache License 2.0
