package io.mycat.config;

import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Strings;

import io.mycat.config.loader.zookeeper.ZookeeperLoader;
import io.mycat.config.loader.zookeeper.ZookeeperSaver;

import org.slf4j.Logger; import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ZkConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(ZkConfig.class);
    private static final String ZK_CONFIG_FILE_NAME = "/myid.properties";

    private ZkConfig() {
    }

    public synchronized static ZkConfig instance() {
        return new ZkConfig();
    }

    private Properties zkProperties=loadMyid();

    public String getZkURL()
    {
        return zkProperties==null?null:zkProperties.getProperty("zkURL")  ;
    }
    public void initZk() {
        Properties pros = zkProperties;

        //disable load from zookeeper,use local file.
        if (pros == null) {
            LOGGER.trace("use local configuration to startup");
            return;
        }
        if (Boolean.parseBoolean(pros.getProperty("loadZk"))) {
            try {
                // xman. 从 zookeeper 服务器上下载配置
                JSONObject jsonObject = new ZookeeperLoader().loadConfig(pros);
                // xman. 将配置写入本地 xml 文件，用于启动 mycat 时读取。
                new ZookeeperSaver().saveConfig(jsonObject);
                LOGGER.trace("use zookeeper configuration to startup");
            } catch (Exception e) {
                LOGGER.error("fail to load configuration form zookeeper,using local file to run!", e);
            }
        }

    }
    public Properties getZkProperties()
    {
       return zkProperties;
    }

    // xman. 加载 myid.properties 配置文件
    private Properties loadMyid() {
        Properties pros = new Properties();

        try (InputStream configIS = ZookeeperLoader.class
            .getResourceAsStream(ZK_CONFIG_FILE_NAME)) {
            if (configIS == null) {
                //file is not exist ,so ues local file.
                return null;
            }

            pros.load(configIS);
        } catch (IOException e) {
            throw new RuntimeException("can't find myid properties file : " + ZK_CONFIG_FILE_NAME);
        }


            //validate
            String zkURL = pros.getProperty("zkURL");
            String myid = pros.getProperty("myid");

            if (Strings.isNullOrEmpty(zkURL) || Strings.isNullOrEmpty(myid)) {
                throw new RuntimeException("zkURL and myid must not be null or empty!");
            }
            return pros;


    }

    public static void main(String[] args) {
       String zk= ZkConfig.instance().getZkURL();
        System.out.println(zk);
    }

}
