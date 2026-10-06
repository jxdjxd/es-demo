package com.jxd;

import com.alibaba.fastjson2.JSONObject;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.*;

/**
 * @author jxd
 * {@code @date} 2024/12/26 20:51
 */
@SpringBootTest
class EsCommonOperationTest {
    @Resource
    private EsCommonOperation esCommonOperation;

    public String indexName = "employee";

    @Test
    void insert() {
        String json = "{\n" +
                "    \"id\": 30,\n" +
                "    \"name\": \"tom\",\n" +
                "    \"job\": \"Scala\",\n" +
                "    \"sex\": 1,\n" +
                "    \"age\": 21,\n" +
                "    \"remark\": \"备注\",\n" +
                "    \"job_no\": \"2010\",\n" +
                "    \"english_name\": \"tom\",\n" +
                "    \"job_day\": \"2024-12-11 09:20:02\"\n" +
                "}";
        final JSONObject jsonObject = JSONObject.parseObject(json);
        System.out.println(esCommonOperation.insert(indexName, jsonObject, "id"));
    }

    @Test
    void batchInsert() {
        String json = "{\n" +
                "    \"id\": 3,\n" +
                "    \"name\": \"小天\",\n" +
                "    \"job\": \"Python\",\n" +
                "    \"sex\": 1,\n" +
                "    \"age\": 21,\n" +
                "    \"salary\": 13000.01,\n" +
                "    \"remark\": \"备注\",\n" +
                "    \"job_no\": \"2010\",\n" +
                "    \"english_name\": \"xiaohong\",\n" +
                "    \"job_day\": \"2024-12-23 09:30:00\"\n" +
                "}";
        String json1 = "{\n" +
                "    \"id\": 4,\n" +
                "    \"name\": \"小白\",\n" +
                "    \"job\": \"Python\",\n" +
                "    \"sex\": 1,\n" +
                "    \"age\": 21,\n" +
                "    \"salary\": 13000.01,\n" +
                "    \"remark\": \"备注\",\n" +
                "    \"job_no\": \"2010\",\n" +
                "    \"english_name\": \"xiaohong\",\n" +
                "    \"job_day\": \"2024-12-23 09:30:00\"\n" +
                "}";
        final JSONObject jsonObject = JSONObject.parseObject(json);
        final JSONObject jsonObject1 = JSONObject.parseObject(json1);
        List<JSONObject> list = new ArrayList<>();
        list.add(jsonObject);
        list.add(jsonObject1);

        System.out.println(esCommonOperation.batchInsert(indexName, list, "id"));
    }

    @Test
    void queryByParams() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "天");
        params.put("job", "Python");

        final List<SearchHit> searchHits = esCommonOperation.queryByParams(indexName, params, null);
        for (SearchHit hit : searchHits) {
            String json = hit.getSourceAsString();
            System.out.println(json);
        }
    }

    @Test
    void queryByParamsAndMustExistFields_normal_case() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "白");
        params.put("job", "Python");
        // 必须有 salary 字段
        List<String> mustExistFields = Collections.singletonList("salary");
        final List<SearchHit> searchHits = esCommonOperation.queryByParamsAndMustExistFields(indexName, params, null,
                mustExistFields);
        for (SearchHit hit : searchHits) {
            String json = hit.getSourceAsString();
            System.out.println(json);
        }
    }

    @Test
    void queryByTermParams() {
        Map<String, List<Object>> inListTermsParams = new HashMap<>();
        final List<Object> englishNames = new ArrayList<>();
        englishNames.add("xiaokeai");
        englishNames.add("xiaomi");
        inListTermsParams.put("english_name", englishNames);

        final List<SearchHit> searchHits = esCommonOperation.queryByTermParams(indexName, null, inListTermsParams,
                null);
        for (SearchHit hit : searchHits) {
            String json = hit.getSourceAsString();
            System.out.println(json);
        }
    }

    @Test
    void queryBetweenTimeStampAndKeyword() {
        // @formatter:off
        final  List<SearchHit> searchHits = esCommonOperation.queryBetweenTimeStampAndKeyword(indexName, "job_day", "2024-12-10 08:30:00", "2024-12-11 09:30:00", null, null);
        // @formatter:on
        for (SearchHit hit : searchHits) {
            String json = hit.getSourceAsString();
            System.out.println(json);
        }
    }

    @Test
    void queryBySourceBuilder() {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(QueryBuilders.termQuery("job",
                "Scala"));
        sourceBuilder.query(boolQueryBuilder);
        final List<SearchHit> searchHits = esCommonOperation.queryBySourceBuilder(indexName, sourceBuilder);
        for (SearchHit hit : searchHits) {
            String json = hit.getSourceAsString();
            System.out.println(json);
        }
    }

    @Test
    void updateById() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "3");
        data.put("name", "小明");
        data.put("job", "Kotlin");
        data.put("sex", 1);
        data.put("age", 21);
        data.put("salary", 13002.11);
        data.put("remark", "备注");
        data.put("job_no", "2010");
        data.put("english_name", "xiaoming");
        data.put("job_day", "2024-01-23 09:30:00");
        System.out.println(esCommonOperation.updateById(indexName, "3", data));
    }

    @Test
    void batchUpdate() {
        List<Map<String, Object>> dataList = new ArrayList<>();

        Map<String, Object> data = new HashMap<>();
        data.put("id", "3");
        data.put("name", "小明");
        data.put("job", "Kotlin");
        data.put("sex", 1);
        data.put("age", 21);
        data.put("salary", 13002.11);
        data.put("remark", "备注");
        data.put("job_no", "2010");
        data.put("english_name", "xiaoming");
        data.put("job_day", "2024-01-23 09:30:00");

        Map<String, Object> data1 = new HashMap<>();
        data1.put("id", "4");
        data1.put("name", "小坤");
        data1.put("job", "Go");
        data1.put("sex", 1);
        data1.put("age", 21);
        data1.put("salary", 102.11);
        data1.put("remark", "备注");
        data1.put("job_no", "2010");
        data1.put("english_name", "xiaokun");
        data1.put("job_day", "2024-02-23 09:30:00");

        dataList.add(data);
        dataList.add(data1);
        System.out.println(esCommonOperation.batchUpdate(indexName, dataList, "id"));
    }

    @Test
    void batchDelete() {
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery().must(QueryBuilders.termQuery("job", "Go"));
        System.out.println(esCommonOperation.batchDelete(indexName, boolQueryBuilder));
    }

    @Test
    void batchDeleteByIds() {
        System.out.println(esCommonOperation.batchDeleteByIds(indexName, Arrays.asList("11", "13")));
    }

    @Test
    void getUniqueItemNm() {
        System.out.println(
                esCommonOperation.getUniqueItemNm(indexName, "job_day", "2024-12-10 08:30:00", "2024-12-11 09:30:00",
                        "job", null, null)
        );
    }
}

