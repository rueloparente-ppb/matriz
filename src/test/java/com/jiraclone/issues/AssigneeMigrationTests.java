package com.jiraclone.issues;

import static org.assertj.core.api.Assertions.assertThat;

import com.jiraclone.AbstractIntegrationTest;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AssigneeMigrationTests extends AbstractIntegrationTest {
	@Autowired DataSource dataSource;
	@Autowired JdbcTemplate jdbc;

	@Test
	void v8PreservesExistingAssignments() {
		String schema = "migration_" + UUID.randomUUID().toString().replace("-", "");
		Flyway.configure().dataSource(dataSource).schemas(schema).target("7")
				.locations("classpath:db/migration").load().migrate();
		jdbc.update("insert into " + schema + ".project(key,name,created_by) values('MIG','Migration', "
				+ "(select id from " + schema + ".app_user where username='alice'))");
		jdbc.update("insert into " + schema + ".issue(project_id,number,title,reporter_id,assignee_id) "
				+ "select p.id,1,'Old assignee',u.id,u.id from " + schema + ".project p "
				+ "join " + schema + ".app_user u on u.username='bob' where p.key='MIG'");
		Flyway.configure().dataSource(dataSource).schemas(schema).locations("classpath:db/migration").load().migrate();
		assertThat(jdbc.queryForObject("select count(*) from " + schema + ".issue_assignee a join " + schema
				+ ".app_user u on u.id=a.user_id where u.username='bob'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? "
				+ "and table_name='issue' and column_name='assignee_id'", Integer.class, schema)).isZero();
	}
}
