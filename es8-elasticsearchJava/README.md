# 项目
本示例基于 Spring Boot 3.3.8，引入 spring-boot-starter-data-elasticsearch，通过 elasticsearch-java 演示对 Elasticsearch 8.xx 的常用操作。

# 实验流程
1. 已经安装了 es，推荐 es 大版本等于 8，且已获得 es 的连接信息并且能连接到 es
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
