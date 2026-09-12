package com.ayun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AyunBlog 项目启动类
 */
@SpringBootApplication
public class AyunBlogSpringboot3Application {

    public static void main(String[] args) {
        // 设置属性以允许访问外部 DTD
//        System.setProperty("javax.xml.accessExternalDTD", "all");
        SpringApplication.run(AyunBlogSpringboot3Application.class, args);
    }

}
