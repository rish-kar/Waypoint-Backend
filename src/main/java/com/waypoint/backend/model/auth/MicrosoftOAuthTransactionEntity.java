package com.waypoint.backend.model.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "microsoft_oauth_transactions")
public class MicrosoftOAuthTransactionEntity {
    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String stateHash;

    @Column(nullable = false, length = 1024)
    private String codeVerifierCiphertext;

    @Column(nullable = false, length = 2048)
    private String extensionRedirectUri;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getStateHash() { return stateHash; }
    public void setStateHash(String stateHash) { this.stateHash = stateHash; }
    public String getCodeVerifierCiphertext() { return codeVerifierCiphertext; }
    public void setCodeVerifierCiphertext(String codeVerifierCiphertext) { this.codeVerifierCiphertext = codeVerifierCiphertext; }
    public String getExtensionRedirectUri() { return extensionRedirectUri; }
    public void setExtensionRedirectUri(String extensionRedirectUri) { this.extensionRedirectUri = extensionRedirectUri; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
