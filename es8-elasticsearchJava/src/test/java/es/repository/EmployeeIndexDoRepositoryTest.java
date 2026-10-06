package es.repository;

import es.index.EmployeeIndexDo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author jxd
 * {@code @date} 2024/12/22 14:51
 */
@SpringBootTest
class EmployeeIndexDoRepositoryTest {

    @Autowired
    private EmployeeIndexDoRepository employeeIndexDoRepository;

    @Test
    public void testInsertData() throws ParseException {
        List<EmployeeIndexDo> list = new ArrayList<>();
        // @formatter:off
//        list.add(new EmployeeIndexDo(1011L, "2001", "张三", "zhangsan", "Java", 1, 19, new BigDecimal("12500.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1012L, "2002", "李四", "lisi", "PHP", 1, 18, new BigDecimal("11600.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1013L, "2003", "王五", "wangwu", "C++", 1, 20, new BigDecimal("9900.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1014L, "2004", "赵六", "zhaoliu", "Java Leader", 1, 20, new BigDecimal("20000.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1015L, "2005", "小五", "xiaowu", "H5", 1, 17, new BigDecimal("10600.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1016L, "2006", "小六", "xaioliu", "web", 1, 20, new BigDecimal("12600.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1017L, "2007", "小七", "xiaoqi", "app", 1, 22, new BigDecimal("20000.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1018L, "2008", "小八", "xaioba", "Java", 1, 21, new BigDecimal("11000.01"), new Date(), "备注"));
//        list.add(new EmployeeIndexDo(1019L, "2009", "小九", "xiaojiu", "Java", 1, 20, new BigDecimal("14000.01"), new Date(), "备注"));
        list.add(new EmployeeIndexDo(1025L, "2010", "大十", "dashi", "Java", 1, 20, new BigDecimal("13000.01"), new Date(), "备注"));
        employeeIndexDoRepository.saveAll(list);
        // @formatter:on
    }

    @Test
    public void selectAll() {
        Iterable<EmployeeIndexDo> all = employeeIndexDoRepository.findAll();
        while (all.iterator().hasNext()) {
            System.out.println(all.iterator().next());
        }
    }
}
