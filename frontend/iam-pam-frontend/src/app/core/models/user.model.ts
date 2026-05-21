export interface DirectoryUser {
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
}

export interface TenantUser {
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
}

export interface UserImportRequest {
  username: string;
  roles: string[];
}
