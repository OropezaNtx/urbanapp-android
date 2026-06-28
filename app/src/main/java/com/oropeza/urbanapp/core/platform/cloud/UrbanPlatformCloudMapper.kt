package com.oropeza.urbanapp.core.platform.cloud

object UrbanPlatformCloudMapper {

    fun toDomain(dto: UrbanOrganizationDto): UrbanOrganization {
        return UrbanOrganization(
            workspaceId = dto.workspaceId,
            name = dto.name,
            status = dto.status,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }

    fun toDto(domain: UrbanOrganization): UrbanOrganizationDto {
        return UrbanOrganizationDto(
            workspaceId = domain.workspaceId,
            name = domain.name,
            status = domain.status,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(dto: UrbanProjectDto): UrbanProject {
        return UrbanProject(
            projectId = dto.projectId,
            workspaceId = dto.workspaceId,
            name = dto.name,
            description = dto.description,
            status = dto.status,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }

    fun toDto(domain: UrbanProject): UrbanProjectDto {
        return UrbanProjectDto(
            projectId = domain.projectId,
            workspaceId = domain.workspaceId,
            name = domain.name,
            description = domain.description,
            status = domain.status,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(dto: UrbanUserDto): UrbanUser {
        return UrbanUser(
            userId = dto.userId,
            email = dto.email,
            firstName = dto.firstName,
            lastName = dto.lastName,
            workspaceId = dto.workspaceId,
            roleId = dto.roleId,
            status = dto.status,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }

    fun toDto(domain: UrbanUser): UrbanUserDto {
        return UrbanUserDto(
            userId = domain.userId,
            email = domain.email,
            firstName = domain.firstName,
            lastName = domain.lastName,
            workspaceId = domain.workspaceId,
            roleId = domain.roleId,
            status = domain.status,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(dto: UrbanRoleDto): UrbanRole {
        return UrbanRole(
            roleId = dto.roleId,
            workspaceId = dto.workspaceId,
            name = dto.name,
            description = dto.description,
            permissionIds = dto.permissionIds,
            status = dto.status,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }

    fun toDto(domain: UrbanRole): UrbanRoleDto {
        return UrbanRoleDto(
            roleId = domain.roleId,
            workspaceId = domain.workspaceId,
            name = domain.name,
            description = domain.description,
            permissionIds = domain.permissionIds,
            status = domain.status,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }

    fun toDomain(dto: UrbanPermissionDto): UrbanPermission {
        return UrbanPermission(
            permissionId = dto.permissionId,
            name = dto.name,
            description = dto.description,
            module = dto.module,
            action = dto.action,
            status = dto.status,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt
        )
    }

    fun toDto(domain: UrbanPermission): UrbanPermissionDto {
        return UrbanPermissionDto(
            permissionId = domain.permissionId,
            name = domain.name,
            description = domain.description,
            module = domain.module,
            action = domain.action,
            status = domain.status,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
}
