package io.github.luozhan.excel.cursor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CursorContext ThreadLocal生命周期测试
 */
class ExcelContextTest {

    @AfterEach
    void cleanup() {
        ExcelContext.clear();
    }

    @Test
    @DisplayName("未激活时isActive为false")
    void shouldBeInactiveByDefault() {
        assertFalse(ExcelContext.isActive());
        assertNull(ExcelContext.get());
    }

    @Test
    @DisplayName("激活后可获取正确状态")
    void shouldActivateCursorModeCorrectly() {
        ExcelContext.activateCursorMode(new String[]{"id"}, new String[]{"id"}, 1000, DemoVo.class, Void.class);

        assertTrue(ExcelContext.isCursorModeActive());
        ExcelContext.CursorState state = ExcelContext.get();
        assertNotNull(state);
        assertArrayEquals(new String[]{"id"}, state.getDbColumns());
        assertArrayEquals(new String[]{"id"}, state.getVoFieldNames());
        assertEquals(1000, state.getBatchSize());
        assertArrayEquals(new Object[]{null}, state.getLastIds());
        assertEquals(DemoVo.class, state.getVoClass());
        assertEquals(Void.class, state.getEntityClass());
    }

    @Test
    @DisplayName("支持表别名字段与VO字段名分离")
    void shouldSupportTableAliasField() {
        ExcelContext.activateCursorMode(new String[]{"t.id"}, new String[]{"id"}, 500, DemoVo.class, Void.class);

        ExcelContext.CursorState state = ExcelContext.get();
        assertArrayEquals(new String[]{"t.id"}, state.getDbColumns());
        assertArrayEquals(new String[]{"id"}, state.getVoFieldNames());
        assertEquals(500, state.getBatchSize());
    }

    @Test
    @DisplayName("多字段复合游标可正确激活")
    void shouldActivateMultiFieldCursorMode() {
        ExcelContext.activateCursorMode(
                new String[]{"create_time", "id"},
                new String[]{"createTime", "id"},
                200, DemoVo.class, Void.class);

        ExcelContext.CursorState state = ExcelContext.get();
        assertArrayEquals(new String[]{"create_time", "id"}, state.getDbColumns());
        assertArrayEquals(new String[]{"createTime", "id"}, state.getVoFieldNames());
        assertArrayEquals(new Object[]{null, null}, state.getLastIds());
    }

    @Test
    @DisplayName("updateLastIds正确更新")
    void shouldUpdateLastIds() {
        ExcelContext.activateCursorMode(new String[]{"id"}, new String[]{"id"}, 1000, DemoVo.class, Void.class);

        ExcelContext.updateLastIds(new Object[]{100L});
        assertArrayEquals(new Object[]{100L}, ExcelContext.get().getLastIds());

        ExcelContext.updateLastIds(new Object[]{999L});
        assertArrayEquals(new Object[]{999L}, ExcelContext.get().getLastIds());
    }

    @Test
    @DisplayName("多字段updateLastIds按位置更新")
    void shouldUpdateMultiFieldLastIds() {
        ExcelContext.activateCursorMode(
                new String[]{"create_time", "id"},
                new String[]{"createTime", "id"},
                500, DemoVo.class, Void.class);

        ExcelContext.updateLastIds(new Object[]{"2026-06-08 10:00:00", 42L});
        assertArrayEquals(new Object[]{"2026-06-08 10:00:00", 42L}, ExcelContext.get().getLastIds());
    }

    @Test
    @DisplayName("updateLastIds长度不一致时抛异常")
    void shouldRejectMismatchedLastIdsLength() {
        ExcelContext.activateCursorMode(
                new String[]{"create_time", "id"},
                new String[]{"createTime", "id"},
                500, DemoVo.class, Void.class);

        assertThrows(IllegalArgumentException.class,
                () -> ExcelContext.updateLastIds(new Object[]{"only-one-value"}));
    }

    @Test
    @DisplayName("clear后恢复未激活状态")
    void shouldClearContext() {
        ExcelContext.activateCursorMode(new String[]{"id"}, new String[]{"id"}, 1000, DemoVo.class, Void.class);
        ExcelContext.updateLastIds(new Object[]{500L});

        ExcelContext.clear();

        assertFalse(ExcelContext.isActive());
        assertNull(ExcelContext.get());
    }

    @Test
    @DisplayName("未激活时updateLastIds不抛异常")
    void shouldNotThrowWhenUpdateWithoutActivation() {
        assertDoesNotThrow(() -> ExcelContext.updateLastIds(new Object[]{100L}));
    }

    @Test
    @DisplayName("激活时dbColumns为空抛异常")
    void shouldRejectEmptyDbColumns() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelContext.activateCursorMode(new String[0], new String[0], 100, DemoVo.class, Void.class));
    }

    @Test
    @DisplayName("激活时voFieldNames与dbColumns长度不一致抛异常")
    void shouldRejectMismatchedActivationLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> ExcelContext.activateCursorMode(new String[]{"id", "name"}, new String[]{"id"}, 100, DemoVo.class, Void.class));
    }

    /**
     * 仅用于测试的占位 VO 类型
     */
    private static class DemoVo {
    }
}
