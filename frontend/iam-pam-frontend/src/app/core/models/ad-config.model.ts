export interface AdConfigSaveRequest {
  serverUrl: string;
  port: number;
  useSsl: boolean;
  bindDn: string;
  bindPassword: string;
  userSearchBase: string;
  userSearchFilter?: string;
  usernameAttribute?: string;
  emailAttribute?: string;
  firstnameAttribute?: string;
  lastnameAttribute?: string;
}

export interface AdConfigResponse {
  id: number;
  serverUrl: string;
  port: number;
  useSsl: boolean;
  bindDn: string;
  userSearchBase: string;
  userSearchFilter: string;
  usernameAttribute: string;
  emailAttribute: string;
  firstnameAttribute: string;
  lastnameAttribute: string;
  configuredAt: string;
}

export interface AdUser {
  username: string;
  email: string;
  firstName: string;
  lastName: string;
  distinguishedName: string;
}

export interface ImportAdUserRequest {
  username: string;
  email?: string;
  firstName?: string;
  lastName?: string;
  roles: string[];
}
