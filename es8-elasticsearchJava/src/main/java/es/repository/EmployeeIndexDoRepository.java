package es.repository;

import es.index.EmployeeIndexDo;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @author jxd
 * {@code @date} 2024/12/22 14:48
 */
public interface EmployeeIndexDoRepository extends ElasticsearchRepository<EmployeeIndexDo, Long> {
}
