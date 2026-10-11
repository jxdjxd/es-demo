package es;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import es.bean.QueryFilter;
import es.index.EmployeeIndexDo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author jxd
 * {@code @date} 2024/12/24 23:38
 */
@SpringBootTest
class EsCommonOperationTest {
    @Autowired
    private EsCommonOperation esCommonOperation;

    String indexName = "employee";

    @Test
    void insert_test() {
        // @formatter:off
        String record = "{\n" +
            "  \"id\": 3,\n" +
            "  \"job_no\": \"EMP001\",\n" +
            "  \"name\": \"张三\",\n" +
            "  \"english_name\": \"John Zhang\",\n" +
            "  \"job\": \"软件工程师\",\n" +
            "  \"sex\": 1,\n" +
            "  \"age\": 30,\n" +
            "  \"salary\": 15000.50,\n" +
            "  \"job_day\": \"2023-05-15 09:30:00\",\n" +
            "  \"remark\": \"优秀员工\"\n" +
            "}\n";
        // @formatter:on
        final JSONObject jsonObject = JSONObject.parseObject(record);
        final boolean res = esCommonOperation.insert(jsonObject, indexName, jsonObject.getLong("id").toString());
        assertTrue(res);
    }

    @Test
    void bulkInsert_test() throws ParseException {
        final List<Object> list = new ArrayList<>();
        String dateString = "2024-12-23 09:30:00";
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        Date date = dateFormat.parse(dateString);

        list.add(new EmployeeIndexDo(4L, "2010", "小白", "xiaohong", "Python", 1, 21, new BigDecimal("13000.01"),
                date, "备注"));
        list.add(new EmployeeIndexDo(5L, "2010", "小小白", "xiaohong", "Python", 1, 21, new BigDecimal("13000.01"),
                date, "备注"));

        esCommonOperation.bulkInsert(indexName, list, "id");
    }

    @Test
    void searchWithFilters_test() {
        // @formatter:off
        final List<String> returnFields = Arrays.asList("id", "job_no", "name", "english_name");
        // @formatter:on

        List<QueryFilter> termFieldFilters = new ArrayList<>();
        termFieldFilters.add(new QueryFilter("id", "1"));

        System.out.println(esCommonOperation.searchWithFilters(indexName, returnFields, termFieldFilters));
    }

    @Test
    void searchWithFilters_test_1() {
        // @formatter:off
        final List<String> returnFields = Arrays.asList("id", "job_no", "name", "english_name");
        // @formatter:on

        // 精确匹配
        List<QueryFilter> termFieldFilters = new ArrayList<>();
        termFieldFilters.add(new QueryFilter("job.keyword", "Python"));

        final ArrayList<QueryFilter> matchFieldFilters = new ArrayList<>();
        matchFieldFilters.add(new QueryFilter("name", "天天"));

        System.out.println(
                esCommonOperation.searchWithFilters(indexName, returnFields, termFieldFilters, matchFieldFilters)
        );
    }

    @Test
    void queryBetweenTimeStampAndKeyword() {
        String startTime = "2025-03-01 17:41:00";
        String endTime = "2025-03-01 17:43:00";
        final List<String> returnFields = Arrays.asList("id", "job_no", "name", "english_name", "job_day");
        final List<JsonNode> list =
                esCommonOperation.queryBetweenTimeStampAndKeyword(indexName, "job_day", startTime, endTime,
                        new ArrayList<>(), returnFields);
        System.out.println(list);
    }

    @Test
    void updateById() {
        String json = "{\n" +
                "    \"id\": 3,\n" +
                "    \"name\": \"小天\",\n" +
                "    \"job\": \"Python\",\n" +
                "    \"sex\": 1,\n" +
                "    \"age\": 20,\n" +
                "    \"salary\": 13000.01,\n" +
                "    \"remark\": \"备注111\",\n" +
                "    \"job_no\": \"2010\",\n" +
                "    \"english_name\": \"dashi\",\n" +
                "    \"job_day\": \"2024-12-23 09:30:00\"\n" +
                "}";
        System.out.println(esCommonOperation.updateById(indexName, "3", JSONObject.parseObject(json)));
    }

