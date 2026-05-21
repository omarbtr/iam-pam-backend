export type RequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REVOKED' | 'EXPIRED';

export interface AccessRequestCreate {
  resourceId: number;
  justification?: string;
  durationHours?: number;
}

export interface AccessRequestReview {
  status: RequestStatus;
  comment?: string;
}

export interface AccessRequestResponse {
  id: number;
  requesterUsername: string;
  tenantId: string;
  resourceId: number;
  resourceName: string;
  resourceType?: string;
  justification?: string;
  status: RequestStatus;
  durationHours: number;
  reviewedBy?: string;
  reviewComment?: string;
  requestedAt: string;
  reviewedAt?: string;
  expiresAt?: string;
  firstAccessAt?: string;
  remainingSeconds?: number;
  isActive: boolean;
}
