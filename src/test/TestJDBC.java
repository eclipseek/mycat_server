package test;

import java.sql.*;

/**
 * Created with IntelliJ IDEA.
 * User: zhangyq
 * Date: 2017-07-26
 * Time: 15:49
 * Description:
 */
public class TestJDBC {

    public static void main(String args[]) {
        testDriver();

//        testManager();
    }

    public static void testManager() {
        String driver = "com.mysql.jdbc.Driver";
        String url = "jdbc:mysql://localhost:17777";
        String username = "test";
        String password = "test";
        Connection conn = null;
        try {
            Class.forName(driver); //classLoader,加载对应驱动
            conn = (Connection) DriverManager.getConnection(url, username, password);

            ResultSet rs = conn.prepareStatement("show @@sql.slow").executeQuery();
            while (rs.next()) {
                String cmd = rs.getString("USER");
                String des = rs.getString("DATASOURCE");
                System.out.println(cmd + "\t\t\t\t\t\t" + des);
            }

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void testDriver() {
        String driver = "com.mysql.jdbc.Driver";
        String url = "jdbc:mysql://localhost:17777";
        String username = "test";
        String password = "test";
        Connection conn = null;
        try {
            Class.forName(driver); //classLoader,加载对应驱动
            conn = (Connection) DriverManager.getConnection(url, username, password);

            conn.prepareStatement("CREATE TABLE ddltest (cola varchar(255) DEFAULT NULL)").execute();

            System.out.println("=====");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
