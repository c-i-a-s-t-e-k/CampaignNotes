package model;

public class User {
    private String id;
    private String email;
    private String passwordHash;
    private long createdAt;
    private long updatedAt;
    private boolean isActive = true;
    private Long deletedAt;
    private boolean emailVerified = false;
    private String emailVerificationToken;
    private Long emailVerificationExpiresAt;
    private Long lastLoginAt;
    private boolean isAdmin = false;
    
    public User() {}
    
    public User(String id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = System.currentTimeMillis() / 1000L; // Unix timestamp
        this.updatedAt = this.createdAt;
    }
    
    public User(String id, String email, String passwordHash, long createdAt, long updatedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }
    
    public Long getDeletedAt() {
        return deletedAt;
    }
    
    public void setDeletedAt(Long deletedAt) {
        this.deletedAt = deletedAt;
    }
    
    public boolean isEmailVerified() {
        return emailVerified;
    }
    
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }
    
    public String getEmailVerificationToken() {
        return emailVerificationToken;
    }
    
    public void setEmailVerificationToken(String emailVerificationToken) {
        this.emailVerificationToken = emailVerificationToken;
    }
    
    public Long getEmailVerificationExpiresAt() {
        return emailVerificationExpiresAt;
    }
    
    public void setEmailVerificationExpiresAt(Long emailVerificationExpiresAt) {
        this.emailVerificationExpiresAt = emailVerificationExpiresAt;
    }
    
    public Long getLastLoginAt() {
        return lastLoginAt;
    }
    
    public void setLastLoginAt(Long lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }
    
    public boolean isAdmin() {
        return isAdmin;
    }
    
    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }
    
    @Override
    public String toString() {
        return "User{" +
                "id='" + id + '\'' +
                ", email='" + email + '\'' +
                ", isActive=" + isActive +
                ", emailVerified=" + emailVerified +
                ", isAdmin=" + isAdmin +
                '}';
    }
}
