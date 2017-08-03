package test;

import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectQuery;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.sql.dialect.mysql.parser.MySqlStatementParser;
import io.mycat.catlets.JoinParser;

/**
 * Created by zhangyq on 2016-10-06.
 */
public class ShareJoinTest {
	private JoinParser joinParser;

	public void joinQuery(String sql) {
		MySqlStatementParser parser = new MySqlStatementParser(sql);
		SQLStatement statement = parser.parseStatement();
		if(statement instanceof SQLSelectStatement) {
			SQLSelectStatement st=(SQLSelectStatement)statement;
			SQLSelectQuery sqlSelectQuery =st.getSelect().getQuery();
			if(sqlSelectQuery instanceof MySqlSelectQueryBlock) {
				MySqlSelectQueryBlock mysqlSelectQuery = (MySqlSelectQueryBlock)st.getSelect().getQuery();
				joinParser=new JoinParser(mysqlSelectQuery, sql);
				joinParser.parser();
			}
		}

		String sql1 = joinParser.getSql();
		System.out.println("sql1 = " + sql1);

		System.out.println(".THE END.");
	}

	public static void main(String args[]) {
		String sql = "select a.id, b.id as hostid, b.host_ip from hps_cfg_label  a  join  hps_host b on b.id=a.id;";
		new ShareJoinTest().joinQuery(sql);

	}
}
