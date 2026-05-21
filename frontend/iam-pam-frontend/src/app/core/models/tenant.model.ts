export type ServiceType = 'PAM' | 'IAM' | 'SSO' | 'MFA' | 'AUDIT';

export interface TenantCreateRequest {
  tenantId: string;
  tenantName: string;
  maxUsers: number;
  maxResources?: number;
  services: ServiceType[];
}

export interface TenantUpdateRequest {
  tenantName?: string;
  maxUsers?: number;
  maxResources?: number;
  services?: ServiceType[];
  isActive?: boolean;
}

export interface TenantDomainConfigRequest {
  domain: string;
}

export interface TenantResponse {
  id: number;
  tenantId: string;
  tenantName: string;
  domain: string;
  domainConfigured: boolean;
  maxUsers: number;
  currentUserCount: number;
  maxResources: number | null;
  currentResources: number;
  auditLogRetentionDays: number;
  isActive: boolean;
  adminUsername: string | null;
  services: string[];
  createdAt: string;
  updatedAt: string;
}
