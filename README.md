# Export Excel

![Java](https://img.shields.io/badge/Java-1.8+-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-6DB33F?logo=springboot&logoColor=white)
![Apache Fesod](https://img.shields.io/badge/Apache%20Fesod-2.0.1-blue?logo=apache&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?logo=apachemaven&logoColor=white)
![Code Size](https://img.shields.io/github/languages/code-size/luo-zhan/export-excel)
![License](https://img.shields.io/github/license/luo-zhan/export-excel)
![Last Commit](https://img.shields.io/github/last-commit/luo-zhan/export-excel)

基于 [Apache Fesod](https://github.com/apache/fesod) 的 Spring Boot Excel 导出组件，通过一个注解 `@ExportExcel` 即可为现有查询接口添加 Excel 导出能力，零侵入业务代码。

## 特性

- **注解驱动**：在 Controller 方法上添加 `@ExportExcel` 注解即可启用导出
- **零改造**：前端在原有 URL 后增加 `/export` 即可触发导出，无需新增接口
- **分批流式导出**：自动识别分页参数，分批查询并流式写入 Excel，内存占用极低
- **自适应列宽**：根据表头和单元格内容自动调整列宽，中英文字符精确计算
- **多框架支持**：内置 MyBatis-Plus (`IPage`) 和 Spring Data (`Page`) 分页适配
- **可扩展**：支持自定义数据转换器和分页参数处理器

## 环境要求

- JDK 1.8+
- Spring Boot 2.7.x（spring-boot-starter-web）

## 快速开始

### 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.luo-zhan</groupId>
    <artifactId>export-excel</artifactId>
    <version>1.0.1</version>
</dependency>
```

Spring Boot 自动装配会自动完成所有配置，无需额外操作。

### 2. 在 VO 上配置列名

使用 `@ExcelProperty` 注解声明导出的列名（与 EasyExcel 用法一致）：

```java
public class DemoVO {

    @ExcelProperty("员工姓名")
    private String name;

    @ExcelProperty("年龄")
    private Integer age;

    @ExcelProperty("所属部门")
    private String department;

    // ...
}
```

如果有其中某些属性不需要导出，需要在类上标记 `@ExcelIgnoreUnannotated` 注解。

### 3. 在 Controller 方法上添加注解

```java
@GetMapping("/list")
@ExportExcel
public IPage<DemoVO> list(PageRequest pageRequest, QueryDTO query) {
    return demoService.list(pageRequest, query);
}
```

### 4. 前端触发导出

在原有查一下接口 URL 后加上 `/export` 即可：

```
GET /page/export
```

另外也可通过 `fileName` 参数指定导出文件名（不需要加`.xlsx`）：

```
GET /list/export?fileName=员工数据报表
```

## 注解参数

`@ExportExcel` 注解支持以下参数：

| 参数 | 说明                             | 默认值        |
|------|--------------------------------|------------|
| `fileName` | 导出文件名（会被 URL 参数 `fileName` 覆盖） | `"导出数据"`   |
| `sheetName` | Sheet 名称                       | `"sheet1"` |
| `limit` | 导出数据量上限，超过则终止导出                | `10000`    |
| `batchSize` | 分批查询时每批的数据量（仅在方法有分页参数时生效）      | `1000`     |

> 注：数据量超过1万容易产生深分页问题，建议这种场景用游标查询单独实现导出
## 支持的返回类型

组件会自动从方法返回值中提取数据，支持以下类型：

| 返回类型 | 说明 |
|----------|------|
| `List<VO>` | 直接使用列表数据 |
| `IPage<VO>` | MyBatis-Plus 分页对象，自动提取 `records` |
| `Page<VO>` | Spring Data 分页对象，自动提取 `content` |
| 其他类型 | 需要自定义 `ExcelDataConverter` 转换器 |



## 自定义扩展

### 自定义数据转换器

如果接口返回类型不在内置支持范围内，可实现 `ExcelDataConverter` 接口并注册为 Spring Bean：

```java
@Component
public class MyCustomConverter implements ExcelDataConverter<MyResult<?>> {

    @Override
    public List<?> convert(MyResult<?> source) {
        return source.getData();
    }
}
```

### 自定义分页参数处理器

如果项目使用了自定义的分页 DTO，可继承 `PageParamHandler` 并注册为 Spring Bean：

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
## 试用
下载源码，找到`SampleApplication.java`运行起来，启动并访问该类中的接口即可
```
http://localhost:8080/page/export
```

## 许可证

Apache License 2.0
