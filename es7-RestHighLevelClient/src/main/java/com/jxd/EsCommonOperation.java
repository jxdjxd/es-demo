package com.jxd;

import co.elastic.clients.elasticsearch.indices.DeleteIndexResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.core.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.DeleteByQueryRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.Cardinality;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;

/**
 * @author jxd
 * {@code @date} 2024/12/25 23:09
 */
@Service
@Slf4j
@SuppressWarnings("deprecation")
public class EsCommonOperation {
    // 分页查询滚动时间,单位为毫秒
    final long scrollTimeoutInMillis = 60_000;
    // 分页查询每页大小
    final int batchSearchPageSize = 10_000;

    private RestHighLevelClient restHighLevelClient;

    @Autowired
    public void setRestHighLevelClient(RestHighLevelClient restHighLevelClient) {
        this.restHighLevelClient = restHighLevelClient;
    }

    public void closeRestHighLevelClient() {
        if (restHighLevelClient != null) {
            try {
                restHighLevelClient.close();
            } catch (IOException e) {
                log.error(e.getMessage());
                log.error(Arrays.toString(e.getStackTrace()));
            }
        }
    }

    public SearchRequest getSearchReqByIndexName(@Nonnull String indexName) {
        return new SearchRequest(indexName);
    }

    /**
     * 查看集群状态，类似于 GET /_cluster/health?pretty
     */
    public void getClusterHealth() throws IOException {
        ClusterHealthRequest request = new ClusterHealthRequest();

        ClusterHealthResponse response =
                restHighLevelClient.cluster().health(request, RequestOptions.DEFAULT);

        System.out.println(JSON.toJSONString(response));
    }

    /**
     * 插入数据
     *
     * @param indexName   索引名称
     * @param record      数据
     * @param idFieldName id 所在的字段的字段名,如果为空,使用 es 自动生成的 _id
     * @return 是否插入成功
     */
    public boolean insert(String indexName, JSONObject record, @Nullable String idFieldName) {
        if (record == null) {
            return false;
        }

        IndexRequest request;
        if (StringUtils.isNotBlank(idFieldName)) {
            request = new IndexRequest(indexName, "_doc")
                    .id(record.getString(idFieldName))
                    .source(record.toJSONString(), XContentType.JSON);
        } else {
            request = new IndexRequest(indexName, "_doc")
                    .source(record.toJSONString(), XContentType.JSON);
        }

        try {
            IndexResponse response = restHighLevelClient.index(request, RequestOptions.DEFAULT);
            RestStatus status = response.status();
            return status == RestStatus.CREATED;
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }
        return false;
    }

