package es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.bulk.DeleteOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.SourceConfig;
import co.elastic.clients.elasticsearch.core.search.SourceFilter;
import co.elastic.clients.json.JsonData;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import es.bean.QueryFilter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * es 通用操作
 *
 * @author jxd
 * {@code @date} 2024/12/22 21:57
 */
@Slf4j
@Service
@SuppressWarnings("unused")
public class EsCommonOperation {

    private ElasticsearchClient elasticsearchClient;

    @Autowired
    private void setElasticsearchClient(ElasticsearchClient elasticsearchClient) {
        this.elasticsearchClient = elasticsearchClient;
    }

    /**
     * 插入数据
     *
     * @param record    数据
     * @param indexName 索引名称
     * @param id        id,传 null 就是让 es 自动生成 _id 的值
     * @return 是否成功
     */
    public boolean insert(JSONObject record, String indexName, @Nullable String id) {
        if (record == null) {
            return false;
        }

        try {
            IndexResponse response;
            if (StringUtils.isNotBlank(id)) {
                response =
                        elasticsearchClient.index(builder -> builder.index(indexName).id(id).document(JsonData.fromJson(record.toJSONString())));
            } else {
                response =
                        elasticsearchClient.index(builder -> builder.index(indexName).document(JsonData.fromJson(record.toJSONString())));
            }

            // 如果指定的 id 的记录在数据库中存在,这里返回的是 Updated
            if (response.result() == Result.Created || response.result() == Result.Updated) {
                return true;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        }

        return false;
    }

    /**
     * 批量插入数据到 Elasticsearch
     *
     * @param indexName   索引名称
     * @param documents   要插入的文档列表,每个文档为 Map 或 POJO
     * @param idFieldName id 所在的字段名,传 null 就是让 es 自动生成 _id 的值
     */
    public boolean bulkInsert(String indexName, List<Object> documents, @Nullable String idFieldName) {
        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Object document : documents) {
                bulkBuilder.operations(op -> {
                            if (StringUtils.isNotBlank(idFieldName)) {
                                Object idValue;
                                try {
                                    Field field = document.getClass().getDeclaredField(idFieldName);
                                    field.setAccessible(true);
                                    idValue = field.get(document);
                                } catch (IllegalAccessException | NoSuchFieldException e) {
                                    throw new RuntimeException(e);
                                }
                                return op.index(
                                        idx -> idx
                                                .index(indexName)
                                                .id(idValue.toString())
                                                // 插入文档（可以是 POJO 或 Map）
                                                .document(document)
                                );
                            } else {
                                return op.index(
                                        idx -> idx
                                                .index(indexName)
                                                .document(document)
                                );
                            }
                        }
                );
            }

            // 执行 BulkRequest
            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());

            if (response.errors()) {
                response.items().forEach(item -> {
                    if (item.error() != null) {
                        log.error("Error: {}", item.error().reason());
                    }
                });
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        }

