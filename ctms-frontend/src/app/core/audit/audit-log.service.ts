import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface AuditLogResponse {
  id: number;
  entityName: string;
  entityId: string;
  action: string;
  performedByUsername: string | null;
  performedAt: string;
  beforeValue: string | null;
  afterValue: string | null;
  reason: string | null;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ESignatureResponse {
  id: number;
  signedByUsername: string;
  entityName: string;
  entityId: string;
  reason: string;
  signedAt: string;
}

export interface TraceabilityResponse {
  entityName: string;
  entityId: string;
  auditTrail: AuditLogResponse[];
  signatures: ESignatureResponse[];
}

@Injectable({ providedIn: 'root' })
export class AuditLogService {
  constructor(private readonly http: HttpClient) {}

  search(entityName: string, entityId: string, page = 0, size = 50): Observable<Page<AuditLogResponse>> {
    const params: Record<string, string | number> = { page, size };
    if (entityName) {
      params['entityName'] = entityName;
    }
    if (entityId) {
      params['entityId'] = entityId;
    }
    return this.http.get<Page<AuditLogResponse>>('/api/audit-logs', { params });
  }

  exportCsv(entityName: string, entityId: string): Observable<string> {
    const params: Record<string, string> = {};
    if (entityName) {
      params['entityName'] = entityName;
    }
    if (entityId) {
      params['entityId'] = entityId;
    }
    return this.http.get('/api/audit-logs/export', { params, responseType: 'text' });
  }

  traceability(entityName: string, entityId: string): Observable<TraceabilityResponse> {
    return this.http.get<TraceabilityResponse>(`/api/audit-logs/traceability/${entityName}/${entityId}`);
  }
}