    /**
     * @param indexName   索引名称
     * @param records     批量插入的记录
     * @param idFieldName id 所在的字段的字段名,如果为空,使用 es 自动生成的 _id
     * @return 批量插入是否成功
     */
    public boolean batchInsert(String indexName, List<JSONObject> records, @Nullable String idFieldName) {
        if (CollectionUtils.isEmpty(records)) {
            return false;
        }

        BulkRequest bulkRequest = new BulkRequest();
        if (StringUtils.isNotBlank(idFieldName)) {
            for (JSONObject record : records) {

                final String id = record.getString(idFieldName);
                bulkRequest.add(new IndexRequest(indexName, "_doc").id(id).source(record.toJSONString(),
                        XContentType.JSON));
            }
        } else {
            for (JSONObject record : records) {
                bulkRequest.add(new IndexRequest(indexName, "_doc").source(record.toJSONString(),
                        XContentType.JSON));
            }
        }

        try {
            BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            // 检查是否有失败的请求
            if (bulkResponse.hasFailures()) {
                // 处理失败的请求
                for (BulkItemResponse itemResponse : bulkResponse.getItems()) {
                    if (itemResponse.isFailed()) {
                        log.error("Failed to insert document: {}", itemResponse.getFailureMessage());
                    }
                }
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }
        return false;
    }

    /**
     * 条件查询(全文检索),会返回所有符合的数据
     *
     * @param indexName    索引名称
     * @param params       查询条件,全文检索
     * @param fetchSources 获取字段
     * @return 查询结果
     */
    public List<SearchHit> queryByParams(String indexName, @Nullable Map<String, Object> params,
                                         @Nullable String[] fetchSources) {
        if (StringUtils.isBlank(indexName)) {
            return new ArrayList<>();
        }

        List<SearchHit> allHits = new ArrayList<>();

        try {
            // 初始化查询请求
            SearchRequest searchRequest = getSearchReqByIndexName(indexName);
            // 查询所有数据
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().size(batchSearchPageSize);
            final BoolQueryBuilder[] boolQuery = {QueryBuilders.boolQuery()};
            if (!MapUtils.isEmpty(params)) {
                params.forEach((key, value) -> boolQuery[0] = boolQuery[0].must(QueryBuilders.termQuery(key, value)));
            }
            if (Objects.nonNull(fetchSources) && fetchSources.length > 0) {
                sourceBuilder.fetchSource(fetchSources, null);
            }
            searchRequest.source(sourceBuilder);
            if (!MapUtils.isEmpty(params)) {
                sourceBuilder.query(boolQuery[0]);
            }
            searchRequest.scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis));

            // 执行初始查询
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            // 获取滚动 ID 和初始结果
            String scrollId = searchResponse.getScrollId();
            SearchHit[] hits = searchResponse.getHits().getHits();

            // 收集查询结果
            while (hits != null && hits.length > 0) {
                allHits.addAll(Arrays.asList(hits));

                // 使用滚动 ID 获取下一页
                searchResponse =
                        restHighLevelClient.scroll(new org.elasticsearch.action.search.SearchScrollRequest(scrollId)
                                .scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis)), RequestOptions.DEFAULT);