        return false;
    }

    /**
     * 查询 Elasticsearch,设置多个筛选条件和字段列表
     *
     * @param indexName    索引名称
     * @param fieldFilters 字段过滤条件,键为字段名,值为查询值
     * @param returnFields 指定返回的字段列表
     * @return 查询结果
     */
    public List<JsonNode> searchWithFilters(String indexName, List<String> returnFields,
                                            List<QueryFilter> fieldFilters) {
        List<JsonNode> allResults = new ArrayList<>();
        int from = 0;
        int size = 10_000;
        boolean hasMoreResults = true;

        final ArrayList<Query> conditions = new ArrayList<>();
        for (QueryFilter filter : fieldFilters) {
            conditions.add(
                    new Query.Builder()
                            .term(new TermQuery.Builder().field(filter.getFieldName()).value(getFieldValue(filter.getValue())).build())
                            .build()
            );
        }
        final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        for (Query condition : conditions) {
            boolQueryBuilder.must(condition);
        }

        while (hasMoreResults) {
            final SourceFilter sourceFilter = new SourceFilter.Builder().includes(returnFields).build();
            SourceConfig sourceConfig = new SourceConfig.Builder().filter(sourceFilter).build();

            SearchRequest request = new SearchRequest.Builder()
                    .index(indexName)
                    .query(new Query.Builder().bool(boolQueryBuilder.build()).build())
                    .source(sourceConfig)
                    .from(from)
                    .size(size)
                    .build();

            SearchResponse<JsonNode> response;
            try {
                response = elasticsearchClient.search(request, JsonNode.class);
            } catch (IOException e) {
                log.error(e.getMessage());
                log.error(Arrays.toString(e.getStackTrace()));
                break;
            }

            if (Objects.nonNull(response)) {
                final List<JsonNode> pageResults =
                        response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
                allResults.addAll(pageResults);

                // 检查是否还有更多结果
                hasMoreResults = response.hits().hits().size() == size;
                // 更新分页起始位置
                from += size;
            } else {
                hasMoreResults = false;
            }
        }

        return allResults;
    }

    /**
     * 查询 Elasticsearch,设置多个筛选条件和字段列表,termFieldFilters 和 matchFieldFilter 是且的关系
     *
     * @param indexName        索引名称
     * @param termFieldFilters 字段过滤条件,键为字段名,值为查询值,精确匹配,建议使用 field_name.keyword 这种格式的精确匹配
     * @param matchFieldFilter 字段过滤条件,键为字段名,值为查询值,模糊匹配
     * @param returnFields     指定返回的字段列表
     * @return 查询结果
     */
    public List<JsonNode> searchWithFilters(String indexName, List<String> returnFields,
                                            List<QueryFilter> termFieldFilters, List<QueryFilter> matchFieldFilter) {
        List<JsonNode> allResults = new ArrayList<>();
        int from = 0;
        int size = 10_000;
        boolean hasMoreResults = true;

        final ArrayList<Query> conditions = new ArrayList<>();
        for (QueryFilter filter : termFieldFilters) {
            conditions.add(
                    new Query.Builder()
                            .term(
                                    new TermQuery.Builder()
                                            .field(filter.getFieldName())
                                            .value(getFieldValue(filter.getValue()))
                                            .build()
                            )
                            .build()
            );
        }
        for (QueryFilter filter : matchFieldFilter) {
            conditions.add(
                    new Query.Builder()
                            .match(
                                    new MatchQuery.Builder()
                                            .field(filter.getFieldName())
                                            .query(getFieldValue(filter.getValue()))
                                            .build()
                            )
                            .build()
            );
        }
        BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        for (Query condition : conditions) {
            boolQueryBuilder.must(condition);
        }

        while (hasMoreResults) {
            final SourceFilter sourceFilter = new SourceFilter.Builder().includes(returnFields).build();
            SourceConfig sourceConfig = new SourceConfig.Builder().filter(sourceFilter).build();

            SearchRequest request = new SearchRequest.Builder()
                    .index(indexName)
                    .query(new Query.Builder().bool(boolQueryBuilder.build()).build())
                    .source(sourceConfig)
                    .from(from)
                    .size(size)
                    .build();

            SearchResponse<JsonNode> response;
            try {
                response = elasticsearchClient.search(request, JsonNode.class);
            } catch (IOException e) {
                log.error(e.getMessage());
                log.error(Arrays.toString(e.getStackTrace()));
                break;
            }

            if (Objects.nonNull(response)) {
                final List<JsonNode> pageResults =
                        response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
                allResults.addAll(pageResults);

                // 检查是否还有更多结果
                hasMoreResults = response.hits().hits().size() == size;
                // 更新分页起始位置
                from += size;
            } else {
                hasMoreResults = false;
            }
        }

        return allResults;
    }

    /**
     * 根据不同类型的值返回适当的 FieldValue
     *
     * @param value 值
     * @return FieldValue
     */
    public FieldValue getFieldValue(Object value) {
        if (value instanceof String) {
            return FieldValue.of((String) value);
        } else if (value instanceof Integer) {
            return FieldValue.of((Integer) value);
        } else if (value instanceof Long) {
            return FieldValue.of((Long) value);
        } else if (value instanceof Boolean) {
            return FieldValue.of((Boolean) value);
        } else if (value instanceof Double) {
            return FieldValue.of((Double) value);
        } else {
            throw new IllegalArgumentException("Unsupported type for term query: " + value.getClass());
        }
    }

    /**
     * 查询 es,支持全文搜索
     *
     * @param indexName      索引名称
     * @param startTimeStamp 开始日期,包含
     * @param endTimeStamp   结束日期,包含
     * @return 查询结果
     */
    public List<JsonNode> queryBetweenTimeStampAndKeyword(String indexName, String timeRangeField,
                                                          String startTimeStamp, String endTimeStamp,
                                                          List<QueryFilter> fieldFilters, List<String> returnFields) {
        List<JsonNode> allResults = new ArrayList<>();
        int from = 0;
        int size = 10_000;
        boolean hasMoreResults = true;

        final ArrayList<Query> conditions = new ArrayList<>();
        for (QueryFilter filter : fieldFilters) {
            conditions.add(
                    new Query.Builder()
                            .term(new TermQuery.Builder().field(filter.getFieldName()).value(getFieldValue(filter.getValue())).build())
                            .build()
            );
        }
        final BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();
        for (Query condition : conditions) {
            boolQueryBuilder.must(condition);
        }

        // 将时间范围查询添加到 boolQueryBuilder 中
        final RangeQuery rangeQuery = new RangeQuery.Builder().field(timeRangeField)
                .gte(JsonData.of(startTimeStamp))
                .lte(JsonData.of(endTimeStamp)).build();
        boolQueryBuilder.must(new Query.Builder().range(rangeQuery).build());

        while (hasMoreResults) {
            final SourceFilter sourceFilter = new SourceFilter.Builder().includes(returnFields).build();
            SourceConfig sourceConfig = new SourceConfig.Builder().filter(sourceFilter).build();

            SearchRequest request = new SearchRequest.Builder()
                    .index(indexName)
                    .query(
                            new Query.Builder().bool(
                                    boolQueryBuilder.build()
                            ).build()
                    )
                    .source(sourceConfig)
                    .from(from)
                    .size(size)
                    .build();

            SearchResponse<JsonNode> response;
            try {
                response = elasticsearchClient.search(request, JsonNode.class);
            } catch (IOException e) {
                log.error(e.getMessage());
                log.error(Arrays.toString(e.getStackTrace()));
                break;
            }

            if (Objects.nonNull(response)) {
                final List<JsonNode> pageResults =
                        response.hits().hits().stream().map(Hit::source).collect(Collectors.toList());
                allResults.addAll(pageResults);

                // 检查是否还有更多结果
                hasMoreResults = response.hits().hits().size() == size;
                // 更新分页起始位置
                from += size;
            } else {
                hasMoreResults = false;
            }
        }

        return allResults;
    }

    /**
     * 根据 id 更新
     *
     * @param indexName 索引名称
     * @param id        id
     * @param record    更新的数据
     * @return 是否更新成功
     */
    public boolean updateById(String indexName, @Nonnull String id, JSONObject record) throws RuntimeException {
        if (StringUtils.isBlank(id)) {
            throw new RuntimeException("id 不能为空");
        }
        UpdateRequest<String, JsonData> updateRequest =
                new UpdateRequest.Builder<String, JsonData>()
                        .index(indexName)
                        .id(id)
                        .doc(JsonData.of(record))
                        .build();

        UpdateResponse<String> response;
        try {
            response = elasticsearchClient.update(updateRequest, JsonData.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (response.result() == Result.Updated) {
            return true;
        } else {
            log.error("文档未更,结果状态: {}", response.result());
            return false;
        }
    }

    /**
     * 根据 id 更新
     *
     * @param indexName 索引名称
     * @param records   更新的数据, key 是 id, value 是数据
     * @return 是否更新成功
     */
    public boolean batchUpdateById(String indexName, Map<String, JSONObject> records) {
        BulkRequest.Builder br = new BulkRequest.Builder();

        records.forEach((id, record) -> br.operations(op -> op
                .index(idx -> idx
                        .index(indexName)
                        .id(id)
                        .document(record)
                )
        ));

        BulkResponse result;
        try {
            result = elasticsearchClient.bulk(br.build());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (result.errors()) {
            for (BulkResponseItem item : result.items()) {
                if (item.error() != null) {
                    log.error(item.error().reason());
                }
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * 部分更新数据
     *
     * @param indexName      索引名称
     * @param id             id
     * @param fieldsToUpdate 需要更新的字段及其值
     */
    public boolean updatePartialById(String indexName, String id, Map<String, Object> fieldsToUpdate) {
        try {
            // 构建 UpdateRequest
            UpdateRequest<Object, Map<String, Object>> updateRequest = UpdateRequest.of(builder ->
                    builder.index(indexName)
                            .id(id)
                            .doc(fieldsToUpdate)
            );

            // 执行更新
            UpdateResponse<Object> response = elasticsearchClient.update(updateRequest, Map.class);
            final String result = response.result().jsonValue();
            return "updated".equals(result) || "noop".equals(result);

        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        }

        return false;
    }

    /**
     * 批量删除
     *
     * @param indexName 索引名称
     * @param ids       id 列表
     * @return 是否删除成功
     */
    public boolean batchDelete(String indexName, List<String> ids) {
        if (StringUtils.isBlank(indexName)) {
            throw new RuntimeException("索引名称不能为空");
        }
        if (Objects.isNull(ids) || ids.isEmpty()) {
            return true;
        }
        try {
            // 创建批量删除请求
            BulkRequest.Builder bulkRequestBuilder = new BulkRequest.Builder();
            for (String id : ids) {
                final DeleteOperation deleteOperation = new DeleteOperation.Builder()
                        .id(id)
                        .index(indexName)
                        .build();
                bulkRequestBuilder.operations(op -> op.delete(deleteOperation));
            }

            // 执行批量删除操作
            BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequestBuilder.build());

            // 检查批量删除结果
            if (bulkResponse.errors()) {
                bulkResponse.items().forEach(item -> {
                    if (item.error() != null) {
                        log.error(item.error().reason());
                    }
                });
                return false;
            } else {
                return true;
            }
        } catch (IOException e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
            return false;
        }
    }

    /**
     * 根据查询批量删除
     *
     * @param indexName 索引名称
     * @param query     查询
     * @return 是否删除成功
     */
    public boolean batchDelete(String indexName, Query query) {
        // 查询出来的所有的 _id
        List<String> allResults = new ArrayList<>();
        int from = 0;
        int size = 10_000;
        boolean hasMoreResults = true;

        while (hasMoreResults) {
            final SourceFilter sourceFilter =
                    new SourceFilter.Builder().includes(Collections.singletonList("_id")).build();
            SourceConfig sourceConfig = new SourceConfig.Builder().filter(sourceFilter).build();

            SearchRequest request = new SearchRequest.Builder()
                    .index(indexName)
                    .query(query)
                    .source(sourceConfig)
                    .from(from)
                    .size(size)
                    .build();

            SearchResponse<JsonNode> response;
            try {
                response = elasticsearchClient.search(request, JsonNode.class);
            } catch (IOException e) {
                log.error(e.getMessage());
                log.error(Arrays.toString(e.getStackTrace()));
                break;
            }

            if (Objects.nonNull(response)) {
                final List<String> pageResults =
                        response.hits().hits().stream().map(Hit::id).collect(Collectors.toList());
                allResults.addAll(pageResults);

                // 检查是否还有更多结果
                hasMoreResults = response.hits().hits().size() == size;
                // 更新分页起始位置
                from += size;
            } else {
                hasMoreResults = false;
            }
        }

        return batchDelete(indexName, allResults);
    }
}