    @Test
    void batchUpdateById() {
        String json = "[\n" +
                "    {\n" +
                "        \"id\": 5,\n" +
                "        \"name\": \"小天\",\n" +
                "        \"job\": \"Python\",\n" +
                "        \"sex\": 1,\n" +
                "        \"age\": 20,\n" +
                "        \"salary\": 100.01,\n" +
                "        \"remark\": \"备注\",\n" +
                "        \"job_no\": \"2010\",\n" +
                "        \"english_name\": \"dashi\",\n" +
                "        \"job_day\": \"2024-12-23 09:30:00\"\n" +
                "    },\n" +
                "    {\n" +
                "        \"id\": 3,\n" +
                "        \"name\": \"小红\",\n" +
                "        \"job\": \"Python\",\n" +
                "        \"sex\": 1,\n" +
                "        \"age\": 20,\n" +
                "        \"salary\": 100.01,\n" +
                "        \"remark\": \"备注\",\n" +
                "        \"job_no\": \"2010\",\n" +
                "        \"english_name\": \"xiaohong\",\n" +
                "        \"job_day\": \"2024-12-23 09:30:00\"\n" +
                "    }\n" +
                "]";

        Map<String, JSONObject> records = new HashMap<>();
        final JSONArray array = JSONArray.parse(json);
        array.forEach(item -> {
            JSONObject jsonObject = (JSONObject) item;
            records.put(jsonObject.getInteger("id").toString(), jsonObject);
        });

        System.out.println(esCommonOperation.batchUpdateById(indexName, records));
    }

    @Test
    void batchDelete() {
        List<String> ids = Collections.singletonList("1");
        System.out.println(esCommonOperation.batchDelete(indexName, ids));
    }

    /**
     * 测试通过传入 query 来批量删除满足条件的记录
     */
    @Test
    void batchDelete_use_query() {
        List<QueryFilter> fieldFilters = new ArrayList<>();
        // remark 的类型是 text + keyword
        fieldFilters.add(new QueryFilter("remark", "测试"));

        final ArrayList<Query> conditions = new ArrayList<>();
        for (QueryFilter filter : fieldFilters) {
            conditions.add(
                    new Query.Builder()
                            .match(new MatchQuery.Builder().field(filter.getFieldName()).query(esCommonOperation.getFieldValue(filter.getValue())).build())
                            .build()
            );
        }
        final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        for (Query condition : conditions) {
            boolQueryBuilder.must(condition);
        }

        final Query query = new Query.Builder().bool(
                boolQueryBuilder.build()
        ).build();

        System.out.println(esCommonOperation.batchDelete(indexName, query));
    }

    /**
     * 按照 id 更新部分字段
     */
    @Test
    void updatePartialById() {
        final Map<String, Object> needUpdateFieldWithValue = new HashMap<>();
        needUpdateFieldWithValue.put("name", "小白");

        esCommonOperation.updatePartialById(indexName, "2", needUpdateFieldWithValue);
    }

    @Test
    void batchDeleteByIds() {
        List<String> ids = Arrays.asList("3", "5");
        System.out.println(esCommonOperation.batchDeleteByIds(indexName, ids));
    }

    /**
     * 所有参数全部传值：
     * - 索引：employee
     * - 时间字段：job_day
     * - 开始时间：2024-01-01T00:00:00.000Z
     * - 结束时间：2025-12-31T23:59:59.999Z
     * - 去重字段：name
     * - term 过滤：job = "Java"，sex = 1
     * - match 过滤：remark 包含 "开发"
     */
    @Test
    void testGetUniqueItemNm_AllParams() {
        // ---------- 1. 准备参数 ---------
        String timeFiled = "job_day";
        Object startTime = "2024-01-01 00:00:00";
        Object endTime = "2025-12-31 23:59:59";
        String distinctFiledName = "name";

        // term 精确过滤
        Map<String, Object> termFilters = new HashMap<>();
        // 注意：sex 是 integer，传 Integer，不要传 "1"
        termFilters.put("sex", 1);

        // match 全文过滤
        Map<String, Object> matchFilters = new HashMap<>();
        matchFilters.put("job", "Java");
        matchFilters.put("remark", "开发");

        // ---------- 2. 调用 ----------
        long uv = esCommonOperation.getUniqueItemNm(
                indexName,
                timeFiled,
                startTime,
                endTime,
                distinctFiledName,
                termFilters,
                matchFilters);

        // ---------- 3. 打印 + 断言 ----------
        System.out.println("========================================");
        System.out.println("索引: " + indexName);
        System.out.println("时间字段: " + timeFiled);
        System.out.println("时间范围: [" + startTime + " ~ " + endTime + "]");
        System.out.println("去重字段: " + distinctFiledName);
        System.out.println("term 过滤: " + termFilters);
        System.out.println("match 过滤: " + matchFilters);
        System.out.println("去重结果 (UV): " + uv);
        System.out.println("========================================");

        // 按我们导入的 20 条数据：
        // job=Java 且 sex=1 且 job_day 在 2024~2025 且 remark 含"开发"的员工
        // → 只有 "张三"（job=Java, sex=1, job_day=2025-01-15, remark=资深开发）
        assertEquals(1L, uv, "应只有 1 个不同的 name");
    }
}
