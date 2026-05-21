export interface AuditLogResponse {
  id: number;
  username: string;
  tenantId: string;
  action: string;
  resource?: string;
  resourceName?: string;
  details?: string;
  ipAddress?: string;
  result?: string;
  timestamp: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}
