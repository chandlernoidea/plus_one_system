package com.nail.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 单人美甲工作室版启动类。
 *
 * <p>只保留账号鉴权 + solo 业务两块；多角色的排班 / 预约 / 评审等模块在 main 分支。
 */
@SpringBootApplication
public class NailApplication {

    public static void main(String[] args) {
        SpringApplication.run(NailApplication.class, args);
    }
}
