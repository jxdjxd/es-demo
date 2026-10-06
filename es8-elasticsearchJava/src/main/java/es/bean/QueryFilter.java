package es.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询筛选条件
 *
 * @author jxd
 * {@code @date} 2024/12/22 22:16
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class QueryFilter {
    /**
     * 字段名
     */
    private String fieldName;
    /**
     * 查询值
     */
    private Object value;
}
