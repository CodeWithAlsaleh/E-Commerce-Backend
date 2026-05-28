package com.sivan.ecommerce.repository.role;

import com.sivan.ecommerce.entity.role.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {
}
