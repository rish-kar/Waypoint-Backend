package com.waypoint.backend.service.auth;

import com.waypoint.backend.repository.auth.MicrosoftExchangeCodeRepository;
import com.waypoint.backend.repository.auth.MicrosoftOAuthTransactionRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class MicrosoftOAuthCleanupService {
    private final MicrosoftOAuthTransactionRepository transactionRepository;
    private final MicrosoftExchangeCodeRepository exchangeCodeRepository;

    public MicrosoftOAuthCleanupService(MicrosoftOAuthTransactionRepository transactionRepository,
                                        MicrosoftExchangeCodeRepository exchangeCodeRepository) {
        this.transactionRepository = transactionRepository;
        this.exchangeCodeRepository = exchangeCodeRepository;
    }

    @Scheduled(fixedDelayString = "${microsoft.cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredOAuthState() {
        Instant now = Instant.now();
        exchangeCodeRepository.deleteByExpiresAtBefore(now);
        transactionRepository.deleteByExpiresAtBefore(now);
    }
}
