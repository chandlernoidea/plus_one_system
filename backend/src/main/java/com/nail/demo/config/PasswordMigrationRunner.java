package com.nail.demo.config;

import com.nail.demo.entity.SysUser;
import com.nail.demo.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PasswordMigrationRunner implements CommandLineRunner {

    private final SysUserRepository sysUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        int migratedCount = 0;
        for (SysUser user : sysUserRepository.findAll()) {
            String password = user.getPassword();
            if (isBcrypt(password)) {
                continue;
            }
            user.setPassword(passwordEncoder.encode(password == null ? "" : password));
            sysUserRepository.save(user);
            migratedCount++;
        }
        log.info("密码迁移完成，共迁移 {} 条", migratedCount);
    }

    private boolean isBcrypt(String password) {
        return password != null
                && (password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$"));
    }
}
