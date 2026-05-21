export type MfaMethod = 'TOTP' | 'EMAIL' | 'SMS' | 'WHATSAPP';

export interface TenantMfaConfig {
  tenantId: string;
  totpEnabled: boolean;
  emailOtpEnabled: boolean;
  smsOtpEnabled: boolean;
  whatsappOtpEnabled: boolean;
  mfaRequired: boolean;
}

export interface MfaStatus {
  enrolled: boolean;
  method?: MfaMethod;
  enrolledAt?: string;
}

export interface InitEnrollResponse {
  method: MfaMethod;
  totpSecret?: string;
  totpQrUri?: string;
  message: string;
}

export interface VerifyResponse {
  success: boolean;
  message: string;
}
