# 项目
本示例基于 Spring Boot 2.7.9，引入 spring-boot-starter-data-elasticsearch，通过 RestHighLevelClient 演示对 Elasticsearch 7.15 及更早版本的常用操作。

# 实验流程
1. 已经安装了 es，推荐 es 版本小于等于 Elasticsearch 7.15，且已获得 es 的连接信息并且能连接到 es
2. 在 es 中创建对应的索引
```json
PUT /employee
{
    "settings": {
        "number_of_shards": 5,
        "number_of_replicas": 1
    },
    "mappings": {
        "properties": {
            "job_no": {
                "type": "keyword"
            },
            "name": {
                "type": "keyword"
            },
            "english_name": {
                "type": "keyword"
            },
            "job": {
                "type": "keyword"
            },
            "sex": {
                "type": "integer"
            },
            "age": {
                "type": "integer"
            },
            "salary": {
                "type": "long"
            },
            "job_day": {
                "type": "date",
                "format": "yyyy-MM-dd HH:mm:ss"
            },
            "remark": {
                "type": "keyword"
            }
        }
    }
}
```
2. 修改 application.yml 文件中关于 es 的相关配置
3. 跑 EsCommonOperationTest 中相关的测试用例，查看效果

# 注意
RestHighLevelClient 已经不再推荐使用了。
RestHighLevelClient 的淘汰是一个渐进的过程，并非在 ES 8 才突然发生：
2021 年 (v7.15): 官方正式宣布弃用 RestHighLevelClient，并同时推出了新的 Elasticsearch Java API Client (elasticsearch-java)。
2021 年 (v7.16): 该客户端停止功能更新，仅修复关键 Bug。
2022 年 (v7.17): 进入最低限度的安全与兼容性维护阶段。
2023 年 (v8.x): 在 ES 8.x 中不再推荐使用，官方主推 elasticsearch-java。RestHighLevelClient 实际上已处于“彻底废弃”的状态，不再随主线演进。