                // 更新滚动 ID 和结果
                scrollId = searchResponse.getScrollId();
                hits = searchResponse.getHits().getHits();
            }

            // 清除滚动上下文
            final ClearScrollRequest req = new ClearScrollRequest();
            req.addScrollId(scrollId);
            restHighLevelClient.clearScroll(req, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }

        return allHits;
    }

    /**
     * 查询 es 中的记录,可以设置过滤条件,设置返回的字段,设置必须包含哪些字段,会返回所有符合的数据
     *
     * @param indexName       索引名
     * @param params          查询限制条件,全文检索
     * @param fetchSources    设置返回哪些字段
     * @param mustExistFields 设置查询的数据必须包含哪些字段
     * @return 查询结果
     */
    public List<SearchHit> queryByParamsAndMustExistFields(String indexName, Map<String, Object> params,
                                                           String[] fetchSources,
                                                           List<String> mustExistFields) {
        List<SearchHit> allHits = new ArrayList<>();

        try {
            SearchRequest searchRequest = new SearchRequest(indexName);

            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().size(batchSearchPageSize);
            final BoolQueryBuilder[] boolQuery = {QueryBuilders.boolQuery()};
            if (!MapUtils.isEmpty(params)) {
                params.forEach((key, value) -> boolQuery[0] = boolQuery[0].must(QueryBuilders.termQuery(key, value)));
            }
            // 限制必须包含哪些字段
            if (Objects.nonNull(mustExistFields) && !mustExistFields.isEmpty()) {
                for (String mustExistField : mustExistFields) {
                    boolQuery[0].must(QueryBuilders.existsQuery(mustExistField));
                }
            }
            sourceBuilder.query(boolQuery[0]);
            if (fetchSources != null) {
                sourceBuilder.fetchSource(fetchSources, null);
            }

            searchRequest.source(sourceBuilder);
            searchRequest.scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis));

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            String scrollId = response.getScrollId();
            SearchHit[] hits = response.getHits().getHits();

            // 收集查询结果
            while (hits != null && hits.length > 0) {
                allHits.addAll(Arrays.asList(hits));

                // 使用滚动 ID 获取下一页
                response =
                        restHighLevelClient.scroll(new org.elasticsearch.action.search.SearchScrollRequest(scrollId)
                                .scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis)), RequestOptions.DEFAULT);

                // 更新滚动 ID 和结果
                scrollId = response.getScrollId();
                hits = response.getHits().getHits();
            }

            // 清除滚动上下文
            final ClearScrollRequest req = new ClearScrollRequest();
            req.addScrollId(scrollId);
            restHighLevelClient.clearScroll(req, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }

        return allHits;
    }

    /**
     * 查询 es 中的记录,可以设置过滤条件,设置返回的字段,设置字段值必须在列表中,会返回所有符合的数据
     *
     * @param indexName         索引名
     * @param termParams        查询条件(精确搜索)
     * @param inListTermsParams 查询条件(精确搜索),查询值是列表(值是否在 list 中)
     * @param fetchSources      设置返回哪些字段
     * @return 查询结果
     */
    public List<SearchHit> queryByTermParams(String indexName, @Nullable Map<String, Object> termParams,
                                             @Nullable Map<String, List<Object>> inListTermsParams,
                                             @Nullable String[] fetchSources) {
        if (StringUtils.isBlank(indexName)) {
            return new ArrayList<>();
        }

        List<SearchHit> allHits = new ArrayList<>();

        try {
            SearchRequest searchRequest = new SearchRequest(indexName);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().size(batchSearchPageSize);
            BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
            if (!MapUtils.isEmpty(termParams)) {
                termParams.forEach((key, value) -> boolQuery.filter(QueryBuilders.termQuery(key, value)));
            }
            if (!MapUtils.isEmpty(inListTermsParams)) {
                inListTermsParams.forEach((key, value) -> {
                    // es 中的数据的字段是否在列表的值中,精确匹配
                    boolQuery.filter(QueryBuilders.termsQuery(key, value));
                });
            }

            sourceBuilder.query(boolQuery);
            if (Objects.nonNull(fetchSources)) {
                sourceBuilder.fetchSource(fetchSources, null);
            }
            searchRequest.source(sourceBuilder);
            searchRequest.scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis));

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            String scrollId = response.getScrollId();
            SearchHit[] hits = response.getHits().getHits();

            // 收集查询结果
            while (hits != null && hits.length > 0) {
                allHits.addAll(Arrays.asList(hits));

                // 使用滚动 ID 获取下一页
                response =
                        restHighLevelClient.scroll(new org.elasticsearch.action.search.SearchScrollRequest(scrollId)
                                .scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis)), RequestOptions.DEFAULT);

                // 更新滚动 ID 和结果
                scrollId = response.getScrollId();
                hits = response.getHits().getHits();
            }

            // 清除滚动上下文
            final ClearScrollRequest req = new ClearScrollRequest();
            req.addScrollId(scrollId);
            restHighLevelClient.clearScroll(req, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }
        return allHits;
    }

    /**
     * 查询 es,支持全文搜索,可以搜索时间范围,可以设置返回字段,会返回所有符合的数据
     *
     * @param indexName    索引名称
     * @param timeFiled    时间字段
     * @param startTime    开始时间,包含
     * @param endTime      结束时间,包含
     * @param params       查询限制条件,全文检索
     * @param fetchSources 返回哪些字段
     * @return 查询结果
     */
    public List<SearchHit> queryBetweenTimeStampAndKeyword(String indexName, @Nullable String timeFiled,
                                                           String startTime,
                                                           Object endTime,
                                                           @Nullable Map<String, Object> params,
                                                           @Nullable String[] fetchSources) {
        if (StringUtils.isBlank(indexName)) {
            return Arrays.asList(SearchHits.EMPTY);
        }

        List<SearchHit> allHits = new ArrayList<>();

        try {
            SearchRequest searchRequest = new SearchRequest(indexName);
            SearchSourceBuilder sourceBuilder = new SearchSourceBuilder().size(batchSearchPageSize);

            final BoolQueryBuilder[] boolQuery = {QueryBuilders.boolQuery()};
            if (!MapUtils.isEmpty(params)) {
                params.forEach((key, value) -> boolQuery[0] = boolQuery[0].must(QueryBuilders.matchQuery(key, value)));
            }

            if (StringUtils.isNotBlank(timeFiled)) {
                boolQuery[0].filter(QueryBuilders.rangeQuery(timeFiled).gte(startTime).lte(endTime));
            }
            if (Objects.nonNull(fetchSources)) {
                sourceBuilder.fetchSource(fetchSources, null);
            }
            sourceBuilder.query(boolQuery[0]);
            searchRequest.source(sourceBuilder);
            searchRequest.scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis));

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            String scrollId = response.getScrollId();
            SearchHit[] hits = response.getHits().getHits();

            // 收集查询结果
            while (hits != null && hits.length > 0) {
                allHits.addAll(Arrays.asList(hits));

                // 使用滚动 ID 获取下一页
                response =
                        restHighLevelClient.scroll(new org.elasticsearch.action.search.SearchScrollRequest(scrollId)
                                .scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis)), RequestOptions.DEFAULT);

                // 更新滚动 ID 和结果
                scrollId = response.getScrollId();
                hits = response.getHits().getHits();
            }

            // 清除滚动上下文
            final ClearScrollRequest req = new ClearScrollRequest();
            req.addScrollId(scrollId);
            restHighLevelClient.clearScroll(req, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }
        return allHits;
    }

    /**
     * 使用 sourceBuilder 查询 es,会返回所有符合的数据
     *
     * @param indexName     索引名
     * @param sourceBuilder 查询条件
     * @return 查询结果
     */
    public List<SearchHit> queryBySourceBuilder(String indexName, SearchSourceBuilder sourceBuilder) {
        if (StringUtils.isBlank(indexName)) {
            return Arrays.asList(SearchHits.EMPTY);
        }

        List<SearchHit> allHits = new ArrayList<>();

        try {
            sourceBuilder.size(batchSearchPageSize);
            SearchRequest searchRequest = new SearchRequest(indexName);
            searchRequest.source(sourceBuilder);
            searchRequest.scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis));

            SearchResponse response = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            String scrollId = response.getScrollId();
            SearchHit[] hits = response.getHits().getHits();

            // 收集查询结果
            while (hits != null && hits.length > 0) {
                allHits.addAll(Arrays.asList(hits));

                // 使用滚动 ID 获取下一页
                response =
                        restHighLevelClient.scroll(new org.elasticsearch.action.search.SearchScrollRequest(scrollId)
                                .scroll(TimeValue.timeValueMillis(scrollTimeoutInMillis)), RequestOptions.DEFAULT);

                // 更新滚动 ID 和结果
                scrollId = response.getScrollId();
                hits = response.getHits().getHits();
            }

            // 清除滚动上下文
            final ClearScrollRequest req = new ClearScrollRequest();
            req.addScrollId(scrollId);
            restHighLevelClient.clearScroll(req, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }

        return allHits;
    }

    /**
     * 根据 id 更新数据
     *
     * @param indexName 索引名
     * @param id        id
     * @param data      数据
     * @return 是否更新成功
     */
    public boolean updateById(String indexName, String id, Map<String, Object> data) {
        if (StringUtils.isBlank(indexName)) {
            return true;
        }
        if (StringUtils.isBlank(id)) {
            return true;
        }

        if (MapUtils.isEmpty(data)) {
            return false;
        }
        UpdateRequest updateRequest = new UpdateRequest(indexName, "_doc", id);
        updateRequest.doc(data, XContentType.JSON);

        try {
            UpdateResponse updateResponse = restHighLevelClient.update(updateRequest, RequestOptions.DEFAULT);
            final DocWriteResponse.Result result = updateResponse.getResult();
            return DocWriteResponse.Result.UPDATED.equals(result) || DocWriteResponse.Result.NOOP.equals(result);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
            return false;
        } finally {
            closeRestHighLevelClient();
        }
    }

    /**
     * 批量更新数据
     *
     * @param indexName 索引名
     * @param data      数据
     * @param idKeyName id 字段名
     * @return 是否更新成功
     */
    public boolean batchUpdate(String indexName, List<Map<String, Object>> data, String idKeyName) {
        if (StringUtils.isBlank(indexName)) {
            return true;
        }
        if (CollectionUtils.isEmpty(data)) {
            return false;
        }
        if (StringUtils.isBlank(idKeyName)) {
            throw new RuntimeException("idKeyName 不能为空");
        }

        BulkRequest bulkRequest = new BulkRequest();
        for (Map<String, Object> record : data) {
            UpdateRequest updateRequest = new UpdateRequest(indexName, "_doc", record.get(idKeyName).toString());
            record.remove(idKeyName);
            updateRequest.doc(record, XContentType.JSON);
            bulkRequest.add(updateRequest);
        }
        try {
            BulkResponse bulk = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            if (bulk.hasFailures()) {
                for (BulkItemResponse itemResponse : bulk) {
                    if (itemResponse.isFailed()) {
                        System.out.println("Failed operation: " + itemResponse.getFailureMessage());
                    }
                }
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }
        return true;
    }

    public boolean batchDelete(String indexName, BoolQueryBuilder boolQueryBuilder) {
        if (StringUtils.isBlank(indexName)) {
            return true;
        }

        // 初始化搜索请求
        SearchRequest searchRequest = new SearchRequest(indexName);
        SearchSourceBuilder searchSourceBuilder;
        if (Objects.nonNull(boolQueryBuilder)) {
            searchSourceBuilder = new SearchSourceBuilder()
                    .query(boolQueryBuilder)
                    .size(batchSearchPageSize);
        } else {
            searchSourceBuilder = new SearchSourceBuilder()
                    .size(batchSearchPageSize);
        }

        final TimeValue timeValue = TimeValue.timeValueMillis(scrollTimeoutInMillis);
        searchRequest.source(searchSourceBuilder);
        searchRequest.scroll(timeValue);

        try {
            // 执行初始搜索请求
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
            String scrollId = searchResponse.getScrollId();

            while (true) {
                SearchHit[] hits = searchResponse.getHits().getHits();
                if (Objects.isNull(hits) || hits.length == 0) {
                    // 没有更多的数据需要删除
                    break;
                }

                // 提取需要删除的文档 ID
                List<String> idsToDelete = new ArrayList<>();
                for (SearchHit hit : hits) {
                    idsToDelete.add(hit.getId());
                }

                // 删除符合条件的数据
                DeleteByQueryRequest deleteRequest = new DeleteByQueryRequest(indexName);
                deleteRequest.setQuery(QueryBuilders.idsQuery().addIds(idsToDelete.toArray(new String[0])));
                restHighLevelClient.deleteByQuery(deleteRequest, RequestOptions.DEFAULT);

                // 获取下一批数据
                SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
                scrollRequest.scroll(timeValue);
                searchResponse = restHighLevelClient.scroll(scrollRequest, RequestOptions.DEFAULT);
                scrollId = searchResponse.getScrollId();
            }

            // 清除滚动上下文
            ClearScrollRequest clearScrollRequest = new ClearScrollRequest();
            clearScrollRequest.addScrollId(scrollId);
            restHighLevelClient.clearScroll(clearScrollRequest, RequestOptions.DEFAULT);
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }
        return true;
    }

    /**
     * 根据 id 列表批量删除 es 中的记录
     *
     * @param indexName 索引名
     * @param ids       id 列表
     * @return 是否删除成功
     */

    public boolean batchDeleteByIds(String indexName, @Nullable List<String> ids) {
        if (StringUtils.isBlank(indexName)) {
            return true;
        }
        if (CollectionUtils.isEmpty(ids)) {
            return true;
        }
        BulkRequest bulkRequest = new BulkRequest();
        for (String docId : ids) {
            DeleteRequest req = new DeleteRequest(indexName, "_doc", docId);
            bulkRequest.add(req);
        }

        BulkResponse bulkResponse;
        try {
            bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
            return !bulkResponse.hasFailures();
        } catch (IOException e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
            return false;
        } finally {
            closeRestHighLevelClient();
        }
    }

    /**
     * 根据时间筛选访问和其他字段筛选数据,再根据指定的字段去重,返回去重后的数量(uv)
     *
     * @param indexName         索引名
     * @param timeFiled         时间字段名称
     * @param startTime         开始时间(es 中的时间字符串格式),包含
     * @param endTime           结束时间(es 中的时间字符串格式),包含
     * @param distinctFiledName 根据哪个字段去重,如果传入为 null,返回的就是记录数
     * @param termFilters       其他过滤条件, key 是字段名, value 是过滤值,精确匹配
     * @param matchFilters      其他过滤条件, key 是字段名, value 是过滤值,全文匹配
     * @return uv
     */
    public long getUniqueItemNm(String indexName, @Nullable String timeFiled, @Nullable Object startTime,
                                @Nullable Object endTime, String distinctFiledName,
                                @Nullable Map<String, Object> termFilters,
                                @Nullable Map<String, Object> matchFilters) {
        if (StringUtils.isBlank(indexName)) {
            return 0;
        }

        SearchRequest searchRequest = new SearchRequest(indexName);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.size(0);

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        // 过滤时间
        if (StringUtils.isNotBlank(timeFiled)) {
            if (Objects.nonNull(startTime)) {
                if (Objects.nonNull(endTime)) {
                    boolQuery.must(
                            QueryBuilders.rangeQuery(timeFiled).gte(startTime).lte(endTime)
                    );
                } else {
                    boolQuery.must(
                            QueryBuilders.rangeQuery(timeFiled).gte(startTime)
                    );
                }
            } else {
                if (Objects.nonNull(endTime)) {
                    boolQuery.must(
                            QueryBuilders.rangeQuery(timeFiled).lt(endTime)
                    );
                }
            }
        }
        // 其他过滤条件
        if (MapUtils.isNotEmpty(termFilters)) {
            termFilters.forEach((k, v) -> boolQuery.must(QueryBuilders.termQuery(k, v)));
        }
        if (MapUtils.isNotEmpty(matchFilters)) {
            matchFilters.forEach((k, v) -> boolQuery.must(QueryBuilders.matchQuery(k, v)));
        }

        searchSourceBuilder.query(boolQuery);
        // 聚合结果的字段名
        String aggregationResFieldName = "unique";
        if (StringUtils.isNotBlank(distinctFiledName)) {
            searchSourceBuilder.aggregation(AggregationBuilders.cardinality(aggregationResFieldName).field(distinctFiledName));
        }

        // 设置搜索源构建器
        searchRequest.source(searchSourceBuilder);

        try {
            SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

            if (StringUtils.isNotBlank(distinctFiledName)) {
                // 获取去重结果的记录数
                final Aggregations aggregations = searchResponse.getAggregations();
                return ((Cardinality) aggregations.get(aggregationResFieldName)).getValue();
            } else {
                // 返回记录数
                final SearchHits hits = searchResponse.getHits();
                if (Objects.isNull(hits)) {
                    return 0L;
                } else {
                    return Objects.isNull(hits.getTotalHits()) ? 0L : hits.getTotalHits().value;
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            log.error(Arrays.toString(e.getStackTrace()));
        } finally {
            closeRestHighLevelClient();
        }
        return 0L;
    }

    /**
     * 删除索引，类似: DELETE /products
     *
     * @param indexName 要删除的索引的索引名称
     * @return
     * @throws IOException
     */
    public boolean deleteIndex(String indexName) throws IOException {
        DeleteIndexRequest request = new DeleteIndexRequest(indexName);
        AcknowledgedResponse response = restHighLevelClient.indices().delete(request, RequestOptions.DEFAULT);
        return response.isAcknowledged();
    }
}
