package com.gestor.model;

import java.time.LocalDateTime;

public record BusinessUser(
        long id,
        long businessId,
        String name,
        String email,
        String role,
        boolean active,
        LocalDateTime createdAt
) {
    public long getId() { return id; }
    public long getBusinessId() { return businessId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean getActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isOwner() { return "OWNER".equals(role); }
    public boolean isManager() { return "MANAGER".equals(role); }
    public boolean getOwner() { return isOwner(); }
    public boolean getManager() { return isManager(); }
}
