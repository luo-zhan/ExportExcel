package io.github.luozhan.excel.writehandle;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.fesod.sheet.enums.CellDataTypeEnum;
import org.apache.fesod.sheet.metadata.data.WriteCellData;
import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自适应表格宽度样式实现
 * 根据表头、单元格内容自适应调整宽度
 *
 * @author luozhan
 * @since 2026-05-04
 */
public class AdaptiveWidthCellWriteHandler implements CellWriteHandler {
    // 采样阈值，避免分析大量记录影响性能
    private static final int SAMPLE_THRESHOLD = 200;
    // 单元格内容的固定边距
    private static final int PADDING = 5;
    private final Map<Integer, Integer> maxColumnWidthCache = new HashMap<>(SAMPLE_THRESHOLD);
    private int currentRowIndex = 0;

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        boolean needSetWidth = context.getHead() || !CollectionUtils.isEmpty(context.getCellDataList());
        if (!needSetWidth) {
            return;
        }
        if (!context.getHead()) {
            if (currentRowIndex >= SAMPLE_THRESHOLD) {
                return;
            }
            currentRowIndex++;
        }

        Integer columnWidth = calculateColumnWidth(context.getCellDataList(), context.getCell(), context.getHead());
        // 宽度固定在10-50之间
        columnWidth = Math.min(Math.max(columnWidth, 10), 50);

        Integer currentIndex = context.getCell().getColumnIndex();
        Integer maxColumnWidth = maxColumnWidthCache.get(currentIndex);
        if (maxColumnWidth == null || columnWidth > maxColumnWidth) {
            maxColumnWidthCache.put(currentIndex, columnWidth);
            Sheet sheet = context.getWriteSheetHolder().getSheet();
            sheet.setColumnWidth(currentIndex, columnWidth * 256);
        }
    }

    private Integer calculateColumnWidth(List<WriteCellData<?>> cellDataList, Cell cell, boolean isHead) {
        if (isHead) {
            // 表头字号较大，需要乘一个系数
            return stringWidth(cell.getStringCellValue()) * 5 / 4;
        }
        WriteCellData<?> cellData = cellDataList.get(0);
        CellDataTypeEnum type = cellData.getType();
        if (type == null) {
            return -1;
        }
        switch (type) {
            case STRING:
                return stringWidth(cellData.getStringValue());
            case BOOLEAN:
                return stringWidth(cellData.getBooleanValue().toString());
            case NUMBER:
                return stringWidth(cellData.getNumberValue().toString());
            case DATE:
                return stringWidth(cellData.getDateValue().toString());
            default:
                return -1;
        }

    }

    private int stringWidth(String str) {
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // 中文宽度*2
            width += isChineseOrFullWidth(c) ? 2 : 1;
        }
        return width + PADDING;
    }

    /**
     * 判断是否为中文、全角符号
     */
    private boolean isChineseOrFullWidth(char c) {
        return (c >= 0x4E00 && c <= 0x9FA5)
                || (c >= 0x3000 && c <= 0x303F)
                || (c >= 0xFF00 && c <= 0xFFEF);
    }

}
