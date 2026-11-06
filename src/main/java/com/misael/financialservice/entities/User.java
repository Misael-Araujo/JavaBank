package com.misael.financialservice.entities;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "user_tb", indexes = {
        @Index(name = "uk_user_email", columnList = "email", unique = true),
        @Index(name = "uk_user_document", columnList = "document", unique = true)
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "complete_name", nullable = false, length = 100)
    private String completeName;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(length = 14)
    private String document;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal wallet;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false, length = 20)
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Version
    @Column(nullable = false)
    private long version;

    protected User() {
    }

    public User(String completeName, String email, String document, String passwordHash, UserType userType) {
        this.completeName = completeName;
        this.email = email;
        this.document = document;
        this.passwordHash = passwordHash;
        this.userType = userType;
        this.wallet = BigDecimal.ZERO.setScale(2);
        this.role = UserRole.USER;
    }

    public static User administrator(String completeName, String email, String passwordHash) {
        User administrator = new User(completeName, email, null, passwordHash, UserType.COMMON);
        administrator.role = UserRole.ADMIN;
        return administrator;
    }

    public Long getId() {
        return id;
    }

    public String getCompleteName() {
        return completeName;
    }

    public String getEmail() {
        return email;
    }

    public String getDocument() {
        return document;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public BigDecimal getWallet() {
        return wallet;
    }

    public UserType getUserType() {
        return userType;
    }

    public UserRole getRole() {
        return role;
    }

    public void debit(BigDecimal amount) {
        wallet = wallet.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        wallet = wallet.add(amount);
    }
}
