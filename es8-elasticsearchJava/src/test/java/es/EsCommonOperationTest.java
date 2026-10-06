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
        matchFieldFilters.add(new QueryFilter("name", "天"));

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
        fieldFilters.add(new QueryFilter("name", "白"));

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
}
