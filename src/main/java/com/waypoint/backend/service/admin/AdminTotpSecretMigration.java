package com.waypoint.backend.service.admin;

import com.waypoint.backend.model.admin.AdminAccountEntity;
import com.waypoint.backend.repository.admin.AdminAccountRepository;
import com.waypoint.backend.security.admin.AdminTotpSecretCipher;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class AdminTotpSecretMigration implements ApplicationRunner {
    private final AdminAccountRepository adminAccountRepository;
    private final AdminTotpSecretCipher totpSecretCipher;

    public AdminTotpSecretMigration(
            AdminAccountRepository adminAccountRepository,
            AdminTotpSecretCipher totpSecretCipher
    ) {
        this.adminAccountRepository = adminAccountRepository;
        this.totpSecretCipher = totpSecretCipher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (AdminAccountEntity account : adminAccountRepository.findAll()) {
            String secret = account.getTotpSecret();
            if (StringUtils.hasText(secret) && totpSecretCipher.needsReencryption(secret)) {
                account.setTotpSecret(totpSecretCipher.encrypt(secret));
            }
        }
    }
}
