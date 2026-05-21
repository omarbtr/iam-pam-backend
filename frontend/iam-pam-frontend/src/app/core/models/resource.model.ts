export type ResourceType = 'SSH' | 'RDP' | 'DATABASE' | 'WEB' | 'API';

export interface ResourceRequest {
  name: string;
  type: ResourceType;
  host: string;
  port?: number;
  description?: string;
  credentialUsername?: string;
  credentialPassword?: string;
  credentialPrivateKey?: string;
}

export interface ResourceResponse {
  id: number;
  name: string;
  type: ResourceType;
  host: string;
  port?: number;
  description?: string;
  tenantId: string;
  isActive: boolean;
  createdAt: string;
  credentialUsername?: string;
  hasPassword?: boolean;
  hasPrivateKey?: boolean;
}
