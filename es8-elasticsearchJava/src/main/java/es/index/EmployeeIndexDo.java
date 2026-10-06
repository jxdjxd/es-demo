package es.index;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 员工索引模型
 *
 * @author jxd
 * {@code @date} 2024/12/22 14:46
 */
@Document(indexName = "employee")
@Setting(shards = 5, replicas = 1)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmployeeIndexDo {

    /**
     * 这个字段会被认为是索引的 _id 字段
     */
    @Id
    private Long id;

    /**
     * 工号
     */
    @Field(name = "job_no")
    @JsonProperty("job_no")
    private String jobNo;

    /**
     * 姓名
     */
    @Field(name = "name")
    private String name;

    /**
     * 英文名
     */
    @Field(name = "english_name")
    @JSONField(name = "english_name")
    @JsonProperty("english_name")
    private String englishName;

    /**
     * 工作岗位
     */
    private String job;

    /**
     * 性别
     */
    private Integer sex;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 薪资
     */
    private BigDecimal salary;

    /**
     * 入职时间
     */
    @Field(name = "job_day", type = FieldType.Date, pattern = "yyyy-MM-dd HH:mm:ss")
    @JSONField(name = "job_day")
    @JsonProperty("job_day")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date jobDay;

    /**
     * 备注
     */
    private String remark;
}
